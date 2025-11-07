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
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void onEnable() {
        // Platform detection will be implemented here later
        startSpigot();
    }

    private void startSpigot() {
        saveDefaultConfig();
        this.debugMode = getConfig().getBoolean("debug", false);
        this.serverId = getConfig().getString("server-id", "default-server");
        if (this.serverId.equals("default-server")) {
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            getLogger().warning("!!! Server-id is not set in config.yml. Using default. !!!");
            getLogger().warning("!!! This is UNSAFE for multi-server setups.           !!!");
            getLogger().warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        databaseManager = new DatabaseManager(getConfig());

        getServer().getScheduler().runTaskAsynchronously(this, this::createServerTable);
        getServer().getScheduler().runTaskAsynchronously(this, this::releaseOrphanedLocks);

        // Create the listener instance
        PlayerListener playerListener = new PlayerListener(databaseManager, this);
        
        // Register its Bukkit events
        getServer().getPluginManager().registerEvents(playerListener, this);

        // Register it as the listener for our custom plugin channel
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "mc-data-bridge:main", playerListener);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "mc-data-bridge:main");

        if (debugMode) {
            getLogger().info("Registered 'mc-data-bridge:main' plugin channel.");
        }

        getLogger().info("mc-data-bridge has been enabled on Spigot/Paper!");
    }

    @Override
    public void onDisable() {
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "mc-data-bridge:main");
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "mc-data-bridge:main");
        databaseManager.close();
        getLogger().info("mc-data-bridge has been disabled!");
    }

    private void createServerTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS player_data (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "data LONGTEXT, " +
                                "is_locked BOOLEAN DEFAULT 0, " +
                                "locking_server VARCHAR(255) DEFAULT NULL, " +
                                "lock_timestamp BIGINT DEFAULT 0, " +
                                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                                "PRIMARY KEY (uuid)) ENGINE=InnoDB;";
        
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(createTableSQL);
            getLogger().info("Successfully verified or created the 'player_data' table.");

            if (!connection.getMetaData().getColumns(null, null, "player_data", "is_locked").next()) {
                statement.executeUpdate("ALTER TABLE player_data ADD COLUMN is_locked BOOLEAN DEFAULT 0");
            }
            if (!connection.getMetaData().getColumns(null, null, "player_data", "locking_server").next()) {
                statement.executeUpdate("ALTER TABLE player_data ADD COLUMN locking_server VARCHAR(255) DEFAULT NULL");
            }
            if (!connection.getMetaData().getColumns(null, null, "player_data", "lock_timestamp").next()) {
                statement.executeUpdate("ALTER TABLE player_data ADD COLUMN lock_timestamp BIGINT DEFAULT 0");
            }
            if (!connection.getMetaData().getColumns(null, null, "player_data", "last_updated").next()) {
                statement.executeUpdate("ALTER TABLE player_data ADD COLUMN last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            }

            ResultSet columns = connection.getMetaData().getColumns(null, null, "player_data", "data");
            if (columns.next() && "NO".equalsIgnoreCase(columns.getString("IS_NULLABLE"))) {
                statement.executeUpdate("ALTER TABLE player_data MODIFY COLUMN data LONGTEXT NULL");
            }
        } catch (Exception e) {
            getLogger().severe("CRITICAL: Error creating or updating player_data table: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void releaseOrphanedLocks() {
        String releaseSQL = "UPDATE player_data SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE locking_server = ?";
        
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(releaseSQL)) {
            
            statement.setString(1, this.serverId);
            int affectedRows = statement.executeUpdate();
            
            if (affectedRows > 0) {
                getLogger().info("Released " + affectedRows + " orphaned player data locks for server: " + this.serverId);
            } else {
                getLogger().info("No orphaned player data locks found for server: " + this.serverId);
            }
        } catch (Exception e) {
            getLogger().severe("CRITICAL: Could not release player data locks for " + this.serverId + "! Error: " + e.getMessage());
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String getServerId() {
        return serverId;
    }

    public static Gson getGson() {
        return GSON;
    }
}
