package com.digitalserverhost.plugins.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final long lockTimeout;

    public DatabaseManager(FileConfiguration config) {
        HikariConfig hikariConfig = new HikariConfig();

        String jdbcUrl = "jdbc:mysql://" + config.getString("database.host") + ":" + config.getInt("database.port")
                + "/" + config.getString("database.database");
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getString("database.username"));
        hikariConfig.setPassword(config.getString("database.password"));

        if (config.isConfigurationSection("database.properties")) {
            for (String key : config.getConfigurationSection("database.properties").getKeys(false)) {
                String value = config.getString("database.properties." + key);
                hikariConfig.addDataSourceProperty(key, value);
            }
        }

        hikariConfig.setMaximumPoolSize(config.getInt("database.pool-settings.maximum-pool-size", 10));
        hikariConfig.setMinimumIdle(config.getInt("database.pool-settings.minimum-idle", 10));
        hikariConfig.setMaxLifetime(config.getInt("database.pool-settings.max-lifetime", 1800000));
        hikariConfig.setConnectionTimeout(config.getInt("database.pool-settings.connection-timeout", 5000));
        hikariConfig.setIdleTimeout(config.getInt("database.pool-settings.idle-timeout", 600000));

        if (config.isConfigurationSection("database.optimizations")) {
            for (String key : config.getConfigurationSection("database.optimizations").getKeys(false)) {
                Object value = config.get("database.optimizations." + key);
                hikariConfig.addDataSourceProperty(key, value);
            }
        }

        this.dataSource = new HikariDataSource(hikariConfig);
        this.lockTimeout = config.getLong("lock-timeout", 60000); // 60 seconds default
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public boolean acquireLock(UUID uuid, String serverId) throws SQLException {
        long currentTime = System.currentTimeMillis();
        long expirationTime = currentTime - lockTimeout;

        try (Connection connection = getConnection()) {
            PreparedStatement updateStmt = connection.prepareStatement(
                    "UPDATE player_data SET is_locked = 1, locking_server = ?, lock_timestamp = ? WHERE uuid = ? AND (is_locked = 0 OR is_locked IS NULL OR lock_timestamp < ?)");
            updateStmt.setString(1, serverId);
            updateStmt.setLong(2, currentTime);
            updateStmt.setString(3, uuid.toString());
            updateStmt.setLong(4, expirationTime);

            if (updateStmt.executeUpdate() > 0) {
                return true; // Lock acquired on existing row
            }

            try {
                PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO player_data (uuid, data, is_locked, locking_server, lock_timestamp) VALUES (?, NULL, 1, ?, ?)");
                insertStmt.setString(1, uuid.toString());
                insertStmt.setString(2, serverId);
                insertStmt.setLong(3, currentTime);
                insertStmt.executeUpdate();
                return true; // Lock acquired via new row
            } catch (SQLException e) {
                // This is expected if a race condition occurred and another server inserted the
                // row first.
            }

            return false;
        }
    }

    public boolean saveAndReleaseLock(String json, UUID uuid, String serverId) throws SQLException {
        String sql = "UPDATE player_data SET data = ?, is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            statement.setString(2, uuid.toString());
            statement.setString(3, serverId);
            return statement.executeUpdate() > 0;
        }
    }

    public void releaseLock(UUID uuid, String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            System.err.println(
                    "[mc-data-bridge] CRITICAL: releaseLock was called with a null or empty serverId for UUID: "
                            + uuid);
            return;
        }

        String sql = "UPDATE player_data SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement releaseStatement = connection.prepareStatement(sql)) {
            releaseStatement.setString(1, uuid.toString());
            releaseStatement.setString(2, serverId);
            releaseStatement.executeUpdate();
        } catch (Exception e) {
            System.err.println("[mc-data-bridge] Failed to release lock for " + uuid + " on server " + serverId + ": "
                    + e.getMessage());
        }
    }

    /**
     * Forcefully releases the lock for a player, regardless of which server holds
     * it.
     * Used by the admin unlock command.
     */
    public boolean releaseLock(UUID uuid) {
        String sql = "UPDATE player_data SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            int rows = statement.executeUpdate();
            return rows > 0; // Return true if a row was actually updated (lock released or at least row
                             // touched)
        } catch (SQLException e) {
            System.err.println("[mc-data-bridge] Failed to force release lock for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public void updateLock(UUID uuid, String serverId) {
        long currentTime = System.currentTimeMillis();
        String sql = "UPDATE player_data SET lock_timestamp = ? WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, currentTime);
            statement.setString(2, uuid.toString());
            statement.setString(3, serverId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[mc-data-bridge] Failed to update lock for " + uuid + ": " + e.getMessage());
        }
    }
}
