package com.digitalserverhost.plugins.security;
 
import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockmc.mockmc.MockMC;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
 
import java.net.InetAddress;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
 
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
 
class IdentityModeTest {
 
    private Logger logger;
 
    @Mock
    private MCDataBridge mockPlugin;
    @Mock
    private DatabaseManager mockDatabaseManager;
    @Mock
    private java.sql.Connection mockConnection;
    @Mock
    private java.sql.PreparedStatement mockStatement;
    @Mock
    private java.sql.ResultSet mockResultSet;
 
    private org.mockito.MockedStatic<?> mockedSchedulerUtils;
 
    @BeforeEach
    void setup() throws java.sql.SQLException {
        MockitoAnnotations.openMocks(this);
        MockMC.mock();
        logger = spy(Logger.getLogger("MCDataBridgeTest"));
 
        var mocked = mockStatic(com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mocked.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getBridge)
                .thenReturn(new com.digitalserverhost.plugins.utils.BukkitBridge());
        this.mockedSchedulerUtils = mocked;
 
        org.bukkit.configuration.file.FileConfiguration mockConfig = mock(
                org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        lenient().when(mockPlugin.getLogger()).thenReturn(logger);
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
        lenient().when(mockPlugin.getSecuritySeed()).thenReturn("test-seed");
 
        lenient().when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        lenient().when(mockStatement.executeQuery()).thenReturn(mockResultSet);
    }
 
    @AfterEach
    void tearDown() {
        mockedSchedulerUtils.close();
        MockMC.unmock();
    }
 
    @Test
    void testPremiumMode_BlocksCollision() throws Exception {
        when(mockPlugin.getIdentityMode()).thenReturn("PREMIUM");
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
 
        String name = "TestPlayer";
        UUID newUuid = UUID.randomUUID();
        UUID oldUuid = UUID.randomUUID();
 
        // Simulate collision
        when(mockDatabaseManager.getUuidByName(name)).thenReturn(oldUuid);
        when(mockDatabaseManager.acquireLock(eq(newUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getTableName()).thenReturn("player_data");
 
        // Mock isLockOwner check
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");
 
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                name, InetAddress.getLoopbackAddress(), newUuid, false);
 
        listener.onAsyncPlayerPreLogin(event);
 
        // Verify severe warning was logged
        verify(logger).log(eq(Level.SEVERE), contains("IDENTITY COLLISION (PREMIUM)"), eq(name));
        // Verify migrateData was NOT called
        verify(mockDatabaseManager, never()).migrateData(any(), any());
    }
 
    @Test
    void testHybridMode_NoAutoMigrate_LogsWarning() throws Exception {
        when(mockPlugin.getIdentityMode()).thenReturn("HYBRID");
        when(mockPlugin.isAutoMigrateFastLogin()).thenReturn(false);
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);
 
        String name = "TestPlayer";
        UUID newUuid = UUID.randomUUID();
        UUID oldUuid = UUID.randomUUID();
 
        when(mockDatabaseManager.getUuidByName(name)).thenReturn(oldUuid);
        when(mockDatabaseManager.acquireLock(eq(newUuid), anyString())).thenReturn(true);
        when(mockDatabaseManager.getTableName()).thenReturn("player_data");
 
        // Mock isLockOwner check
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");
 
        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                name, InetAddress.getLoopbackAddress(), newUuid, false);
 
        listener.onAsyncPlayerPreLogin(event);
 
        // Verify normal warning was logged
        verify(logger).log(eq(Level.WARNING), contains("IDENTITY COLLISION"), eq(name));
        verify(mockDatabaseManager, never()).migrateData(any(), any());
    }
}
