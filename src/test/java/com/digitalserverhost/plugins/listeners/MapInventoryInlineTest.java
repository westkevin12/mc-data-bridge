package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.utils.MapSnapshot;
import com.digitalserverhost.plugins.utils.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import org.mockmc.mockmc.MockMC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapInventoryInlineTest {

    @BeforeEach
    void setUp() {
        MockMC.mock();
    }

    @AfterEach
    void tearDown() {
        MockMC.unmock();
    }

    @Test
    void testProcessMapItemsInline_CreatesCleanMapWithValidMapView() {
        Player player = MockMC.getMock().addPlayer("TestMapPlayer");
        MCDataBridge plugin = mock(MCDataBridge.class);
        org.bukkit.configuration.file.FileConfiguration config = mock(org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(config.getBoolean(eq("maps.lock-global-maps"), anyBoolean())).thenReturn(true);
        when(plugin.isSyncEnabledNewFeature("maps")).thenReturn(true);
        when(plugin.isSyncEnabled("inventory")).thenReturn(true);

        // Create initial item array containing raw filled map
        ItemStack[] items = new ItemStack[36];
        ItemStack rawMap = new ItemStack(Material.FILLED_MAP);
        MapMeta rawMeta = (MapMeta) rawMap.getItemMeta();
        assertNotNull(rawMeta);
        rawMeta.setDisplayName("Custom Map Title");
        rawMap.setItemMeta(rawMeta);
        items[0] = rawMap;

        // Create sample map snapshot with 16384 byte canvas
        byte[] samplePixels = new byte[16384];
        samplePixels[0] = (byte) 12; // Color sample
        MapSnapshot snapshot = new MapSnapshot("resource-1", 9, 0, "MAIN", "{\"type\":\"FILLED_MAP\"}");
        snapshot.setCanvasPixels(samplePixels);
        MapSnapshot[] snapshots = new MapSnapshot[]{snapshot};

        PlayerData data = mock(PlayerData.class);
        when(data.getInventoryContents()).thenReturn(items);
        when(data.getMapsNBT()).thenReturn(com.digitalserverhost.plugins.MCDataBridge.getGson().toJson(snapshots));

        // Instantiate PlayerListener
        PlayerListener listener = new PlayerListener(null, plugin);

        // Process maps inline
        try {
            java.lang.reflect.Method applyInvMethod = PlayerListener.class.getDeclaredMethod("applyInventory", Player.class, PlayerData.class);
            applyInvMethod.setAccessible(true);
            applyInvMethod.invoke(listener, player, data);
        } catch (Exception e) {
            fail("applyInventory invocation failed: " + e.getMessage());
        }

        // Verify slot 0 item
        ItemStack restoredItem = player.getInventory().getItem(0);
        assertNotNull(restoredItem, "Restored map item should not be null");
        assertEquals(Material.FILLED_MAP, restoredItem.getType());
        assertTrue(restoredItem.getItemMeta() instanceof MapMeta, "ItemMeta should be MapMeta");
        MapMeta restoredMeta = (MapMeta) restoredItem.getItemMeta();
        assertEquals("Custom Map Title", restoredMeta.getDisplayName(), "Display name should be preserved");
        assertTrue(restoredMeta.hasMapView(), "Restored map MUST have a valid local MapView assigned");
    }

    @Test
    void testEnderChestAndStackedMapsEdgeCase() {
        Player player = MockMC.getMock().addPlayer("EnderPlayer");
        MCDataBridge plugin = mock(MCDataBridge.class);
        org.bukkit.configuration.file.FileConfiguration config = mock(org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(config.getBoolean(eq("maps.lock-global-maps"), anyBoolean())).thenReturn(true);
        when(plugin.isSyncEnabledNewFeature("maps")).thenReturn(true);
        when(plugin.isSyncEnabledNewFeature("ender-chest")).thenReturn(true);

        // Ender chest map stack of 5 with custom lore
        ItemStack[] ecItems = new ItemStack[27];
        ItemStack stackedMap = new ItemStack(Material.FILLED_MAP, 5);
        MapMeta ecMeta = (MapMeta) stackedMap.getItemMeta();
        assertNotNull(ecMeta);
        ecMeta.setLore(java.util.List.of("Rare Artwork"));
        stackedMap.setItemMeta(ecMeta);
        ecItems[2] = stackedMap;

        byte[] pixels = new byte[16384];
        MapSnapshot snapshot = new MapSnapshot("resource-1", 100, 2, "ENDERCHEST", "{\"type\":\"FILLED_MAP\"}");
        snapshot.setCanvasPixels(pixels);

        PlayerData data = mock(PlayerData.class);
        when(data.getEnderChestContents()).thenReturn(ecItems);
        when(data.getMapsNBT()).thenReturn(MCDataBridge.getGson().toJson(new MapSnapshot[]{snapshot}));

        PlayerListener listener = new PlayerListener(null, plugin);

        try {
            java.lang.reflect.Method applyInvMethod = PlayerListener.class.getDeclaredMethod("applyInventory", Player.class, PlayerData.class);
            applyInvMethod.setAccessible(true);
            applyInvMethod.invoke(listener, player, data);
        } catch (Exception e) {
            fail("applyInventory invocation failed: " + e.getMessage());
        }

        ItemStack restored = player.getEnderChest().getItem(2);
        assertNotNull(restored);
        assertEquals(5, restored.getAmount(), "Stack count of 5 must be preserved");
        MapMeta restoredMeta = (MapMeta) restored.getItemMeta();
        assertNotNull(restoredMeta);
        assertEquals(java.util.List.of("Rare Artwork"), restoredMeta.getLore(), "Lore must be preserved");
    }
}
