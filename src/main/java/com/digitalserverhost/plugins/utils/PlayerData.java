package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.google.gson.Gson;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Base64;

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
    private SerializablePotionEffect[] potionEffects;

    /**
     * ★★★ NEW CONSTRUCTOR ★★★
     * Snapshots a live player's data.
     */
    public PlayerData(Player player) {
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exhaustion = player.getExhaustion();
        this.totalExperience = player.getTotalExperience();
        this.exp = player.getExp();
        this.level = player.getLevel();
        this.inventoryContentsNBT = serializeItemStackArray(player.getInventory().getContents());
        this.armorContentsNBT = serializeItemStackArray(player.getInventory().getArmorContents());
        this.potionEffects = convertPotionEffectArrayToSerializable(player.getActivePotionEffects().toArray(new PotionEffect[0]));
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

    private ItemStack[] deserializeItemStackArray(List<String> serializedItems) {
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
                    SerializableItemStack serializableItem = gson.fromJson(itemJson, SerializableItemStack.class);
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

    public double getHealth() { return health; }
    public int getFoodLevel() { return foodLevel; }
    public float getSaturation() { return saturation; }
    public float getExhaustion() { return exhaustion; }
    public int getTotalExperience() { return totalExperience; }
    public float getExp() { return exp; }
    public int getLevel() { return level; }
    public ItemStack[] getInventoryContents() { return deserializeItemStackArray(inventoryContentsNBT); }
    public ItemStack[] getArmorContents() { return deserializeItemStackArray(armorContentsNBT); }
    public PotionEffect[] getPotionEffects() { return convertSerializablePotionEffectArrayToPotionEffect(potionEffects); }


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

    private PotionEffect[] convertSerializablePotionEffectArrayToPotionEffect(SerializablePotionEffect[] serializableEffects) {
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
                ", inventoryContentsNBT=" + (inventoryContentsNBT != null ? inventoryContentsNBT.size() : "null") + " items" +
                ", armorContentsNBT=" + (armorContentsNBT != null ? armorContentsNBT.size() : "null") + " items" +
                ", potionEffects=" + Arrays.toString(potionEffects) +
                "}";
    }

    private static class SerializableItemStack {
        private final String itemAsBase64;
        private final String material;
        private final int amount;
        private final String nbt;

        public SerializableItemStack(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                this.itemAsBase64 = null;
            } else {
                this.itemAsBase64 = Base64.getEncoder().encodeToString(item.serializeAsBytes());
            }
            this.material = null;
            this.amount = 0;
            this.nbt = null;
        }

        public ItemStack toItemStack() {
            if (this.itemAsBase64 != null) {
                try {
                    byte[] itemBytes = Base64.getDecoder().decode(this.itemAsBase64);
                    return ItemStack.deserializeBytes(itemBytes);
                } catch (Exception e) {
                    System.err.println("[mc-data-bridge] Failed to deserialize item from Base64! Data: " + this.itemAsBase64);
                    return new ItemStack(Material.AIR);
                }
            }

            // Legacy NBT handling for old data formats
            if (this.material != null) {
                try {
                    ItemStack item = new ItemStack(Material.valueOf(material), amount);
                    if (nbt != null) {
                        @SuppressWarnings("deprecation")
                        NBTItem nbtItem = new NBTItem(item); // Deprecated: NBTItem is legacy, but required for this legacy data path
                        @SuppressWarnings("deprecation")
                        de.tr7zw.changeme.nbtapi.NBTCompound compound = new NBTContainer(nbt); // Deprecated: NBTContainer is legacy
                        nbtItem.mergeCompound(compound);
                        return nbtItem.getItem();
                    }
                    return item;
                } catch (Exception e) {
                    System.err.println("[mc-data-bridge] Failed to deserialize OLD (NBT) item data! Material: " + this.material);
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
            // Using deprecated getName() to maintain compatibility with existing database data
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
                    '}' ;
        }
    }
}
