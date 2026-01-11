package de.jumpstone.plugins;

import de.jumpstone.plugins.listeners.PlayerListener;
import de.jumpstone.plugins.managers.DatabaseManager;
import de.jumpstone.plugins.managers.EconomyManager;
import de.jumpstone.plugins.managers.PlaytimeManager;
import de.jumpstone.plugins.managers.RedisManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class HMTSync extends JavaPlugin {

    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private PlaytimeManager playtimeManager;
    private RedisManager redisManager;
    private boolean debugMode;
    private String serverId;
    private String tableName;
    private boolean redisEnabled = false;
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void onEnable() {
        // Detect platform and initialize appropriate implementation
        String platform = detectPlatform();

        switch (platform.toLowerCase()) {
            case "spigot":
            case "paper":
            case "bukkit":
                startSpigot();
                break;
            case "velocity":
                getLogger().severe(
                        "This JAR is intended for Spigot/Paper servers only. For Velocity, use the Velocity-specific build or remove plugin.yml from this JAR.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            case "bungee":
            case "bungeecord":
                getLogger().severe(
                        "This JAR is intended for Spigot/Paper servers only. For BungeeCord, use the BungeeCord-specific build or remove plugin.yml from this JAR.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            default:
                getLogger().warning("Unknown platform detected: " + platform + ". Assuming Spigot/Paper.");
                startSpigot();
                break;
        }
    }

    private String detectPlatform() {
        try {
            // Check if we're running on Velocity
            Class.forName("com.velocitypowered.api.plugin.Plugin");
            return "velocity";
        } catch (ClassNotFoundException e) {
            // Not Velocity
        }

        try {
            // Check if we're running on BungeeCord
            Class.forName("net.md_5.bungee.api.plugin.Plugin");
            return "bungee";
        } catch (ClassNotFoundException e) {
            // Not BungeeCord
        }

        // Assume Spigot/Paper if neither Velocity nor BungeeCord classes are found
        return "spigot";
    }

    private void startSpigot() {
        saveDefaultConfig();
        updateConfig(); // Check and update config if missing new keys
        this.debugMode = getConfig().getBoolean("debug", false);
        this.serverId = getConfig().getString("server-id", "default-server");
        this.tableName = getConfig().getString("table-prefix", "") + "player_data";
        if (this.serverId.equals("default-server")) {
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().warning("!!! Server-id is not set in config.yml. Using default. !!!");
            getLogger().warning("!!! This is UNSAFE for multi-server setups.           !!!");
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        databaseManager = new DatabaseManager(getConfig(), this.tableName);
        economyManager = new EconomyManager();
        playtimeManager = new PlaytimeManager();

        // Initialize Redis if enabled
        initializeRedis();

        getServer().getScheduler().runTaskAsynchronously(this, this::createServerTable);
        getServer().getScheduler().runTaskAsynchronously(this, this::releaseOrphanedLocks);

        // Log manager statuses
        if (debugMode) {
            getLogger().info("Economy Manager enabled: " + economyManager.isEnabled());
            getLogger().info("Playtime Manager enabled: " + playtimeManager.isEnabled());
        }

        // Create the listener instance
        PlayerListener playerListener = new PlayerListener(databaseManager, this);

        // Register its Bukkit events
        getServer().getPluginManager().registerEvents(playerListener, this);

        // Register Commands
        if (getCommand("hmtsync") != null) {
            getCommand("hmtsync")
                    .setExecutor(new de.jumpstone.plugins.commands.UnlockCommand(databaseManager));
        }

        // Register Debug Command
        if (getCommand("hmtsync-debug") != null) {
            getCommand("hmtsync-debug")
                    .setExecutor(new de.jumpstone.plugins.commands.DebugCommand(this));
        }

        // Register it as the listener for our custom plugin channel
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "hmt-sync:main", playerListener);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "hmt-sync:main");

        if (debugMode) {
            getLogger().info("Registered 'hmt-sync:main' plugin channel.");
        }

        getLogger().info("HMT Sync has been enabled on Spigot/Paper!");
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "hmt-sync:main");
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "hmt-sync:main");

        // Shutdown Redis connection if enabled
        if (redisManager != null) {
            redisManager.shutdown();
            getLogger().info("[HMTSync-Redis] Redis connection closed");
        }

        databaseManager.close();
        getLogger().info("HMT Sync has been disabled!");
    }

    private void createServerTable() {
        String escapedTableName = "`" + tableName + "`";
        String createTableSQL = "CREATE TABLE IF NOT EXISTS " + escapedTableName + " (" +
                "uuid VARCHAR(36) NOT NULL, " +
                "data LONGTEXT, " +
                "is_locked BOOLEAN DEFAULT 0, " +
                "locking_server VARCHAR(255) DEFAULT NULL, " +
                "lock_timestamp BIGINT DEFAULT 0, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (uuid)) ENGINE=InnoDB;";

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
                statement.executeUpdate(
                        "ALTER TABLE " + escapedTableName
                                + " ADD COLUMN last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            }

            ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, "data");
            if (columns.next()) {
                String typeName = columns.getString("TYPE_NAME");
                boolean needsMigration = "LONGTEXT".equalsIgnoreCase(typeName) || "TEXT".equalsIgnoreCase(typeName);

                if (needsMigration) {
                    if (getConfig().getBoolean("auto-update-schema", false)) {
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

        // Load strictly from file to check valid keys without defaults interference
        org.bukkit.configuration.file.YamlConfiguration fileConfig = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(configFile);

        StringBuilder newConfigContent = new StringBuilder();
        boolean updated = false;

        // Check for 'debug'
        if (!fileConfig.contains("debug")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Enable debug mode for verbose logging.\n");
            newConfigContent.append("debug: false\n");
            updated = true;
        }

        // Check for 'server-id'
        if (!fileConfig.contains("server-id")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Unique identifier for this server (Required).\n");
            newConfigContent.append("server-id: \"default-server\"\n");
            updated = true;
        }

        // Check for 'table-prefix'
        if (!fileConfig.contains("table-prefix")) {
            newConfigContent.append("\n");
            newConfigContent.append("# Set to prefix the player_data table (e.g., 'hmt_sync_').\n");
            newConfigContent.append("table-prefix: \"\"\n");
            updated = true;
        }

        // Check for 'database' section
        if (!fileConfig.contains("database")) {
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

        // Check for 'lock-timeout'
        if (!fileConfig.contains("lock-timeout")) {
            newConfigContent.append("\n");
            newConfigContent
                    .append("# The duration in milliseconds after which a player data lock is considered expired.\n");
            newConfigContent.append("# Default: 60000 (1 minute)\n");
            newConfigContent.append("lock-timeout: 60000\n");
            updated = true;
        }

        // Check if lock-heartbeat-seconds exists
        if (!fileConfig.contains("lock-heartbeat-seconds")) {
            newConfigContent.append("\n");
            newConfigContent
                    .append("# The interval in seconds between lock updates (heartbeats) while a player is online.\n");
            newConfigContent.append("# Default: 30\n");
            newConfigContent.append("lock-heartbeat-seconds: 30\n");
            updated = true;
        }

        // Check for 'auto-update-schema'
        if (!fileConfig.contains("auto-update-schema")) {
            newConfigContent.append("\n");
            newConfigContent
                    .append("# Automatically migrate 'data' column from LONGTEXT to MEDIUMBLOB for performance?\n");
            newConfigContent.append("# WARNING: This causes an ALTER TABLE which might lock the table briefly.\n");
            newConfigContent.append("auto-update-schema: true\n");
            updated = true;
        }

        // Check if sync-data exists
        if (!fileConfig.contains("sync-data")) {
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
            updated = true;
        }

        // Check if sync-blacklist exists
        if (!fileConfig.contains("sync-blacklist")) {
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

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public PlaytimeManager getPlaytimeManager() {
        return playtimeManager;
    }

    /**
     * Initialize Redis connection if enabled in config
     */
    private void initializeRedis() {
        if (!getConfig().getBoolean("redis.enabled", false)) {
            getLogger().info("[HMTSync-Redis] Redis integration disabled in configuration");
            return;
        }

        String strategy = getConfig().getString("redis.storage-strategy", "mysql-only");
        if (!"redis-mysql-hybrid".equals(strategy)) {
            getLogger().info("[HMTSync-Redis] Storage strategy is '" + strategy + "', Redis integration disabled");
            return;
        }

        String host = getConfig().getString("redis.host", "localhost");
        int port = getConfig().getInt("redis.port", 6379);
        String password = getConfig().getString("redis.password", "");
        int timeoutMs = getConfig().getInt("redis.timeout-ms", 2000);

        redisManager = new RedisManager(getLogger());
        redisEnabled = redisManager.initialize(host, port, password, timeoutMs);

        if (redisEnabled) {
            getLogger().info("[HMTSync-Redis] Successfully initialized Redis connection");
            // Enable hybrid mode in database manager
            databaseManager.enableHybridMode(redisManager);
            databaseManager.setLogger(getLogger());
        } else {
            getLogger()
                    .warning("[HMTSync-Redis] Failed to initialize Redis connection, falling back to MySQL-only mode");
            redisEnabled = false;
        }
    }

    /**
     * Get Redis manager instance
     */
    public RedisManager getRedisManager() {
        return redisManager;
    }

    /**
     * Check if Redis is enabled and available
     */
    public boolean isRedisEnabled() {
        return redisEnabled && redisManager != null && redisManager.isAvailable();
    }
}