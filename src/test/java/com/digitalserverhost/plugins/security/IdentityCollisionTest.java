package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class IdentityCollisionTest {

    private ServerMock server;
    private Logger logger;

    @Mock
    private MCDataBridge mockPlugin;
    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private Connection mockConnection;
    @Mock
    private PreparedStatement mockStatement;
    @Mock
    private ResultSet mockResultSet;

    private org.mockito.MockedStatic<com.digitalserverhost.plugins.utils.SchedulerUtils> mockedSchedulerUtils;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        server = MockBukkit.mock();
        logger = spy(Logger.getLogger("MCDataBridgeTest"));

        // Mock SchedulerUtils
        mockedSchedulerUtils = mockStatic(com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mockedSchedulerUtils.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getBridge).thenReturn(new com.digitalserverhost.plugins.utils.BukkitBridge());

        // Stub plugin
        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        lenient().when(mockPlugin.getLogger()).thenReturn(logger);
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
    }

    @AfterEach
    void tearDown() {
        mockedSchedulerUtils.close();
        MockBukkit.unmock();
    }

    @Test
    void testIdentityCollisionWarning() throws Exception {
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
        
        String commonName = "Notch";
        UUID premiumUuid = UUID.randomUUID();
        UUID crackedUuid = UUID.randomUUID();
        
        // Scenario: crackedUuid is joining with name "Notch", 
        // but premiumUuid already "owns" that name in the DB.
        
        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        when(mockDatabaseManager.acquireLock(eq(crackedUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getUuidByName(commonName)).thenReturn(premiumUuid); // Collision!
        
        // Mocks for DB
        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        
        // Sequence for ResultSet:
        // 1. isLockOwner check -> rs.next() = true, rs.getString("locking_server") = "test-server"
        // 2. Data load check -> rs.next() = false (simulate new player)
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");

        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                commonName, InetAddress.getLoopbackAddress(), crackedUuid, false);

        listener.onAsyncPlayerPreLogin(event);

        // Verify that a warning was logged containing the critical information
        verify(logger, atLeastOnce()).warning(contains("IDENTITY COLLISION"));
        verify(logger, atLeastOnce()).warning(contains(premiumUuid.toString()));
        verify(logger, atLeastOnce()).warning(contains(crackedUuid.toString()));
        verify(logger, atLeastOnce()).warning(contains("Cracked -> Premium"));
    }
}
