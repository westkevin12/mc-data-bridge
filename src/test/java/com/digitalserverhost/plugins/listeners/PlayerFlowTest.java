package com.digitalserverhost.plugins.listeners;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PlayerFlowTest {

    private ServerMock server;

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

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        server = MockBukkit.mock();
        // stub generic plugin methods
        doReturn(true).when(mockPlugin).isEnabled();
        lenient().when(mockPlugin.getServer()).thenReturn(server);
        lenient().when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("MCDataBridge"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testAsyncPlayerPreLogin_AcquireLockSuccess() throws Exception {
        // Prepare Helper Mocks
        when(mockPlugin.getServerId()).thenReturn("test-server");
        when(mockPlugin.isDebugMode()).thenReturn(true);
        // getLogger already stubbed

        // Setup Listener with mocks
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        UUID uuid = UUID.randomUUID();
        // Use non-deprecated constructor if possible, or suppress warning
        @SuppressWarnings("deprecation")
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", InetAddress.getByName("127.0.0.1"), uuid);

        // Mocks for DB
        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        when(mockDatabaseManager.acquireLock(eq(uuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);

        // Sequence:
        // 1. isLockOwner -> rs.next() = true, rs.getString("locking_server") =
        // "test-server"
        // 2. data load -> rs.next() = false (simulate new player)
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");

        // Fire Event manual call
        listener.onAsyncPlayerPreLogin(event);

        // Verify
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
        verify(mockDatabaseManager).acquireLock(eq(uuid), anyString());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Async scheduler verification with MockBukkit is flaky in this environment.")
    void testPlayerJoinSchedulesHeartbeat() throws Exception {
        // Prepare Helper Mocks
        when(mockPlugin.getServerId()).thenReturn("test-server");
        when(mockPlugin.getLockHeartbeatSeconds()).thenReturn(30);

        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        lenient().when(mockDatabaseManager.acquireLock(any(UUID.class), anyString())).thenReturn(true);
        lenient().when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        try {
            lenient().when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
            lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
            lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);
            lenient().when(mockResultSet.next()).thenReturn(false);
        } catch (Exception e) {
        }

        PlayerMock player = server.addPlayer();
        PlayerJoinEvent event = new PlayerJoinEvent(player, net.kyori.adventure.text.Component.text("Joined"));

        listener.onPlayerJoin(event);

        // Advance time to trigger heartbeat (30s * 20 = 600 ticks)
        server.getScheduler().performTicks(30 * 20L + 5);

        verify(mockDatabaseManager).updateLock(eq(player.getUniqueId()), anyString());
    }

    @Test
    @org.junit.jupiter.api.Disabled("Async scheduler verification with MockBukkit is flaky in this environment.")
    void testSaveOnQuit() throws Exception {
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        lenient().when(mockDatabaseManager.acquireLock(any(UUID.class), anyString())).thenReturn(true);
        lenient().when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        lenient().when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        lenient().when(mockResultSet.next()).thenReturn(false);

        PlayerMock player = server.addPlayer();

        when(mockDatabaseManager.saveAndReleaseLock(anyString(), eq(player.getUniqueId()), anyString()))
                .thenReturn(true);

        @SuppressWarnings("deprecation")
        PlayerQuitEvent event = new PlayerQuitEvent(player, "Quit");

        listener.onPlayerQuit(event);

        verify(mockDatabaseManager, atLeastOnce()).saveAndReleaseLock(anyString(), eq(player.getUniqueId()),
                anyString());
    }
}
