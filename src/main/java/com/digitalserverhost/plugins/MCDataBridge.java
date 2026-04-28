package com.digitalserverhost.plugins;

import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class MCDataBridge extends JavaPlugin {

    private DatabaseManager databaseManager;
    private boolean debugMode;
    private String serverId;
    private String tableName;
    private PlayerListener playerListener;
    private static final Gson GSON = new GsonBuilder().create();

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
        if (!tablePrefix.matches("^[a-zA-Z0-9_]*$")) {
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().severe("!!! INVALID table-prefix: '" + tablePrefix + "'");
            getLogger().severe("!!! Only alphanumeric characters and underscores are allowed.");
            getLogger().severe("!!! Plugin will now disable to prevent SQL errors.   !!!");
            getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.tableName = tablePrefix + "player_data";
        if (this.serverId.equals("default-server")) {
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().warning("!!! Server-id is not set in config.yml. Using default. !!!");
            getLogger().warning("!!! This is UNSAFE for multi-server setups.           !!!");
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        databaseManager = new DatabaseManager(getConfig(), this.tableName);
        
        // Ensure table and columns exist synchronously before events are registered
        createServerTable();
        
        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(this, this::releaseOrphanedLocks);

        // Create the listener instance
        this.playerListener = new PlayerListener(databaseManager, this);

        // Register its Bukkit events
        getServer().getPluginManager().registerEvents(this.playerListener, this);

        org.bukkit.command.PluginCommand cmd = getCommand("databridge");
        if (cmd != null) {
            cmd.setExecutor(new com.digitalserverhost.plugins.commands.BridgeCommand(databaseManager));
        }

        // Register it as the listener for our custom plugin channel
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "mc-data-bridge:main", this.playerListener);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "mc-data-bridge:main");

        // Initialize Backup Manager
        new com.digitalserverhost.plugins.managers.BackupManager(this, databaseManager);

        getLogger().info("mc-data-bridge has been enabled on Spigot/Paper/Folia!");
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "mc-data-bridge:main");
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "mc-data-bridge:main");
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
        String createTableSQL;
        String dbType = getConfig().getString("database.type", "mysql").toLowerCase();

        if (dbType.equals("sqlite")) {
            createTableSQL = "CREATE TABLE IF NOT EXISTS " + escapedTableName + " (" +
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
            createTableSQL = "CREATE TABLE IF NOT EXISTS " + escapedTableName + " (" +
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

        try (Connection connection = databaseManager.getConnection();
                Statement statement = connection.createStatement()) {

            // Check for migration from default 'player_data' to prefixed table
            if (!tableName.equals("player_data")) {
                try {
                    ResultSet oldTable = connection.getMetaData().getTables(null, null, "player_data", null);
                    boolean oldExists = oldTable.next();
                    oldTable.close();

                    ResultSet newTable = connection.getMetaData().getTables(null, null, tableName, null);
                    boolean newExists = newTable.next();
                    newTable.close();

                    if (oldExists && !newExists) {
                        getLogger().warning("Detected old 'player_data' table and new prefix setting.");
                        getLogger().warning("Migrating 'player_data' to '" + tableName + "'...");
                        statement.executeUpdate("RENAME TABLE `player_data` TO " + escapedTableName);
                        getLogger().info("Migration successful!");
                    }
                } catch (Exception e) {
                    getLogger().severe("Failed to migrate table: " + e.getMessage());
                }
            }

            statement.executeUpdate(createTableSQL);
            getLogger().info("Successfully verified or created the '" + tableName + "' table.");

            if (!connection.getMetaData().getColumns(null, null, tableName, "is_locked").next()) {
                statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN is_locked BOOLEAN DEFAULT 0");
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "locking_server").next()) {
                statement.executeUpdate(
                        "ALTER TABLE " + escapedTableName + " ADD COLUMN locking_server VARCHAR(255) DEFAULT NULL");
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "lock_timestamp").next()) {
                statement.executeUpdate(
                        "ALTER TABLE " + escapedTableName + " ADD COLUMN lock_timestamp BIGINT DEFAULT 0");
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "last_known_name").next()) {
                if (dbType.equals("sqlite")) {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN last_known_name TEXT DEFAULT NULL");
                } else {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN last_known_name VARCHAR(16) DEFAULT NULL");
                }
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "data_checksum").next()) {
                if (dbType.equals("sqlite")) {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN data_checksum TEXT DEFAULT NULL");
                } else {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN data_checksum VARCHAR(64) DEFAULT NULL");
                }
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "identity_hash").next()) {
                if (dbType.equals("sqlite")) {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN identity_hash TEXT DEFAULT NULL");
                } else {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN identity_hash VARCHAR(64) DEFAULT NULL");
                }
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "name_last_updated").next()) {
                if (dbType.equals("sqlite")) {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN name_last_updated INTEGER DEFAULT 0");
                } else {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN name_last_updated BIGINT DEFAULT 0");
                }
            }
            if (!connection.getMetaData().getColumns(null, null, tableName, "last_updated").next()) {
                if (dbType.equals("sqlite")) {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN last_updated DATETIME DEFAULT CURRENT_TIMESTAMP");
                } else {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " ADD COLUMN last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                }
            }

            ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "data");
            if (columns.next()) {
                String typeName = columns.getString("TYPE_NAME");
                boolean needsMigration = "LONGTEXT".equalsIgnoreCase(typeName) || "TEXT".equalsIgnoreCase(typeName);

                if (needsMigration) {
                    if (getConfig().getBoolean("auto-update-schema", false)) {
                        if (dbType.equals("sqlite")) {
                            // SQLite BLOB is dynamic and doesn't need explicit 'LONGBLOB' migration
                            return;
                        }
                        getLogger().info("Migrating 'data' column from " + typeName + " to LONGBLOB as requested...");
                        statement
                                .executeUpdate("ALTER TABLE " + escapedTableName + " MODIFY COLUMN data LONGBLOB NULL");
                        getLogger().info("Migration complete! 'data' is now LONGBLOB.");
                    } else {
                        getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                        getLogger().warning("!!! YOUR DATABASE IS USING '" + typeName + "' FOR 'data' COLUMN. !!!");
                        getLogger().warning("!!! IT IS RECOMMENDED TO SWITCH TO 'LONGBLOB' !!!");
                        getLogger().warning("!!! ENABLE 'auto-update-schema: true' IN CONFIG TO FIX AUTOMATICALLY !!!");
                        getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    }
                }

                if ("NO".equalsIgnoreCase(columns.getString("IS_NULLABLE"))) {
                    statement.executeUpdate("ALTER TABLE " + escapedTableName + " MODIFY COLUMN data LONGBLOB NULL");
                }
            }
        } catch (Exception e) {
            getLogger().severe("CRITICAL: Error creating or updating player_data table: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
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
                getLogger()
                        .info("Released " + affectedRows + " orphaned player data locks for server: " + this.serverId);
            } else {
                getLogger().info("No orphaned player data locks found for server: " + this.serverId);
            }
        } catch (Exception e) {
            getLogger().severe("CRITICAL: Could not release player data locks for " + this.serverId + "! Error: "
                    + e.getMessage());
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

    public boolean isSyncEnabled(String key) {
        return getConfig().getBoolean("sync-data." + key, true); // Default to true for safety
    }

    public boolean isSyncEnabledNewFeature(String key) {
        return getConfig().getBoolean("sync-data." + key, false); // Default to false for new features
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

        // Load strictly from file to check valid keys without defaults interference
        org.bukkit.configuration.file.YamlConfiguration fileConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(configFile);

        java.util.List<String> lines;
        try {
            lines = java.nio.file.Files.readAllLines(configFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            getLogger().severe("Failed to read config.yml for update: " + e.getMessage());
            return;
        }

        boolean updated = false;

        // 1. Check for top-level keys (Append to end if missing)
        StringBuilder topLevelAppends = new StringBuilder();
        if (!fileConfig.contains("debug")) {
            topLevelAppends.append("\n# Enable debug mode for verbose logging.\ndebug: false\n");
            updated = true;
        }
        if (!fileConfig.contains("server-id")) {
            topLevelAppends.append("\n# Unique identifier for this server (Required).\nserver-id: \"default-server\"\n");
            updated = true;
        }
        if (!fileConfig.contains("table-prefix")) {
            topLevelAppends.append("\n# Set to prefix the player_data table (e.g., 'mc_data_bridge_').\ntable-prefix: \"\"\n");
            updated = true;
        }
        if (!fileConfig.contains("lock-timeout")) {
            topLevelAppends.append("\n# Lock expiration in milliseconds.\nlock-timeout: 60000\n");
            updated = true;
        }
        if (!fileConfig.contains("lock-heartbeat-seconds")) {
            topLevelAppends.append("\n# Interval between lock updates.\nlock-heartbeat-seconds: 30\n");
            updated = true;
        }
        if (!fileConfig.contains("auto-update-schema")) {
            topLevelAppends.append("\n# Automatically migrate database schema.\nauto-update-schema: true\n");
            updated = true;
        }

        // 2. Check for nested sync-data keys (Structural insertion)
        String[] syncKeys = {"statistics", "pdc", "flight-gamemode"};
        java.util.List<String> missingSyncKeys = new java.util.ArrayList<>();
        for (String key : syncKeys) {
            if (!fileConfig.contains("sync-data." + key)) {
                missingSyncKeys.add(key);
            }
        }

        if (!missingSyncKeys.isEmpty()) {
            int syncDataLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("sync-data:")) {
                    syncDataLine = i;
                    break;
                }
            }

            if (syncDataLine != -1) {
                // Insert after the header
                for (String key : missingSyncKeys) {
                    lines.add(syncDataLine + 1, "  " + key + ": false");
                }
                updated = true;
            } else {
                // If section itself is missing, append it to top-level
                topLevelAppends.append("\nsync-data:\n");
                for (String key : missingSyncKeys) {
                    topLevelAppends.append("  ").append(key).append(": false\n");
                }
                updated = true;
            }
        }

        if (updated) {
            try {
                java.util.List<String> finalLines = new java.util.ArrayList<>(lines);
                if (topLevelAppends.length() > 0) {
                    finalLines.add(topLevelAppends.toString());
                }
                java.nio.file.Files.write(configFile.toPath(), finalLines, java.nio.charset.StandardCharsets.UTF_8);
                getLogger().info("Successfully updated config.yml with missing settings.");
                reloadConfig();
            } catch (java.io.IOException e) {
                getLogger().severe("Failed to write updated config.yml: " + e.getMessage());
            }
        }
    }

    public static Gson getGson() {
        return GSON;
    }
}
