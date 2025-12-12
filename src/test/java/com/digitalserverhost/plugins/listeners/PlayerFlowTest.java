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
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
        lenient().when(mockPlugin.getLockHeartbeatSeconds()).thenReturn(30);
        lenient().when(mockPlugin.isDebugMode()).thenReturn(true);
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

        // Use timeout to verify async execution managed by MockBukkit's pool
        verify(mockDatabaseManager, timeout(2000).atLeastOnce()).updateLock(eq(player.getUniqueId()), anyString());
    }

    @Test
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

        // Verify async save call with timeout
        verify(mockDatabaseManager, timeout(2000)).saveAndReleaseLock(anyString(), eq(player.getUniqueId()),
                anyString());
    }

    @Test
    void testLockCancellation() throws Exception {
        // Prepare Helper Mocks
        when(mockPlugin.getServerId()).thenReturn("test-server");
        when(mockPlugin.getLockHeartbeatSeconds()).thenReturn(30);

        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        // Setup passing checks
        lenient().when(mockDatabaseManager.acquireLock(any(UUID.class), anyString())).thenReturn(true);
        lenient().when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        lenient().when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);

        PlayerMock player = server.addPlayer();
        PlayerJoinEvent event = new PlayerJoinEvent(player, net.kyori.adventure.text.Component.text("Joined"));

        listener.onPlayerJoin(event);

        // 1. Advance time -> triggers heartbeat
        server.getScheduler().performTicks(30 * 20L + 50);
        verify(mockDatabaseManager, timeout(2000).atLeastOnce()).updateLock(eq(player.getUniqueId()), anyString());

        // Reset invocations to verify future calls cleanly
        clearInvocations(mockDatabaseManager);

        // 2. Quit -> triggers cancelHeartbeat (and save)
        @SuppressWarnings("deprecation")
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, "Quit");
        listener.onPlayerQuit(quitEvent);

        // Wait for save to complete (async)
        verify(mockDatabaseManager, timeout(2000)).saveAndReleaseLock(anyString(), eq(player.getUniqueId()),
                anyString());

        // 3. Advance time again -> Heartbeat should NOT run
        server.getScheduler().performTicks(30 * 20L + 50);

        // Verify updateLock was NEVER called after quit
        verify(mockDatabaseManager, never()).updateLock(eq(player.getUniqueId()), anyString());
    }

    @Test
    void testBlacklistedServerSkipsLock() throws Exception {
        // Prepare Helper Mocks
        when(mockPlugin.getServerId()).thenReturn("test-server");
        when(mockPlugin.isServerBlacklisted("test-server")).thenReturn(true);

        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        UUID uuid = UUID.randomUUID();
        @SuppressWarnings("deprecation")
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", InetAddress.getLoopbackAddress(), uuid);

        listener.onAsyncPlayerPreLogin(event);

        verify(mockDatabaseManager, never()).acquireLock(any(UUID.class), anyString());
    }

    @Test
    void testProxyMessageTriggersSaveAndSkipQuit() throws Exception {
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        // Setup passing checks for save
        lenient().when(mockDatabaseManager.saveAndReleaseLock(anyString(), any(UUID.class), anyString()))
                .thenReturn(true);

        PlayerMock player = server.addPlayer();
        UUID uuid = player.getUniqueId();

        // Construct Plugin Message: "SaveAndRelease" + UUID
        @SuppressWarnings("UnstableApiUsage")
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("SaveAndRelease");
        out.writeUTF(uuid.toString());
        byte[] message = out.toByteArray();

        // 1. Receive Message -> Triggers async save
        listener.onPluginMessageReceived("mc-data-bridge:main", player, message);

        verify(mockDatabaseManager, timeout(2000)).saveAndReleaseLock(anyString(), eq(uuid), anyString());

        // Clear invocations to verify Quit behavior
        clearInvocations(mockDatabaseManager);

        // 2. Quit Event -> Should be ignored due to switchingPlayers flag
        @SuppressWarnings("deprecation")
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, "Quit");
        listener.onPlayerQuit(quitEvent);

        // Verify save was NOT called again
        verify(mockDatabaseManager, never()).saveAndReleaseLock(anyString(), eq(uuid), anyString());
    }
}
