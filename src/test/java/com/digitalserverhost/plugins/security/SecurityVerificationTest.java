package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.MetricsManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockmc.mockmc.MockMC;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityVerificationTest {

    @BeforeEach
    void setup() {
        MockMC.mock();
    }

    @AfterEach
    void tearDown() {
        MockMC.unmock();
    }

    @Test
    void testStartSpigot_InsecureSeed_DisablesPlugin() throws Exception {
        MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
        doNothing().when(plugin).saveDefaultConfig();
        doReturn(new java.io.File("target/test-data")).when(plugin).getDataFolder();
        
        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(org.bukkit.configuration.file.FileConfiguration.class);
        doReturn(mockConfig).when(plugin).getConfig();
        when(mockConfig.getString("security.seed", "change-me-to-a-long-random-string"))
                .thenReturn("change-me-to-a-long-random-string");
        when(mockConfig.getString("server-id", "default-server")).thenReturn("test-server");
        when(mockConfig.getString("table-prefix", "")).thenReturn("");

        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        org.bukkit.plugin.PluginManager mockPluginManager = mock(org.bukkit.plugin.PluginManager.class);
        doReturn(mockServer).when(plugin).getServer();
        when(mockServer.getPluginManager()).thenReturn(mockPluginManager);
        doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();

        java.lang.reflect.Method method = MCDataBridge.class.getDeclaredMethod("startSpigot");
        method.setAccessible(true);
        try {
            method.invoke(plugin);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // If it throws during databaseManager initialization or createServerTable, that's fine,
            // as long as disablePlugin was called.
        }

        verify(mockPluginManager).disablePlugin(plugin);
    }

    @Test
    void testEnsureColumnExists_WhitelistedColumn_Succeeds() throws Exception {
        MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
        
        java.lang.reflect.Field tableNameField = MCDataBridge.class.getDeclaredField("tableName");
        tableNameField.setAccessible(true);
        tableNameField.set(plugin, "player_data");

        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
        when(mockMetaData.getColumns(any(), any(), any(), any())).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(false);

        java.lang.reflect.Method method = MCDataBridge.class.getDeclaredMethod("ensureColumnExists",
                Connection.class, Statement.class, String.class, String.class, String.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(plugin, mockConnection, mockStatement, "`player_data`", "mysql", "is_locked", "BOOLEAN DEFAULT 0", "INTEGER DEFAULT 0");

        verify(mockStatement).executeUpdate(contains("ADD COLUMN is_locked BOOLEAN DEFAULT 0"));
    }

    @Test
    void testEnsureColumnExists_UnwhitelistedColumn_ThrowsException() throws Exception {
        MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);

        java.lang.reflect.Method method = MCDataBridge.class.getDeclaredMethod("ensureColumnExists",
                Connection.class, Statement.class, String.class, String.class, String.class, String.class, String.class);
        method.setAccessible(true);

        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);

        try {
            method.invoke(plugin, mockConnection, mockStatement, "`player_data`", "mysql", "malicious_column", "VARCHAR(255)", "TEXT");
            fail("Expected SecurityException wrapped in InvocationTargetException");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof SecurityException);
            assertTrue(e.getCause().getMessage().contains("Blocked attempt to add un-whitelisted column"));
        }
    }

    @Test
    void testMetricsManager_TracksMetricsCorrectly() {
        MetricsManager manager = MetricsManager.getInstance();
        long contentionStart = manager.getLockContentionRetries();
        long failuresStart = manager.getSyncFailures();

        manager.incrementLockContentionRetries();
        manager.incrementLockContentionRetries();
        manager.incrementSyncFailures();
        manager.recordLockAcquisitionLatency(42);

        assertEquals(contentionStart + 2, manager.getLockContentionRetries());
        assertEquals(failuresStart + 1, manager.getSyncFailures());
        assertEquals(42, manager.getLastLockAcquisitionLatency());
    }
}
