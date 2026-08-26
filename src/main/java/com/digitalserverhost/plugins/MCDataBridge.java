package com.digitalserverhost.plugins;

import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class MCDataBridge extends JavaPlugin {

    private DatabaseManager databaseManager;
    private boolean debugMode;
    private String serverId;
    private String tableName;
    private String securitySeed;
    private String identityMode;
    private boolean autoMigrateFastLogin;
    private boolean autoMigrateAuthMe;
    private PlayerListener playerListener;
    private static final Gson GSON = new GsonBuilder().create();
    private static final String BANNER = "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!";
    private static final String DEFAULT_TABLE_NAME = "player_data";
    private static final String PLUGIN_CHANNEL = "mc-data-bridge:main";
    private static final String SQLITE = "sqlite";
    private static final String ALTER_TABLE_SQL = "ALTER TABLE ";
    private static final String SYNC_DATA_PREFIX = "sync-data.";
    private static final String INTEGER_DEFAULT_0 = "INTEGER DEFAULT 0";
    private static final String TEXT_DEFAULT_NULL = "TEXT DEFAULT NULL";
    private static final String BIGINT_DEFAULT_0 = "BIGINT DEFAULT 0";
    private static final String VARCHAR64_DEFAULT_NULL = "VARCHAR(64) DEFAULT NULL";
    private static final String CONFIG_TABLE_PREFIX = "table-prefix";
    private static final String DEFAULT_SEED = "change-me-to-a-long-random-string";
    private static final String CREATE_TABLE_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS ";
    private static final String AUTO_UPDATE_SCHEMA = "auto-update-schema";

    @Override
    public void onEnable() {
        // Platform detection will be implemented here later
        startSpigot();
    }

    private static final java.util.Map<String, java.util.Map.Entry<String, String>> COLUMN_WHITELIST = java.util.Map.of(
            "is_locked", java.util.Map.entry("BOOLEAN DEFAULT 0", INTEGER_DEFAULT_0),
            "locking_server", java.util.Map.entry("VARCHAR(255) DEFAULT NULL", TEXT_DEFAULT_NULL),
            "lock_timestamp", java.util.Map.entry(BIGINT_DEFAULT_0, INTEGER_DEFAULT_0),
            "last_known_name", java.util.Map.entry("VARCHAR(16) DEFAULT NULL", TEXT_DEFAULT_NULL),
            "data_checksum", java.util.Map.entry(VARCHAR64_DEFAULT_NULL, TEXT_DEFAULT_NULL),
            "identity_hash", java.util.Map.entry(VARCHAR64_DEFAULT_NULL, TEXT_DEFAULT_NULL),
            "name_last_updated", java.util.Map.entry(BIGINT_DEFAULT_0, INTEGER_DEFAULT_0),
            "last_updated", java.util.Map.entry("TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
                    "DATETIME DEFAULT CURRENT_TIMESTAMP"));

    private void startSpigot() {
        saveDefaultConfig();
        updateConfig(); // Check and update config if missing new keys
        this.debugMode = getConfig().getBoolean("debug", false);
        this.serverId = getConfig().getString("server-id", "default-server");
        String tablePrefix = getConfig().getString(CONFIG_TABLE_PREFIX, "");
        if (!tablePrefix.isEmpty() && !tablePrefix.matches("\\w+")) {
            getLogger().severe(BANNER);
            getLogger().log(java.util.logging.Level.SEVERE, "!!! INVALID table-prefix: {0}", tablePrefix);
            getLogger().severe("!!! Only alphanumeric characters and underscores are allowed.");
            getLogger().severe("!!! Plugin will now disable to prevent SQL errors.   !!!");
            getLogger().severe(BANNER);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.tableName = tablePrefix + DEFAULT_TABLE_NAME;
        if (this.serverId.equals("default-server")) {
            getLogger().warning(BANNER);
            getLogger().warning("!!! Server-id is not set in config.yml. Using default. !!!");
            getLogger().warning("!!! This is UNSAFE for multi-server setups.           !!!");
            getLogger().warning(BANNER);
        }

        this.securitySeed = getConfig().getString("security.seed", DEFAULT_SEED);
        if (this.securitySeed == null || this.securitySeed.isEmpty() || this.securitySeed.equals(DEFAULT_SEED)) {
            String envSeed = System.getenv("DATABRIDGE_SEED");
            if (envSeed != null && !envSeed.isEmpty() && !envSeed.equals(DEFAULT_SEED)) {
                this.securitySeed = envSeed;
                getLogger().info("Loaded secure security seed from environment variable DATABRIDGE_SEED.");
            } else {
                getLogger().severe(BANNER);
                getLogger().severe("!!! CRITICAL SECURITY ERROR: security.seed IS NOT CONFIGURED !!!");
                getLogger().severe("!!! You must set a custom security seed in config.yml or    !!!");
                getLogger().severe("!!! pass it via the DATABRIDGE_SEED environment variable.    !!!");
                getLogger().severe("!!! The engine will now forcefully disable itself immediately. !!!");
                getLogger().severe(BANNER);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        this.identityMode = getConfig().getString("identity.mode", "PREMIUM").toUpperCase();
        this.autoMigrateFastLogin = getConfig().getBoolean("identity.auto-migrate-fastlogin", false);
        this.autoMigrateAuthMe = getConfig().getBoolean("identity.auto-migrate-authme", false);
        databaseManager = new DatabaseManager(getConfig(), this.tableName);

        // Initialize Metrics Exporter if enabled
        if (getConfig().getBoolean("metrics.enabled", false)) {
            int metricsPort = getConfig().getInt("metrics.port", 8080);
            String metricsPath = getConfig().getString("metrics.path", "/metrics");
            com.digitalserverhost.plugins.managers.MetricsManager.getInstance().start(this, databaseManager,
                    metricsPort, metricsPath);
        }

        // Ensure table and columns exist synchronously before events are registered
        createServerTable();

        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(this, this::releaseOrphanedLocks);

        // Create the listener instance
        this.playerListener = new PlayerListener(databaseManager, this);

        // Register its Bukkit events
        getServer().getPluginManager().registerEvents(this.playerListener, this);

        // Register AuthMe auto-migration listener if enabled
        if (getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getServer().getPluginManager().registerEvents(
                    new com.digitalserverhost.plugins.listeners.AuthMeListener(this, databaseManager), this);
            getLogger().info("AuthMe integration enabled for auto-migration.");
        }

        // Initialize GUI Manager and register GUI click listener
        com.digitalserverhost.plugins.utils.DataManagementGUI guiManager = new com.digitalserverhost.plugins.utils.DataManagementGUI(this, databaseManager);
        getServer().getPluginManager().registerEvents(guiManager, this);

        org.bukkit.command.PluginCommand cmd = getCommand("databridge");
        if (cmd != null) {
            com.digitalserverhost.plugins.commands.BridgeCommand bridgeCmd = new com.digitalserverhost.plugins.commands.BridgeCommand(databaseManager, guiManager);
            cmd.setExecutor(bridgeCmd);
            cmd.setTabCompleter(bridgeCmd);
        }

        // Register it as the listener for our custom plugin channel
        this.getServer().getMessenger().registerIncomingPluginChannel(this, PLUGIN_CHANNEL, this.playerListener);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, PLUGIN_CHANNEL);

        // Initialize Backup Manager
        new com.digitalserverhost.plugins.managers.BackupManager(this, databaseManager);

        getLogger().info("mc-data-bridge has been enabled on Spigot/Paper/Folia!");
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, PLUGIN_CHANNEL);
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, PLUGIN_CHANNEL);
        if (playerListener != null) {
            playerListener.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        com.digitalserverhost.plugins.managers.MetricsManager.getInstance().stop();
        getLogger().info("mc-data-bridge has been disabled!");
    }

    private void createServerTable() {
        if (!tableName.matches("\\w+")) {
            throw new SecurityException("Invalid table name: " + tableName);
        }
        String escapedTableName = "`" + tableName + "`";
        String dbType = getConfig().getString("database.type", "mysql").toLowerCase();

        try (Connection connection = databaseManager.getConnection();
                Statement statement = connection.createStatement()) {

            migrateFromLegacyTable(connection, statement, escapedTableName);

            String createTableSQL = getCreateTableSQL(dbType, escapedTableName);
            statement.executeUpdate(createTableSQL);
            getLogger().log(java.util.logging.Level.INFO, "Successfully verified or created the {0} table.", tableName);

            ensureColumnExists(connection, statement, escapedTableName, dbType, "is_locked", "BOOLEAN DEFAULT 0",
                    INTEGER_DEFAULT_0);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "locking_server",
                    "VARCHAR(255) DEFAULT NULL", TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "lock_timestamp", BIGINT_DEFAULT_0,
                    INTEGER_DEFAULT_0);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "last_known_name",
                    "VARCHAR(16) DEFAULT NULL", TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "data_checksum", VARCHAR64_DEFAULT_NULL,
                    TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "identity_hash", VARCHAR64_DEFAULT_NULL,
                    TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "name_last_updated", BIGINT_DEFAULT_0,
                    INTEGER_DEFAULT_0);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "last_updated",
                    "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
                    "DATETIME DEFAULT CURRENT_TIMESTAMP");

            migrateDataColumn(connection, statement, escapedTableName, dbType);

            // Create component tables for normalized schema
            String tablePrefix = getConfig().getString(CONFIG_TABLE_PREFIX, "");
            String escapedInventories = "`" + tablePrefix + "databridge_inventories`";
            String escapedStatistics = "`" + tablePrefix + "databridge_statistics`";
            String escapedMetadata = "`" + tablePrefix + "databridge_metadata`";
            String escapedCompanions = "`" + tablePrefix + "databridge_companions`";
            String escapedMaps = "`" + tablePrefix + "databridge_maps`";

            if (dbType.equals(SQLITE)) {
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedInventories + " (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "inventory_blob TEXT, " +
                        "armor_blob TEXT, " +
                        "ender_chest_blob TEXT, " +
                        "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedStatistics + " (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "health REAL DEFAULT 20.0, " +
                        "food_level INTEGER DEFAULT 20, " +
                        "xp_level INTEGER DEFAULT 0, " +
                        "xp_exp REAL DEFAULT 0.0, " +
                        "xp_total INTEGER DEFAULT 0, " +
                        "saturation REAL DEFAULT 5.0, " +
                        "exhaustion REAL DEFAULT 0.0, " +
                        "vanilla_stats_json TEXT DEFAULT NULL, " +
                        "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedMetadata + " (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "pdc_data TEXT DEFAULT NULL, " +
                        "advancements TEXT DEFAULT NULL, " +
                        "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedCompanions + " (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "companions_nbt TEXT DEFAULT NULL, " +
                        "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedMaps + " (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "maps_nbt TEXT DEFAULT NULL, " +
                        "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);");
            } else {
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedInventories + " (" +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "inventory_blob LONGBLOB DEFAULT NULL, " +
                        "armor_blob LONGBLOB DEFAULT NULL, " +
                        "ender_chest_blob LONGBLOB DEFAULT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (uuid)) ENGINE=InnoDB;");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedStatistics + " (" +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "health DOUBLE DEFAULT 20.0, " +
                        "food_level INT DEFAULT 20, " +
                        "xp_level INT DEFAULT 0, " +
                        "xp_exp FLOAT DEFAULT 0.0, " +
                        "xp_total INT DEFAULT 0, " +
                        "saturation FLOAT DEFAULT 5.0, " +
                        "exhaustion FLOAT DEFAULT 0.0, " +
                        "vanilla_stats_json LONGTEXT DEFAULT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (uuid)) ENGINE=InnoDB;");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedMetadata + " (" +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "pdc_data LONGTEXT DEFAULT NULL, " +
                        "advancements LONGTEXT DEFAULT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (uuid)) ENGINE=InnoDB;");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedCompanions + " (" +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "companions_nbt LONGTEXT DEFAULT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (uuid)) ENGINE=InnoDB;");
                statement.executeUpdate(CREATE_TABLE_IF_NOT_EXISTS + escapedMaps + " (" +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "maps_nbt LONGTEXT DEFAULT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (uuid)) ENGINE=InnoDB;");
            }

            migrateStatisticsColumn(connection, statement, dbType);

        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "CRITICAL: Error creating or updating player_data table: {0}", e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private String getCreateTableSQL(String dbType, String escapedTableName) {
        if (dbType.equals(SQLITE)) {
            return CREATE_TABLE_IF_NOT_EXISTS + escapedTableName + " (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "data BLOB, " +
                    "is_locked INTEGER DEFAULT 0, " +
                    "locking_server TEXT DEFAULT NULL, " +
                    "lock_timestamp INTEGER DEFAULT 0, " +
                    "last_known_name TEXT DEFAULT NULL, " +
                    "data_checksum TEXT DEFAULT NULL, " +
                    "identity_hash TEXT DEFAULT NULL, " +
                    "name_last_updated INTEGER DEFAULT 0, " +
                    "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);";
        } else {
            return CREATE_TABLE_IF_NOT_EXISTS + escapedTableName + " (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "data LONGBLOB, " +
                    "is_locked BOOLEAN DEFAULT 0, " +
                    "locking_server VARCHAR(255) DEFAULT NULL, " +
                    "lock_timestamp BIGINT DEFAULT 0, " +
                    "last_known_name VARCHAR(16) DEFAULT NULL, " +
                    "data_checksum VARCHAR(64) DEFAULT NULL, " +
                    "identity_hash VARCHAR(64) DEFAULT NULL, " +
                    "name_last_updated BIGINT DEFAULT 0, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY (uuid)) ENGINE=InnoDB;";
        }
    }

    private void migrateFromLegacyTable(Connection connection, Statement statement, String escapedTableName)
            throws SQLException {
        if (!tableName.equals(DEFAULT_TABLE_NAME)) {
            try {
                ResultSet oldTable = connection.getMetaData().getTables(null, null, DEFAULT_TABLE_NAME, null);
                boolean oldExists = oldTable.next();
                oldTable.close();

                ResultSet newTable = connection.getMetaData().getTables(null, null, tableName, null);
                boolean newExists = newTable.next();
                newTable.close();

                if (oldExists && !newExists) {
                    getLogger().warning("Detected old 'player_data' table and new prefix setting. Migrating...");
                    statement.executeUpdate("RENAME TABLE `" + DEFAULT_TABLE_NAME + "` TO " + escapedTableName);
                    getLogger().info("Migration successful!");
                }
            } catch (Exception e) {
                getLogger().log(java.util.logging.Level.SEVERE, "Failed to migrate table: {0}", e.getMessage());
            }
        }
    }

    private void ensureColumnExists(Connection connection, Statement statement, String escapedTableName, String dbType,
            String column, String mysqlType, String sqliteType) throws SQLException {
        java.util.Map.Entry<String, String> allowedTypes = COLUMN_WHITELIST.get(column);
        if (allowedTypes == null) {
            throw new SecurityException("Blocked attempt to add un-whitelisted column: " + column);
        }
        if (!allowedTypes.getKey().equals(mysqlType) || !allowedTypes.getValue().equals(sqliteType)) {
            throw new SecurityException("Blocked attempt to add column " + column + " with un-whitelisted types.");
        }

        if (!connection.getMetaData().getColumns(null, null, tableName, column).next()) {
            String type = dbType.equals(SQLITE) ? sqliteType : mysqlType;
            statement.executeUpdate(ALTER_TABLE_SQL + escapedTableName + " ADD COLUMN " + column + " " + type);
        }
    }

    private void migrateDataColumn(Connection connection, Statement statement, String escapedTableName, String dbType)
            throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "data")) {
            if (columns.next()) {
                String typeName = columns.getString("TYPE_NAME");
                boolean needsMigration = "LONGTEXT".equalsIgnoreCase(typeName) || "TEXT".equalsIgnoreCase(typeName);

                if (needsMigration) {
                    if (getConfig().getBoolean(AUTO_UPDATE_SCHEMA, false)) {
                        if (dbType.equals(SQLITE))
                            return;
                        getLogger().log(java.util.logging.Level.INFO,
                                "Migrating 'data' column from {0} to LONGBLOB as requested...", typeName);
                        statement.executeUpdate(
                                ALTER_TABLE_SQL + escapedTableName + " MODIFY COLUMN data LONGBLOB NULL");
                    } else {
                        getLogger().warning(BANNER);
                        getLogger().log(java.util.logging.Level.WARNING,
                                "!!! YOUR DATABASE IS USING {0} FOR 'data' COLUMN. !!!", typeName);
                        getLogger().warning("!!! IT IS RECOMMENDED TO SWITCH TO 'LONGBLOB' !!!");
                        getLogger().warning("!!! ENABLE 'auto-update-schema: true' IN CONFIG TO FIX AUTOMATICALLY !!!");
                        getLogger().warning(BANNER);
                    }
                }

                if ("NO".equalsIgnoreCase(columns.getString("IS_NULLABLE"))) {
                    statement.executeUpdate(ALTER_TABLE_SQL + escapedTableName + " MODIFY COLUMN data LONGBLOB NULL");
                }
            }
        }
    }

    private void migrateStatisticsColumn(Connection connection, Statement statement, String dbType)
            throws SQLException {
        if (dbType.equals(SQLITE)) {
            return;
        }
        String tablePrefix = getConfig().getString(CONFIG_TABLE_PREFIX, "");
        String statisticsTable = tablePrefix + "databridge_statistics";
        String escapedStatistics = "`" + statisticsTable + "`";

        try (ResultSet columns = connection.getMetaData().getColumns(null, null, statisticsTable,
                "vanilla_stats_json")) {
            if (columns.next()) {
                String typeName = columns.getString("TYPE_NAME");
                if ("TEXT".equalsIgnoreCase(typeName)) {
                    if (getConfig().getBoolean(AUTO_UPDATE_SCHEMA, false)) {
                        getLogger().log(java.util.logging.Level.INFO,
                                "Migrating 'vanilla_stats_json' column in {0} from TEXT to LONGTEXT...",
                                statisticsTable);
                        statement.executeUpdate(ALTER_TABLE_SQL + escapedStatistics
                                + " MODIFY COLUMN vanilla_stats_json LONGTEXT DEFAULT NULL");
                    } else {
                        getLogger().warning(BANNER);
                        getLogger().log(java.util.logging.Level.WARNING,
                                "!!! STATISTICS TABLE IS USING {0} FOR 'vanilla_stats_json' COLUMN. !!!", typeName);
                        getLogger().warning(
                                "!!! IT IS HIGHLY RECOMMENDED TO SWITCH TO 'LONGTEXT' TO PREVENT DATA TRUNCATION ERRORS. !!!");
                        getLogger().warning(
                                "!!! ENABLE 'auto-update-schema: true' IN CONFIG TO FIX AUTOMATICALLY, OR RUN: !!!");
                        getLogger().log(java.util.logging.Level.WARNING,
                                "!!! ALTER TABLE {0} MODIFY COLUMN vanilla_stats_json LONGTEXT DEFAULT NULL; !!!",
                                escapedStatistics);
                        getLogger().warning(BANNER);
                    }
                }
            }
        }
    }

    private void releaseOrphanedLocks() {
        String escapedTableName = "`" + tableName + "`";
        String releaseSQL = "UPDATE " + escapedTableName
                + " SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE locking_server = ?";

        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(releaseSQL)) {

            statement.setString(1, this.serverId);
            int affectedRows = statement.executeUpdate();

            if (affectedRows > 0) {
                getLogger().log(java.util.logging.Level.INFO, "Released {0} orphaned player data locks for server: {1}",
                        new Object[] { affectedRows, this.serverId });
            } else {
                getLogger().log(java.util.logging.Level.INFO, "No orphaned player data locks found for server: {0}",
                        this.serverId);
            }
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "CRITICAL: Could not release player data locks for {0}! Error: {1}",
                    new Object[] { this.serverId, e.getMessage() });
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String getServerId() {
        return serverId;
    }

    public int getLockHeartbeatSeconds() {
        return getConfig().getInt("lock-heartbeat-seconds", 30);
    }

    public String getSecuritySeed() {
        return securitySeed;
    }

    public String getIdentityMode() {
        return identityMode;
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    public boolean isAutoMigrateFastLogin() {
        return autoMigrateFastLogin;
    }

    public boolean isAutoMigrateAuthMe() {
        return autoMigrateAuthMe;
    }

    public boolean isSyncEnabled(String key) {
        return getConfig().getBoolean(SYNC_DATA_PREFIX + key, true); // Default to true for safety
    }

    public boolean isSyncEnabledNewFeature(String key) {
        return getConfig().getBoolean(SYNC_DATA_PREFIX + key, false); // Default to false for new features
    }

    public boolean isServerBlacklisted(String serverName) {
        return getConfig().getStringList("sync-blacklist.servers").contains(serverName);
    }

    public boolean isWorldBlacklisted(String worldName) {
        return getConfig().getStringList("sync-blacklist.worlds").contains(worldName);
    }

    private void updateConfig() {
        java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
        if (!configFile.exists())
            return;

        org.bukkit.configuration.file.YamlConfiguration fileConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(configFile);
        java.util.List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            getLogger().severe("Failed to read config.yml for update: " + e.getMessage());
            return;
        }

        StringBuilder topLevelAppends = new StringBuilder();
        boolean updated = checkTopLevelKeys(fileConfig, topLevelAppends);
        updated |= checkSyncKeys(fileConfig, lines, topLevelAppends);

        if (updated) {
            saveUpdatedConfig(configFile, lines, topLevelAppends);
        }
    }

    private boolean checkTopLevelKeys(org.bukkit.configuration.file.YamlConfiguration fileConfig,
            StringBuilder appends) {
        boolean updated = false;
        if (!fileConfig.contains("debug")) {
            appends.append("\n# Enable debug mode for verbose logging.\ndebug: false\n");
            updated = true;
        }
        if (!fileConfig.contains("server-id")) {
            appends.append("\n# Unique identifier for this server (Required).\nserver-id: \"default-server\"\n");
            updated = true;
        }
        if (!fileConfig.contains(CONFIG_TABLE_PREFIX)) {
            appends.append("\n# Set to prefix the player_data table (e.g., 'mc_data_bridge_').\ntable-prefix: \"\"\n");
            updated = true;
        }
        if (!fileConfig.contains("lock-timeout")) {
            appends.append("\n# Lock expiration in milliseconds.\nlock-timeout: 60000\n");
            updated = true;
        }
        if (!fileConfig.contains("lock-heartbeat-seconds")) {
            appends.append("\n# Interval between lock updates.\nlock-heartbeat-seconds: 30\n");
            updated = true;
        }
        if (!fileConfig.contains(AUTO_UPDATE_SCHEMA)) {
            appends.append("\n# Automatically migrate database schema.\n" + AUTO_UPDATE_SCHEMA + ": true\n");
            updated = true;
        }
        if (!fileConfig.contains("security.seed")) {
            appends.append("\n# A secret seed used to salt all cryptographic hashes.\nsecurity:\n  seed: \""
                    + DEFAULT_SEED + "\"\n");
            updated = true;
        }
        if (!fileConfig.contains("identity.mode")) {
            appends.append(
                    "\n# Identity and Migration Settings\nidentity:\n  mode: PREMIUM\n  auto-migrate-fastlogin: false\n");
            updated = true;
        }
        if (!fileConfig.contains("companions.scan-radius")) {
            appends.append(
                    "\n# Companion/pet sync settings. Requires sync-data.companions: true.\ncompanions:\n  scan-radius: 32\n  mode: \"follow\"\n");
            updated = true;
        } else if (!fileConfig.contains("companions.mode")) {
            appends.append("\ncompanions:\n  mode: \"follow\"\n");
            updated = true;
        }
        if (!fileConfig.contains("maps.mode")) {
            appends.append("\n# Map synchronization mode across servers.\nmaps:\n  mode: \"return\"\n");
            updated = true;
        }
        return updated;
    }

    private boolean checkSyncKeys(org.bukkit.configuration.file.YamlConfiguration fileConfig,
            java.util.List<String> lines, StringBuilder appends) {
        String[] syncKeys = { "statistics", "pdc", "flight-gamemode", "companions", "maps" };
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : syncKeys) {
            if (!fileConfig.contains(SYNC_DATA_PREFIX + key)) {
                missing.add(key);
            }
        }

        if (missing.isEmpty())
            return false;

        int syncDataLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("sync-data:")) {
                syncDataLine = i;
                break;
            }
        }

        if (syncDataLine != -1) {
            for (String key : missing) {
                boolean defaultValue = key.equals("maps"); // Default true for maps, false for others
                lines.add(syncDataLine + 1, "  " + key + ": " + defaultValue);
            }
            return true;
        } else {
            appends.append("\nsync-data:\n");
            for (String key : missing) {
                boolean defaultValue = key.equals("maps");
                appends.append("  ").append(key).append(": ").append(defaultValue).append("\n");
            }
            return true;
        }
    }

    private void saveUpdatedConfig(java.io.File configFile, java.util.List<String> lines, StringBuilder appends) {
        try {
            java.util.List<String> finalLines = new java.util.ArrayList<>(lines);
            if (!appends.isEmpty()) {
                finalLines.add(appends.toString());
            }
            java.nio.file.Files.write(configFile.toPath(), finalLines, java.nio.charset.StandardCharsets.UTF_8);
            getLogger().info("Successfully updated config.yml with missing settings.");
            reloadConfig();
        } catch (java.io.IOException e) {
            getLogger().severe("Failed to write updated config.yml: " + e.getMessage());
        }
    }

    public static Gson getGson() {
        return GSON;
    }
}
