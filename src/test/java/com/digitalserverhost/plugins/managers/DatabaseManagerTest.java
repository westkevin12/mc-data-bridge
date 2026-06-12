package com.digitalserverhost.plugins.managers;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseManagerTest {

    @Mock
    private HikariDataSource mockDataSource;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockStatement;
    @Mock
    private ResultSet mockResultSet;

    private DatabaseManager databaseManager;
    private final UUID uuid = UUID.randomUUID();
    private final String serverId = "test-server";

    @BeforeEach
    void setup() throws SQLException {
        databaseManager = new DatabaseManager(mockDataSource, "player_data", 60000);
        // Lenient stubbing for connection handling to avoid boilerplate strictly
        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
    }

    @Test
    void testAcquireLock_Success_ExistingRow() throws SQLException {
        // Condition: Update returns > 0 (row existed and was updated)
        when(mockStatement.executeUpdate()).thenReturn(1);

        boolean result = databaseManager.acquireLock(uuid, serverId);

        assertTrue(result);
        verify(mockConnection, times(1)).prepareStatement(contains("UPDATE `player_data`"));
        verify(mockStatement).setString(1, serverId); // locking_server
        verify(mockStatement).setString(2, uuid.toString()); // uuid
        verify(mockStatement).setLong(3, 60000L); // lock_timeout
        verify(mockConnection, never()).prepareStatement(contains("INSERT")); // Should not insert
    }

    @Test
    void testAcquireLock_Success_NewRow() throws SQLException {
        // Condition: Update returns 0 (no row matching criteria), then Insert succeeds
        // Condition: Update returns 0 (no row matching criteria), then Insert succeeds
        // We mocked the statements separately so we don't need to mock
        // mockStatement.executeUpdate() here.

        // We need to handle multiple prepareStatement calls: Update then Insert
        PreparedStatement updateStmt = mock(PreparedStatement.class);
        PreparedStatement insertStmt = mock(PreparedStatement.class);

        when(mockConnection.prepareStatement(contains("UPDATE"))).thenReturn(updateStmt);
        when(mockConnection.prepareStatement(contains("INSERT"))).thenReturn(insertStmt);

        when(updateStmt.executeUpdate()).thenReturn(0);
        when(insertStmt.executeUpdate()).thenReturn(1);

        boolean result = databaseManager.acquireLock(uuid, serverId);

        assertTrue(result);
        verify(updateStmt).executeUpdate();
        verify(insertStmt).executeUpdate();
        verify(insertStmt).setString(1, uuid.toString());
        verify(insertStmt).setString(2, serverId);
    }

    @Test
    void testAcquireLock_Failure_RaceCondition() throws SQLException {
        // Condition: Update returns 0, Insert throws SQLException (Duplicate Entry)
        PreparedStatement updateStmt = mock(PreparedStatement.class);
        PreparedStatement insertStmt = mock(PreparedStatement.class);

        when(mockConnection.prepareStatement(contains("UPDATE"))).thenReturn(updateStmt);
        when(mockConnection.prepareStatement(contains("INSERT"))).thenReturn(insertStmt);

        when(updateStmt.executeUpdate()).thenReturn(0);
        when(insertStmt.executeUpdate()).thenThrow(new SQLException("Duplicate entry"));

        boolean result = databaseManager.acquireLock(uuid, serverId);

        assertFalse(result); // Should fail safely
    }

    @Test
    void testSaveAndReleaseLock_Success() throws SQLException {
        String json = "{\"data\": \"test\"}";
        String checksum = "abc";
        String playerName = "Player1";
        String seed = "test-seed";
        when(mockStatement.executeUpdate()).thenReturn(1);

        boolean result = databaseManager.saveAndReleaseLock(json, checksum, playerName, uuid, serverId, seed);

        assertTrue(result);
        verify(mockConnection).prepareStatement(contains("UPDATE `player_data` SET data = ?, data_checksum = ?, last_known_name = ?, identity_hash = ?, name_last_updated = ?, is_locked = 0"));
        // cannot easily verify setBytes with argument matchers for specific content but
        // we verify interactions
        verify(mockStatement).setString(2, checksum);
        verify(mockStatement).setString(3, playerName);
        verify(mockStatement).setString(eq(4), anyString()); // identity_hash (salted)
        verify(mockStatement).setLong(eq(5), anyLong()); // name_last_updated
        verify(mockStatement).setString(6, uuid.toString());
        verify(mockStatement).setString(7, serverId);
    }

    @Test
    void testReleaseLock() throws SQLException {
        databaseManager.releaseLock(uuid, serverId);

        verify(mockConnection).prepareStatement(contains("UPDATE `player_data` SET is_locked = 0"));
        verify(mockStatement).setString(1, uuid.toString());
        verify(mockStatement).setString(2, serverId);
        verify(mockStatement).executeUpdate();
    }

    @Test
    void testMigrateData_Success() throws SQLException {
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        
        // 1. Check if newUuid exists (rs.next() = false)
        lenient().when(mockConnection.prepareStatement(contains("SELECT uuid FROM `player_data` WHERE uuid = ?"))).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        lenient().when(mockResultSet.next()).thenReturn(false);
        
        // 2. Perform migration
        PreparedStatement migrateStmt = mock(PreparedStatement.class);
        lenient().when(mockConnection.prepareStatement(contains("UPDATE `player_data` SET uuid = ? WHERE uuid = ?"))).thenReturn(migrateStmt);
        lenient().when(migrateStmt.executeUpdate()).thenReturn(1);
        
        boolean result = databaseManager.migrateData(oldUuid, newUuid);
        
        assertTrue(result);
        verify(migrateStmt).setString(1, newUuid.toString());
        verify(migrateStmt).setString(2, oldUuid.toString());
        
        // Verify all components were migrated
        verify(mockConnection).prepareStatement(contains("UPDATE `databridge_inventories` SET uuid = ? WHERE uuid = ?"));
        verify(mockConnection).prepareStatement(contains("UPDATE `databridge_statistics` SET uuid = ? WHERE uuid = ?"));
        verify(mockConnection).prepareStatement(contains("UPDATE `databridge_metadata` SET uuid = ? WHERE uuid = ?"));
        verify(mockConnection).prepareStatement(contains("UPDATE `databridge_companions` SET uuid = ? WHERE uuid = ?"));
        verify(mockConnection).commit();
    }

    @Test
    void testMigrateData_Fail_TargetExists() throws SQLException {
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        
        when(mockConnection.prepareStatement(contains("SELECT uuid FROM `player_data` WHERE uuid = ?"))).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true); // Target already exists
        
        boolean result = databaseManager.migrateData(oldUuid, newUuid);
        
        assertFalse(result);
        verify(mockConnection, never()).prepareStatement(contains("UPDATE `player_data` SET uuid = ?"));
    }
}
