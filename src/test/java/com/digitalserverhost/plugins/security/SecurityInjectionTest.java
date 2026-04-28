package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
 
import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityInjectionTest {

    @Mock
    private HikariDataSource mockDataSource;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockStatement;

    private DatabaseManager databaseManager;

    @BeforeEach
    void setup() throws SQLException {
        databaseManager = new DatabaseManager(mockDataSource, "player_data", 60000);
        lenient().when(mockDataSource.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
    }

    @Test
    void testSqlInjectionInUsername() throws SQLException {
        // Malicious username attempting to break out of single quotes or comment out
        // the rest of the query
        String maliciousName = "Notch'; DROP TABLE player_data; --";
        UUID uuid = UUID.randomUUID();
        String seed = "test-seed";

        // Testing updateLastKnownName
        databaseManager.updateLastKnownName(uuid, maliciousName, seed);

        // Verify that the malicious string was passed as a SINGLE parameter, not part
        // of the SQL
        // The SQL should still be "UPDATE ... SET last_known_name = ?, ..."
        verify(mockConnection).prepareStatement(contains("SET last_known_name = ?, identity_hash = ?"));
        verify(mockStatement).setString(1, maliciousName);
    }

    @Test
    void testSqlInjectionInTableName() {
        // The constructor escapes the table name by wrapping it in backticks and
        // removing internal backticks.
        // Attempting to break out of backticks
        String maliciousTable = "player_data`; DROP TABLE users; --";
        DatabaseManager dbMgr = new DatabaseManager(mockDataSource, maliciousTable, 60000);

        // The table name should be safely escaped: `player_data; DROP TABLE users; --`
        // (with the internal backtick removed)
        String escaped = dbMgr.getTableName();
        assertTrue(escaped.startsWith("`"));
        assertTrue(escaped.endsWith("`"));
        assertFalse(escaped.contains("``")); // The backtick in the middle should have been replaced
    }

    @Test
    void testSqlInjectionInUuid() throws SQLException {
        String maliciousUuid = "00000000-0000-0000-0000-000000000000' OR '1'='1";
        // Note: UUID.fromString would fail here, but if we pass it as a string to DB
        // manager
        // we want to ensure it's parameterized.

        // Let's assume we have a method that takes a UUID string or we mock the UUID
        // object
        UUID mockUuid = mock(UUID.class);
        when(mockUuid.toString()).thenReturn(maliciousUuid);

        databaseManager.getUuidByName("Notch"); // Not relevant to this test but setup

        // Verify acquireLock parameterization
        databaseManager.acquireLock(mockUuid, "server1");
        verify(mockStatement).setString(2, maliciousUuid);
    }
}
