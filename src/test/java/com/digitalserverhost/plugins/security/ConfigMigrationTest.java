package com.digitalserverhost.plugins.security;

import com.digitalserverhost.plugins.MCDataBridge;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockmc.mockmc.MockMC;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigMigrationTest {

    private File tempDir;
    private File configFile;

    @BeforeEach
    void setup() {
        MockMC.mock();
        tempDir = new File("target/test-config-migration");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        configFile = new File(tempDir, "config.yml");
        if (configFile.exists()) {
            configFile.delete();
        }
    }

    @AfterEach
    void tearDown() {
        MockMC.unmock();
        if (configFile.exists()) {
            configFile.delete();
        }
        if (tempDir.exists()) {
            tempDir.delete();
        }
    }

    @Test
    void testUpdateConfig_WithLegacyConfig_AppendsNewKeysCleanly() throws Exception {
        // 1. Create a legacy config lacking newer version features/keys
        String legacyYaml = """
                server-id: "my-legacy-server"
                lock-timeout: 30000
                sync-data:
                  inventory: true
                  ender-chest: true
                """;

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(legacyYaml);
        }

        // 2. Mock MCDataBridge plugin
        MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
        doReturn(tempDir).when(plugin).getDataFolder();
        doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
        doNothing().when(plugin).reloadConfig();

        // 3. Trigger private updateConfig() method via reflection
        Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
        updateConfigMethod.setAccessible(true);
        updateConfigMethod.invoke(plugin);

        // 4. Verify updated file contents and structural parsing
        assertTrue(configFile.exists(), "config.yml should still exist after migration");

        YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);

        // Verify legacy keys are fully preserved
        assertEquals("my-legacy-server", updatedConfig.getString("server-id"));
        assertEquals(30000, updatedConfig.getInt("lock-timeout"));
        assertTrue(updatedConfig.getBoolean("sync-data.inventory"));
        assertTrue(updatedConfig.getBoolean("sync-data.ender-chest"));

        // Verify new keys are auto-appended and contain correct defaults
        assertFalse(updatedConfig.getBoolean("debug"));
        assertEquals("", updatedConfig.getString("table-prefix"));
        assertEquals(30, updatedConfig.getInt("lock-heartbeat-seconds"));
        assertTrue(updatedConfig.getBoolean("auto-update-schema"));
        assertEquals("change-me-to-a-long-random-string", updatedConfig.getString("security.seed"));
        assertEquals("PREMIUM", updatedConfig.getString("identity.mode"));
        assertFalse(updatedConfig.getBoolean("identity.auto-migrate-fastlogin"));
        assertEquals(32, updatedConfig.getInt("companions.scan-radius"));
        assertEquals("follow", updatedConfig.getString("companions.mode"));
        assertFalse(updatedConfig.getBoolean("maps.lock-global-maps"));

        // Verify sync-data features are appended
        assertFalse(updatedConfig.getBoolean("sync-data.statistics"));
        assertFalse(updatedConfig.getBoolean("sync-data.pdc"));
        assertFalse(updatedConfig.getBoolean("sync-data.flight-gamemode"));
        assertFalse(updatedConfig.getBoolean("sync-data.companions"));
        assertFalse(updatedConfig.getBoolean("sync-data.maps"));
        assertFalse(updatedConfig.getBoolean("sync-data.separate-gamemode-inventories"));
    }

    @Test
    void testUpdateConfig_WithLegacyMapModeGlobal_MigratesToTrueTrue() throws Exception {
        String legacyYaml = """
                server-id: "test-server"
                maps:
                  mode: "global"
                """;

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(legacyYaml);
        }

        MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
        doReturn(tempDir).when(plugin).getDataFolder();
        doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
        doNothing().when(plugin).reloadConfig();

        Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
        updateConfigMethod.setAccessible(true);
        updateConfigMethod.invoke(plugin);

        YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(updatedConfig.getBoolean("sync-data.maps"), "global mode should migrate to sync-data.maps: true");
        assertTrue(updatedConfig.getBoolean("maps.lock-global-maps"), "global mode should migrate to maps.lock-global-maps: true");
        assertNull(updatedConfig.get("maps.mode"), "legacy maps.mode key should be removed");
    }

    @Test
    void testUpdateConfig_WithLegacyMapModeReturn_MigratesToFalseFalse() throws Exception {
        String legacyYaml = """
                server-id: "test-server"
                maps:
                  mode: "return"
                """;

        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(legacyYaml);
        }

        MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
        doReturn(tempDir).when(plugin).getDataFolder();
        doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
        doNothing().when(plugin).reloadConfig();

        Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
        updateConfigMethod.setAccessible(true);
        updateConfigMethod.invoke(plugin);

        YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
        assertFalse(updatedConfig.getBoolean("sync-data.maps"), "return mode should migrate to sync-data.maps: false");
        assertFalse(updatedConfig.getBoolean("maps.lock-global-maps"), "return mode should migrate to maps.lock-global-maps: false");
        assertNull(updatedConfig.get("maps.mode"), "legacy maps.mode key should be removed");
    }
}
