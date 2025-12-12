package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlayerDataConstructorTest {

    @Mock
    private MCDataBridge mockPlugin;

    // Use MockBukkit for Player and Server environment
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
    void testPlayerDataSnapshotWhenSyncEnabled() {
        // Create a MockBukkit player
        be.seeseemelk.mockbukkit.ServerMock server = be.seeseemelk.mockbukkit.MockBukkit.getMock();
        Player player = server.addPlayer();

        // Setup Plugin Toggles
        when(mockPlugin.isSyncEnabled(anyString())).thenReturn(true);
        when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);
        // Note: We might skip some complex ones like advancements if they require
        // complex mocking, but MockBukkit player has default empty inventories etc.

        // Execute
        PlayerData playerData = new PlayerData(player, mockPlugin);

        // Verify that data was read from the player
        // MockBukkit player defaults to 20.0 health
        assertNotNull(playerData);
        assertEquals(20.0, playerData.getHealth(), 0.01);

        // Inventory should be empty but not null (as per deserialize logic)
        assertNotNull(playerData.getInventoryContents());
    }

    @Test
    void testPlayerDataSnapshotWhenSyncDisabled() {
        // Create a MockBukkit player
        be.seeseemelk.mockbukkit.ServerMock server = be.seeseemelk.mockbukkit.MockBukkit.getMock();
        Player player = server.addPlayer();

        // Setup Plugin Toggles to FALSE
        when(mockPlugin.isSyncEnabled(anyString())).thenReturn(false);
        when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(false);

        // Execute
        PlayerData playerData = new PlayerData(player, mockPlugin);

        // Verify successful instantiation and that validation logic skipped fields
        assertNotNull(playerData);
        // Since sync is disabled, health should NOT be updated from player (default 0.0
        // for double field)
        assertEquals(0.0, playerData.getHealth(), 0.01);
    }
}
