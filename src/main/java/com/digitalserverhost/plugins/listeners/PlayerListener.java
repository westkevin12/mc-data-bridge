package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.google.common.io.ByteStreams;
import com.google.common.io.ByteArrayDataInput;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerListener implements Listener, PluginMessageListener {

    private static final String WARNING_BORDER = "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!";
    private static final String PLAYER_PREFIX = "Player ";

    private final DatabaseManager databaseManager;
    private final MCDataBridge plugin;
    private final Gson gson;
    private final Map<UUID, PlayerData> loadingCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savingPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> switchingPlayers = new ConcurrentHashMap<>();

    public PlayerListener(DatabaseManager databaseManager, MCDataBridge plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        this.gson = MCDataBridge.getGson();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("mc-data-bridge:main")) {
            return;
        }

        if (message == null)
            return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        if (subchannel.equals("SaveAndRelease")) {
            String uuidStr = in.readUTF();
            UUID uuid = UUID.fromString(uuidStr);
            Player playerToSave = Bukkit.getPlayer(uuid);

            if (playerToSave != null) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().log(Level.INFO, "Received ''SaveAndRelease'' request for {0}. Triggering save.", playerToSave.getName());
                }

                switchingPlayers.put(uuid, true);
                savePlayerDataAndReleaseLock(playerToSave);
            }
        } else if (subchannel.equals("ForceUnlock")) {
            String uuidStr = in.readUTF();
            UUID uuid = UUID.fromString(uuidStr);
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "Received ''ForceUnlock'' request for UUID: {0}", uuid);
            }
            databaseManager.releaseLock(uuid);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        String serverId = plugin.getServerId();

        if (plugin.isServerBlacklisted(serverId)) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "Server {0} is on the blacklist. Skipping sync.", serverId);
            }
            return;
        }

        try {
            if (!waitForLock(uuid, name, serverId)) {
                String kickMsg = "§c[DataBridge] Your data is still being saved by another server.\n§7Please wait a few seconds and try again.";
                com.digitalserverhost.plugins.utils.SchedulerUtils.getBridge().disallowPlayer(
                    event, AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMsg
                );
                plugin.getLogger().log(Level.WARNING, "{0}{1} was disallowed due to a persistent data lock.", new Object[]{PLAYER_PREFIX, name});
                return;
            }

            handleIdentityCollision(uuid, name);
            verifyIdentityRecord(uuid, name);

            PlayerData data = loadPlayerData(uuid, name);
            if (data != null) {
                loadingCache.put(uuid, data);
            }
        } catch (PlayerData.ItemDeserializationException e) {
            plugin.getLogger().log(Level.SEVERE, "A critical error occurred while deserializing inventory for player {0}. {1}", new Object[]{name, e.getMessage()});
            String kickMsg = "§c[DataBridge] A critical error occurred while deserializing your inventory. Please contact an administrator.";
            com.digitalserverhost.plugins.utils.SchedulerUtils.getBridge().disallowPlayer(event, AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMsg);
            databaseManager.releaseLock(uuid, serverId);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "Pre-login thread was interrupted for player {0}", name);
            databaseManager.releaseLock(uuid, serverId);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Critical error during pre-login for player {0}: {1}", new Object[]{name, e.getMessage()});
            String kickMsg = "§c[DataBridge] Could not process your player data. Please relog.";
            com.digitalserverhost.plugins.utils.SchedulerUtils.getBridge().disallowPlayer(event, AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMsg);
            databaseManager.releaseLock(uuid, serverId);
        }
    }

    private boolean waitForLock(UUID uuid, String name, String serverId) throws InterruptedException, SQLException {
        int attempts = 0;
        final int MAX_ATTEMPTS = 20;
        final long WAIT_TIME_MS = 500;

        while (attempts < MAX_ATTEMPTS) {
            if (databaseManager.acquireLock(uuid, serverId)) {
                return true;
            }

            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "{0}{1}''s data is locked. Waiting... (Attempt {2})", new Object[]{PLAYER_PREFIX, name, attempts + 1});
            }
            Thread.sleep(WAIT_TIME_MS);
            attempts++;
        }
        return isLockOwner(uuid, serverId);
    }

    private void handleIdentityCollision(UUID uuid, String name) {
        UUID nameOwnerUuid = databaseManager.getUuidByName(name);
        if (nameOwnerUuid == null || nameOwnerUuid.equals(uuid)) {
            return;
        }

        boolean autoMigrated = false;
        if (plugin.isAutoMigrateFastLogin() && "HYBRID".equals(plugin.getIdentityMode())) {
            autoMigrated = attemptFastLoginMigration(name, uuid, nameOwnerUuid);
        }

        if (!autoMigrated) {
            if ("PREMIUM".equals(plugin.getIdentityMode())) {
                plugin.getLogger().log(Level.SEVERE, WARNING_BORDER);
                plugin.getLogger().log(Level.SEVERE, "!!! IDENTITY COLLISION (PREMIUM): {0} joined with new UUID !!!", name);
                plugin.getLogger().log(Level.SEVERE, "!!! Current UUID:  {0}", uuid);
                plugin.getLogger().log(Level.SEVERE, "!!! Previous UUID: {0}", nameOwnerUuid);
                plugin.getLogger().log(Level.SEVERE, "!!! Join ALLOWED, but data load is BLOCKED for safety. !!!");
                plugin.getLogger().log(Level.SEVERE, "!!! Use ''/databridge migrate'' to unblock this user.    !!!");
                plugin.getLogger().log(Level.SEVERE, WARNING_BORDER);
            } else if (plugin.getConfig().getBoolean("security.log-uuid-mismatches", true)) {
                plugin.getLogger().log(Level.WARNING, WARNING_BORDER);
                plugin.getLogger().log(Level.WARNING, "!!! IDENTITY COLLISION: Player {0} joined with new UUID !!!", name);
                plugin.getLogger().log(Level.WARNING, "!!! Current UUID:  {0}", uuid);
                plugin.getLogger().log(Level.WARNING, "!!! Previous UUID: {0}", nameOwnerUuid);
                plugin.getLogger().log(Level.WARNING, "!!! This is common in Cracked -> Premium transitions.   !!!");
                plugin.getLogger().log(Level.WARNING, "!!! Data is SECURE (tied to UUID), but sync is paused. !!!");
                plugin.getLogger().log(Level.WARNING, "!!! Use ''/databridge migrate'' to transfer data safely. !!!");
                plugin.getLogger().log(Level.WARNING, WARNING_BORDER);
            }
        }
    }

    private boolean attemptFastLoginMigration(String name, UUID uuid, UUID nameOwnerUuid) {
        try {
            org.bukkit.plugin.Plugin fastLoginPlugin = Bukkit.getPluginManager().getPlugin("FastLogin");
            if (fastLoginPlugin != null && fastLoginPlugin.isEnabled()) {
                Object status = fastLoginPlugin.getClass().getMethod("getStatus", UUID.class).invoke(fastLoginPlugin, uuid);
                boolean isPremium = (boolean) status.getClass().getMethod("isPremium").invoke(status);
                
                if (isPremium) {
                    plugin.getLogger().log(Level.INFO, "FastLogin verified Premium status for {0}. Attempting auto-migration from {1} to {2}", new Object[]{name, nameOwnerUuid, uuid});
                    if (databaseManager.migrateData(nameOwnerUuid, uuid)) {
                        plugin.getLogger().log(Level.INFO, "Auto-migration successful for {0}", name);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.WARNING, "FastLogin integration check failed: {0}", e.getMessage());
            }
        }
        return false;
    }

    private void verifyIdentityRecord(UUID uuid, String name) {
        DatabaseManager.IdentityRecord identityRecord = databaseManager.getIdentityRecord(uuid);
        if (identityRecord != null) {
            String currentHash = com.digitalserverhost.plugins.utils.HashUtils.generateIdentityHash(name, uuid, plugin.getSecuritySeed());
            String storedHash = identityRecord.getIdentityHash();
            
            if (storedHash != null && !currentHash.equals(storedHash) && plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "Verified name change or seed update for {0}: {1} -> {2}", 
                    new Object[]{uuid, identityRecord.getLastKnownName(), name});
            }
        }
    }

    private boolean isLockOwner(UUID uuid, String serverId) {
        try (Connection connection = databaseManager.getConnection()) {
            String query = "SELECT locking_server FROM " + databaseManager.getTableName() + " WHERE uuid = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(query)) {
                checkStmt.setString(1, uuid.toString());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return serverId.equals(rs.getString("locking_server"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check lock owner for {0}: {1}", new Object[]{uuid, e.getMessage()});
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverId = plugin.getServerId();

        if (plugin.isServerBlacklisted(serverId) || plugin.isWorldBlacklisted(player.getWorld().getName())) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "{0}{1} joined a blacklisted server/world. Sync disabled.", new Object[]{PLAYER_PREFIX, player.getName()});
            }
            return;
        }

        switchingPlayers.remove(uuid);
        savingPlayers.remove(player.getUniqueId());
        PlayerData data = loadingCache.remove(player.getUniqueId());
        if (data != null) {
            applyPlayerData(player, data);
        } else {
            plugin.getLogger().log(Level.INFO, "{0}{1} joining with fresh profile. Lock will be released on quit.", new Object[]{PLAYER_PREFIX, player.getName()});
        }

        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(plugin, () -> 
            databaseManager.updateLastKnownName(uuid, player.getName(), plugin.getSecuritySeed())
        );

        long heartbeatTicks = plugin.getLockHeartbeatSeconds() * 20L;
        com.digitalserverhost.plugins.utils.SchedulerUtils.getScheduler().startHeartbeat(
            plugin, player, uuid, serverId, heartbeatTicks,
            targetUuid -> databaseManager.updateLock(targetUuid, serverId)
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        if (switchingPlayers.remove(event.getPlayer().getUniqueId()) != null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "PlayerKickEvent for {0} ignored, handled by ''SaveAndRelease''.", event.getPlayer().getName());
            }
            return;
        }
        savePlayerDataAndReleaseLock(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (switchingPlayers.remove(event.getPlayer().getUniqueId()) != null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "PlayerQuitEvent for {0} ignored, handled by ''SaveAndRelease''.", event.getPlayer().getName());
            }
            return;
        }
        savePlayerDataAndReleaseLock(event.getPlayer());
    }

    public void savePlayerDataAndReleaseLock(Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final String serverId = plugin.getServerId();

        cancelHeartbeat(uuid);

        if (savingPlayers.putIfAbsent(uuid, true) != null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "Save for {0} skipped: already in progress.", name);
            }
            return;
        }

        final PlayerData finalData;
        try {
            finalData = new PlayerData(player, plugin);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create final data snapshot for {0}. Data will not be saved. Error: {1}", new Object[]{name, e.getMessage()});
            databaseManager.releaseLock(uuid, serverId);
            savingPlayers.remove(uuid);
            return;
        }

        if (plugin.isDebugMode()) {
            plugin.getLogger().log(Level.INFO, "Got data snapshot for {0}. Scheduling save and lock release.", name);
        }

        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(plugin, () -> {
            try {
                String json = gson.toJson(finalData);
                String seed = plugin.getSecuritySeed();
                String checksum = PlayerData.calculateChecksum(json, seed);
                boolean success = databaseManager.saveAndReleaseLock(json, checksum, name, uuid, serverId, seed);

                if (success) {
                    if (plugin.isDebugMode()) {
                        plugin.getLogger().log(Level.INFO, "Successfully saved data and released lock for {0}.", name);
                    }
                } else if (plugin.isDebugMode()) {
                    plugin.getLogger().log(Level.WARNING, "Could not save data for {0}: lock was lost or not held by this server ({1}).", new Object[]{name, serverId});
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "A critical error occurred during async save for {0}. Releasing lock to prevent player being stuck. ERROR: {1}", new Object[]{name, e.getMessage()});
                databaseManager.releaseLock(uuid, serverId);
            } finally {
                savingPlayers.remove(uuid);
            }
        });
    }

    public void savePlayerDataAndReleaseLockSync(Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final String serverId = plugin.getServerId();

        cancelHeartbeat(uuid);

        try {
            PlayerData finalData = new PlayerData(player, plugin);
            String json = gson.toJson(finalData);
            String seed = plugin.getSecuritySeed();
            String checksum = PlayerData.calculateChecksum(json, seed);
            databaseManager.saveAndReleaseLock(json, checksum, name, uuid, serverId, seed);
            if (plugin.isDebugMode()) {
                plugin.getLogger().log(Level.INFO, "Synchronously saved data for {0}.", name);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to sync save data for " + name + ": " + e.getMessage());
            databaseManager.releaseLock(uuid, serverId);
        } finally {
            savingPlayers.remove(uuid);
        }
    }

    public void shutdown() {
        plugin.getLogger().log(Level.INFO, "Performing final data sync for all players...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerDataAndReleaseLockSync(player);
        }
    }

    private void cancelHeartbeat(UUID uuid) {
        com.digitalserverhost.plugins.utils.SchedulerUtils.getScheduler().cancelHeartbeat(uuid);
        if (plugin.isDebugMode()) {
            plugin.getLogger().log(Level.INFO, "Requested heartbeat cancellation for UUID: {0}", uuid);
        }
    }

    public void applyPlayerData(Player player, PlayerData data) {
        if (data == null || player == null) return;
 
        com.digitalserverhost.plugins.utils.SchedulerUtils.runOnEntity(plugin, player, () -> {
            try {
                if (!player.isOnline()) return;
 
                applyPotionEffects(player, data);
                applyBasicStats(player, data);
                applyInventory(player, data);
                applyAdvancementsAndRecipes(player, data);
                applyStatistics(player, data);
                applyPersistentData(player, data);
                applyFlightAndGameMode(player, data);
                applyLocation(player, data);
 
                plugin.getLogger().log(Level.INFO, "Successfully applied data to player {0}", player.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "A critical error occurred while applying data to player {0}. {1}", new Object[]{player.getName(), e.getMessage()});
                e.printStackTrace();
            }
        });
    }
 
    private void applyBasicStats(Player player, PlayerData data) {
        if (plugin.isSyncEnabled("food-level")) {
            player.setFoodLevel(data.getFoodLevel());
            player.setSaturation(data.getSaturation());
            player.setExhaustion(data.getExhaustion());
        }
        if (plugin.isSyncEnabled("experience")) {
            player.setTotalExperience(data.getTotalExperience());
            player.setExp(data.getExp());
            player.setLevel(data.getLevel());
        }
        if (plugin.isSyncEnabled("health")) {
            org.bukkit.attribute.AttributeInstance maxHealthAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
            player.setHealth(Math.min(data.getHealth(), maxHealth));
        }
    }
 
    private void applyInventory(Player player, PlayerData data) {
        if (plugin.isSyncEnabled("inventory")) {
            player.getInventory().setContents(data.getInventoryContents());
        }
        if (plugin.isSyncEnabled("armor")) {
            player.getInventory().setArmorContents(data.getArmorContents());
        }
        if (plugin.isSyncEnabledNewFeature("ender-chest") && data.getEnderChestContents() != null && data.getEnderChestContents().length > 0) {
            player.getEnderChest().setContents(data.getEnderChestContents());
        }
    }
 
    private void applyPotionEffects(Player player, PlayerData data) {
        if (plugin.isSyncEnabled("potion-effects")) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            if (data.getPotionEffects() != null) {
                for (PotionEffect effect : data.getPotionEffects()) {
                    if (effect != null) {
                        player.addPotionEffect(effect);
                    }
                }
            }
        }
    }
 
    private void applyAdvancementsAndRecipes(Player player, PlayerData data) {
        if (!plugin.isSyncEnabledNewFeature("advancements")) return;
        applyDiscoveredRecipes(player, data);
        applyAdvancementProgress(player, data);
    }

    private void applyDiscoveredRecipes(Player player, PlayerData data) {
        if (data.getDiscoveredRecipes() == null) return;
        for (String recipeKey : data.getDiscoveredRecipes()) {
            try {
                org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(recipeKey);
                if (key != null && !player.hasDiscoveredRecipe(key)) {
                    player.discoverRecipe(key);
                }
            } catch (Exception _) {
                // Skip invalid recipes
            }
        }
    }

    private void applyAdvancementProgress(Player player, PlayerData data) {
        if (data.getAdvancements() == null) return;
        for (java.util.Map.Entry<String, java.util.List<String>> entry : data.getAdvancements().entrySet()) {
            applySingleAdvancement(player, entry.getKey(), entry.getValue());
        }
    }

    private void applySingleAdvancement(Player player, String advancementKey, java.util.List<String> criteria) {
        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(advancementKey);
            if (key != null) {
                org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(key);
                if (adv != null) {
                    org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(adv);
                    for (String criterion : criteria) {
                        if (!progress.getAwardedCriteria().contains(criterion)) {
                            progress.awardCriteria(criterion);
                        }
                    }
                }
            }
        } catch (Exception _) {
            // Skip invalid advancements
        }
    }
 
    private void applyStatistics(Player player, PlayerData data) {
        if (plugin.isSyncEnabledNewFeature("statistics") && data.getStatistics() != null) {
            for (Map.Entry<String, Integer> entry : data.getStatistics().entrySet()) {
                try {
                    org.bukkit.Statistic stat = org.bukkit.Statistic.valueOf(entry.getKey());
                    player.setStatistic(stat, entry.getValue());
                } catch (Exception _) {
                    // Skip invalid statistics
                }
            }
        }
    }
 
    private void applyPersistentData(Player player, PlayerData data) {
        if (plugin.isSyncEnabledNewFeature("pdc") && data.getPdcNBT() != null && !data.getPdcNBT().isEmpty()) {
            try {
                de.tr7zw.changeme.nbtapi.NBT.modify(player, (java.util.function.Consumer<de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT>) nbt -> 
                    nbt.getOrCreateCompound("PublicBukkitValues").mergeCompound(de.tr7zw.changeme.nbtapi.NBT.parseNBT(data.getPdcNBT()))
                );
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply PDC for {0}: {1}", new Object[]{player.getName(), e.getMessage()});
            }
        }
    }
 
    private void applyFlightAndGameMode(Player player, PlayerData data) {
        if (!plugin.isSyncEnabledNewFeature("flight-gamemode")) return;
 
        if (data.getGameMode() != null) {
            try {
                player.setGameMode(org.bukkit.GameMode.valueOf(data.getGameMode()));
            } catch (Exception _) {
                // Ignore invalid gamemode
            }
        }
        
        com.digitalserverhost.plugins.utils.SchedulerUtils.runOnEntityLater(plugin, player, () -> {
            if (player.isOnline()) {
                player.setAllowFlight(data.isAllowFlight());
                player.setFlying(data.isFlying());
                if (plugin.isDebugMode()) {
                    plugin.getLogger().log(Level.INFO, "Delayed flight applied for {0}: Allow={1}, Flying={2}", new Object[]{player.getName(), data.isAllowFlight(), data.isFlying()});
                }
            }
        }, 5L);
    }
 
    private void applyLocation(Player player, PlayerData data) {
        if (plugin.isSyncEnabledNewFeature("location") && data.getWorld() != null) {
            org.bukkit.World world = Bukkit.getWorld(data.getWorld());
            if (world != null) {
                org.bukkit.Location loc = new org.bukkit.Location(world, data.getX(), data.getY(), data.getZ(), data.getYaw(), data.getPitch());
                player.teleport(loc);
            }
        }
    }

    public PlayerData loadPlayerData(UUID uuid) {
        return loadPlayerData(uuid, null);
    }
 
    public PlayerData loadPlayerData(UUID uuid, String name) {
        try (Connection connection = databaseManager.getConnection()) {
            String query = "SELECT data, data_checksum FROM " + databaseManager.getTableName() + " WHERE uuid = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return processPlayerDataResultSet(resultSet, uuid, name);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for {0}: {1}", new Object[]{uuid, e.getMessage()});
        }
        return null;
    }

    private PlayerData processPlayerDataResultSet(ResultSet resultSet, UUID uuid, String name) throws SQLException {
        byte[] dataBytes = resultSet.getBytes("data");
        String json = (dataBytes != null) ? new String(dataBytes, java.nio.charset.StandardCharsets.UTF_8) : null;

        if (json != null && !json.trim().isEmpty() && !"{}".equals(json)) {
            if (shouldVerifyIntegrity() && !verifyAndMigrateChecksum(json, resultSet.getString("data_checksum"), uuid, name)) {
                return null;
            }
            return gson.fromJson(json, PlayerData.class);
        }
        return null;
    }

    private boolean shouldVerifyIntegrity() {
        return plugin.getConfig().getBoolean("security.verify-data-integrity", true);
    }

    private boolean verifyAndMigrateChecksum(String json, String checksum, UUID uuid, String name) {
        if (checksum == null) return true;
        
        String seed = plugin.getSecuritySeed();
        if (!PlayerData.verifyChecksum(json, checksum, seed)) {
            // Legacy Fallback
            if (!PlayerData.verifyChecksum(json, checksum, null)) {
                plugin.getLogger().log(Level.SEVERE, "CRITICAL: Data integrity violation for {0}! Checksum mismatch.", uuid);
                return false;
            }
            if (name != null) {
                plugin.getLogger().log(Level.INFO, "Migrating data for {0} to salted checksum format", name);
            }
        }
        return true;
    }
}
