package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.google.gson.Gson;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.Serializable;
import java.util.*;

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
    private List<String> inventoryContentsNBT;
    private List<String> armorContentsNBT;
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
        org.bukkit.Statistic.CROUCH_ONE_CM,
        org.bukkit.Statistic.JUMP,
        org.bukkit.Statistic.DAMAGE_DEALT,
        org.bukkit.Statistic.DAMAGE_TAKEN,
        org.bukkit.Statistic.LEAVE_GAME
    };
    
    public PlayerData(Player player, MCDataBridge plugin) {
        if (plugin.isSyncEnabled("health"))
            this.health = player.getHealth();
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
        // These are more expensive, so skipping them saves performance too
        if (plugin.isSyncEnabled("inventory"))
            this.inventoryContentsNBT = serializeItemStackArray(player.getInventory().getContents());
        if (plugin.isSyncEnabled("armor"))
            this.armorContentsNBT = serializeItemStackArray(player.getInventory().getArmorContents());
        if (plugin.isSyncEnabled("potion-effects"))
            this.potionEffects = convertPotionEffectArrayToSerializable(
                    player.getActivePotionEffects().toArray(new PotionEffect[0]));

        // New Features
        if (plugin.isSyncEnabledNewFeature("ender-chest")) {
            this.enderChestContentsNBT = serializeItemStackArray(player.getEnderChest().getContents());
        }

        if (plugin.isSyncEnabledNewFeature("advancements")) {
            this.discoveredRecipes = new ArrayList<>();
            for (NamespacedKey key : player.getDiscoveredRecipes()) {
                this.discoveredRecipes.add(key.toString());
            }

            this.advancements = new HashMap<>();
            Iterator<Advancement> it = org.bukkit.Bukkit.advancementIterator();
            while (it.hasNext()) {
                Advancement adv = it.next();
                if (adv == null) continue;
                AdvancementProgress progress = player.getAdvancementProgress(adv);
                // Only save if there is any progress
                if (!progress.getAwardedCriteria().isEmpty()) {
                    this.advancements.put(adv.getKey().toString(), new ArrayList<>(progress.getAwardedCriteria()));
                }
            }
        }

        if (plugin.isSyncEnabledNewFeature("location")) {
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

        if (plugin.isSyncEnabledNewFeature("statistics")) {
            this.statistics = new HashMap<>();
            // Essential statistics whitelist to avoid performance bottlenecks
            for (org.bukkit.Statistic stat : ESSENTIAL_STATS) {
                if (stat == null) continue;
                try {
                    int value = player.getStatistic(stat);
                    if (value > 0) {
                        this.statistics.put(stat.name(), value);
                    }
                } catch (Exception ignored) {}
            }
        }

        if (plugin.isSyncEnabledNewFeature("pdc")) {
            if (org.bukkit.Bukkit.getServer() != null) {
                try {
                    // Custom PDC serialization logic using modern NBTAPI static methods
                    this.pdcNBT = de.tr7zw.changeme.nbtapi.NBT.get(player, nbt -> {
                        de.tr7zw.changeme.nbtapi.iface.ReadableNBT compound = nbt.getCompound("BukkitValues");
                        return (compound != null) ? compound.toString() : null;
                    });
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to capture PDC for " + player.getName() + ": " + e.getMessage());
                }
            }
        }

        if (plugin.isSyncEnabledNewFeature("flight-gamemode")) {
            this.isFlying = player.isFlying();
            this.allowFlight = player.getAllowFlight();
            this.gameMode = player.getGameMode().name();
        }
    }

    private List<String> serializeItemStackArray(ItemStack[] items) {
        List<String> serializedItems = new ArrayList<>();
        Gson gson = MCDataBridge.getGson();
        if (items == null) {
            return serializedItems;
        }
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                SerializableItemStack serializableItem = new SerializableItemStack(item);
                serializedItems.add(gson.toJson(serializableItem));
            } else {
                serializedItems.add(null);
            }
        }
        return serializedItems;
    }

    // Package-private for testing
    ItemStack[] deserializeItemStackArray(List<String> serializedItems) {
        if (serializedItems == null) {
            return new ItemStack[0];
        }
        ItemStack[] items = new ItemStack[serializedItems.size()];
        Gson gson = MCDataBridge.getGson();
        for (int i = 0; i < serializedItems.size(); i++) {
            String itemJson = serializedItems.get(i);
            if (itemJson != null && !itemJson.isEmpty()) {
                if (itemJson.equals("{}")) {
                    items[i] = new ItemStack(Material.AIR);
                    continue;
                }
                try {
                    SerializableItemStack serializableItem = java.util.Objects.requireNonNull(gson.fromJson(itemJson, SerializableItemStack.class));
                    items[i] = serializableItem.toItemStack();
                } catch (Exception e) {
                    throw new ItemDeserializationException("Failed to deserialize item from JSON: " + itemJson, e);
                }
            } else {
                items[i] = new ItemStack(Material.AIR);
            }
        }
        return items;
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
        private final String material;
        private final int amount;
        private final String nbt;

        public SerializableItemStack(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                this.itemAsBase64 = null;
            } else {
                try {
                    // Use NBTAPI for robust, non-deprecated serialization
                    this.itemAsBase64 = de.tr7zw.changeme.nbtapi.NBT.itemStackToNBT(item).toString();
                } catch (Exception e) {
                    System.err.println("[mc-data-bridge] NBTAPI serialization failed for " + item.getType() + ": " + e.getMessage());
                    this.itemAsBase64 = null;
                    // Fallback to Paper's binary method if NBTAPI fails
                    try {
                        this.itemAsBase64 = Base64.getEncoder().encodeToString(item.serializeAsBytes());
                    } catch (Exception e2) {
                        System.err.println("[mc-data-bridge] Paper binary serialization fallback failed: " + e2.getMessage());
                    }
                }
            }
            this.material = null;
            this.amount = 0;
            this.nbt = null;
        }

        public ItemStack toItemStack() {
            if (this.itemAsBase64 != null) {
                if (this.itemAsBase64.startsWith("{")) {
                    try {
                        return de.tr7zw.changeme.nbtapi.NBT.itemStackFromNBT(de.tr7zw.changeme.nbtapi.NBT.parseNBT(this.itemAsBase64));
                    } catch (Exception ignored) {}
                }

                try {
                    byte[] itemBytes = Base64.getDecoder().decode(this.itemAsBase64);
                    if (itemBytes.length > 2) {
                        // Detect format: AC ED = Java/Bukkit Serialization, 1F 8B = GZIP/Paper Binary
                        if (itemBytes[0] == (byte) 0xAC && itemBytes[1] == (byte) 0xED) {
                            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(itemBytes);
                            // Deprecated in 1.21, but kept for backward compatibility with old data
                            try {
                                @SuppressWarnings("deprecation")
                                org.bukkit.util.io.BukkitObjectInputStream dataInput = new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
                                ItemStack item = (ItemStack) dataInput.readObject();
                                dataInput.close();
                                return item;
                            } catch (Exception e) {
                                // Fall through
                            }
                        } else if (itemBytes[0] == (byte) 0x1F && itemBytes[1] == (byte) 0x8B) {
                            return ItemStack.deserializeBytes(itemBytes);
                        }
                    }
                    // Fallback to trying Paper's method directly if detection fails
                    return ItemStack.deserializeBytes(itemBytes);
                } catch (Exception e) {
                    System.err.println("[mc-data-bridge] Failed to deserialize item from binary format! Fallback to AIR.");
                    e.printStackTrace();
                    // Final fallback to legacy path or AIR
                }
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
