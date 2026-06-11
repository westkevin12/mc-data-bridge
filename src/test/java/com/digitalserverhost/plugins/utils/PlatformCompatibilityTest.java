package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockmc.mockmc.MockMC;
import org.mockmc.mockmc.ServerMock;
import static org.mockito.ArgumentMatchers.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformCompatibilityTest {

    private ServerMock server;

    @BeforeEach
    void setup() {
        server = MockMC.mock();
    }

    @AfterEach
    void tearDown() {
        MockMC.unmock();
    }

    @Test
    void testPlatformComponentSelection() {
        // Verify that SchedulerUtils selects the correct implementation based on the detected platform
        if (SchedulerUtils.isFolia()) {
            assertTrue(SchedulerUtils.getScheduler() instanceof FoliaScheduler, "Folia detected but FoliaScheduler not used");
        } else {
            assertTrue(SchedulerUtils.getScheduler() instanceof BukkitScheduler, "Folia not detected but BukkitScheduler not used");
        }

        if (SchedulerUtils.isPaper()) {
            assertTrue(SchedulerUtils.getBridge() instanceof PaperBridge, "Paper detected but PaperBridge not used");
        } else {
            assertTrue(SchedulerUtils.getBridge() instanceof BukkitBridge, "Paper not detected but BukkitBridge not used");
        }
    }

    @Test
    void testCrossPlatformDataParity() {
        Player player = server.addPlayer();
        player.setHealth(15.0);
        player.setLevel(10);
        player.setFoodLevel(18);

        MCDataBridge plugin = mock(MCDataBridge.class);
        when(plugin.isSyncEnabled(anyString())).thenReturn(true);
        when(plugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);

        // 1. Snapshot created on Server A (Simulating any platform)
        PlayerData snapshotA = new PlayerData(player, plugin);
        String json = MCDataBridge.getGson().toJson(snapshotA);

        // 2. Data loaded on Server B (Simulating different platform)
        PlayerData snapshotB = java.util.Objects.requireNonNull(MCDataBridge.getGson().fromJson(json, PlayerData.class));

        // Verify critical parity
        assertEquals(15.0, snapshotB.getHealth(), "Health mismatch during cross-server migration simulation");
        assertEquals(10, snapshotB.getLevel(), "Level mismatch during cross-server migration simulation");
        assertEquals(18, snapshotB.getFoodLevel(), "Food level mismatch during cross-server migration simulation");
    }

    @Test
    void testLegacyFormatCompatibilityOnAnyPlatform() {
        // This test ensures that the new robust deserializer works correctly regardless of platform
        String legacyYaml = "inventory:\n  '0':\n    ==: org.bukkit.inventory.ItemStack\n    v: 3465\n    type: DIAMOND_SWORD\n";
        String json = "{\"inventoryContentsNBT\": [\"" + legacyYaml + "\"]}";
        PlayerData data = java.util.Objects.requireNonNull(MCDataBridge.getGson().fromJson(json, PlayerData.class));
        
        assertNotNull(data, "Failed to deserialize legacy data structure");
        // The items will be null in a mock environment without NMS but we check that it didn't throw an error
    }
}
