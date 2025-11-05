package com.digitalserverhost.plugins;

import com.digitalserverhost.plugins.listeners.PlayerListener;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.Statement;

public class MCDataBridge extends JavaPlugin {

    private DatabaseManager databaseManager;
    private boolean debugMode;
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.debugMode = getConfig().getBoolean("debug", false);
        databaseManager = new DatabaseManager(getConfig());

        // Run table creation asynchronously
        getServer().getScheduler().runTaskAsynchronously(this, this::createServerTable);

        getServer().getPluginManager().registerEvents(new PlayerListener(databaseManager, this), this);
        getLogger().info("mc-data-bridge has been enabled!");
    }

    @Override
    public void onDisable() {
        databaseManager.close();
        getLogger().info("mc-data-bridge has been disabled!");
    }

    private void createServerTable() {
        // The SQL statement now includes the is_locked column and a more robust structure.
        String createTableSQL = "CREATE TABLE IF NOT EXISTS player_data (" +
                                "uuid VARCHAR(36) NOT NULL, " +
                                "data LONGTEXT NOT NULL, " +
                                "is_locked BOOLEAN DEFAULT 0, " +
                                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                                "PRIMARY KEY (uuid)) ENGINE=InnoDB;";

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate(createTableSQL);
            getLogger().info("Successfully verified or created the 'player_data' table.");

            // Example of how you might alter the table if it already exists without the new columns
            // This is a simple approach; a more robust migration system would be better for production
            if (!connection.getMetaData().getColumns(null, null, "player_data", "is_locked").next()) {
                statement.executeUpdate("ALTER TABLE player_data ADD COLUMN is_locked BOOLEAN DEFAULT 0");
                getLogger().info("Added 'is_locked' column to 'player_data' table.");
            }
            if (!connection.getMetaData().getColumns(null, null, "player_data", "last_updated").next()) {
                statement.executeUpdate("ALTER TABLE player_data ADD COLUMN last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                getLogger().info("Added 'last_updated' column to 'player_data' table.");
            }

        } catch (Exception e) {
            getLogger().severe("CRITICAL: Error creating or updating player_data table: " + e.getMessage());
            getLogger().severe("Disabling mc-data-bridge! The database is not in a usable state.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public static Gson getGson() {
        return GSON;
    }
}
