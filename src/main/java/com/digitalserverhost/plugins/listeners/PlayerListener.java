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
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener, PluginMessageListener {

    private final DatabaseManager databaseManager;
    private final MCDataBridge plugin;
    private final Gson gson;
    private final Map<UUID, PlayerData> loadingCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savingPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeLockTasks = new ConcurrentHashMap<>();

    // This will track players handled by the 'SaveAndRelease' message
    // to prevent 'PlayerQuitEvent' from firing a redundant save.
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
            return; // Add null check to satisfy linter

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        if (subchannel.equals("SaveAndRelease")) {
            String uuidStr = in.readUTF();
            UUID uuid = UUID.fromString(uuidStr);
            Player playerToSave = Bukkit.getPlayer(uuid);

            if (playerToSave != null) {
                if (plugin.isDebugMode()) {
                    plugin.getLogger().info(
                            "Received 'SaveAndRelease' request for " + playerToSave.getName() + ". Triggering save.");
                }

                // Flag the player as switching *before* calling the save.
                switchingPlayers.put(uuid, true);

                savePlayerDataAndReleaseLock(playerToSave);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        String serverId = plugin.getServerId();

        if (plugin.isServerBlacklisted(serverId)) {
            // If this server is blacklisted, we do NOT sync (ignoring locks, saves, etc.)
            // We just return and let them play with local-only data.
            if (plugin.isDebugMode())
                plugin.getLogger().info("Server " + serverId + " is on the blacklist. Skipping sync.");
            return;
        }

        try {
            int attempts = 0;
            final int MAX_ATTEMPTS = 20; // 10 seconds
            final long WAIT_TIME_MS = 500;

            while (attempts < MAX_ATTEMPTS) {
                if (databaseManager.acquireLock(uuid, serverId)) {
                    break; // Lock acquired
                }

                if (plugin.isDebugMode()) {
                    plugin.getLogger()
                            .info("Player " + name + "'s data is locked. Waiting... (Attempt " + (attempts + 1) + ")");
                }
                Thread.sleep(WAIT_TIME_MS);
                attempts++;
            }

            if (!isLockOwner(uuid, serverId)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        Component.text(
                                "[DataBridge] Your data is still being saved by another server. Please try again.",
                                net.kyori.adventure.text.format.NamedTextColor.RED));
                plugin.getLogger().warning("Player " + name + " was disallowed due to a persistent data lock.");
                return;
            }

            if (plugin.isDebugMode()) {
                plugin.getLogger().info("Successfully acquired data lock for player " + name + ".");
            }

            // --- DATA IS LOCKED, PROCEED WITH LOADING ---
            try (Connection connection = databaseManager.getConnection()) {
                String query = "SELECT data FROM " + databaseManager.getTableName() + " WHERE uuid = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, uuid.toString());
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    byte[] dataBytes = resultSet.getBytes("data");
                    String json = (dataBytes != null) ? new String(dataBytes, java.nio.charset.StandardCharsets.UTF_8)
                            : null;

                    if (json != null && !json.trim().isEmpty() && !json.equals("{}")) {
                        PlayerData data = gson.fromJson(json, PlayerData.class);
                        loadingCache.put(uuid, data);
                        if (plugin.isDebugMode()) {
                            plugin.getLogger().info("Player data for " + name + " loaded into cache.");
                        }
                    } else {
                        plugin.getLogger()
                                .info("No existing player data found for " + name + ". A new profile will be created.");
                    }
                } else {
                    plugin.getLogger().info("No row found for 'new' player " + name + ".");
                }
            }
        } catch (PlayerData.ItemDeserializationException e) {
            plugin.getLogger().severe("A critical error occurred while deserializing inventory for player " + name
                    + ". " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "[DataBridge] A critical error occurred while deserializing your inventory. Please contact an administrator.",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            databaseManager.releaseLock(uuid, serverId); // Release the lock we acquired
        } catch (Exception e) {
            plugin.getLogger().severe("Critical error during pre-login for player " + name + ": " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("[DataBridge] Could not process your player data. Please relog.",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            databaseManager.releaseLock(uuid, serverId); // Release the lock we acquired
        }
    }

    private boolean isLockOwner(UUID uuid, String serverId) {
        try (Connection connection = databaseManager.getConnection()) {
            String query = "SELECT locking_server FROM " + databaseManager.getTableName() + " WHERE uuid = ?";
            PreparedStatement checkStmt = connection.prepareStatement(query);
            checkStmt.setString(1, uuid.toString());
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                return serverId.equals(rs.getString("locking_server"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check lock owner for " + uuid + ": " + e.getMessage());
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String serverId = plugin.getServerId();

        if (plugin.isServerBlacklisted(serverId) || plugin.isWorldBlacklisted(player.getWorld().getName())) {
            if (plugin.isDebugMode())
                plugin.getLogger()
                        .info("Player " + player.getName() + " joined a blacklisted server/world. Sync disabled.");
            return;
        }

        // Clean up the switching flag in case of a failed/rejoined switch.
        switchingPlayers.remove(uuid);

        savingPlayers.remove(player.getUniqueId());
        PlayerData data = loadingCache.remove(player.getUniqueId());
        if (data != null) {
            applyPlayerData(player, data);
        } else {
            plugin.getLogger()
                    .info("Player " + player.getName() + " joining with fresh profile. Lock will be released on quit.");
        }

        // Start heartbeat task to periodically update the lock
        long heartbeatTicks = plugin.getLockHeartbeatSeconds() * 20L;
        BukkitTask lockTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (player.isOnline()) { // Player might have logged off
                databaseManager.updateLock(uuid, serverId);
            } else {
                cancelHeartbeat(uuid); // Stop if player is no longer online
            }
        }, heartbeatTicks, heartbeatTicks);

        activeLockTasks.put(uuid, lockTask);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        // If 'switchingPlayers' contains the UUID, it means 'SaveAndRelease'
        // already handled the save. We remove the flag and stop.
        if (switchingPlayers.remove(event.getPlayer().getUniqueId()) != null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("PlayerKickEvent for " + event.getPlayer().getName()
                        + " ignored, handled by 'SaveAndRelease'.");
            }
            return;
        }

        if (plugin.isDebugMode()) {
            plugin.getLogger().info(
                    "PlayerKickEvent fired for " + event.getPlayer().getName() + ". Triggering save as a fallback.");
        }
        savePlayerDataAndReleaseLock(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // If 'switchingPlayers' contains the UUID, it means 'SaveAndRelease'
        // already handled the save. We remove the flag and stop.
        if (switchingPlayers.remove(event.getPlayer().getUniqueId()) != null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("PlayerQuitEvent for " + event.getPlayer().getName()
                        + " ignored, handled by 'SaveAndRelease'.");
            }
            return;
        }

        if (plugin.isDebugMode()) {
            plugin.getLogger().info(
                    "PlayerQuitEvent fired for " + event.getPlayer().getName() + ". Triggering save as a fallback.");
        }
        savePlayerDataAndReleaseLock(event.getPlayer());
    }

    public void savePlayerDataAndReleaseLock(Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final String serverId = plugin.getServerId();

        // ★★★ (Still cancel the heartbeat first!) ★★★
        cancelHeartbeat(uuid);

        if (savingPlayers.putIfAbsent(uuid, true) != null) {
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("Save for " + name + " skipped: already in progress.");
            }
            return;
        }

        final PlayerData finalData;
        try {
            // This captures the player's live data at the moment of saving.
            finalData = new PlayerData(player, plugin);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to create final data snapshot for " + name
                    + ". Data will not be saved. Error: " + e.getMessage());
            databaseManager.releaseLock(uuid, serverId);
            savingPlayers.remove(uuid);
            return;
        }

        if (plugin.isDebugMode()) {
            plugin.getLogger().info("Got data snapshot for " + name + ". Scheduling save and lock release.");
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String json = gson.toJson(finalData);
                boolean success = databaseManager.saveAndReleaseLock(json, uuid, serverId);

                if (success) {
                    if (plugin.isDebugMode()) {
                        plugin.getLogger().info("Successfully saved data and released lock for " + name + ".");
                    }
                } else {
                    if (plugin.isDebugMode()) {
                        plugin.getLogger().warning("Could not save data for " + name
                                + ": lock was lost or not held by this server (" + serverId
                                + "). This is normal if another process (like a proxy message) already saved the data.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("A critical error occurred during async save for " + name
                        + ". Releasing lock to prevent player being stuck. ERROR: " + e.getMessage());
                databaseManager.releaseLock(uuid, serverId); // Still release lock on error
            } finally {
                savingPlayers.remove(uuid);
            }
        });
    }

    private void cancelHeartbeat(UUID uuid) {
        BukkitTask task = activeLockTasks.remove(uuid);
        if (task != null) {
            task.cancel();
            if (plugin.isDebugMode()) {
                plugin.getLogger().info("Cancelled heartbeat for UUID: " + uuid);
            }
        }
    }

    private void applyPlayerData(Player player, PlayerData data) {
        try {
            if (player == null || !player.isOnline())
                return;

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
            if (plugin.isSyncEnabled("inventory"))
                player.getInventory().setContents(data.getInventoryContents());
            if (plugin.isSyncEnabled("armor"))
                player.getInventory().setArmorContents(data.getArmorContents());
            if (plugin.isSyncEnabledNewFeature("ender-chest")) {
                if (data.getEnderChestContents() != null && data.getEnderChestContents().length > 0) {
                    player.getEnderChest().setContents(data.getEnderChestContents());
                }
            }

            if (plugin.isSyncEnabled("potion-effects")) {
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                if (data.getPotionEffects() != null) {
                    for (PotionEffect effect : data.getPotionEffects()) {
                        if (effect != null)
                            player.addPotionEffect(effect);
                    }
                }
            }

            if (plugin.isSyncEnabled("health")) {
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                player.setHealth(Math.min(data.getHealth(), maxHealth));
            }

            if (plugin.isSyncEnabledNewFeature("advancements")) {
                // Apply Recipes
                if (data.getDiscoveredRecipes() != null) {
                    for (String recipeKey : data.getDiscoveredRecipes()) {
                        try {
                            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(recipeKey);
                            if (key != null && !player.hasDiscoveredRecipe(key)) {
                                player.discoverRecipe(key);
                            }
                        } catch (Exception e) {
                            // Ignore invalid keys
                        }
                    }
                }

                // Apply Advancements
                if (data.getAdvancements() != null) {
                    for (Map.Entry<String, java.util.List<String>> entry : data.getAdvancements().entrySet()) {
                        try {
                            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(entry.getKey());
                            if (key == null)
                                continue;

                            org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(key);
                            if (adv == null)
                                continue;

                            org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(adv);
                            for (String criteria : entry.getValue()) {
                                if (!progress.getAwardedCriteria().contains(criteria)) {
                                    progress.awardCriteria(criteria);
                                }
                            }
                        } catch (Exception e) {
                            // Ignore errors
                        }
                    }
                }
            }

            plugin.getLogger().info("Successfully applied data to player " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("A critical error occurred while applying data to player "
                    + (player != null ? player.getName() : "null") + ". " + e.getMessage());
            if (player != null) {
                player.kick(Component.text("[DataBridge] An error occurred applying your data.",
                        net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
    }
}
