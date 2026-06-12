package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import com.zaxxer.hikari.HikariDataSource;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies that a player with a legacy (unsalted) checksum is transparently migrated
 * and that login is allowed.
 */
class SecurityMigrationTest {

    @Mock private MCDataBridge mockPlugin;
    @Mock private HikariDataSource mockDataSource;
    @Mock private Connection mockConnection;
    @Mock private PreparedStatement genericStmt;
    @Mock private ResultSet        emptyRs;
    @Mock private PreparedStatement legacyDataStmt;
    @Mock private ResultSet        legacyDataRs;

    private DatabaseManager databaseManager;
    private org.mockito.MockedStatic<?> mockedSchedulerUtils;

    @BeforeEach
    void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        MockMC.mock();

        var mocked = mockStatic(com.digitalserverhost.plugins.utils.SchedulerUtils.class);
        mocked.when(com.digitalserverhost.plugins.utils.SchedulerUtils::getBridge)
                .thenReturn(new com.digitalserverhost.plugins.utils.BukkitBridge());
        this.mockedSchedulerUtils = mocked;

        org.bukkit.configuration.file.FileConfiguration mockConfig =
                mock(org.bukkit.configuration.file.FileConfiguration.class);
        lenient().when(mockPlugin.getConfig()).thenReturn(mockConfig);
        lenient().when(mockConfig.getBoolean(anyString(), anyBoolean())).thenReturn(true);
        lenient().when(mockPlugin.getLogger())
                .thenReturn(java.util.logging.Logger.getLogger("MCDataBridge"));
        lenient().when(mockPlugin.getServerId()).thenReturn("test-server");
        lenient().when(mockPlugin.getSecuritySeed()).thenReturn("new-salted-seed");
        lenient().when(mockPlugin.getIdentityMode()).thenReturn("HYBRID");
        lenient().when(mockPlugin.isEnabled()).thenReturn(true);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);

        // genericStmt: fallback for all UPDATE/INSERT/other SELECT statements
        lenient().when(genericStmt.executeUpdate()).thenReturn(1); // UPDATEs succeed (including acquireLock)
        lenient().when(genericStmt.executeQuery()).thenReturn(emptyRs);
        lenient().when(emptyRs.next()).thenReturn(false);

        // Default: all statements → genericStmt
        lenient().when(mockConnection.prepareStatement(anyString())).thenReturn(genericStmt);

        // emptyRs for executeQuery on genericStmt already done above

        databaseManager = new DatabaseManager(mockDataSource, "player_data", 60000);
    }

    @AfterEach
    void tearDown() {
        mockedSchedulerUtils.close();
        MockMC.unmock();
    }

    @Test
    void testLegacyChecksumMigration() throws Exception {
        String name = "TestPlayer";
        UUID uuid = UUID.randomUUID();
        String json = "{\"health\": 20.0}";
        // Legacy checksum — computed WITHOUT a seed
        String legacyChecksum = PlayerData.calculateChecksum(json, null);

        // legacyDataStmt responds to "SELECT data, data_checksum" with the legacy row
        when(legacyDataStmt.executeQuery()).thenReturn(legacyDataRs);
        when(legacyDataRs.next()).thenReturn(true).thenReturn(false);
        when(legacyDataRs.getBytes("data"))
                .thenReturn(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(legacyDataRs.getString("data_checksum")).thenReturn(legacyChecksum);

        // Override the default: route legacy SELECT to legacyDataStmt (declared AFTER generic,
        // so Mockito's last-stub-wins ordering makes this more specific match win for this SQL)
        when(mockConnection.prepareStatement(argThat(sql ->
                sql != null && sql.contains("SELECT data, data_checksum"))))
                .thenReturn(legacyDataStmt);

        PlayerListener listener = new PlayerListener(databaseManager, mockPlugin);

        AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                name, InetAddress.getLoopbackAddress(), uuid, false);

        listener.onAsyncPlayerPreLogin(event);

        // Migration is transparent — login must succeed
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, event.getLoginResult());

        // The legacy data column must be nulled out, proving migration executed
        verify(mockConnection, atLeastOnce()).prepareStatement(
                contains("UPDATE `player_data` SET data = NULL WHERE uuid = ?"));
    }
}
