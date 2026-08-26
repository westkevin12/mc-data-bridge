package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockmc.mockmc.MockMC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamemodeInventorySyncTest {

    @Mock
    private MCDataBridge mockPlugin;

    @Mock
    private DatabaseManager mockDatabaseManager;

    @Mock
    private FileConfiguration mockConfig;

    @BeforeEach
    void setUp() {
        MockMC.mock();
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockPlugin.getServerId()).thenReturn("survival-1");
        lenient().when(mockPlugin.isSyncEnabledNewFeature("separate-gamemode-inventories")).thenReturn(true);
        lenient().when(mockPlugin.isSyncEnabled(anyString())).thenReturn(true);
        lenient().when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        MockMC.unmock();
    }

    @Test
    void testGamemodeInventorySeparationDefaults() {
        Player player = MockMC.getMock().addPlayer("StaffMember");
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));

        PlayerData survivalData = new PlayerData(player, mockPlugin);
        assertEquals("SURVIVAL", survivalData.getGameMode());
        assertNotNull(survivalData.getInventoryContentsNBT());

        player.setGameMode(GameMode.CREATIVE);
        PlayerData creativeData = new PlayerData(player, mockPlugin);
        assertEquals("CREATIVE", creativeData.getGameMode());
    }

    @Test
    void testGamemodeInventorySyncDisabled_UsesStandardProfile() {
        Player player = MockMC.getMock().addPlayer("StaffMember2");
        player.setGameMode(GameMode.SURVIVAL);

        MCDataBridge plugin = mock(MCDataBridge.class);
        lenient().when(plugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);
        lenient().when(plugin.isSyncEnabledNewFeature("separate-gamemode-inventories")).thenReturn(false);

        PlayerData data = new PlayerData(player, plugin);
        assertEquals("SURVIVAL", data.getGameMode());
    }
}
