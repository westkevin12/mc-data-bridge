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
public class DatabaseManagerTest {

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
        verify(mockStatement).setString(3, uuid.toString()); // uuid
        verify(mockStatement).setString(3, uuid.toString()); // uuid
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
        when(mockStatement.executeUpdate()).thenReturn(1);

        boolean result = databaseManager.saveAndReleaseLock(json, uuid, serverId);

        assertTrue(result);
        verify(mockConnection).prepareStatement(contains("UPDATE `player_data` SET data = ?"));
        // cannot easily verify setBytes with argument matchers for specific content but
        // we verify interactions
        verify(mockStatement).setString(2, uuid.toString());
        verify(mockStatement).setString(3, serverId);
    }

    @Test
    void testReleaseLock() throws SQLException {
        databaseManager.releaseLock(uuid, serverId);

        verify(mockConnection).prepareStatement(contains("UPDATE `player_data` SET is_locked = 0"));
        verify(mockStatement).setString(1, uuid.toString());
        verify(mockStatement).setString(2, serverId);
        verify(mockStatement).executeUpdate();
    }
}
