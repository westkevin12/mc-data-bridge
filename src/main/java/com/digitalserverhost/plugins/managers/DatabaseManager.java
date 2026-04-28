package com.digitalserverhost.plugins.managers;

import com.digitalserverhost.plugins.utils.HashUtils;
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
    private final String tableName;
    private final String currentTimeFunction;

    public DatabaseManager(FileConfiguration config, String tableName) {
        this.tableName = "`" + tableName.replace("`", "") + "`"; // Escape table name
        HikariConfig hikariConfig = new HikariConfig();

        String type = config.getString("database.type", "mysql").toLowerCase();
        String jdbcUrl;

        if (type.equals("sqlite")) {
            String fileName = config.getString("database.sqlite-file", "player_data.db");
            jdbcUrl = "jdbc:sqlite:" + fileName;
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
        } else {
            jdbcUrl = "jdbc:mysql://" + config.getString("database.host") + ":" + config.getInt("database.port")
                    + "/" + config.getString("database.database");
            // mysql-connector-j is the default for MySQL/MariaDB in this project
        }

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
        if (type.equals("sqlite")) {
            this.currentTimeFunction = "(strftime('%s','now') * 1000)";
        } else {
            this.currentTimeFunction = "(UNIX_TIMESTAMP() * 1000)";
        }
    }

    /**
     * Constructor for testing purposes.
     * Allows injection of a mock DataSource.
     */
    public DatabaseManager(HikariDataSource dataSource, String tableName, long lockTimeout) {
        this.dataSource = dataSource;
        this.tableName = "`" + tableName.replace("`", "") + "`";
        this.lockTimeout = lockTimeout;
        this.currentTimeFunction = "(UNIX_TIMESTAMP() * 1000)"; // Default for tests
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
        try (Connection connection = getConnection()) {
            // Use database-side time to prevent race conditions caused by clock drift between servers
            PreparedStatement updateStmt = connection.prepareStatement(
                    "UPDATE " + tableName
                            + " SET is_locked = 1, locking_server = ?, lock_timestamp = " + currentTimeFunction
                            + " WHERE uuid = ? AND (is_locked = 0 OR is_locked IS NULL OR lock_timestamp < " + currentTimeFunction + " - ?)");
            updateStmt.setString(1, serverId);
            updateStmt.setString(2, uuid.toString());
            updateStmt.setLong(3, lockTimeout);

            if (updateStmt.executeUpdate() > 0) {
                return true; // Lock acquired on existing row
            }

            try {
                PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO " + tableName
                                + " (uuid, data, is_locked, locking_server, lock_timestamp) VALUES (?, NULL, 1, ?, " + currentTimeFunction + ")");
                insertStmt.setString(1, uuid.toString());
                insertStmt.setString(2, serverId);
                insertStmt.executeUpdate();
                return true; // Lock acquired via new row
            } catch (SQLException e) {
                // This is expected if a race condition occurred and another server inserted the
                // row first.
            }

            return false;
        }
    }

    public boolean saveAndReleaseLock(String json, String checksum, String name, UUID uuid, String serverId) throws SQLException {
        String sql = "UPDATE " + tableName
                + " SET data = ?, data_checksum = ?, last_known_name = ?, identity_hash = ?, name_last_updated = ?, is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            statement.setString(2, checksum);
            statement.setString(3, name);
            statement.setString(4, HashUtils.generateIdentityHash(name, uuid));
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, uuid.toString());
            statement.setString(7, serverId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean saveAndReleaseLock(String json, UUID uuid, String serverId) throws SQLException {
        return saveAndReleaseLock(json, null, null, uuid, serverId);
    }

    public void releaseLock(UUID uuid, String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            System.err.println(
                    "[mc-data-bridge] CRITICAL: releaseLock was called with a null or empty serverId for UUID: "
                            + uuid);
            return;
        }

        String sql = "UPDATE " + tableName
                + " SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
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
        String sql = "UPDATE " + tableName
                + " SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ?";
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
        String sql = "UPDATE " + tableName + " SET lock_timestamp = " + currentTimeFunction + " WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, serverId);
            statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[mc-data-bridge] Failed to update lock for " + uuid + ": " + e.getMessage());
        }
    }

    public void updateLastKnownName(UUID uuid, String name) {
        String sql = "UPDATE " + tableName + " SET last_known_name = ?, identity_hash = ?, name_last_updated = ? WHERE uuid = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, HashUtils.generateIdentityHash(name, uuid));
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[mc-data-bridge] Failed to update last known name for " + uuid + ": " + e.getMessage());
        }
    }

    public UUID getUuidByName(String name) {
        String sql = "SELECT uuid FROM " + tableName + " WHERE last_known_name = ? ORDER BY name_last_updated DESC, last_updated DESC LIMIT 1";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (Exception e) {
            System.err.println("[mc-data-bridge] Failed to get UUID by name: " + e.getMessage());
        }
        return null;
    }

    public IdentityRecord getIdentityRecord(UUID uuid) {
        String sql = "SELECT last_known_name, identity_hash, name_last_updated FROM " + tableName + " WHERE uuid = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new IdentityRecord(
                            rs.getString("last_known_name"),
                            rs.getString("identity_hash"),
                            rs.getLong("name_last_updated")
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("[mc-data-bridge] Failed to get identity record for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public static class IdentityRecord {
        private final String lastKnownName;
        private final String identityHash;
        private final long nameLastUpdated;

        public IdentityRecord(String lastKnownName, String identityHash, long nameLastUpdated) {
            this.lastKnownName = lastKnownName;
            this.identityHash = identityHash;
            this.nameLastUpdated = nameLastUpdated;
        }

        public String getLastKnownName() { return lastKnownName; }
        public String getIdentityHash() { return identityHash; }
        public long getNameLastUpdated() { return nameLastUpdated; }
    }

    public boolean migrateData(UUID oldUuid, UUID newUuid) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Check if target UUID already has data. If so, we might want to backup or delete it.
                // For now, we'll assume we are overwriting the target or merging.
                // A safe approach is to delete the target's existing data row if it exists.
                String deleteSql = "DELETE FROM " + tableName + " WHERE uuid = ?";
                try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, newUuid.toString());
                    deleteStmt.executeUpdate();
                }

                // Update the old row to the new UUID
                String updateSql = "UPDATE " + tableName + " SET uuid = ? WHERE uuid = ?";
                try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                    updateStmt.setString(1, newUuid.toString());
                    updateStmt.setString(2, oldUuid.toString());
                    if (updateStmt.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public String getTableName() {
        return tableName;
    }
}
