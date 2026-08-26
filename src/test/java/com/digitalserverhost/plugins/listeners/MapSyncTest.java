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
        lenient().when(mockConfig.getString(eq("maps.mode"), anyString())).thenReturn("return");
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
}
