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

import java.util.UUID;

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

    private org.mockito.MockedStatic<com.digitalserverhost.plugins.utils.SchedulerUtils> mockedSchedulerUtils;

    @BeforeEach
    void setUp() {
        MockMC.mock();
        mockedSchedulerUtils = mockStatic(com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mockedSchedulerUtils.when(() -> com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(any(), any()))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(1);
                    runnable.run();
                    return null;
                });
        mockedSchedulerUtils.when(() -> com.digitalserverhost.plugins.utils.SchedulerUtils.runOnEntity(any(), any(), any()))
                .thenAnswer(invocation -> {
                    Runnable runnable = invocation.getArgument(2);
                    runnable.run();
                    return null;
                });

        lenient().when(mockPlugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("GamemodeInventorySyncTest"));
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockPlugin.getServerId()).thenReturn("survival-1");
        lenient().when(mockPlugin.isSyncEnabledNewFeature("separate-gamemode-inventories")).thenReturn(true);
        lenient().when(mockPlugin.isSyncEnabled(anyString())).thenReturn(true);
        lenient().when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(true);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (mockedSchedulerUtils != null) {
            mockedSchedulerUtils.close();
        }
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

    @Test
    void testGamemodeInventoryMigrationFromUnifiedTable() throws Exception {
        com.zaxxer.hikari.HikariDataSource mockDs = mock(com.zaxxer.hikari.HikariDataSource.class);
        java.sql.Connection mockConn = mock(java.sql.Connection.class);
        java.sql.PreparedStatement mockGamemodeStmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet mockGamemodeRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement mockUnifiedStmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet mockUnifiedRs = mock(java.sql.ResultSet.class);
        java.sql.PreparedStatement mockUpsertStmt = mock(java.sql.PreparedStatement.class);

        java.sql.PreparedStatement mockDefaultStmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet mockDefaultRs = mock(java.sql.ResultSet.class);
        lenient().when(mockConn.prepareStatement(anyString())).thenReturn(mockDefaultStmt);
        lenient().when(mockDefaultStmt.executeQuery()).thenReturn(mockDefaultRs);
        lenient().when(mockDefaultRs.next()).thenReturn(false);

        lenient().when(mockDs.getConnection()).thenReturn(mockConn);
        java.sql.PreparedStatement mockLegacyStmt = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet mockLegacyRs = mock(java.sql.ResultSet.class);
        lenient().when(mockConn.prepareStatement(contains("SELECT data, data_checksum FROM"))).thenReturn(mockLegacyStmt);
        lenient().when(mockLegacyStmt.executeQuery()).thenReturn(mockLegacyRs);
        lenient().when(mockLegacyRs.next()).thenReturn(false);

        lenient().when(mockConn.prepareStatement(contains("FROM `databridge_gamemode_inventories`"))).thenReturn(mockGamemodeStmt);
        lenient().when(mockGamemodeStmt.executeQuery()).thenReturn(mockGamemodeRs);
        lenient().when(mockGamemodeRs.next()).thenReturn(false); // No existing gamemode row

        lenient().when(mockConn.prepareStatement(contains("FROM `databridge_inventories`"))).thenReturn(mockUnifiedStmt);
        lenient().when(mockUnifiedStmt.executeQuery()).thenReturn(mockUnifiedRs);
        lenient().when(mockUnifiedRs.next()).thenReturn(true); // Existing unified row present
        lenient().when(mockUnifiedRs.getBytes("inventory_blob")).thenReturn("[\"item1\"]".getBytes());
        lenient().when(mockUnifiedRs.getBytes("armor_blob")).thenReturn(null);
        lenient().when(mockUnifiedRs.getBytes("ender_chest_blob")).thenReturn(null);

        lenient().when(mockConn.prepareStatement(contains("INSERT INTO `databridge_gamemode_inventories`"))).thenReturn(mockUpsertStmt);

        DatabaseManager dbManager = new DatabaseManager(mockDs, "player_data", 60000L);
        UUID uuid = UUID.randomUUID();
        PlayerData data = new PlayerData();
        data.setGameMode("SURVIVAL");

        PlayerData loaded = dbManager.loadPlayerDataComponents(mockPlugin, uuid);
        assertNotNull(loaded);
        assertNotNull(loaded.getInventoryContentsNBT());
        assertEquals(1, loaded.getInventoryContentsNBT().size());
        assertEquals("item1", loaded.getInventoryContentsNBT().get(0));

        // Verify that the inventory was migrated into databridge_gamemode_inventories
        verify(mockConn).prepareStatement(contains("INSERT INTO `databridge_gamemode_inventories`"));
        verify(mockUpsertStmt).executeUpdate();
    }

    @Test
    void testOnGameModeChangeSwapsInventory() throws Exception {
        Player player = MockMC.getMock().addPlayer("Swapper");
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));

        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
        org.bukkit.event.player.PlayerGameModeChangeEvent event = new org.bukkit.event.player.PlayerGameModeChangeEvent(player, GameMode.CREATIVE);

        PlayerData creativeData = new PlayerData();
        creativeData.setGameMode("CREATIVE");
        creativeData.setInventoryContentsNBT(java.util.Collections.singletonList(null));

        when(mockDatabaseManager.loadPlayerDataComponents(eq(mockPlugin), eq(player.getUniqueId()), eq(player.getName()), eq("CREATIVE")))
            .thenReturn(creativeData);

        listener.onGameModeChange(event);

        verify(mockDatabaseManager).saveInventoryComponent(eq(mockPlugin), any(PlayerData.class), eq(player.getUniqueId()));
        verify(mockDatabaseManager).loadPlayerDataComponents(eq(mockPlugin), eq(player.getUniqueId()), eq(player.getName()), eq("CREATIVE"));
    }

    @Test
    void testApplyPlayerData_SuppressesOnGameModeChange() throws Exception {
        Player player = MockMC.getMock().addPlayer("Joiner");
        player.setGameMode(GameMode.SURVIVAL);

        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        PlayerData joinData = new PlayerData();
        joinData.setGameMode("CREATIVE");

        listener.applyPlayerData(player, joinData);

        // Verify that applyPlayerData set player to CREATIVE without triggering onGameModeChange inventory save
        assertEquals(GameMode.CREATIVE, player.getGameMode());
        verify(mockDatabaseManager, never()).saveInventoryComponent(eq(mockPlugin), any(PlayerData.class), eq(player.getUniqueId()));
    }

    @Test
    void testApplyPlayerData_GamemodeMismatch_LoadsTargetGamemodeInventory() throws Exception {
        Player player = MockMC.getMock().addPlayer("JoinerSurvival");
        player.setGameMode(GameMode.SURVIVAL);

        // Suppose flight-gamemode sync is disabled or server forced SURVIVAL
        lenient().when(mockPlugin.isSyncEnabledNewFeature("flight-gamemode")).thenReturn(false);

        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        PlayerData joinData = new PlayerData();
        joinData.setGameMode("CREATIVE");
        joinData.setInventoryContentsNBT(java.util.Collections.singletonList("creativeItemJson"));

        listener.applyPlayerData(player, joinData);

        // Because player remains in SURVIVAL mode and gamemode sync is disabled, loadAndApplyGamemodeInventory should be triggered for SURVIVAL
        assertEquals(GameMode.SURVIVAL, player.getGameMode());
        verify(mockDatabaseManager).loadPlayerDataComponents(eq(mockPlugin), eq(player.getUniqueId()), eq(player.getName()), eq("SURVIVAL"));
    }
}
