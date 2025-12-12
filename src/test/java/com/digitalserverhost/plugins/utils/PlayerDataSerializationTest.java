package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.utils.PlayerData.SerializableItemStack;
import com.digitalserverhost.plugins.utils.PlayerData.SerializablePotionEffect;
import com.google.gson.Gson;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PlayerDataSerializationTest {

    private static final Gson GSON = new Gson();

    @BeforeAll
    static void setupBukkit() {
        if (org.bukkit.Bukkit.getServer() == null) {
            be.seeseemelk.mockbukkit.MockBukkit.mock();
        }
    }

    @AfterAll
    static void tearDownBukkit() {
        be.seeseemelk.mockbukkit.MockBukkit.unmock();
    }

    @Test
    void testModernItemStackSerializationRoundTrip() {
        // Use real ItemStack with MockBukkit support
        ItemStack realItem = new ItemStack(Material.DIAMOND_SWORD);

        // Instantiate SerializableItemStack
        SerializableItemStack serializableItem = new SerializableItemStack(realItem);

        // Test Gson Serialization
        String json = GSON.toJson(serializableItem);
        assertNotNull(json);

        // Test Deserialization
        SerializableItemStack deserialized = GSON.fromJson(json, SerializableItemStack.class);
        ItemStack resultItem = deserialized.toItemStack();

        assertNotNull(resultItem);
        assertEquals(Material.DIAMOND_SWORD, resultItem.getType());
    }

    @Test
    void testAirAndNullItemHandling() {
        // Test null
        SerializableItemStack nullItemWrapper = new SerializableItemStack(null);
        assertEquals(Material.AIR, nullItemWrapper.toItemStack().getType());

        // Test AIR
        ItemStack airItem = new ItemStack(Material.AIR);
        SerializableItemStack airItemWrapper = new SerializableItemStack(airItem);
        assertEquals(Material.AIR, airItemWrapper.toItemStack().getType());
    }

    @Test
    void testPotionEffectSerializationRoundTrip() {
        // Use real PotionEffect with MockBukkit support
        PotionEffect realEffect = new PotionEffect(PotionEffectType.SPEED, 200, 1, true, false, true);

        // Serialize
        SerializablePotionEffect serializableEffect = new SerializablePotionEffect(realEffect);

        // Test toPotionEffect()
        PotionEffect resultEffect = serializableEffect.toPotionEffect();

        assertNotNull(resultEffect);
        assertEquals(PotionEffectType.SPEED, resultEffect.getType());
        assertEquals(200, resultEffect.getDuration());
        assertEquals(1, resultEffect.getAmplifier());
        assertTrue(resultEffect.isAmbient());
        assertFalse(resultEffect.hasParticles());
        assertTrue(resultEffect.hasIcon());
    }

    @Test
    void testInventoryArrayStructureIntegrity() {
        // Create valid serialized JSON using the helper class
        SerializableItemStack validItem = new SerializableItemStack(new ItemStack(Material.STONE));
        String validItemJson = GSON.toJson(validItem);

        List<String> cachedData = Arrays.asList(validItemJson, null, "{}", validItemJson);

        // We need an instance of PlayerData to test the package-private method.
        // We can pass nulls for player/plugin since we only call a utility method that
        // uses Gson (static/instance field)
        // actually PlayerData.deserializeItemStackArray uses MCDataBridge.getGson()
        // which is static.
        // But the method is instance method.
        // We'll just create a dummy instance. The constructor might crash if checks
        // run.
        // We need a dummy player and plugin.

        // Refactoring PlayerData separated the constructor logic.
        // We can mock the dependencies just to pass the constructor.
        org.bukkit.entity.Player mockPlayer = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        com.digitalserverhost.plugins.MCDataBridge mockPlugin = org.mockito.Mockito
                .mock(com.digitalserverhost.plugins.MCDataBridge.class);

        // Ensure constructor doesn't fail
        org.mockito.Mockito.when(mockPlugin.isSyncEnabled(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        org.mockito.Mockito.when(mockPlugin.isSyncEnabledNewFeature(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);

        PlayerData playerData = new PlayerData(mockPlayer, mockPlugin);

        ItemStack[] result = playerData.deserializeItemStackArray(cachedData);

        assertEquals(4, result.length);
        assertEquals(Material.STONE, result[0].getType());
        assertEquals(Material.AIR, result[1].getType()); // null -> AIR
        assertEquals(Material.AIR, result[2].getType()); // "{}" -> AIR
        assertEquals(Material.STONE, result[3].getType());
    }

    @Test
    void testItemStackWithCustomNBTAndMetaRoundTrip() {
        // Create an item with Meta
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
            meta.lore(Arrays.asList(
                    net.kyori.adventure.text.Component.text("The legendary sword"),
                    net.kyori.adventure.text.Component.text("of King Arthur")));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
            item.setItemMeta(meta);
        }

        // Serialize
        SerializableItemStack serializableItem = new SerializableItemStack(item);

        // Deserialize
        String json = GSON.toJson(serializableItem);
        SerializableItemStack deserialized = GSON.fromJson(json, SerializableItemStack.class);
        ItemStack result = deserialized.toItemStack();

        // Verify
        assertNotNull(result);
        assertEquals(Material.DIAMOND_SWORD, result.getType());
        assertTrue(result.hasItemMeta());
        org.bukkit.inventory.meta.ItemMeta resultMeta = result.getItemMeta();
        assertEquals(net.kyori.adventure.text.Component.text("Excalibur"), resultMeta.displayName());
        assertEquals(2, resultMeta.lore().size());
        assertEquals(net.kyori.adventure.text.Component.text("The legendary sword"), resultMeta.lore().get(0));
        assertEquals(5, resultMeta.getEnchantLevel(org.bukkit.enchantments.Enchantment.SHARPNESS));
    }
}
