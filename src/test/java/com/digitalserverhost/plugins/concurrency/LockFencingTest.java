package com.digitalserverhost.plugins.concurrency;

import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
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
class LockFencingTest {

    @Mock
    private HikariDataSource mockDataSource;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockStatement;
    @Mock
    private ResultSet mockResultSet;
    @Mock
    private com.digitalserverhost.plugins.MCDataBridge mockPlugin;

    private DatabaseManager databaseManager;
    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setup() throws SQLException {
        databaseManager = new DatabaseManager(mockDataSource, "player_data", 60000);
        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);
    }

    @Test
    void testFencingToken_StaleServerSaveRejected() throws SQLException {
        // Server A acquired lockVersion = 1, Server B stole lock and incremented to lockVersion = 2.
        // Server A tries saveAndReleaseLockComponents with lockVersion = 1.
        // The SQL update query with AND lock_version = 1 will return 0 updated rows because lock_version is now 2.

        PlayerData staleData = new PlayerData();
        staleData.setLockVersion(1L);

        // PreparedStatement for UPDATE returns 0 rows updated due to lockVersion mismatch
        when(mockStatement.executeUpdate()).thenReturn(0);

        boolean result = databaseManager.saveAndReleaseLockComponents(
                mockPlugin, staleData, "StalePlayer", uuid, "server-A", "secret-seed");

        assertFalse(result, "Stale server save with lockVersion=1 should be rejected when DB lockVersion is 2");
        verify(mockConnection).rollback(); // Transaction rolled back cleanly
    }

    @Test
    void testFencingToken_CurrentServerSaveSucceeds() throws SQLException {
        // Server B tries saveAndReleaseLockComponents with current lockVersion = 2.
        // The SQL update query with AND lock_version = 2 matches 1 row.

        PlayerData validData = new PlayerData();
        validData.setLockVersion(2L);

        when(mockStatement.executeUpdate()).thenReturn(1);

        boolean result = databaseManager.saveAndReleaseLockComponents(
                mockPlugin, validData, "ValidPlayer", uuid, "server-B", "secret-seed");

        assertTrue(result, "Current server save with matching lockVersion should succeed");
        verify(mockConnection).commit(); // Transaction committed successfully
    }

    @Test
    void testStaleSaveAfterExpiration_EndToEndSimulation() throws SQLException {
        // Step 1: Server A holds lock (token = 41)
        PlayerData serverAData = new PlayerData();
        serverAData.setLockVersion(41L);

        // Step 2: Server A freezes (GC pause). Server B acquires lock (token = 42).
        // Server B completes save and releases lock with token = 42.
        PlayerData serverBData = new PlayerData();
        serverBData.setLockVersion(42L);
        when(mockStatement.executeUpdate()).thenReturn(1);

        boolean serverBSave = databaseManager.saveAndReleaseLockComponents(
                mockPlugin, serverBData, "TestPlayer", uuid, "server-B", "secret-seed");
        assertTrue(serverBSave, "Server B with token 42 should commit successfully");

        // Step 3: Server A unfreezes and attempts stale save with token = 41.
        // DB returns 0 affected rows because lock_version in DB is now 42.
        when(mockStatement.executeUpdate()).thenReturn(0);

        boolean serverASave = databaseManager.saveAndReleaseLockComponents(
                mockPlugin, serverAData, "TestPlayer", uuid, "server-A", "secret-seed");
        assertFalse(serverASave, "Stale Server A with token 41 must fail to save after Server B epoch 42");
    }

    @Test
    void testCompetingServerCannotStealActiveLock() throws SQLException {
        // Server A holds active lock (token = 41).
        // Server B attempts acquireLock while lock is still active and un-expired.
        // DB update returns 0 affected rows.
        when(mockStatement.executeUpdate()).thenReturn(0);

        boolean acquireResult = databaseManager.acquireLock(uuid, "server-B");
        assertFalse(acquireResult, "Server B should be denied lock acquisition while Server A lock is active");
    }

    @Test
    void testStaleHeartbeatAndReleaseRejected() throws SQLException {
        // Server A holds token 41, but Server B has stolen lock and incremented to token 42.
        // Server A attempts updateLock (heartbeat) or releaseLock with token 41.
        when(mockStatement.executeUpdate()).thenReturn(0);

        // Attempt heartbeat with stale token 41
        databaseManager.updateLock(uuid, "server-A", 41L);
        verify(mockStatement).setLong(3, 41L);

        // Attempt release lock with stale token 41
        databaseManager.releaseLock(uuid, "server-A", 41L);
        verify(mockStatement, times(2)).setLong(3, 41L);
    }
}
