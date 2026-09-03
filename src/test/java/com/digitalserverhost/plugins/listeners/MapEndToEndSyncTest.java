package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;

import org.mockmc.mockmc.MockMC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapEndToEndSyncTest {

    @BeforeEach
    void setUp() {
        MockMC.mock();
    }

    @AfterEach
    void tearDown() {
        MockMC.unmock();
    }

    @Test
    void testEndToEndMapSyncLifecycle_DepartureToArrival() {
        // --- PHASE 1: Departure from Server A (Resource) ---
        Player playerServerA = MockMC.getMock().addPlayer("HopPlayer");
        ItemStack sourceMap = new ItemStack(Material.FILLED_MAP);
        MapMeta sourceMeta = (MapMeta) sourceMap.getItemMeta();
        assertNotNull(sourceMeta);
        sourceMeta.setDisplayName("Treasure Map Artwork");
        sourceMeta.setLore(java.util.List.of("Custom Lore"));
        sourceMap.setItemMeta(sourceMeta);
        playerServerA.getInventory().setItem(0, sourceMap);

        MCDataBridge pluginServerA = mock(MCDataBridge.class);
        FileConfiguration configA = mock(FileConfiguration.class);
        lenient().when(pluginServerA.getConfig()).thenReturn(configA);
        lenient().when(configA.getString(eq("table-prefix"), anyString())).thenReturn("");
        lenient().when(configA.getString(eq("database.serialization-format"), anyString())).thenReturn("json");
        lenient().when(configA.getString(eq("database.type"), anyString())).thenReturn("sqlite");
        lenient().when(configA.getString(eq("database.sqlite-file"), anyString())).thenReturn("test.db");
        lenient().when(pluginServerA.isSyncEnabledNewFeature("maps")).thenReturn(true);
        lenient().when(pluginServerA.isSyncEnabled("inventory")).thenReturn(true);
        lenient().when(pluginServerA.getServerId()).thenReturn("resource-1");

        // Take snapshot on Server A
        PlayerData snapshotA = new PlayerData(playerServerA, pluginServerA);
        assertNotNull(snapshotA.getMapsNBT(), "Maps NBT snapshot must be populated on departure");
        assertTrue(snapshotA.getMapsNBT().contains("resource-1"), "Snapshot must tag origin server");

        // --- PHASE 2: Database Merge ---
        DatabaseManager dbManager = mock(DatabaseManager.class);
        when(dbManager.mergeMapsNbt(anyString(), any())).thenCallRealMethod();

        String mergedMapsNbt = dbManager.mergeMapsNbt(snapshotA.getMapsNBT(), null);
        assertNotNull(mergedMapsNbt, "Merged maps NBT must not be null");

        // --- PHASE 3: Arrival on Server B (Towny) ---
        Player playerServerB = MockMC.getMock().addPlayer("HopPlayer");
        MCDataBridge pluginServerB = mock(MCDataBridge.class);
        FileConfiguration configB = mock(FileConfiguration.class);
        lenient().when(pluginServerB.getConfig()).thenReturn(configB);
        lenient().when(pluginServerB.isSyncEnabledNewFeature("maps")).thenReturn(true);
        lenient().when(pluginServerB.isSyncEnabled("inventory")).thenReturn(true);
        lenient().when(pluginServerB.getServerId()).thenReturn("towny-1");

        // Reconstruct payload on Server B
        PlayerData dataOnArrival = new PlayerData();
        dataOnArrival.setInventoryContentsNBT(snapshotA.getInventoryContentsNBT());
        dataOnArrival.setMapsNBT(mergedMapsNbt);

        PlayerListener listenerB = new PlayerListener(dbManager, pluginServerB);

        try {
            java.lang.reflect.Method applyInvMethod = PlayerListener.class.getDeclaredMethod("applyInventory", Player.class, PlayerData.class);
            applyInvMethod.setAccessible(true);
            applyInvMethod.invoke(listenerB, playerServerB, dataOnArrival);
        } catch (Exception e) {
            fail("applyInventory on Server B failed: " + e.getMessage());
        }

        // --- PHASE 4: Verification on Server B ---
        ItemStack restoredMap = playerServerB.getInventory().getItem(0);
        assertNotNull(restoredMap, "Restored map item on Server B must exist");
        assertEquals(Material.FILLED_MAP, restoredMap.getType());

        MapMeta restoredMeta = (MapMeta) restoredMap.getItemMeta();
        assertNotNull(restoredMeta, "Restored MapMeta must not be null");
        assertEquals("Treasure Map Artwork", restoredMeta.getDisplayName(), "Map display name must persist");
        assertEquals(java.util.List.of("Custom Lore"), restoredMeta.getLore(), "Map lore must persist");
        assertTrue(restoredMeta.hasMapView(), "Restored map item MUST have a valid local MapView assigned on Server B");
    }

    @Test
    void testEndToEndMapSyncLifecycle_MapLockingEnforcement() {
        Player playerServerA = MockMC.getMock().addPlayer("LockTestPlayer");
        ItemStack sourceMap = new ItemStack(Material.FILLED_MAP);
        MapMeta sourceMeta = (MapMeta) sourceMap.getItemMeta();
        assertNotNull(sourceMeta);
        sourceMeta.setDisplayName("Locked Art");
        sourceMap.setItemMeta(sourceMeta);
        playerServerA.getInventory().setItem(0, sourceMap);

        MCDataBridge pluginServerA = mock(MCDataBridge.class);
        FileConfiguration configA = mock(FileConfiguration.class);
        lenient().when(pluginServerA.getConfig()).thenReturn(configA);
        lenient().when(configA.getBoolean(eq("maps.lock-global-maps"), anyBoolean())).thenReturn(true);
        lenient().when(pluginServerA.isSyncEnabledNewFeature("maps")).thenReturn(true);
        lenient().when(pluginServerA.isSyncEnabled("inventory")).thenReturn(true);
        lenient().when(pluginServerA.getServerId()).thenReturn("server-a");

        PlayerData snapshotA = new PlayerData(playerServerA, pluginServerA);

        Player playerServerB = MockMC.getMock().addPlayer("LockTestPlayer");
        MCDataBridge pluginServerB = mock(MCDataBridge.class);
        FileConfiguration configB = mock(FileConfiguration.class);
        lenient().when(pluginServerB.getConfig()).thenReturn(configB);
        lenient().when(configB.getBoolean(eq("maps.lock-global-maps"), anyBoolean())).thenReturn(true);
        lenient().when(pluginServerB.isSyncEnabledNewFeature("maps")).thenReturn(true);
        lenient().when(pluginServerB.isSyncEnabled("inventory")).thenReturn(true);
        lenient().when(pluginServerB.getServerId()).thenReturn("server-b");

        DatabaseManager dbManager = mock(DatabaseManager.class);
        PlayerListener listenerB = new PlayerListener(dbManager, pluginServerB);

        PlayerData dataOnArrival = new PlayerData();
        dataOnArrival.setInventoryContentsNBT(snapshotA.getInventoryContentsNBT());
        dataOnArrival.setMapsNBT(snapshotA.getMapsNBT());

        try {
            java.lang.reflect.Method applyInvMethod = PlayerListener.class.getDeclaredMethod("applyInventory", Player.class, PlayerData.class);
            applyInvMethod.setAccessible(true);
            applyInvMethod.invoke(listenerB, playerServerB, dataOnArrival);
        } catch (Exception e) {
            fail("applyInventory failed: " + e.getMessage());
        }

        ItemStack restoredMap = playerServerB.getInventory().getItem(0);
        assertNotNull(restoredMap);
        MapMeta restoredMeta = (MapMeta) restoredMap.getItemMeta();
        assertNotNull(restoredMeta);
        assertTrue(restoredMeta.hasMapView(), "Restored map must have a MapView");
        assertTrue(restoredMeta.getMapView().isLocked(), "MapView should be locked when maps.lock-global-maps is true");
    }
}
