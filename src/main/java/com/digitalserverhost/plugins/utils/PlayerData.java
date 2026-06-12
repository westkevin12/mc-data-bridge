package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.Serializable;
import java.util.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PlayerData {

    public static class ItemDeserializationException extends RuntimeException {
        public ItemDeserializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private double health;
    private int foodLevel;
    private float saturation;
    private float exhaustion;
    private int totalExperience;
    private float exp;
    private int level;
    @SerializedName(value = "inventoryContentsNBT", alternate = {"inventoryContents"})
    private List<String> inventoryContentsNBT;
    @SerializedName(value = "armorContentsNBT", alternate = {"armorContents"})
    private List<String> armorContentsNBT;
    @SerializedName(value = "enderChestContentsNBT", alternate = {"enderChestContents"})
    private List<String> enderChestContentsNBT;
    private SerializablePotionEffect[] potionEffects;
    private List<String> discoveredRecipes;
    private Map<String, List<String>> advancements;
    private Map<String, Integer> statistics;
    private String pdcNBT;
    private boolean isFlying;
    private boolean allowFlight;
    private String gameMode;

    private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    // Companion / Pet sync (default disabled)
    private String companionsNBT;


    public PlayerData() {}

    /**
     * ★★★ NEW CONSTRUCTOR ★★★
     * Snapshots a live player's data.
     */
    public PlayerData(Player player) {
        this(player, JavaPlugin.getPlugin(MCDataBridge.class));
    }

    private static final org.bukkit.Statistic[] ESSENTIAL_STATS = {
        org.bukkit.Statistic.DEATHS,
        org.bukkit.Statistic.PLAYER_KILLS,
        org.bukkit.Statistic.MOB_KILLS,
        org.bukkit.Statistic.PLAY_ONE_MINUTE,
        org.bukkit.Statistic.WALK_ONE_CM,
        org.bukkit.Statistic.SPRINT_ONE_CM,
        org.bukkit.Statistic.FLY_ONE_CM,
        org.bukkit.Statistic.JUMP,
        org.bukkit.Statistic.DAMAGE_DEALT,
        org.bukkit.Statistic.DAMAGE_TAKEN,
        org.bukkit.Statistic.LEAVE_GAME,
        org.bukkit.Statistic.TIME_SINCE_DEATH,
        org.bukkit.Statistic.SNEAK_TIME,
        org.bukkit.Statistic.TALKED_TO_VILLAGER,
        org.bukkit.Statistic.TRADED_WITH_VILLAGER,
        org.bukkit.Statistic.FISH_CAUGHT
    };

    /**
     * Primary constructor used by listeners to create a snapshot.
     */
    public PlayerData(Player player, MCDataBridge plugin) {
        if (player != null) {
            snapshotHealth(player, plugin);
            snapshotFood(player, plugin);
            snapshotExperience(player, plugin);
            snapshotInventories(player, plugin);
            snapshotPotionEffects(player, plugin);
            snapshotAdvancements(player, plugin);
            snapshotStatistics(player, plugin);
            snapshotPdc(player, plugin);
            snapshotLocation(player);
            snapshotFlightAndGamemode(player, plugin);
            snapshotCompanions(player, plugin);
        }
    }

    private void snapshotHealth(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabled("health")) {
            this.health = player.getHealth();
        }
    }

    private void snapshotFood(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabled("food-level")) {
            this.foodLevel = player.getFoodLevel();
            this.saturation = player.getSaturation();
            this.exhaustion = player.getExhaustion();
        }
    }

    private void snapshotExperience(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabled("experience")) {
            this.totalExperience = player.getTotalExperience();
            this.exp = player.getExp();
            this.level = player.getLevel();
        }
    }

    private void snapshotInventories(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabled("inventory")) {
            this.inventoryContentsNBT = serializeItemStackArray(player.getInventory().getContents());
        }
        if (plugin.isSyncEnabled("armor")) {
            this.armorContentsNBT = serializeItemStackArray(player.getInventory().getArmorContents());
        }
        if (plugin.isSyncEnabledNewFeature("ender-chest")) {
            this.enderChestContentsNBT = serializeItemStackArray(player.getEnderChest().getContents());
        }
    }

    private void snapshotPotionEffects(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabled("potion-effects")) {
            this.potionEffects = convertPotionEffectArrayToSerializable(
                    player.getActivePotionEffects().toArray(new PotionEffect[0]));
        }
    }

    private void snapshotAdvancements(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabledNewFeature("advancements")) {
            this.discoveredRecipes = new ArrayList<>();
            for (org.bukkit.NamespacedKey key : player.getDiscoveredRecipes()) {
                this.discoveredRecipes.add(key.toString());
            }

            this.advancements = new HashMap<>();
            Iterator<Advancement> it = org.bukkit.Bukkit.advancementIterator();
            while (it.hasNext()) {
                Advancement adv = java.util.Objects.requireNonNull(it.next());
                AdvancementProgress progress = player.getAdvancementProgress(adv);
                if (progress.isDone()) {
                    this.advancements.put(adv.getKey().toString(), new ArrayList<>(progress.getAwardedCriteria()));
                }
            }
        }
    }

    private void snapshotStatistics(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabledNewFeature("statistics")) {
            this.statistics = new HashMap<>();
            for (org.bukkit.Statistic stat : ESSENTIAL_STATS) {
                try {
                    this.statistics.put(stat.name(), player.getStatistic(stat));
                } catch (Exception _) {
                    // Ignore unsupported statistics
                }
            }
        }
    }

    private void snapshotPdc(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabledNewFeature("pdc")) {
            try {
                this.pdcNBT = de.tr7zw.changeme.nbtapi.NBT.get(player, nbt -> {
                    de.tr7zw.changeme.nbtapi.iface.ReadableNBT compound = nbt.getCompound("PublicBukkitValues");
                    return (compound != null) ? compound.toString() : null;
                });
            } catch (Exception | LinkageError _) {
                // Fallback for mock environments where NBTAPI reflection fails
                this.pdcNBT = null;
            }
        }
    }

    private void snapshotLocation(Player player) {
        try {
            this.world = player.getWorld().getName();
        } catch (NullPointerException _) {
            // Handled for unit tests where player.getWorld() returns null
        }
        try {
            org.bukkit.Location loc = player.getLocation();
            if (loc != null) {
                this.x = loc.getX();
                this.y = loc.getY();
                this.z = loc.getZ();
                this.yaw = loc.getYaw();
                this.pitch = loc.getPitch();
            }
        } catch (NullPointerException _) {
            // Handled for unit tests where player.getLocation() returns null
        }
    }

    private void snapshotFlightAndGamemode(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabledNewFeature("flight-gamemode")) {
            this.isFlying = player.isFlying();
            this.allowFlight = player.getAllowFlight();
            this.gameMode = player.getGameMode().name();
        }
    }

    private void snapshotCompanions(Player player, MCDataBridge plugin) {
        if (!plugin.isSyncEnabledNewFeature("companions")) return;
        double radius = 32.0;
        try {
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            if (cfg != null) radius = cfg.getDouble("companions.scan-radius", 32.0);
        } catch (Exception _) { /* test environment — use default */ }
        java.util.List<CompanionSnapshot> snapshots = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof org.bukkit.entity.Tameable tame
                    && tame.isTamed()
                    && tame.getOwner() != null
                    && tame.getOwner().getUniqueId().equals(player.getUniqueId())) {
                snapshots.add(new CompanionSnapshot(entity));
                entity.remove(); // despawn on source server to prevent duplication
            }
        }
        if (!snapshots.isEmpty()) {
            this.companionsNBT = new com.google.gson.Gson().toJson(snapshots);
        }
    }

    private List<String> serializeItemStackArray(ItemStack[] items) {
        List<String> serializedItems = new ArrayList<>();
        Gson gson = MCDataBridge.getGson();
        if (items == null) {
            return serializedItems;
        }
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                serializedItems.add(null);
            } else {
                try {
                    SerializableItemStack serializableItem = new SerializableItemStack(item);
                    serializedItems.add(gson.toJson(serializableItem));
                } catch (Exception _) {
                    org.bukkit.Bukkit.getLogger().warning("[mc-data-bridge] Failed to serialize item: " + item.getType());
                    serializedItems.add(null);
                }
            }
        }
        return serializedItems;
    }

    ItemStack[] deserializeItemStackArray(List<String> serializedItems) {
        if (serializedItems == null) {
            return new ItemStack[0];
        }
        ItemStack[] items = new ItemStack[serializedItems.size()];
        Gson gson = MCDataBridge.getGson();
        for (int i = 0; i < serializedItems.size(); i++) {
            String s = serializedItems.get(i);
            if (s == null || s.isEmpty() || s.equals("null")) {
                items[i] = new ItemStack(Material.AIR);
                continue;
            }

            try {
                // Detect if it's our new JSON format or an old YAML string
                if (s.startsWith("{")) {
                    SerializableItemStack entry = java.util.Objects.requireNonNull(gson.fromJson(s, SerializableItemStack.class));
                    items[i] = entry.toItemStack();
                } else {
                    // Legacy YAML format (used in older versions)
                    items[i] = legacyDeserialize(s);
                }
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().warning("[mc-data-bridge] Failed to deserialize item at index " + i + ": " + e.getMessage());
                items[i] = new ItemStack(Material.AIR);
            }
        }
        return items;
    }

    private ItemStack legacyDeserialize(String yaml) {
        try {
            org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
            config.loadFromString(yaml);
            return config.getItemStack("item");
        } catch (Exception _) {
            return new ItemStack(Material.AIR);
        }
    }

    public double getHealth() {
        return health;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }

    public float getExhaustion() {
        return exhaustion;
    }

    public int getTotalExperience() {
        return totalExperience;
    }

    public float getExp() {
        return exp;
    }

    public int getLevel() {
        return level;
    }

    public ItemStack[] getInventoryContents() {
        return deserializeItemStackArray(inventoryContentsNBT);
    }

    public ItemStack[] getArmorContents() {
        return deserializeItemStackArray(armorContentsNBT);
    }

    public ItemStack[] getEnderChestContents() {
        return deserializeItemStackArray(enderChestContentsNBT);
    }

    public PotionEffect[] getPotionEffects() {
        return convertSerializablePotionEffectArrayToPotionEffect(potionEffects);
    }

    public List<String> getDiscoveredRecipes() {
        return discoveredRecipes;
    }

    public Map<String, List<String>> getAdvancements() {
        return advancements;
    }

    public Map<String, Integer> getStatistics() {
        return statistics;
    }

    public String getPdcNBT() {
        return pdcNBT;
    }

    public boolean isFlying() {
        return isFlying;
    }

    public boolean isAllowFlight() {
        return allowFlight;
    }

    public String getGameMode() {
        return gameMode;
    }

    public String getWorld() {
        return world;
    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public List<String> getInventoryContentsNBT() { return inventoryContentsNBT; }
    public List<String> getArmorContentsNBT() { return armorContentsNBT; }
    public List<String> getEnderChestContentsNBT() { return enderChestContentsNBT; }
    public String getCompanionsNBT() { return companionsNBT; }

    public void setHealth(double health) { this.health = health; }
    public void setFoodLevel(int foodLevel) { this.foodLevel = foodLevel; }
    public void setSaturation(float saturation) { this.saturation = saturation; }
    public void setExhaustion(float exhaustion) { this.exhaustion = exhaustion; }
    public void setTotalExperience(int totalExperience) { this.totalExperience = totalExperience; }
    public void setExp(float exp) { this.exp = exp; }
    public void setLevel(int level) { this.level = level; }
    public void setInventoryContentsNBT(List<String> inventoryContentsNBT) { this.inventoryContentsNBT = inventoryContentsNBT; }
    public void setArmorContentsNBT(List<String> armorContentsNBT) { this.armorContentsNBT = armorContentsNBT; }
    public void setEnderChestContentsNBT(List<String> enderChestContentsNBT) { this.enderChestContentsNBT = enderChestContentsNBT; }
    public void setPotionEffects(PotionEffect[] effects) { this.potionEffects = convertPotionEffectArrayToSerializable(effects); }
    public void setDiscoveredRecipes(List<String> discoveredRecipes) { this.discoveredRecipes = discoveredRecipes; }
    public void setAdvancements(Map<String, List<String>> advancements) { this.advancements = advancements; }
    public void setStatistics(Map<String, Integer> statistics) { this.statistics = statistics; }
    public void setPdcNBT(String pdcNBT) { this.pdcNBT = pdcNBT; }
    public void setCompanionsNBT(String companionsNBT) { this.companionsNBT = companionsNBT; }
    public void setFlying(boolean flying) { this.isFlying = flying; }
    public void setAllowFlight(boolean allowFlight) { this.allowFlight = allowFlight; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
    public void setWorld(String world) { this.world = world; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setZ(double z) { this.z = z; }
    public void setYaw(float yaw) { this.yaw = yaw; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    private SerializablePotionEffect[] convertPotionEffectArrayToSerializable(PotionEffect[] effects) {
        if (effects == null) {
            return new SerializablePotionEffect[0];
        }
        SerializablePotionEffect[] serializableEffects = new SerializablePotionEffect[effects.length];
        for (int i = 0; i < effects.length; i++) {
            serializableEffects[i] = new SerializablePotionEffect(effects[i]);
        }
        return serializableEffects;
    }

    private PotionEffect[] convertSerializablePotionEffectArrayToPotionEffect(
            SerializablePotionEffect[] serializableEffects) {
        if (serializableEffects == null) {
            return new PotionEffect[0];
        }
        PotionEffect[] effects = new PotionEffect[serializableEffects.length];
        for (int i = 0; i < serializableEffects.length; i++) {
            if (serializableEffects[i] != null) {
                effects[i] = serializableEffects[i].toPotionEffect();
            }
        }
        return effects;
    }

    /**
     * Calculates a SHA-256 checksum of the JSON string, optionally salted with a seed.
     */
    public static String calculateChecksum(String json, String seed) {
        if (json == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = json + (seed != null ? ":" + seed : "");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException _) {
            return null;
        }
    }

    public static String calculateChecksum(String json) {
        return calculateChecksum(json, null);
    }

    /**
     * Verifies if the provided checksum matches the calculated checksum of the JSON.
     */
    public static boolean verifyChecksum(String json, String expectedChecksum, String seed) {
        if (json == null || expectedChecksum == null) return false;
        String calculated = calculateChecksum(json, seed);
        return expectedChecksum.equalsIgnoreCase(calculated);
    }

    public static boolean verifyChecksum(String json, String expectedChecksum) {
        return verifyChecksum(json, expectedChecksum, null);
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "health=" + health +
                ", foodLevel=" + foodLevel +
                ", saturation=" + saturation +
                ", exhaustion=" + exhaustion +
                ", totalExperience=" + totalExperience +
                ", exp=" + exp +
                ", level=" + level +
                ", inventoryContentsNBT=" + (inventoryContentsNBT != null ? inventoryContentsNBT.size() : "null")
                + " items" +
                ", armorContentsNBT=" + (armorContentsNBT != null ? armorContentsNBT.size() : "null") + " items" +
                ", enderChestContentsNBT=" + (enderChestContentsNBT != null ? enderChestContentsNBT.size() : "null")
                + " items" +
                ", potionEffects=" + Arrays.toString(potionEffects) +
                ", recipes=" + (discoveredRecipes != null ? discoveredRecipes.size() : "0") +
                ", advancements=" + (advancements != null ? advancements.size() : "0") +
                ", companions=" + (companionsNBT != null ? "present" : "none") +
                "}";
    }

    /**
     * Snapshot of a single tamed companion entity for cross-server transfer.
     */
    public static class CompanionSnapshot implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String entityType;
        public final double health;
        public final double maxHealth;
        public final String customName;
        public final boolean isSitting;
        public final String nbtData;

        @SuppressWarnings("deprecation")
        public CompanionSnapshot(org.bukkit.entity.Entity entity) {
            this.entityType = entity.getType().name();
            this.customName = entity.getCustomName();

            if (entity instanceof org.bukkit.entity.Damageable db) {
                this.health = db.getHealth();
            } else {
                this.health = 20.0;
            }

            if (entity instanceof org.bukkit.attribute.Attributable attributable) {
                org.bukkit.attribute.AttributeInstance attr = attributable.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                this.maxHealth = (attr != null) ? attr.getValue() : 20.0;
            } else {
                this.maxHealth = 20.0;
            }

            this.isSitting = (entity instanceof org.bukkit.entity.Sittable sittable) && sittable.isSitting();

            String serializedNbt = null;
            try {
                serializedNbt = de.tr7zw.changeme.nbtapi.NBT.get(entity,
                        (java.util.function.Function<de.tr7zw.changeme.nbtapi.iface.ReadableNBT, String>) nbt -> nbt.toString());
            } catch (Throwable _) {
                // NBTAPI unavailable or failed — companion saved without raw NBT
            }
            this.nbtData = serializedNbt;
        }
    }

    static class SerializableItemStack {
        private static final String DISPLAY_NAME = "display-name";
        private static final String ITEM_NAME = "item-name";
        private static final String LORE = "lore";

        private String itemAsBase64;
        @SuppressWarnings("unused")
        private int v; // DataVersion (short name to save database space)
        private final String material;
        private final int amount;
        private final String nbt;

        public SerializableItemStack(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                this.itemAsBase64 = null;
            } else {
                serializeItem(item);
            }
            this.material = null;
            this.amount = 0;
            this.nbt = null;
        }

        private void serializeItem(ItemStack item) {
            try {
                this.itemAsBase64 = Base64.getEncoder().encodeToString(item.serializeAsBytes());
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().warning("[mc-data-bridge] serializeAsBytes failed, falling back to NBTAPI. Error: " + e.toString());
                serializeItemFallback(item);
            }
        }

        private void serializeItemFallback(ItemStack item) {
            try {
                this.itemAsBase64 = de.tr7zw.changeme.nbtapi.NBT.itemStackToNBT(item).toString();
                this.v = getDataVersionSafe();
            } catch (Exception | LinkageError e2) {
                org.bukkit.Bukkit.getLogger().severe("[mc-data-bridge] Item serialization failed (no NMS): " + e2.getMessage());
                this.itemAsBase64 = null;
            }
        }

        @SuppressWarnings("deprecation")
        private int getDataVersionSafe() {
            try {
                return org.bukkit.Bukkit.getUnsafe().getDataVersion();
            } catch (Exception | LinkageError _) {
                return 0;
            }
        }

        public ItemStack toItemStack() {
            if (this.itemAsBase64 == null) {
                return new ItemStack(Material.AIR);
            }

            if (this.itemAsBase64.startsWith("{")) {
                return deserializeFromJsonNbt();
            }

            try {
                byte[] itemBytes = Base64.getDecoder().decode(this.itemAsBase64);
                if (itemBytes.length > 2) {
                    if (itemBytes[0] == (byte) '{') {
                        return deserializeFromMockMcJson(itemBytes);
                    }
                    if (itemBytes[0] == (byte) 0x1F && itemBytes[1] == (byte) 0x8B) {
                        return deserializeFromBinaryCompressed(itemBytes);
                    }
                    if (itemBytes[0] == (byte) 0xAC && itemBytes[1] == (byte) 0xED) {
                        return deserializeFromJavaSerialization(itemBytes);
                    }
                }
                return ItemStack.deserializeBytes(itemBytes);
            } catch (NoSuchMethodError _) {
                return new ItemStack(Material.AIR);
            } catch (Exception _) {
                return deserializeFromLegacyNbt();
            }
        }

        private ItemStack deserializeFromJsonNbt() {
            try {
                return de.tr7zw.changeme.nbtapi.NBT.itemStackFromNBT(
                        de.tr7zw.changeme.nbtapi.NBT.parseNBT(this.itemAsBase64)
                );
            } catch (Exception | LinkageError _) {
                return new ItemStack(Material.AIR);
            }
        }

        @SuppressWarnings({"unchecked", "null"})
        private ItemStack deserializeFromMockMcJson(byte[] itemBytes) {
            try {
                String json = new String(itemBytes, java.nio.charset.StandardCharsets.UTF_8);
                java.util.Map<String, Object> map = new com.google.gson.Gson().fromJson(json, java.util.Map.class);
                if (map != null) {
                    sanitizeMetaMap((java.util.Map<String, Object>) map.get("components"));
                    sanitizeMetaMap((java.util.Map<String, Object>) map.get("meta"));
                }
                
                ItemStack result = deserializeItemStackFromMap(map);
                if (result != null) {
                    hydrateItemMetaReflectively(result, map);
                    return result;
                }
            } catch (Exception _) {
                org.bukkit.Bukkit.getLogger().warning("[mc-data-bridge] outer deserialize block failed");
            }
            return new ItemStack(Material.AIR);
        }

        private ItemStack deserializeItemStackFromMap(java.util.Map<String, Object> map) {
            try {
                Class<?> mockClass = Class.forName("org.mockmc.mockmc.inventory.ItemStackMock");
                java.lang.reflect.Method deserializeMethod = mockClass.getMethod("deserialize", java.util.Map.class);
                return (ItemStack) deserializeMethod.invoke(null, map);
            } catch (Exception _) {
                return deserializeItemStackFromMapLegacy(map);
            }
        }

        private ItemStack deserializeItemStackFromMapLegacy(java.util.Map<String, Object> map) {
            try {
                Class<?> legacyMockClass = Class.forName("org.mockbukkit.mockbukkit.inventory.ItemStackMock");
                java.lang.reflect.Method deserializeMethod = legacyMockClass.getMethod("deserialize", java.util.Map.class);
                return (ItemStack) deserializeMethod.invoke(null, map);
            } catch (Exception _) {
                try {
                    return ItemStack.deserialize(map);
                } catch (Exception _) {
                    return null;
                }
            }
        }

        @SuppressWarnings("all")
        private void hydrateItemMetaReflectively(ItemStack result, java.util.Map<String, Object> map) {
            if (result.getClass().getName().contains("ItemStackMock")) {
                try {
                    Object metaObj = map.getOrDefault("components", map.get("meta"));
                    if (metaObj instanceof java.util.Map) {
                        java.util.Map<String, Object> metaMap = (java.util.Map<String, Object>) metaObj;
                        org.bukkit.inventory.meta.ItemMeta itemMeta = org.bukkit.Bukkit.getItemFactory().getItemMeta(result.getType());
                        if (itemMeta != null) {
                            Class<?> current = itemMeta.getClass();
                            java.lang.reflect.Method deserializeInternal = null;
                            while (current != null) {
                                try {
                                    deserializeInternal = current.getDeclaredMethod("deserializeInternal", java.util.Map.class);
                                    break;
                                } catch (NoSuchMethodException _) {
                                    current = current.getSuperclass();
                                }
                            }
                            if (deserializeInternal != null) {
                                deserializeInternal.setAccessible(true);
                                deserializeInternal.invoke(itemMeta, metaMap);
                                result.setItemMeta(itemMeta);
                            }
                        }
                    }
                } catch (Exception _) {
                    org.bukkit.Bukkit.getLogger().warning("[mc-data-bridge] Reflection hydration failed");
                }
            }
        }

        private ItemStack deserializeFromBinaryCompressed(byte[] itemBytes) {
            try {
                return ItemStack.deserializeBytes(itemBytes);
            } catch (Exception _) {
                return deserializeFromBinaryCompressedFallback(itemBytes);
            }
        }

        private ItemStack deserializeFromBinaryCompressedFallback(byte[] itemBytes) {
            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(itemBytes)) {
                @SuppressWarnings("deprecation")
                de.tr7zw.changeme.nbtapi.NBTContainer container = new de.tr7zw.changeme.nbtapi.NBTContainer(bis);
                return de.tr7zw.changeme.nbtapi.NBT.itemStackFromNBT(container);
            } catch (Exception | LinkageError _) {
                org.bukkit.Bukkit.getLogger().severe("[mc-data-bridge] Spigot failed to translate Paper binary item");
                return new ItemStack(Material.AIR);
            }
        }

        private ItemStack deserializeFromJavaSerialization(byte[] itemBytes) {
            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(itemBytes);
                 @SuppressWarnings("deprecation")
                 org.bukkit.util.io.BukkitObjectInputStream bois = new org.bukkit.util.io.BukkitObjectInputStream(bis)) {
                return (ItemStack) bois.readObject();
            } catch (Exception _) {
                return new ItemStack(Material.AIR);
            }
        }

        private ItemStack deserializeFromLegacyNbt() {
            if (this.material != null) {
                try {
                    ItemStack item = new ItemStack(Material.valueOf(material), amount);
                    if (nbt != null) {
                        @SuppressWarnings("deprecation")
                        NBTItem nbtItem = new NBTItem(item);
                        @SuppressWarnings("deprecation")
                        de.tr7zw.changeme.nbtapi.NBTCompound compound = new NBTContainer(nbt);
                        nbtItem.mergeCompound(compound);
                        return nbtItem.getItem();
                    }
                    return item;
                } catch (Exception | LinkageError _) {
                    org.bukkit.Bukkit.getLogger().severe(
                            "[mc-data-bridge] Failed to deserialize OLD (NBT) item data! Material: " + this.material);
                    return new ItemStack(Material.AIR);
                }
            }
            return new ItemStack(Material.AIR);
        }

        private void sanitizeMetaMap(java.util.Map<String, Object> metaMap) {
            if (metaMap == null) return;
            com.google.gson.Gson gson = new com.google.gson.Gson();
            
            sanitizeDisplayName(metaMap, gson);
            sanitizeItemName(metaMap, gson);
            sanitizeLore(metaMap, gson);
        }

        private void sanitizeDisplayName(java.util.Map<String, Object> metaMap, com.google.gson.Gson gson) {
            if (metaMap.containsKey(DISPLAY_NAME)) {
                Object val = metaMap.get(DISPLAY_NAME);
                if (val instanceof String str) {
                    String trimmed = str.trim();
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
                        metaMap.put(DISPLAY_NAME, gson.toJson(str));
                    }
                }
            }
        }

        private void sanitizeItemName(java.util.Map<String, Object> metaMap, com.google.gson.Gson gson) {
            if (metaMap.containsKey(ITEM_NAME)) {
                Object val = metaMap.get(ITEM_NAME);
                if (val instanceof String str) {
                    String trimmed = str.trim();
                    if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
                        metaMap.put(ITEM_NAME, gson.toJson(str));
                    }
                }
            }
        }

        private void sanitizeLore(java.util.Map<String, Object> metaMap, com.google.gson.Gson gson) {
            if (metaMap.containsKey(LORE)) {
                Object val = metaMap.get(LORE);
                if (val instanceof java.util.List<?> list) {
                    java.util.List<Object> sanitizedList = new java.util.ArrayList<>();
                    for (Object item : list) {
                        sanitizedList.add(sanitizeSingleLoreItem(item, gson));
                    }
                    metaMap.put(LORE, sanitizedList);
                }
            }
        }

        private Object sanitizeSingleLoreItem(Object item, com.google.gson.Gson gson) {
            if (item instanceof String str) {
                String trimmed = str.trim();
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.startsWith("\"")) {
                    return gson.toJson(str);
                }
            }
            return item;
        }
    }

    public static class SerializablePotionEffect implements Serializable {
        private static final long serialVersionUID = 72L;
        private final String type;
        private final int duration;
        private final int amplifier;
        private final boolean ambient;
        private final boolean particles;
        private final boolean icon;

        public SerializablePotionEffect(PotionEffect effect) {
            // Using deprecated getName() to maintain compatibility with existing database
            // data
            // that stores effect types as UPPERCASE names (e.g., "SPEED").
            // Switching to getKey() would break compatibility without a database migration.
            @SuppressWarnings("deprecation")
            String name = effect.getType().getName();
            this.type = name;

            this.duration = effect.getDuration();
            this.amplifier = effect.getAmplifier();
            this.ambient = effect.isAmbient();
            this.particles = effect.hasParticles();
            this.icon = effect.hasIcon();
        }

        public PotionEffect toPotionEffect() {
            // Using deprecated getByName() to read legacy uppercase names from database
            @SuppressWarnings("deprecation")
            PotionEffectType effectType = PotionEffectType.getByName(type);
            if (effectType == null) {
                return null;
            }
            return new PotionEffect(effectType, duration, amplifier, ambient, particles, icon);
        }

        @Override
        public String toString() {
            return "SerializablePotionEffect{" +
                    "type='" + type + "'" +
                    ", duration=" + duration +
                    ", amplifier=" + amplifier +
                    ", ambient=" + ambient +
                    ", particles=" + particles +
                    ", icon=" + icon +
                    '}';
        }
    }
}
