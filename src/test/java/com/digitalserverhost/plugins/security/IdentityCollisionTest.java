package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IdentityCollisionTest {

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

    private org.mockito.MockedStatic<?> mockedSchedulerUtils;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        MockBukkit.mock();
        logger = spy(Logger.getLogger("MCDataBridgeTest"));

        // Mock SchedulerUtils
        var mocked = mockStatic(com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mocked.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getBridge)
                .thenReturn(new com.digitalserverhost.plugins.utils.BukkitBridge());
        this.mockedSchedulerUtils = mocked;

        // Stub plugin
        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(
                org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        lenient().when(mockPlugin.getLogger()).thenReturn(logger);
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
        lenient().when(mockPlugin.getIdentityMode()).thenReturn("HYBRID");
        lenient().when(mockPlugin.getSecuritySeed()).thenReturn("test-seed");
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
        // 1. isLockOwner check -> rs.next() = true, rs.getString("locking_server") =
        // "test-server"
        // 2. Data load check -> rs.next() = false (simulate new player)
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");

        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                commonName, InetAddress.getLoopbackAddress(), crackedUuid, false);

        listener.onAsyncPlayerPreLogin(event);

        // Verify that a warning was logged containing the critical information
        verify(logger, atLeastOnce()).log(eq(Level.WARNING), contains("IDENTITY COLLISION"), eq(commonName));
        verify(logger, atLeastOnce()).log(eq(Level.WARNING), contains("Current UUID"), eq(crackedUuid));
        verify(logger, atLeastOnce()).log(eq(Level.WARNING), contains("Previous UUID"), eq(premiumUuid));
        verify(logger, atLeastOnce()).log(eq(Level.WARNING), contains("Cracked -> Premium"));
    }
}
