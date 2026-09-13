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
    assertTrue(updatedConfig.getBoolean("maps.lock-global-maps"),
        "global mode should migrate to maps.lock-global-maps: true");
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
    assertFalse(updatedConfig.getBoolean("maps.lock-global-maps"),
        "return mode should migrate to maps.lock-global-maps: false");
    assertNull(updatedConfig.get("maps.mode"), "legacy maps.mode key should be removed");
  }

  @Test
  void testUpdateConfig_WithLegacyIdentitySeed_MigratesToSecuritySeed() throws Exception {
    String legacyYaml = """
        server-id: "test-server"
        identity:
          mode: PREMIUM
          seed: "my-custom-secret-seed-123"
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
    assertEquals("my-custom-secret-seed-123", updatedConfig.getString("security.seed"),
        "legacy identity.seed should migrate to security.seed");
    assertNull(updatedConfig.get("identity.seed"), "legacy identity.seed key should be removed from identity block");
  }

  @Test
  void testUpdateConfig_WithCorruptedDuplicateSections_DeduplicatesAndCleanlyMigrates() throws Exception {
    String corruptedYaml = """
        database:
          host: db
          port: 3306

        companions:

        companions:
          scan-radius: 32

        maps:

        maps:
          lock-global-maps: true

        identity:
          mode: PREMIUM

        companions:
          scan-radius: 32
        """;

    try (FileWriter writer = new FileWriter(configFile)) {
      writer.write(corruptedYaml);
    }

    MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
    doReturn(tempDir).when(plugin).getDataFolder();
    doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
    doNothing().when(plugin).reloadConfig();

    Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
    updateConfigMethod.setAccessible(true);
    updateConfigMethod.invoke(plugin);

    java.util.List<String> rawLines = java.nio.file.Files.readAllLines(configFile.toPath());
    long companionsHeaderCount = rawLines.stream().filter(line -> line.trim().equals("companions:")).count();
    long mapsHeaderCount = rawLines.stream().filter(line -> line.trim().equals("maps:")).count();

    assertEquals(1, companionsHeaderCount, "companions: section header should be deduplicated to exactly 1 instance");
    assertEquals(1, mapsHeaderCount, "maps: section header should be deduplicated to exactly 1 instance");

    YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
    assertEquals(32, updatedConfig.getInt("companions.scan-radius"));
    assertEquals("follow", updatedConfig.getString("companions.mode"));
    assertTrue(updatedConfig.getBoolean("maps.lock-global-maps"));
    assertEquals("PREMIUM", updatedConfig.getString("identity.mode"));
  }

  @Test
  void testUpdateConfig_WithFoliaCorruptedConfig_DeduplicatesAndRestoresMissingKeys() throws Exception {
    String foliaCorruptedYaml = """
        database:
          host: db
          port: 3306

        debug: true
        server-id: "minecraft-folia"

        identity:
          auto-migrate-fastlogin: false


        # Companion/pet sync settings. Requires sync-data.companions: true.
          # Identity and Migration Settings
          mode: PREMIUM
        companions:
          scan-radius: 32




        # Companion/pet sync settings. Requires sync-data.companions: true.


        # Map Synchronization Settings
          mode: "follow"
        maps:
          # Force Map Locking on Synced Maps (true = locked, false = vanilla style)
          lock-global-maps: true


        # Identity and Migration Settings
        """;

    try (FileWriter writer = new FileWriter(configFile)) {
      writer.write(foliaCorruptedYaml);
    }

    MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
    doReturn(tempDir).when(plugin).getDataFolder();
    doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
    doNothing().when(plugin).reloadConfig();

    Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
    updateConfigMethod.setAccessible(true);
    updateConfigMethod.invoke(plugin);

    java.util.List<String> rawLines = java.nio.file.Files.readAllLines(configFile.toPath());
    long companionsHeaderCount = rawLines.stream().filter(line -> line.trim().equals("companions:")).count();
    long identityHeaderCount = rawLines.stream().filter(line -> line.trim().equals("identity:")).count();
    long mapsHeaderCount = rawLines.stream().filter(line -> line.trim().equals("maps:")).count();

    assertEquals(1, companionsHeaderCount, "companions: section header should be deduplicated to exactly 1 instance");
    assertEquals(1, identityHeaderCount, "identity: section header should be deduplicated to exactly 1 instance");
    assertEquals(1, mapsHeaderCount, "maps: section header should be deduplicated to exactly 1 instance");

    // Verify raw line ordering: mode: PREMIUM must appear under identity, mode: "follow" must appear under companions
    int identityIdx = -1, companionsIdx = -1, mapsIdx = -1, metricsIdx = -1;
    int modePremiumIdx = -1, modeFollowIdx = -1;
    for (int i = 0; i < rawLines.size(); i++) {
      String line = rawLines.get(i).trim();
      if (line.equals("identity:")) identityIdx = i;
      else if (line.equals("companions:")) companionsIdx = i;
      else if (line.equals("maps:")) mapsIdx = i;
      else if (line.equals("metrics:")) metricsIdx = i;
      else if (line.equals("mode: PREMIUM")) modePremiumIdx = i;
      else if (line.equals("mode: \"follow\"")) modeFollowIdx = i;
    }

    assertTrue(modePremiumIdx > identityIdx && modePremiumIdx < companionsIdx, "mode: PREMIUM must be located under identity: section");
    assertTrue(modeFollowIdx > companionsIdx && modeFollowIdx < (metricsIdx != -1 ? metricsIdx : rawLines.size()), "mode: \"follow\" must be located under companions: section");

    YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
    assertEquals("PREMIUM", updatedConfig.getString("identity.mode"), "identity.mode should be PREMIUM");
    assertFalse(updatedConfig.getBoolean("identity.auto-migrate-fastlogin"));
    assertEquals("follow", updatedConfig.getString("companions.mode"), "companions.mode should be follow");
    assertEquals(32, updatedConfig.getInt("companions.scan-radius"));
    assertTrue(updatedConfig.getBoolean("maps.lock-global-maps"));
  }

  @Test
  void testUpdateConfig_WithTownyConfig_PreservesExistingStructureCleanly() throws Exception {
    String townyYaml = """
        database:
          host: db
          port: 3306

        debug: true
        server-id: "minecraft-towny"

        security:
          seed: "change-me-to-a-long-random-string"

        identity:
          mode: PREMIUM
          auto-migrate-fastlogin: false

        companions:
          scan-radius: 32
          mode: "follow"

        maps:
          lock-global-maps: true
        """;

    try (FileWriter writer = new FileWriter(configFile)) {
      writer.write(townyYaml);
    }

    MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
    doReturn(tempDir).when(plugin).getDataFolder();
    doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
    doNothing().when(plugin).reloadConfig();

    Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
    updateConfigMethod.setAccessible(true);
    updateConfigMethod.invoke(plugin);

    YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
    assertEquals("PREMIUM", updatedConfig.getString("identity.mode"));
    assertFalse(updatedConfig.getBoolean("identity.auto-migrate-fastlogin"));
    assertEquals("follow", updatedConfig.getString("companions.mode"));
    assertEquals(32, updatedConfig.getInt("companions.scan-radius"));
    assertTrue(updatedConfig.getBoolean("maps.lock-global-maps"));
  }

  @Test
  void testUpdateConfig_WithResourceConfigFlatKeys_ConvertsFlatBackupsCleanly() throws Exception {
    String resourceYaml = """
        database.backups.enabled: false
        database.backups.interval-hours: 24
        database.backups.max-backups: 7
        database.backups.path: "backups/"

        identity:
          mode: PREMIUM
          auto-migrate-fastlogin: false

        companions:
          scan-radius: 32
          mode: "follow"

        maps:
          lock-global-maps: true
        """;

    try (FileWriter writer = new FileWriter(configFile)) {
      writer.write(resourceYaml);
    }

    MCDataBridge plugin = mock(MCDataBridge.class, CALLS_REAL_METHODS);
    doReturn(tempDir).when(plugin).getDataFolder();
    doReturn(Logger.getLogger("MCDataBridgeTest")).when(plugin).getLogger();
    doNothing().when(plugin).reloadConfig();

    Method updateConfigMethod = MCDataBridge.class.getDeclaredMethod("updateConfig");
    updateConfigMethod.setAccessible(true);
    updateConfigMethod.invoke(plugin);

    java.util.List<String> rawLines = java.nio.file.Files.readAllLines(configFile.toPath());
    boolean hasFlatKeys = rawLines.stream().anyMatch(line -> line.trim().startsWith("database.backups."));
    assertFalse(hasFlatKeys, "Flat database.backups.* lines should be removed");

    YamlConfiguration updatedConfig = YamlConfiguration.loadConfiguration(configFile);
    assertFalse(updatedConfig.getBoolean("backups.enabled"));
    assertEquals(24, updatedConfig.getInt("backups.interval-hours"));
    assertEquals(7, updatedConfig.getInt("backups.max-backups"));
    assertEquals("backups/", updatedConfig.getString("backups.path"));
  }
}
