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

    @Override
    public void onEnable() {
        // Platform detection will be implemented here later
        startSpigot();
    }

    private void startSpigot() {
        saveDefaultConfig();
        updateConfig(); // Check and update config if missing new keys
        this.debugMode = getConfig().getBoolean("debug", false);
        this.serverId = getConfig().getString("server-id", "default-server");
        String tablePrefix = getConfig().getString("table-prefix", "");
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
        this.securitySeed = getConfig().getString("security.seed", "change-me-to-a-long-random-string");
        this.identityMode = getConfig().getString("identity.mode", "PREMIUM").toUpperCase();
        this.autoMigrateFastLogin = getConfig().getBoolean("identity.auto-migrate-fastlogin", false);
        this.autoMigrateAuthMe = getConfig().getBoolean("identity.auto-migrate-authme", false);
        databaseManager = new DatabaseManager(getConfig(), this.tableName);
        
        // Ensure table and columns exist synchronously before events are registered
        createServerTable();
        
        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(this, this::releaseOrphanedLocks);

        // Create the listener instance
        this.playerListener = new PlayerListener(databaseManager, this);

        // Register its Bukkit events
        getServer().getPluginManager().registerEvents(this.playerListener, this);

        // Register AuthMe auto-migration listener if enabled
        if (getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            getServer().getPluginManager().registerEvents(new com.digitalserverhost.plugins.listeners.AuthMeListener(this, databaseManager), this);
            getLogger().info("AuthMe integration enabled for auto-migration.");
        }

        org.bukkit.command.PluginCommand cmd = getCommand("databridge");
        if (cmd != null) {
            cmd.setExecutor(new com.digitalserverhost.plugins.commands.BridgeCommand(databaseManager));
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
        getLogger().info("mc-data-bridge has been disabled!");
    }

    private void createServerTable() {
        String escapedTableName = "`" + tableName + "`";
        String dbType = getConfig().getString("database.type", "mysql").toLowerCase();

        try (Connection connection = databaseManager.getConnection();
                Statement statement = connection.createStatement()) {

            migrateFromLegacyTable(connection, statement, escapedTableName);
            
            String createTableSQL = getCreateTableSQL(dbType, escapedTableName);
            statement.executeUpdate(createTableSQL);
            getLogger().log(java.util.logging.Level.INFO, "Successfully verified or created the {0} table.", tableName);

            ensureColumnExists(connection, statement, escapedTableName, dbType, "is_locked", "BOOLEAN DEFAULT 0", INTEGER_DEFAULT_0);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "locking_server", "VARCHAR(255) DEFAULT NULL", TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "lock_timestamp", "BIGINT DEFAULT 0", INTEGER_DEFAULT_0);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "last_known_name", "VARCHAR(16) DEFAULT NULL", TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "data_checksum", "VARCHAR(64) DEFAULT NULL", TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "identity_hash", "VARCHAR(64) DEFAULT NULL", TEXT_DEFAULT_NULL);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "name_last_updated", "BIGINT DEFAULT 0", INTEGER_DEFAULT_0);
            ensureColumnExists(connection, statement, escapedTableName, dbType, "last_updated", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", "DATETIME DEFAULT CURRENT_TIMESTAMP");

            migrateDataColumn(connection, statement, escapedTableName, dbType);
            
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "CRITICAL: Error creating or updating player_data table: {0}", e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private String getCreateTableSQL(String dbType, String escapedTableName) {
        if (dbType.equals(SQLITE)) {
            return "CREATE TABLE IF NOT EXISTS " + escapedTableName + " (" +
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
            return "CREATE TABLE IF NOT EXISTS " + escapedTableName + " (" +
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

    private void migrateFromLegacyTable(Connection connection, Statement statement, String escapedTableName) throws SQLException {
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
        if (!connection.getMetaData().getColumns(null, null, tableName, column).next()) {
            String type = dbType.equals(SQLITE) ? sqliteType : mysqlType;
            statement.executeUpdate(ALTER_TABLE_SQL + escapedTableName + " ADD COLUMN " + column + " " + type);
        }
    }

    private void migrateDataColumn(Connection connection, Statement statement, String escapedTableName, String dbType) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "data")) {
            if (columns.next()) {
                String typeName = columns.getString("TYPE_NAME");
                boolean needsMigration = "LONGTEXT".equalsIgnoreCase(typeName) || "TEXT".equalsIgnoreCase(typeName);

                if (needsMigration) {
                    if (getConfig().getBoolean("auto-update-schema", false)) {
                        if (dbType.equals(SQLITE)) return;
                        getLogger().log(java.util.logging.Level.INFO, "Migrating 'data' column from {0} to LONGBLOB as requested...", typeName);
                        statement.executeUpdate(ALTER_TABLE_SQL + escapedTableName + " MODIFY COLUMN data LONGBLOB NULL");
                    } else {
                        getLogger().warning(BANNER);
                        getLogger().log(java.util.logging.Level.WARNING, "!!! YOUR DATABASE IS USING {0} FOR 'data' COLUMN. !!!", typeName);
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

    private void releaseOrphanedLocks() {
        String escapedTableName = "`" + tableName + "`";
        String releaseSQL = "UPDATE " + escapedTableName
                + " SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE locking_server = ?";

        try (Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(releaseSQL)) {

            statement.setString(1, this.serverId);
            int affectedRows = statement.executeUpdate();

            if (affectedRows > 0) {
                getLogger().log(java.util.logging.Level.INFO, "Released {0} orphaned player data locks for server: {1}", new Object[]{affectedRows, this.serverId});
            } else {
                getLogger().log(java.util.logging.Level.INFO, "No orphaned player data locks found for server: {0}", this.serverId);
            }
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "CRITICAL: Could not release player data locks for {0}! Error: {1}", new Object[]{this.serverId, e.getMessage()});
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
        if (!configFile.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration fileConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
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

    private boolean checkTopLevelKeys(org.bukkit.configuration.file.YamlConfiguration fileConfig, StringBuilder appends) {
        boolean updated = false;
        if (!fileConfig.contains("debug")) {
            appends.append("\n# Enable debug mode for verbose logging.\ndebug: false\n");
            updated = true;
        }
        if (!fileConfig.contains("server-id")) {
            appends.append("\n# Unique identifier for this server (Required).\nserver-id: \"default-server\"\n");
            updated = true;
        }
        if (!fileConfig.contains("table-prefix")) {
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
        if (!fileConfig.contains("auto-update-schema")) {
            appends.append("\n# Automatically migrate database schema.\nauto-update-schema: true\n");
            updated = true;
        }
        if (!fileConfig.contains("security.seed")) {
            appends.append("\n# A secret seed used to salt all cryptographic hashes.\nsecurity:\n  seed: \"change-me-to-a-long-random-string\"\n");
            updated = true;
        }
        if (!fileConfig.contains("identity.mode")) {
            appends.append("\n# Identity and Migration Settings\nidentity:\n  mode: PREMIUM\n  auto-migrate-fastlogin: false\n");
            updated = true;
        }
        return updated;
    }

    private boolean checkSyncKeys(org.bukkit.configuration.file.YamlConfiguration fileConfig, java.util.List<String> lines, StringBuilder appends) {
        String[] syncKeys = {"statistics", "pdc", "flight-gamemode"};
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String key : syncKeys) {
            if (!fileConfig.contains(SYNC_DATA_PREFIX + key)) {
                missing.add(key);
            }
        }

        if (missing.isEmpty()) return false;

        int syncDataLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("sync-data:")) {
                syncDataLine = i;
                break;
            }
        }

        if (syncDataLine != -1) {
            for (String key : missing) {
                lines.add(syncDataLine + 1, "  " + key + ": false");
            }
            return true;
        } else {
            appends.append("\nsync-data:\n");
            for (String key : missing) {
                appends.append("  ").append(key).append(": false\n");
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
