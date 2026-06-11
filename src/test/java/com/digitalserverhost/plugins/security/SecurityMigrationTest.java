package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockmc.mockmc.MockMC;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SecurityMigrationTest {

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
        lenient().when(mockPlugin.getSecuritySeed()).thenReturn("new-salted-seed");
        lenient().when(mockPlugin.getIdentityMode()).thenReturn("HYBRID");
    }

    @AfterEach
    void tearDown() {
        mockedSchedulerUtils.close();
        MockMC.unmock();
    }

    @Test
    void testLegacyChecksumMigration() throws Exception {
        PlayerListener listener = new PlayerListener(mockDatabaseManager, mockPlugin);

        String name = "TestPlayer";
        UUID uuid = UUID.randomUUID();
        String json = "{\"health\": 20.0}";
        // Calculate checksum WITHOUT seed (Legacy format)
        String legacyChecksum = PlayerData.calculateChecksum(json, null);

        when(mockDatabaseManager.getTableName()).thenReturn("`player_data`");
        when(mockDatabaseManager.acquireLock(eq(uuid), anyString())).thenReturn(true);

        when(mockDatabaseManager.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);

        // ResultSet returns legacy checksum
        when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(mockResultSet.getString("locking_server")).thenReturn("test-server");
        when(mockResultSet.getBytes("data")).thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(mockResultSet.getString("data_checksum")).thenReturn(legacyChecksum);

        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                name, InetAddress.getLoopbackAddress(), uuid, false);

        listener.onAsyncPlayerPreLogin(event);

        // Verify that the login was allowed and migration was logged
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());
        verify(logger).log(eq(Level.INFO), contains("Migrating data"), eq(name));
    }
}
