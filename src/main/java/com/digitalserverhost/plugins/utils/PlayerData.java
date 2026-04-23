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

    // Location Data (Logging/Admin Use Only - RESTORATION SUPPORTED)
    private String world;
    private double x, y, z;
    private float yaw, pitch;

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
            if (plugin.isSyncEnabled("health")) {
                this.health = player.getHealth();
            }
            if (plugin.isSyncEnabled("food-level")) {
                this.foodLevel = player.getFoodLevel();
                this.saturation = player.getSaturation();
                this.exhaustion = player.getExhaustion();
            }
            if (plugin.isSyncEnabled("experience")) {
                this.totalExperience = player.getTotalExperience();
                this.exp = player.getExp();
                this.level = player.getLevel();
            }
            if (plugin.isSyncEnabled("inventory")) {
                this.inventoryContentsNBT = serializeItemStackArray(player.getInventory().getContents());
            }
            if (plugin.isSyncEnabled("armor")) {
                this.armorContentsNBT = serializeItemStackArray(player.getInventory().getArmorContents());
            }
            if (plugin.isSyncEnabledNewFeature("ender-chest")) {
                this.enderChestContentsNBT = serializeItemStackArray(player.getEnderChest().getContents());
            }
            if (plugin.isSyncEnabled("potion-effects")) {
                this.potionEffects = convertPotionEffectArrayToSerializable(
                        player.getActivePotionEffects().toArray(new PotionEffect[0]));
            }

            if (plugin.isSyncEnabledNewFeature("advancements")) {
                // Snapshot Recipes
                this.discoveredRecipes = new ArrayList<>();
                for (org.bukkit.NamespacedKey key : player.getDiscoveredRecipes()) {
                    this.discoveredRecipes.add(key.toString());
                }

                // Snapshot Advancements
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

            if (plugin.isSyncEnabledNewFeature("statistics")) {
                this.statistics = new HashMap<>();
                for (org.bukkit.Statistic stat : ESSENTIAL_STATS) {
                    try {
                        this.statistics.put(stat.name(), player.getStatistic(stat));
                    } catch (Exception ignored) {}
                }
            }

            if (plugin.isSyncEnabledNewFeature("pdc")) {
                this.pdcNBT = de.tr7zw.changeme.nbtapi.NBT.get(player, nbt -> {
                    de.tr7zw.changeme.nbtapi.iface.ReadableNBT compound = nbt.getCompound("PublicBukkitValues");
                    return (compound != null) ? compound.toString() : null;
                });
            }

            // Location
            if (player.getWorld() != null) {
                this.world = player.getWorld().getName();
                org.bukkit.Location loc = player.getLocation();
                if (loc != null) {
                    this.x = loc.getX();
                    this.y = loc.getY();
                    this.z = loc.getZ();
                    this.yaw = loc.getYaw();
                    this.pitch = loc.getPitch();
                }
            }

            // Flight & Gamemode
            if (plugin.isSyncEnabledNewFeature("flight-gamemode")) {
                this.isFlying = player.isFlying();
                this.allowFlight = player.getAllowFlight();
                this.gameMode = player.getGameMode().name();
            }
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
                } catch (Exception e) {
                    System.err.println("[mc-data-bridge] Failed to serialize item: " + item.getType());
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
                System.err.println("[mc-data-bridge] Failed to deserialize item at index " + i + ": " + e.getMessage());
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
        } catch (Exception e) {
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
     * Calculates a SHA-256 checksum of the JSON string.
     */
    public static String calculateChecksum(String json) {
        if (json == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * Verifies if the provided checksum matches the calculated checksum of the JSON.
     */
    public static boolean verifyChecksum(String json, String expectedChecksum) {
        if (json == null || expectedChecksum == null) return false;
        String calculated = calculateChecksum(json);
        return expectedChecksum.equalsIgnoreCase(calculated);
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
                "}";
    }

    static class SerializableItemStack {
        private String itemAsBase64;
        @SuppressWarnings("unused")
        private int v; // DataVersion (short name to save database space)
        private final String material;
        private final int amount;
        private final String nbt;

        @SuppressWarnings("deprecation")
        public SerializableItemStack(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                this.itemAsBase64 = null;
            } else {
                // PRIMARY: Use Paper/Folia native binary method for 100% integrity and performance
                try {
                    this.itemAsBase64 = Base64.getEncoder().encodeToString(item.serializeAsBytes());
                    // Paper's binary format already includes the DataVersion in the bytes
                } catch (NoSuchMethodError | Exception e) {
                    // FALLBACK: Use NBTAPI only for Spigot servers that lack native binary API
                    try {
                        this.itemAsBase64 = de.tr7zw.changeme.nbtapi.NBT.itemStackToNBT(item).toString();
                        // Store the current server's DataVersion so Paper can run DFU on it later if needed
                        try {
                            this.v = org.bukkit.Bukkit.getUnsafe().getDataVersion();
                        } catch (Exception ignored) {}
                    } catch (Exception e2) {
                        System.err.println("[mc-data-bridge] Item serialization failed: " + e2.getMessage());
                        this.itemAsBase64 = null;
                    }
                }
            }
            this.material = null;
            this.amount = 0;
            this.nbt = null;
        }

        public ItemStack toItemStack() {
            if (this.itemAsBase64 == null) return new ItemStack(Material.AIR);

            // Handle NBT JSON format (Spigot Fallback or Legacy)
            if (this.itemAsBase64.startsWith("{")) {
                try {
                    return de.tr7zw.changeme.nbtapi.NBT.itemStackFromNBT(de.tr7zw.changeme.nbtapi.NBT.parseNBT(this.itemAsBase64));
                } catch (Exception ignored) {}
            }

            // Handle Binary format (Native Paper/Folia)
            try {
                byte[] itemBytes = Base64.getDecoder().decode(this.itemAsBase64);
                if (itemBytes.length > 2) {
                    // Check for GZIP header (1F 8B) used by Paper's binary format
                    if (itemBytes[0] == (byte) 0x1F && itemBytes[1] == (byte) 0x8B) {
                        try {
                            // NATIVE PATH: Try Paper's native deserializer first
                            return ItemStack.deserializeBytes(itemBytes);
                        } catch (NoSuchMethodError | Exception e) {
                            // SPIGOT ADAPTIVE LAYER: If on Spigot, use NBT-API to translate Paper's binary data
                            try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(itemBytes)) {
                                @SuppressWarnings("deprecation")
                                de.tr7zw.changeme.nbtapi.NBTContainer container = new de.tr7zw.changeme.nbtapi.NBTContainer(bis);
                                return de.tr7zw.changeme.nbtapi.NBT.itemStackFromNBT(container);
                            } catch (Exception e2) {
                                System.err.println("[mc-data-bridge] Spigot failed to translate Paper binary item: " + e2.getMessage());
                            }
                        }
                    } 
                    
                    // Fallback for Java/Bukkit Serialization (AC ED)
                    if (itemBytes[0] == (byte) 0xAC && itemBytes[1] == (byte) 0xED) {
                        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(itemBytes);
                             @SuppressWarnings("deprecation")
                             org.bukkit.util.io.BukkitObjectInputStream bois = new org.bukkit.util.io.BukkitObjectInputStream(bis)) {
                            return (ItemStack) bois.readObject();
                        } catch (Exception ignored) {}
                    }
                }
                
                // Final attempt: deserialize directly if detection failed
                return ItemStack.deserializeBytes(itemBytes);
            } catch (NoSuchMethodError e) {
                // If we reach here on Spigot without success, the item is incompatible
                return new ItemStack(Material.AIR);
            } catch (Exception e) {
                // Fall through to legacy NBT handling
            }

            // Legacy NBT handling for old data formats
            if (this.material != null) {
                try {
                    ItemStack item = new ItemStack(Material.valueOf(material), amount);
                    if (nbt != null) {
                        @SuppressWarnings("deprecation")
                        NBTItem nbtItem = new NBTItem(item); // Deprecated: NBTItem is legacy, but required for this
                                                             // legacy data path
                        @SuppressWarnings("deprecation")
                        de.tr7zw.changeme.nbtapi.NBTCompound compound = new NBTContainer(nbt); // Deprecated:
                                                                                               // NBTContainer is legacy
                        nbtItem.mergeCompound(compound);
                        return nbtItem.getItem();
                    }
                    return item;
                } catch (Exception e) {
                    System.err.println(
                            "[mc-data-bridge] Failed to deserialize OLD (NBT) item data! Material: " + this.material);
                    return new ItemStack(Material.AIR);
                }
            }
            return new ItemStack(Material.AIR);
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
