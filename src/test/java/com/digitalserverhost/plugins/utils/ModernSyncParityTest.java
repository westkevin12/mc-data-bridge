package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModernSyncParityTest {

    @Test
    @SuppressWarnings("all")
    void testFullPlayerParityWithModernComponents() {
        System.out.println("DEBUG: Starting testFullPlayerParityWithModernComponents");
        MockBukkit.mock();
        try {
            MCDataBridge mockPlugin = mock(MCDataBridge.class);
            when(mockPlugin.isSyncEnabled(anyString())).thenReturn(false);
            when(mockPlugin.isSyncEnabled("inventory")).thenReturn(true);
            when(mockPlugin.isSyncEnabled("health")).thenReturn(true);
            when(mockPlugin.isSyncEnabled("food-level")).thenReturn(true);
            when(mockPlugin.isSyncEnabled("experience")).thenReturn(true);
            when(mockPlugin.isSyncEnabled("potion-effects")).thenReturn(true);
            when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("MCDataBridge"));

            when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(false);
            when(mockPlugin.isDebugMode()).thenReturn(true);

            PlayerMock source = MockBukkit.getMock().addPlayer("SourcePlayer");

            ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Legendary Plate"));
            meta.lore(List.of(Component.text("Forged in fire")));

            AttributeModifier modifier = new AttributeModifier(
                    new NamespacedKey("databridge", "test_mod"),
                    10.0,
                    AttributeModifier.Operation.ADD_NUMBER);
            meta.addAttributeModifier(Attribute.ARMOR, modifier);

            NamespacedKey itemKey = new NamespacedKey("databridge", "item_data");
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "ItemValue");

            item.setItemMeta(meta);
            source.getInventory().setItem(0, item);

            source.getPersistentDataContainer().set(new NamespacedKey("databridge", "player_data"),
                    PersistentDataType.STRING, "PlayerValue");
            source.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1000, 1));

            PlayerData data = new PlayerData(source, mockPlugin);
            Gson gson = new GsonBuilder().create();
            String json = gson.toJson(data);

            PlayerMock target = MockBukkit.getMock().addPlayer("TargetPlayer");
            PlayerData deserializedData = gson.fromJson(json, PlayerData.class);

            target.setHealth(deserializedData.getHealth());
            target.setFoodLevel(deserializedData.getFoodLevel());
            target.getInventory().setContents(deserializedData.getInventoryContents());

            for (PotionEffect effect : deserializedData.getPotionEffects()) {
                target.addPotionEffect(effect);
            }

            ItemStack targetItem = target.getInventory().getItem(0);
            assertNotNull(targetItem);
            assertEquals(Material.DIAMOND_CHESTPLATE, targetItem.getType());

            ItemMeta targetMeta = targetItem.getItemMeta();
            assertEquals(Component.text("Legendary Plate"), targetMeta.displayName());
            assertTrue(targetMeta.hasAttributeModifiers());

            String storedValue = targetMeta.getPersistentDataContainer().get(itemKey,
                    (PersistentDataType<String, String>) PersistentDataType.STRING);
            assertEquals("ItemValue", storedValue);

            System.out.println("DEBUG: Test completed successfully");
        } catch (Throwable t) {
            System.out.println("DEBUG: Test failed with: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
            throw t;
        } finally {
            MockBukkit.unmock();
        }
    }
}
