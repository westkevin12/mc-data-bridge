package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.google.gson.Gson;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    public PlayerData(double health, int foodLevel, float saturation, float exhaustion, int totalExperience, float exp, int level,
                      ItemStack[] inventoryContents,
                      ItemStack[] armorContents,
                      PotionEffect[] potionEffects) {
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.totalExperience = totalExperience;
        this.exp = exp;
        this.level = level;
        this.inventoryContentsNBT = serializeItemStackArray(inventoryContents);
        this.armorContentsNBT = serializeItemStackArray(armorContents);
        this.potionEffects = convertPotionEffectArrayToSerializable(potionEffects);
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
                // Handle old, invalid data
                if (itemJson.equals("{}")) {
                    items[i] = null;
                    continue;
                }
                try {
                    SerializableItemStack serializableItem = gson.fromJson(itemJson, SerializableItemStack.class);
                    items[i] = serializableItem.toItemStack();
                } catch (Exception e) {
                    throw new ItemDeserializationException("Failed to deserialize item from JSON: " + itemJson, e);
                }
            } else {
                items[i] = null;
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

    public PotionEffect[] getPotionEffects() {
        return convertSerializablePotionEffectArrayToPotionEffect(potionEffects);
    }

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
        private final String material;
        private final int amount;
        private final String nbt;

        public SerializableItemStack(ItemStack item) {
            this.material = item.getType().name();
            this.amount = item.getAmount();
            String nbtString = new NBTItem(item).toString();
            if (nbtString.equals("{}")) {
                this.nbt = null;
            } else {
                this.nbt = nbtString;
            }
        }

        public ItemStack toItemStack() {
            ItemStack item = new ItemStack(Material.valueOf(material), amount);
            if (nbt != null) {
                NBTItem nbtItem = new NBTItem(item);
                nbtItem.mergeCompound(new NBTContainer(nbt));
                return nbtItem.getItem();
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
            this.type = effect.getType().getName();
            this.duration = effect.getDuration();
            this.amplifier = effect.getAmplifier();
            this.ambient = effect.isAmbient();
            this.particles = effect.hasParticles();
            this.icon = effect.hasIcon();
        }

        public PotionEffect toPotionEffect() {
            PotionEffectType effectType = PotionEffectType.getByName(type);
            if (effectType == null) {
                return null;
            }
            return new PotionEffect(effectType, duration, amplifier, ambient, particles, icon);
        }

        @Override
        public String toString() {
            return "SerializablePotionEffect{" +
                    "type='" + type + "\'" +
                    ", duration=" + duration +
                    ", amplifier=" + amplifier +
                    ", ambient=" + ambient +
                    ", particles=" + particles +
                    ", icon=" + icon +
                    '}';
        }
    }
}
