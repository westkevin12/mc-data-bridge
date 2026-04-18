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

        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(this, this::createServerTable);
        com.digitalserverhost.plugins.utils.SchedulerUtils.runAsync(this, this::releaseOrphanedLocks);

        // Create the listener instance
        PlayerListener playerListener = new PlayerListener(databaseManager, this);

        // Register its Bukkit events
        getServer().getPluginManager().registerEvents(playerListener, this);

        // Register Commands
        org.bukkit.command.PluginCommand cmd = getCommand("databridge");
        if (cmd != null) {
            cmd.setExecutor(new com.digitalserverhost.plugins.commands.UnlockCommand(databaseManager));
        }

        // Register it as the listener for our custom plugin channel
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "mc-data-bridge:main", playerListener);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "mc-data-bridge:main");

        // Initialize Backup Manager
        new com.digitalserverhost.plugins.managers.BackupManager(this, databaseManager);

        getLogger().info("mc-data-bridge has been enabled on Spigot/Paper!");
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "mc-data-bridge:main");
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "mc-data-bridge:main");
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
                    "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP);";
        } else {
            createTableSQL = "CREATE TABLE IF NOT EXISTS " + escapedTableName + " (" +
                    "uuid VARCHAR(36) NOT NULL, " +
                    "data LONGBLOB, " +
                    "is_locked BOOLEAN DEFAULT 0, " +
                    "locking_server VARCHAR(255) DEFAULT NULL, " +
                    "lock_timestamp BIGINT DEFAULT 0, " +
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
        // Simple append-based updater to preserve comments in existing file
        java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
        if (!configFile.exists())
            return;

        String existingContent = "";
        try {
            existingContent = new String(java.nio.file.Files.readAllBytes(configFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {}

        // Load strictly from file to check valid keys without defaults interference
        org.bukkit.configuration.file.YamlConfiguration fileConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(configFile);

        StringBuilder newConfigContent = new StringBuilder();
        boolean updated = false;

        // Check for 'debug'
        if (!fileConfig.contains("debug") && !existingContent.contains("debug:")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Enable debug mode for verbose logging.\n");
            newConfigContent.append("debug: false\n");
            updated = true;
        }

        // Check for 'server-id'
        if (!fileConfig.contains("server-id") && !existingContent.contains("server-id:")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Unique identifier for this server (Required).\n");
            newConfigContent.append("server-id: \"default-server\"\n");
            updated = true;
        }

        // Check for 'table-prefix'
        if (!fileConfig.contains("table-prefix") && !existingContent.contains("table-prefix:")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Set to prefix the player_data table (e.g., 'mc_data_bridge_').\n");
            newConfigContent.append("table-prefix: \"\"\n");
            updated = true;
        }

        // Check for 'database' section
        if (!fileConfig.contains("database") && !existingContent.contains("database:")) {
            newConfigContent.append("\n");
            newConfigContent.append("database:\n");
            newConfigContent.append("  host: \"localhost\"\n");
            newConfigContent.append("  port: 3306\n");
            newConfigContent.append("  name: \"minecraft\"\n");
            newConfigContent.append("  username: \"root\"\n");
            newConfigContent.append("  password: \"password\"\n");
            newConfigContent.append("  useSSL: false\n");
            newConfigContent.append("  connection-timeout: 30000\n");
            newConfigContent.append("  idle-timeout: 600000\n");
            newConfigContent.append("  max-lifetime: 1800000\n");
            updated = true;
        }

        // Check for 'database.backups'
        if (!fileConfig.contains("database.backups") && !existingContent.contains("backups:") && !existingContent.contains("database.backups")) {
            newConfigContent.append("\n");
            if (!fileConfig.contains("database") && !existingContent.contains("database:")) {
                newConfigContent.append("database:\n");
                newConfigContent.append("  # Redundancy System (JSON Exports)\n");
                newConfigContent.append("  # This is NOT a true backup if stored on the same machine/container.\n");
                newConfigContent.append("  backups:\n");
                newConfigContent.append("    enabled: false\n");
                newConfigContent.append("    interval-hours: 24\n");
                newConfigContent.append("    max-backups: 7\n");
                newConfigContent.append("    path: \"backups/\"\n");
            } else {
                // Parent exists, use dot-notation at root to avoid overriding the whole 'database' section
                newConfigContent.append("database.backups.enabled: false\n");
                newConfigContent.append("database.backups.interval-hours: 24\n");
                newConfigContent.append("database.backups.max-backups: 7\n");
                newConfigContent.append("database.backups.path: \"backups/\"\n");
            }
            newConfigContent.append("\n");
            newConfigContent.append("  # =========================================================================\n");
            newConfigContent.append("  # TRUE OFFSITE BACKUPS (RECOMMENDED)\n");
            newConfigContent.append("  # =========================================================================\n");
            newConfigContent.append("  # For production servers, we STRONGLY recommend using external database\n");
            newConfigContent.append("  # management tools rather than the internal redundancy system above.\n");
            newConfigContent.append("  #\n");
            newConfigContent.append("  # Example (Linux/MySQL):\n");
            newConfigContent.append("  #   mysqldump -u [user] -p[password] [database] [table] > backup.sql\n");
            newConfigContent.append("  #\n");
            newConfigContent.append("  # Best Practices:\n");
            newConfigContent.append("  # 1. Automate: Use a cron job to run backups daily.\n");
            newConfigContent.append("  # 2. Offsite: Use 'rclone' or 'aws s3 cp' to move the .sql file to cloud storage.\n");
            newConfigContent.append("  # 3. Isolation: NEVER store true backups in the Minecraft server directory.\n");
            newConfigContent.append("  # 4. Managed: Consider using AWS RDS, Google CloudSQL, or DigitalOcean Managed\n");
            newConfigContent.append("  #    Databases for automatic point-in-time recovery.\n");
            newConfigContent.append("  # =========================================================================\n");
            updated = true;
        }

        // Check for 'lock-timeout'
        if (!fileConfig.contains("lock-timeout") && !existingContent.contains("lock-timeout:")) {
            newConfigContent.append("\n");
            newConfigContent
                    .append("# The duration in milliseconds after which a player data lock is considered expired.\n");
            newConfigContent.append("# Default: 60000 (1 minute)\n");
            newConfigContent.append("lock-timeout: 60000\n");
            updated = true;
        }

        // Check if lock-heartbeat-seconds exists
        if (!fileConfig.contains("lock-heartbeat-seconds") && !existingContent.contains("lock-heartbeat-seconds:")) {
            newConfigContent.append("\n");
            newConfigContent
                    .append("# The interval in seconds between lock updates (heartbeats) while a player is online.\n");
            newConfigContent.append("# Default: 30\n");
            newConfigContent.append("lock-heartbeat-seconds: 30\n");
            updated = true;
        }

        // Check for 'auto-update-schema'
        if (!fileConfig.contains("auto-update-schema") && !existingContent.contains("auto-update-schema:")) {
            newConfigContent.append("\n");
            newConfigContent
                    .append("# Automatically migrate 'data' column from LONGTEXT to MEDIUMBLOB for performance?\n");
            newConfigContent.append("# WARNING: This causes an ALTER TABLE which might lock the table briefly.\n");
            newConfigContent.append("auto-update-schema: true\n");
            updated = true;
        }

        // Check if sync-data exists
        if (!fileConfig.contains("sync-data") && !existingContent.contains("sync-data:")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Granular Data Synchronization Toggles\n");
            newConfigContent.append("# Enable or disable synchronization for specific data components.\n");
            newConfigContent.append("sync-data:\n");
            newConfigContent.append("  health: true\n");
            newConfigContent.append("  food-level: true\n");
            newConfigContent.append("  experience: true\n");
            newConfigContent.append("  inventory: true\n");
            newConfigContent.append("  armor: true\n");
            newConfigContent.append("  potion-effects: true\n");
            newConfigContent.append("  ender-chest: false\n");
            newConfigContent.append("  location: false\n");
            newConfigContent.append("  advancements: false\n");
            newConfigContent.append("  statistics: false\n");
            newConfigContent.append("  pdc: false\n");
            newConfigContent.append("  flight-gamemode: false\n");
            updated = true;
        }

        // Check for new sync-data keys specifically if section exists
        if (fileConfig.contains("sync-data")) {
            String[] newKeys = {"statistics", "pdc", "flight-gamemode"};
            for (String key : newKeys) {
                if (!fileConfig.contains("sync-data." + key) && !existingContent.contains(key + ":") && !existingContent.contains("sync-data." + key)) {
                    // To avoid indentation issues with append-only mode, we add as root keys
                    // if the section already exists, though this is a temporary fix.
                    newConfigContent.append("sync-data." + key + ": false\n");
                    updated = true;
                }
            }
        }

        // Check if sync-blacklist exists
        if (!fileConfig.contains("sync-blacklist") && !existingContent.contains("sync-blacklist:")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Server/World Blacklist\n");
            newConfigContent.append(
                    "# Data synchronization will be disabled for players on these servers or in these worlds.\n");
            newConfigContent.append("sync-blacklist:\n");
            newConfigContent.append("  servers:\n");
            newConfigContent.append("    - \"example-blacklisted-server\"\n");
            newConfigContent.append("  worlds:\n");
            newConfigContent.append("    - \"example_world_nether\"\n");
            updated = true;
        }

        if (updated) {
            try (java.io.FileWriter writer = new java.io.FileWriter(configFile, true)) {
                writer.write(newConfigContent.toString());
                getLogger().info("Automatically updated config.yml with new settings.");
            } catch (java.io.IOException e) {
                getLogger().severe("Failed to update config.yml: " + e.getMessage());
            }
            reloadConfig(); // Reload the config so internal logic sees the new values
        }
    }

    public static Gson getGson() {
        return GSON;
    }
}
