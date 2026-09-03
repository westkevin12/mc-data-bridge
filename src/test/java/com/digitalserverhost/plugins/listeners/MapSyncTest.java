package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.utils.MapSnapshot;
import com.digitalserverhost.plugins.utils.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapSyncTest {

    @Mock
    private MCDataBridge mockPlugin;

    @Mock
    private FileConfiguration mockConfig;

    @BeforeEach
    void setUp() {
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockPlugin.isSyncEnabledNewFeature("maps")).thenReturn(true);
        lenient().when(mockPlugin.getServerId()).thenReturn("survival-1");
    }

    @Test
    void testMapSnapshotSerialization() {
        MapSnapshot snapshot = new MapSnapshot("survival-1", 12, 4, "MAIN", "{\"type\":\"FILLED_MAP\"}");
        assertEquals("survival-1", snapshot.getSourceServerId());
        assertEquals(12, snapshot.getOriginalMapId());
        assertEquals(4, snapshot.getSlot());
        assertEquals("MAIN", snapshot.getInventoryType());
        assertEquals("{\"type\":\"FILLED_MAP\"}", snapshot.getItemNBT());
    }

    @Test
    void testPlayerDataMapGetterSetter() {
        PlayerData data = new PlayerData();
        assertNull(data.getMapsNBT());

        data.setMapsNBT("[{\"sourceServerId\":\"survival-1\",\"originalMapId\":10}]");
        assertNotNull(data.getMapsNBT());
        assertTrue(data.getMapsNBT().contains("survival-1"));
    }

    @Test
    void testMapSyncDisabled_DoesNotSnapshotMaps() {
        org.mockmc.mockmc.MockMC.mock();
        try {
            org.bukkit.entity.Player player = org.mockmc.mockmc.MockMC.getMock().addPlayer("MapPlayer");
            player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP));

            MCDataBridge plugin = mock(MCDataBridge.class);
            lenient().when(plugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);
            when(plugin.isSyncEnabledNewFeature("maps")).thenReturn(false);

            PlayerData data = new PlayerData(player, plugin);
            assertNull(data.getMapsNBT());
        } finally {
            org.mockmc.mockmc.MockMC.unmock();
        }
    }

    @Test
    void testMapSyncEnabled_SnapshotsMaps() {
        org.mockmc.mockmc.MockMC.mock();
        try {
            org.bukkit.entity.Player player = org.mockmc.mockmc.MockMC.getMock().addPlayer("MapPlayer");
            player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP));

            MCDataBridge plugin = mock(MCDataBridge.class);
            FileConfiguration config = mock(FileConfiguration.class);
            lenient().when(plugin.getConfig()).thenReturn(config);
            lenient().when(plugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);
            lenient().when(plugin.getServerId()).thenReturn("survival-1");

            PlayerData data = new PlayerData(player, plugin);
            assertNotNull(data.getMapsNBT());
            assertTrue(data.getMapsNBT().contains("survival-1"));
        } finally {
            org.mockmc.mockmc.MockMC.unmock();
        }
    }
}
