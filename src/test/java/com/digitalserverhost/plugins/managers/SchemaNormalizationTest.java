package com.digitalserverhost.plugins.managers;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.utils.PlayerData;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchemaNormalizationTest {

    @Mock
    private HikariDataSource mockDataSource;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockStatement;
    @Mock
    private ResultSet mockResultSet;
    @Mock
    private MCDataBridge mockPlugin;
    @Mock
    private FileConfiguration mockConfig;

    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setup() throws SQLException {
        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);
    }

    private void setPrivateField(Object obj, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    @Test
    void testBinarySerializationParity() throws Exception {
        DatabaseManager dbManagerJson = new DatabaseManager(mockDataSource, "player_data", 60000L);
        List<String> list = Arrays.asList(
            Base64.getEncoder().encodeToString("item1".getBytes()),
            Base64.getEncoder().encodeToString("item2".getBytes()),
            null,
            Base64.getEncoder().encodeToString("item3".getBytes())
        );
        byte[] jsonBlob = dbManagerJson.serializeListToBlob(list);
        assertNotNull(jsonBlob);
        List<String> deserializedJson = dbManagerJson.deserializeListFromBlob(jsonBlob);
        assertEquals(list, deserializedJson);

        DatabaseManager dbManagerBinary = new DatabaseManager(mockDataSource, "player_data", 60000L);
        setPrivateField(dbManagerBinary, "serializationFormat", "binary");
        byte[] binaryBlob = dbManagerBinary.serializeListToBlob(list);
        assertNotNull(binaryBlob);
        List<String> deserializedBinary = dbManagerBinary.deserializeListFromBlob(binaryBlob);
        assertEquals(list, deserializedBinary);
    }

    @Test
    void testSavePlayerDataComponents_GranularSync() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(mockDataSource, "player_data", 60000L);
        
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getBytes("inventory_blob")).thenReturn("legacy_inv".getBytes());
        when(mockResultSet.getDouble("health")).thenReturn(10.0);
        when(mockResultSet.getInt("food_level")).thenReturn(12);
        when(mockResultSet.getString("vanilla_stats_json")).thenReturn("{\"allowFlight\":true}");

        when(mockPlugin.isSyncEnabled("inventory")).thenReturn(false);
        when(mockPlugin.isSyncEnabled("armor")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("ender-chest")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("health")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("food-level")).thenReturn(false);
        when(mockPlugin.isSyncEnabled("experience")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("potion-effects")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("flight-gamemode")).thenReturn(true);
        when(mockPlugin.isSyncEnabledNewFeature("location")).thenReturn(true);
        when(mockPlugin.isSyncEnabledNewFeature("statistics")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("pdc")).thenReturn(true);
        when(mockPlugin.isSyncEnabled("advancements")).thenReturn(true);

        PlayerData data = new PlayerData();
        data.setInventoryContentsNBT(Arrays.asList("new_inv"));
        data.setHealth(20.0);
        data.setFoodLevel(20);

        boolean result = dbManager.savePlayerDataComponents(mockPlugin, data, uuid);
        assertTrue(result);

        verify(mockConnection, atLeastOnce()).prepareStatement(contains("SELECT inventory_blob"));
        verify(mockConnection, atLeastOnce()).prepareStatement(contains("SELECT health"));
        verify(mockConnection, atLeastOnce()).prepareStatement(contains("INSERT INTO `databridge_inventories`"));
        verify(mockConnection, atLeastOnce()).prepareStatement(contains("INSERT INTO `databridge_statistics`"));
    }

    @Test
    void testLoadPlayerDataComponents_LegacyMigration() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(mockDataSource, "player_data", 60000L);
        
        when(mockConnection.prepareStatement(contains("SELECT data, data_checksum FROM `player_data` WHERE uuid = ?"))).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true);
        
        String legacyJson = "{\"health\":18.0,\"foodLevel\":15}";
        when(mockResultSet.getBytes("data")).thenReturn(legacyJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(mockPlugin.getConfig()).thenReturn(mockConfig);
        when(mockConfig.getBoolean("security.verify-data-integrity", true)).thenReturn(false);

        PreparedStatement mockSaveStmt = mock(PreparedStatement.class);
        lenient().when(mockConnection.prepareStatement(contains("SELECT inventory_blob"))).thenReturn(mockSaveStmt);
        ResultSet mockSaveRs = mock(ResultSet.class);
        lenient().when(mockSaveStmt.executeQuery()).thenReturn(mockSaveRs);

        PlayerData loaded = dbManager.loadPlayerDataComponents(mockPlugin, uuid);
        assertNotNull(loaded);
        assertEquals(18.0, loaded.getHealth());
        assertEquals(15, loaded.getFoodLevel());

        verify(mockConnection).prepareStatement(contains("UPDATE `player_data` SET data = NULL WHERE uuid = ?"));
    }

    @Test
    void testCompanionComponentSync() throws Exception {
        DatabaseManager dbManager = new DatabaseManager(mockDataSource, "player_data", 60000L);

        lenient().when(mockPlugin.isSyncEnabledNewFeature(anyString())).thenReturn(false);
        lenient().when(mockPlugin.isSyncEnabledNewFeature("companions")).thenReturn(true);
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getString("companions.mode", "follow")).thenReturn("follow");

        // For save: SELECT companions_nbt FROM databridge_companions WHERE uuid = ?
        PreparedStatement mockSelectStmt = mock(PreparedStatement.class);
        ResultSet mockSelectRs = mock(ResultSet.class);
        when(mockConnection.prepareStatement(contains("SELECT companions_nbt FROM `databridge_companions`"))).thenReturn(mockSelectStmt);
        when(mockSelectStmt.executeQuery()).thenReturn(mockSelectRs);
        when(mockSelectRs.next()).thenReturn(true);
        when(mockSelectRs.getString("companions_nbt")).thenReturn("[{\"entityType\":\"WOLF\",\"health\":20.0,\"maxHealth\":20.0,\"isSitting\":false}]");

        // Mock remaining queries for savePlayerDataComponents
        PreparedStatement mockInvSelect = mock(PreparedStatement.class);
        lenient().when(mockConnection.prepareStatement(contains("SELECT inventory_blob"))).thenReturn(mockInvSelect);
        ResultSet mockInvRs = mock(ResultSet.class);
        lenient().when(mockInvSelect.executeQuery()).thenReturn(mockInvRs);

        PlayerData data = new PlayerData();
        data.setCompanionsNBT("[{\"entityType\":\"CAT\",\"health\":10.0,\"maxHealth\":10.0,\"isSitting\":true}]");

        boolean saveResult = dbManager.savePlayerDataComponents(mockPlugin, data, uuid);
        assertTrue(saveResult);

        verify(mockConnection, atLeastOnce()).prepareStatement(contains("INSERT INTO `databridge_companions`"));

        // For load: SELECT companions_nbt FROM databridge_companions WHERE uuid = ?
        when(mockConnection.prepareStatement(contains("SELECT companions_nbt FROM `databridge_companions` WHERE uuid = ?"))).thenReturn(mockSelectStmt);
        when(mockSelectRs.getString("companions_nbt")).thenReturn("[{\"entityType\":\"CAT\",\"health\":10.0,\"maxHealth\":10.0,\"isSitting\":true}]");

        // Mock loadLegacyData returning null
        PreparedStatement mockLegacySelect = mock(PreparedStatement.class);
        ResultSet mockLegacyRs = mock(ResultSet.class);
        when(mockConnection.prepareStatement(contains("SELECT data, data_checksum FROM `player_data` WHERE uuid = ?"))).thenReturn(mockLegacySelect);
        when(mockLegacySelect.executeQuery()).thenReturn(mockLegacyRs);
        when(mockLegacyRs.next()).thenReturn(false);

        // Mock other components load to return false
        PreparedStatement mockStatsSelect = mock(PreparedStatement.class);
        ResultSet mockStatsRs = mock(ResultSet.class);
        lenient().when(mockConnection.prepareStatement(contains("SELECT vanilla_stats_json FROM `databridge_statistics`"))).thenReturn(mockStatsSelect);
        lenient().when(mockStatsSelect.executeQuery()).thenReturn(mockStatsRs);
        lenient().when(mockStatsRs.next()).thenReturn(false);

        PlayerData loaded = dbManager.loadPlayerDataComponents(mockPlugin, uuid);
        assertNotNull(loaded);
        assertEquals("[{\"entityType\":\"CAT\",\"health\":10.0,\"maxHealth\":10.0,\"isSitting\":true}]", loaded.getCompanionsNBT());
    }
}
