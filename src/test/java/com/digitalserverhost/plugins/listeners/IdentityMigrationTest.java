package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.commands.BridgeCommand;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityMigrationTest {

    @Mock
    private MCDataBridge mockPlugin;
    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private CommandSender mockSender;

    private org.mockito.MockedStatic<com.digitalserverhost.plugins.utils.SchedulerUtils> mockedSchedulerUtils;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        MockBukkit.mock();
        
        @SuppressWarnings("null")
        org.mockito.MockedStatic<com.digitalserverhost.plugins.utils.SchedulerUtils> staticMock = mockStatic(com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mockedSchedulerUtils = staticMock;
        mockedSchedulerUtils.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getBridge).thenReturn(new com.digitalserverhost.plugins.utils.BukkitBridge());
        mockedSchedulerUtils.when(com.digitalserverhost.plugins.utils.SchedulerUtils::isFolia).thenReturn(false);

        lenient().when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("MCDataBridge"));
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
        lenient().when(mockPlugin.getConfig()).thenReturn(mock(org.bukkit.configuration.file.FileConfiguration.class));
        lenient().when(mockPlugin.isEnabled()).thenReturn(true);
        lenient().when(mockPlugin.isServerBlacklisted(anyString())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        if (mockedSchedulerUtils != null) {
            mockedSchedulerUtils.close();
        }
        MockBukkit.unmock();
    }

    @Test
    void testUUIDMismatchAlert() throws Exception {
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
        
        String playerName = "XvGwest";
        UUID onlineUuid = UUID.randomUUID();
        UUID offlineUuid = UUID.randomUUID();

        // 1. Database knows the "online" UUID
        when(mockDatabaseManager.getUuidByName(playerName)).thenReturn(onlineUuid);
        
        // 2. Player joins with "offline" UUID (e.g. joining cracked server directly)
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                playerName, InetAddress.getByName("127.0.0.1"), offlineUuid, false);

        // Stub database lock/loading to proceed
        when(mockDatabaseManager.acquireLock(any(), anyString())).thenReturn(true);
        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        
        java.sql.Connection mockConnection = mock(java.sql.Connection.class);
        java.sql.PreparedStatement mockStatement = mock(java.sql.PreparedStatement.class);
        java.sql.ResultSet mockResultSet = mock(java.sql.ResultSet.class);
        
        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);
        
        // isLockOwner sequence: rs.next() = true, rs.getString("locking_server") = "test-server"
        // data loading sequence: rs.next() = false (simulate new player)
        when(mockResultSet.next()).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");

        listener.onAsyncPlayerPreLogin(event);

        // Verify that the login is still ALLOWED (we don't block, we just alert)
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
        
        // Check that mismatch was checked
        verify(mockDatabaseManager).getUuidByName(playerName);
    }

    @Test
    void testManualMigrationCommand() throws Exception {
        BridgeCommand bridgeCommand = new BridgeCommand(mockDatabaseManager);
        Command mockCommand = mock(Command.class);
        
        UUID sourceUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        
        // Mock permission
        when(mockSender.hasPermission("databridge.admin")).thenReturn(true);
        
        // Mock UUID resolution
        // Case 1: Resolving by literal UUID string
        // Case 2: Resolving by name (mocked in DatabaseManager)
        when(mockDatabaseManager.getUuidByName("OfflinePlayer")).thenReturn(sourceUuid);
        when(mockDatabaseManager.getUuidByName("OnlinePlayer")).thenReturn(targetUuid);
        
        when(mockDatabaseManager.migrateData(sourceUuid, targetUuid)).thenReturn(true);

        String[] args = {"migrate", "OfflinePlayer", "OnlinePlayer"};
        boolean result = bridgeCommand.onCommand(mockSender, mockCommand, "databridge", args);

        assertTrue(result);
        
        // Verify migration was triggered in DB
        // Note: BridgeCommand runs this async, so we might need a small wait or use direct call if possible.
        // For unit test, we can verify the call logic.
        
        // Since it's async in BridgeCommand, we verify the interaction with the scheduler if needed, 
        // or just verify that migrateData is eventually called.
        verify(mockDatabaseManager, timeout(2000)).migrateData(sourceUuid, targetUuid);
    }
}
