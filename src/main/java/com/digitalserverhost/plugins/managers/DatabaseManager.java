package com.digitalserverhost.plugins.managers;

import com.digitalserverhost.plugins.utils.HashUtils;
import com.digitalserverhost.plugins.utils.PlayerData;
import com.digitalserverhost.plugins.utils.PlayerData.CompanionSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger("mc-data-bridge");
    private static final String UPDATE_SQL_PREFIX = "UPDATE ";
    private static final String INSERT_INTO_SQL = "INSERT INTO ";
    private static final String WHERE_UUID_SQL = " WHERE uuid = ?";
    private static final String MIGRATE_UUID_SQL = " SET uuid = ? WHERE uuid = ?";
    private static final String COL_HEALTH = "health";
    private static final String COL_ADVANCEMENTS = "advancements";
    private static final String MODE_FOLLOW = "follow";
    private static final String MODE_RETURN = "return";
    private static final Gson GSON = new GsonBuilder().create();

    private final HikariDataSource dataSource;
    private final long lockTimeout;
    private final String tableName;
    private final String currentTimeFunction;

    private final String tablePrefix;
    private final String inventoriesTable;
    private final String statisticsTable;
    private final String metadataTable;
    private final String companionsTable;
    private final String serializationFormat;
    private final boolean isSQLite;

    public DatabaseManager(FileConfiguration config, String tableName) {
        this.tableName = "`" + tableName.replace("`", "") + "`"; // Escape table name
        this.tablePrefix = config.getString("table-prefix", "").replace("`", "");
        this.inventoriesTable = "`" + this.tablePrefix + "databridge_inventories`";
        this.statisticsTable = "`" + this.tablePrefix + "databridge_statistics`";
        this.metadataTable = "`" + this.tablePrefix + "databridge_metadata`";
        this.companionsTable = "`" + this.tablePrefix + "databridge_companions`";
        this.serializationFormat = config.getString("database.serialization-format", "json").toLowerCase();

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

        this.isSQLite = type.equals("sqlite");
        this.dataSource = new HikariDataSource(hikariConfig);
        this.lockTimeout = config.getLong("lock-timeout", 60000); // 60 seconds default
        if (this.isSQLite) {
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
        this.tablePrefix = "";
        this.inventoriesTable = "`databridge_inventories`";
        this.statisticsTable = "`databridge_statistics`";
        this.metadataTable = "`databridge_metadata`";
        this.companionsTable = "`databridge_companions`";
        this.serializationFormat = "json";
        this.isSQLite = false;
    }

    public int getActiveConnections() {
        if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
            return dataSource.getHikariPoolMXBean().getActiveConnections();
        }
        return 0;
    }

    public int getPendingThreads() {
        if (dataSource != null && dataSource.getHikariPoolMXBean() != null) {
            return dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection();
        }
        return 0;
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
        long startTime = System.currentTimeMillis();
        try {
            return acquireLockInternal(uuid, serverId);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            MetricsManager.getInstance().recordLockAcquisitionLatency(duration);
        }
    }

    private boolean acquireLockInternal(UUID uuid, String serverId) throws SQLException {
        try (Connection connection = getConnection()) {
            // Use database-side time to prevent race conditions caused by clock drift between servers
            String updateSql = UPDATE_SQL_PREFIX + tableName
                    + " SET is_locked = 1, locking_server = ?, lock_timestamp = " + currentTimeFunction
                    + " WHERE uuid = ? AND (is_locked = 0 OR is_locked IS NULL OR lock_timestamp < " + currentTimeFunction + " - ?)";
            
            try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                updateStmt.setString(1, serverId);
                updateStmt.setString(2, uuid.toString());
                updateStmt.setLong(3, lockTimeout);

                if (updateStmt.executeUpdate() > 0) {
                    return true; // Lock acquired on existing row
                }
            }

            try {
                String insertSql = INSERT_INTO_SQL + tableName
                        + " (uuid, data, is_locked, locking_server, lock_timestamp) VALUES (?, NULL, 1, ?, " + currentTimeFunction + ")";
                try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setString(2, serverId);
                    return insertStmt.executeUpdate() > 0;
                }
            } catch (SQLException _) {
                // Potential race condition: someone else inserted the row while we were trying.
                // Re-attempting the update one last time.
                try (PreparedStatement retryUpdateStmt = connection.prepareStatement(updateSql)) {
                    retryUpdateStmt.setString(1, serverId);
                    retryUpdateStmt.setString(2, uuid.toString());
                    retryUpdateStmt.setLong(3, lockTimeout);
                    return retryUpdateStmt.executeUpdate() > 0;
                }
            }
        }
    }

    public boolean saveAndReleaseLockComponents(com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, String name, UUID uuid, String serverId, String seed) throws SQLException {
        savePlayerDataComponents(plugin, data, uuid);
        
        String sql = UPDATE_SQL_PREFIX + tableName
                + " SET data = NULL, data_checksum = NULL, last_known_name = ?, identity_hash = ?, name_last_updated = ?, is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, HashUtils.generateIdentityHash(name, uuid, seed));
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, uuid.toString());
            statement.setString(5, serverId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean saveAndReleaseLock(String json, String checksum, String name, UUID uuid, String serverId, String seed) throws SQLException {
        String sql = UPDATE_SQL_PREFIX + tableName
                + " SET data = ?, data_checksum = ?, last_known_name = ?, identity_hash = ?, name_last_updated = ?, is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            statement.setString(2, checksum);
            statement.setString(3, name);
            statement.setString(4, HashUtils.generateIdentityHash(name, uuid, seed));
            statement.setLong(5, System.currentTimeMillis());
            statement.setString(6, uuid.toString());
            statement.setString(7, serverId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean saveAndReleaseLock(String json, String checksum, String name, UUID uuid, String serverId) throws SQLException {
        return saveAndReleaseLock(json, checksum, name, uuid, serverId, null);
    }

    public boolean saveAndReleaseLock(String json, UUID uuid, String serverId) throws SQLException {
        return saveAndReleaseLock(json, null, null, uuid, serverId);
    }

    public void releaseLock(UUID uuid, String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            LOGGER.log(java.util.logging.Level.SEVERE, "CRITICAL: releaseLock was called with a null or empty serverId for UUID: {0}", uuid);
            return;
        }

        String sql = UPDATE_SQL_PREFIX + tableName
                + " SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement releaseStatement = connection.prepareStatement(sql)) {
            releaseStatement.setString(1, uuid.toString());
            releaseStatement.setString(2, serverId);
            releaseStatement.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to release lock for {0} on server {1}: {2}", new Object[]{uuid, serverId, e.getMessage()});
        }
    }

    /**
     * Forcefully releases the lock for a player, regardless of which server holds
     * it.
     * Used by the admin unlock command.
     */
    public boolean releaseLock(UUID uuid) {
        String sql = UPDATE_SQL_PREFIX + tableName
                + " SET is_locked = 0, locking_server = NULL, lock_timestamp = 0 WHERE uuid = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            int rows = statement.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to force release lock for {0}: {1}", new Object[]{uuid, e.getMessage()});
            return false;
        }
    }

    public void updateLock(UUID uuid, String serverId) {
        String sql = UPDATE_SQL_PREFIX + tableName + " SET lock_timestamp = " + currentTimeFunction + " WHERE uuid = ? AND locking_server = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, serverId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to update lock for {0}: {1}", new Object[]{uuid, e.getMessage()});
        }
    }

    public void updateLastKnownName(UUID uuid, String name, String seed) {
        String sql = UPDATE_SQL_PREFIX + tableName + " SET last_known_name = ?, identity_hash = ?, name_last_updated = ? WHERE uuid = ?";
        try (Connection connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, HashUtils.generateIdentityHash(name, uuid, seed));
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to update last known name for {0}: {1}", new Object[]{uuid, e.getMessage()});
        }
    }

    public void updateLastKnownName(UUID uuid, String name) {
        updateLastKnownName(uuid, name, null);
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
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to get UUID by name: {0}", e.getMessage());
        }
        return null;
    }

    public IdentityRecord getIdentityRecord(UUID uuid) {
        String sql = "SELECT last_known_name, identity_hash, name_last_updated FROM " + tableName + WHERE_UUID_SQL;
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
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to get identity record for {0}: {1}", new Object[]{uuid, e.getMessage()});
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


    @SuppressWarnings("java:S1168") // null preserves SQL NULL semantics for BLOB columns
    public byte[] serializeListToBlob(List<String> list) {
        if (list == null) return null;
        if ("binary".equals(serializationFormat)) {
            return serializeListToBinary(list);
        } else {
            return serializeListToJson(list);
        }
    }

    public List<String> deserializeListFromBlob(byte[] blob) {
        if (blob == null) return new ArrayList<>();
        if (blob.length > 0 && blob[0] == (byte) '[') {
            return deserializeListFromJson(blob);
        } else {
            return deserializeListFromBinary(blob);
        }
    }

    private byte[] serializeListToBinary(List<String> list) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream dos = new java.io.DataOutputStream(baos)) {
            dos.writeInt(list.size());
            for (String item : list) {
                if (item == null || item.isEmpty()) {
                    dos.writeInt(-1);
                } else {
                    byte[] bytes;
                    if (item.startsWith("{")) {
                        bytes = item.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        bytes = decodeItemBytes(item);
                    }
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                }
            }
        } catch (java.io.IOException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed binary list serialization: {0}", e.getMessage());
        }
        return baos.toByteArray();
    }

    private List<String> deserializeListFromBinary(byte[] blob) {
        List<String> list = new ArrayList<>();
        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(blob);
        try (java.io.DataInputStream dis = new java.io.DataInputStream(bais)) {
            int size = dis.readInt();
            if (size < 0 || size > 1000) {
                LOGGER.log(java.util.logging.Level.WARNING, "Binary deserialization aborted: invalid list size {0}", size);
                return list;
            }
            boolean success = true;
            for (int i = 0; i < size && success; i++) {
                success = readAndAddBinaryItem(dis, list, i);
            }
        } catch (java.io.IOException _) {
            // Corrupt or empty stream
        }
        return list;
    }

    private boolean readAndAddBinaryItem(java.io.DataInputStream dis, List<String> list, int index) throws java.io.IOException {
        if (dis.available() < 4) {
            LOGGER.log(java.util.logging.Level.WARNING, "Binary deserialization aborted: unexpected end of stream at index {0}", index);
            return false;
        }
        int len = dis.readInt();
        if (len == -1) {
            list.add(null);
            return true;
        }
        if (len < 0 || len > 1024 * 1024) { // 1 MB limit
            LOGGER.log(java.util.logging.Level.WARNING, "Binary deserialization aborted: invalid item length {0} at index {1}", new Object[]{len, index});
            return false;
        }
        if (dis.available() < len) {
            LOGGER.log(java.util.logging.Level.WARNING, "Binary deserialization aborted: not enough bytes available for item of length {0} at index {1}", new Object[]{len, index});
            return false;
        }
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        if (bytes.length > 2 && bytes[0] == (byte) '{') {
            list.add(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } else {
            list.add(Base64.getEncoder().encodeToString(bytes));
        }
        return true;
    }

    private byte[] serializeListToJson(List<String> list) {
        String json = GSON.toJson(list);
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] decodeItemBytes(String item) {
        try {
            return Base64.getDecoder().decode(item);
        } catch (IllegalArgumentException _) {
            return item.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> deserializeListFromJson(byte[] blob) {
        String json = new String(blob, java.nio.charset.StandardCharsets.UTF_8);
        return GSON.fromJson(json, List.class);
    }

    public boolean savePlayerDataComponents(com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                saveInventoryComponent(connection, plugin, data, uuid);
                saveStatisticsComponent(connection, plugin, data, uuid);
                saveMetadataComponent(connection, plugin, data, uuid);
                saveCompanionComponent(connection, plugin, data, uuid);
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

    public boolean saveInventoryComponent(com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        try (Connection connection = getConnection()) {
            saveInventoryComponent(connection, plugin, data, uuid);
            return true;
        }
    }

    private void saveInventoryComponent(Connection connection, com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        byte[] dbInventory = null;
        byte[] dbArmor = null;
        byte[] dbEnderChest = null;

        String sqlSelect = "SELECT inventory_blob, armor_blob, ender_chest_blob FROM " + inventoriesTable + WHERE_UUID_SQL;
        try (PreparedStatement selectStmt = connection.prepareStatement(sqlSelect)) {
            selectStmt.setString(1, uuid.toString());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    dbInventory = rs.getBytes("inventory_blob");
                    dbArmor = rs.getBytes("armor_blob");
                    dbEnderChest = rs.getBytes("ender_chest_blob");
                }
            }
        }

        byte[] targetInventory = plugin.isSyncEnabled("inventory") ? serializeListToBlob(data.getInventoryContentsNBT()) : dbInventory;
        byte[] targetArmor = plugin.isSyncEnabled("armor") ? serializeListToBlob(data.getArmorContentsNBT()) : dbArmor;
        byte[] targetEnderChest = plugin.isSyncEnabled("ender-chest") ? serializeListToBlob(data.getEnderChestContentsNBT()) : dbEnderChest;

        String sqlUpsert;
        if (isSQLite) {
            sqlUpsert = INSERT_INTO_SQL + inventoriesTable + " (uuid, inventory_blob, armor_blob, ender_chest_blob) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET inventory_blob = excluded.inventory_blob, armor_blob = excluded.armor_blob, ender_chest_blob = excluded.ender_chest_blob";
        } else {
            sqlUpsert = INSERT_INTO_SQL + inventoriesTable + " (uuid, inventory_blob, armor_blob, ender_chest_blob) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE inventory_blob = VALUES(inventory_blob), armor_blob = VALUES(armor_blob), ender_chest_blob = VALUES(ender_chest_blob)";
        }
        try (PreparedStatement stmt = connection.prepareStatement(sqlUpsert)) {
            stmt.setString(1, uuid.toString());
            stmt.setBytes(2, targetInventory);
            stmt.setBytes(3, targetArmor);
            stmt.setBytes(4, targetEnderChest);
            stmt.executeUpdate();
        }
    }

    @SuppressWarnings("null")
    private void saveStatisticsComponent(Connection connection, com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        double dbHealth = 20.0;
        int dbFoodLevel = 20;
        int dbXpLevel = 0;
        float dbXpExp = 0.0f;
        int dbXpTotal = 0;
        float dbSaturation = 5.0f;
        float dbExhaustion = 0.0f;
        String dbVanillaStatsJson = "{}";

        String sqlSelect = "SELECT " + COL_HEALTH + ", food_level, xp_level, xp_exp, xp_total, saturation, exhaustion, vanilla_stats_json FROM " + statisticsTable + WHERE_UUID_SQL;
        try (PreparedStatement selectStmt = connection.prepareStatement(sqlSelect)) {
            selectStmt.setString(1, uuid.toString());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    dbHealth = rs.getDouble(COL_HEALTH);
                    dbFoodLevel = rs.getInt("food_level");
                    dbXpLevel = rs.getInt("xp_level");
                    dbXpExp = rs.getFloat("xp_exp");
                    dbXpTotal = rs.getInt("xp_total");
                    dbSaturation = rs.getFloat("saturation");
                    dbExhaustion = rs.getFloat("exhaustion");
                    dbVanillaStatsJson = rs.getString("vanilla_stats_json");
                    if (dbVanillaStatsJson == null) dbVanillaStatsJson = "{}";
                }
            }
        }

        double targetHealth = plugin.isSyncEnabled(COL_HEALTH) ? data.getHealth() : dbHealth;
        int targetFoodLevel = dbFoodLevel;
        float targetSaturation = dbSaturation;
        float targetExhaustion = dbExhaustion;
        if (plugin.isSyncEnabled("food-level")) {
            targetFoodLevel = data.getFoodLevel();
            targetSaturation = data.getSaturation();
            targetExhaustion = data.getExhaustion();
        }
        int targetXpLevel = dbXpLevel;
        float targetXpExp = dbXpExp;
        int targetXpTotal = dbXpTotal;
        if (plugin.isSyncEnabled("experience")) {
            targetXpLevel = data.getLevel();
            targetXpExp = data.getExp();
            targetXpTotal = data.getTotalExperience();
        }

        PlayerData jsonStatsData;
        try {
            PlayerData parsed = GSON.fromJson(dbVanillaStatsJson, PlayerData.class);
            jsonStatsData = (parsed != null) ? parsed : new PlayerData();
        } catch (Exception e) {
            throw new SQLException("Failed to parse vanilla statistics JSON from database, aborting save to prevent data loss: " + e.getMessage(), e);
        }
        if (plugin.isSyncEnabled("potion-effects")) jsonStatsData.setPotionEffects(data.getPotionEffects());
        if (plugin.isSyncEnabled("flight-gamemode")) {
            jsonStatsData.setFlying(data.isFlying());
            jsonStatsData.setAllowFlight(data.isAllowFlight());
            jsonStatsData.setGameMode(data.getGameMode());
        }
        if (plugin.isSyncEnabledNewFeature("location")) {
            jsonStatsData.setWorld(data.getWorld());
            jsonStatsData.setX(data.getX());
            jsonStatsData.setY(data.getY());
            jsonStatsData.setZ(data.getZ());
            jsonStatsData.setYaw(data.getYaw());
            jsonStatsData.setPitch(data.getPitch());
        }
        if (plugin.isSyncEnabledNewFeature("statistics")) jsonStatsData.setStatistics(data.getStatistics());
        jsonStatsData.setDiscoveredRecipes(data.getDiscoveredRecipes());

        String targetVanillaStatsJson = GSON.toJson(jsonStatsData);

        String sqlUpsert;
        if (isSQLite) {
            sqlUpsert = INSERT_INTO_SQL + statisticsTable + " (uuid, " + COL_HEALTH + ", food_level, xp_level, xp_exp, xp_total, saturation, exhaustion, vanilla_stats_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET " + COL_HEALTH + " = excluded." + COL_HEALTH + ", food_level = excluded.food_level, xp_level = excluded.xp_level, xp_exp = excluded.xp_exp, " +
                        "xp_total = excluded.xp_total, saturation = excluded.saturation, exhaustion = excluded.exhaustion, vanilla_stats_json = excluded.vanilla_stats_json";
        } else {
            sqlUpsert = INSERT_INTO_SQL + statisticsTable + " (uuid, " + COL_HEALTH + ", food_level, xp_level, xp_exp, xp_total, saturation, exhaustion, vanilla_stats_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " + COL_HEALTH + " = VALUES(" + COL_HEALTH + "), food_level = VALUES(food_level), xp_level = VALUES(xp_level), xp_exp = VALUES(xp_exp), " +
                        "xp_total = VALUES(xp_total), saturation = VALUES(saturation), exhaustion = VALUES(exhaustion), vanilla_stats_json = VALUES(vanilla_stats_json)";
        }
        try (PreparedStatement stmt = connection.prepareStatement(sqlUpsert)) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, targetHealth);
            stmt.setInt(3, targetFoodLevel);
            stmt.setInt(4, targetXpLevel);
            stmt.setFloat(5, targetXpExp);
            stmt.setInt(6, targetXpTotal);
            stmt.setFloat(7, targetSaturation);
            stmt.setFloat(8, targetExhaustion);
            stmt.setString(9, targetVanillaStatsJson);
            stmt.executeUpdate();
        }
    }

    private void saveMetadataComponent(Connection connection, com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        String dbPdc = null;
        String dbAdvancements = null;

        String sqlSelect = "SELECT pdc_data, " + COL_ADVANCEMENTS + " FROM " + metadataTable + WHERE_UUID_SQL;
        try (PreparedStatement selectStmt = connection.prepareStatement(sqlSelect)) {
            selectStmt.setString(1, uuid.toString());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    dbPdc = rs.getString("pdc_data");
                    dbAdvancements = rs.getString(COL_ADVANCEMENTS);
                }
            }
        }

        String targetPdc = plugin.isSyncEnabled("pdc") ? data.getPdcNBT() : dbPdc;
        String targetAdvancements = plugin.isSyncEnabled(COL_ADVANCEMENTS) ? GSON.toJson(data.getAdvancements()) : dbAdvancements;

        String sqlUpsert;
        if (isSQLite) {
            sqlUpsert = INSERT_INTO_SQL + metadataTable + " (uuid, pdc_data, " + COL_ADVANCEMENTS + ") VALUES (?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET pdc_data = excluded.pdc_data, " + COL_ADVANCEMENTS + " = excluded." + COL_ADVANCEMENTS;
        } else {
            sqlUpsert = INSERT_INTO_SQL + metadataTable + " (uuid, pdc_data, " + COL_ADVANCEMENTS + ") VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE pdc_data = VALUES(pdc_data), " + COL_ADVANCEMENTS + " = VALUES(" + COL_ADVANCEMENTS + ")";
        }
        try (PreparedStatement stmt = connection.prepareStatement(sqlUpsert)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, targetPdc);
            stmt.setString(3, targetAdvancements);
            stmt.executeUpdate();
        }
    }

    private void saveCompanionComponent(Connection connection, com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        if (!plugin.isSyncEnabledNewFeature("companions")) return;
        String mode = MODE_FOLLOW;
        try {
            mode = plugin.getConfig().getString("companions.mode", MODE_FOLLOW).toLowerCase();
        } catch (Exception _) { /* default to follow if config is missing */ }
        if (mode.equals("untracked") || mode.equals("off")) return;

        String dbCompanionsNbt = null;
        String sqlSelect = "SELECT companions_nbt FROM " + companionsTable + WHERE_UUID_SQL;
        try (PreparedStatement selectStmt = connection.prepareStatement(sqlSelect)) {
            selectStmt.setString(1, uuid.toString());
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) dbCompanionsNbt = rs.getString("companions_nbt");
            }
        }

        String targetCompanionsNbt = mergeCompanionsNbt(data.getCompanionsNBT(), dbCompanionsNbt, mode, plugin.getServerId());

        String sqlUpsert;
        if (isSQLite) {
            sqlUpsert = INSERT_INTO_SQL + companionsTable + " (uuid, companions_nbt) VALUES (?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET companions_nbt = excluded.companions_nbt";
        } else {
            sqlUpsert = INSERT_INTO_SQL + companionsTable + " (uuid, companions_nbt) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE companions_nbt = VALUES(companions_nbt)";
        }
        try (PreparedStatement stmt = connection.prepareStatement(sqlUpsert)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, targetCompanionsNbt);
            stmt.executeUpdate();
        }
    }

    private String mergeCompanionsNbt(String localCompanionsNbt, String dbCompanionsNbt, String mode, String currentServer) {
        if (localCompanionsNbt == null) {
            if (!mode.equals(MODE_RETURN)) {
                return null;
            }
            List<CompanionSnapshot> toKeep = new ArrayList<>();
            CompanionSnapshot[] dbSnaps = parseCompanionSnapshots(dbCompanionsNbt);
            addOtherServerCompanions(dbSnaps, toKeep, currentServer);
            return toKeep.isEmpty() ? null : GSON.toJson(toKeep);
        }

        if (dbCompanionsNbt == null || dbCompanionsNbt.isEmpty()) {
            return localCompanionsNbt;
        }

        try {
            CompanionSnapshot[] localSnaps = parseCompanionSnapshots(localCompanionsNbt);
            CompanionSnapshot[] dbSnaps = parseCompanionSnapshots(dbCompanionsNbt);
            List<CompanionSnapshot> merged = new ArrayList<>();

            if (mode.equals(MODE_RETURN)) {
                addOtherServerCompanions(dbSnaps, merged, currentServer);
            }

            addAllNonNullSnapshots(localSnaps, merged);
            return merged.isEmpty() ? null : GSON.toJson(merged);
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Error merging companions: {0}", e.getMessage());
            return localCompanionsNbt;
        }
    }

    private CompanionSnapshot[] parseCompanionSnapshots(String json) {
        if (json == null || json.isEmpty()) return new CompanionSnapshot[0];
        try {
            CompanionSnapshot[] result = GSON.fromJson(json, CompanionSnapshot[].class);
            return result != null ? result : new CompanionSnapshot[0];
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Failed to parse companion snapshots: {0}", e.getMessage());
            return new CompanionSnapshot[0];
        }
    }

    private void addOtherServerCompanions(CompanionSnapshot[] snaps, List<CompanionSnapshot> list, String currentServer) {
        if (snaps == null) return;
        for (CompanionSnapshot snap : snaps) {
            if (snap != null && snap.getSourceServerId() != null && !snap.getSourceServerId().equalsIgnoreCase(currentServer)) {
                list.add(snap);
            }
        }
    }

    private void addAllNonNullSnapshots(CompanionSnapshot[] snaps, List<CompanionSnapshot> list) {
        if (snaps == null) return;
        for (CompanionSnapshot snap : snaps) {
            if (snap != null) {
                list.add(snap);
            }
        }
    }

    public PlayerData loadPlayerDataComponents(com.digitalserverhost.plugins.MCDataBridge plugin, UUID uuid) throws SQLException {
        return loadPlayerDataComponents(plugin, uuid, null);
    }

    public PlayerData loadPlayerDataComponents(com.digitalserverhost.plugins.MCDataBridge plugin, UUID uuid, String name) throws SQLException {
        // First check for legacy monolithic data in the main table
        try (Connection connection = getConnection()) {
            PlayerData legacy = loadLegacyData(connection, plugin, uuid, name);
            if (legacy != null) return legacy;
        }

        // Component-based loading
        PlayerData data = new PlayerData();
        boolean loadedAny = false;

        try (Connection connection = getConnection()) {
            if (plugin.isSyncEnabled("inventory") || plugin.isSyncEnabled("armor") || plugin.isSyncEnabled("ender-chest")) {
                loadedAny |= loadInventoryComponent(connection, data, uuid);
            }
            loadedAny |= loadStatisticsComponent(connection, data, uuid);
            if (plugin.isSyncEnabled("pdc") || plugin.isSyncEnabled(COL_ADVANCEMENTS)) {
                loadedAny |= loadMetadataComponent(connection, data, uuid);
            }
            if (plugin.isSyncEnabledNewFeature("companions")) {
                loadedAny |= loadCompanionComponent(connection, plugin, data, uuid);
            }
        }

        return loadedAny ? data : null;
    }

    @SuppressWarnings("null")
    private PlayerData loadLegacyData(Connection connection, com.digitalserverhost.plugins.MCDataBridge plugin, UUID uuid, String name) throws SQLException {
        String sqlSelectLegacy = "SELECT data, data_checksum FROM " + tableName + WHERE_UUID_SQL;
        try (PreparedStatement statement = connection.prepareStatement(sqlSelectLegacy)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                byte[] dataBytes = rs.getBytes("data");
                if (dataBytes == null || dataBytes.length == 0) return null;
                String json = new String(dataBytes, java.nio.charset.StandardCharsets.UTF_8);
                if (json.trim().isEmpty() || "{}".equals(json)) return null;

                String checksum = rs.getString("data_checksum");
                String seed = plugin.getSecuritySeed();
                if (plugin.getConfig().getBoolean("security.verify-data-integrity", true)
                        && checksum != null
                        && !PlayerData.verifyChecksum(json, checksum, seed)
                        && !PlayerData.verifyChecksum(json, checksum, null)) {
                    LOGGER.log(java.util.logging.Level.SEVERE, "CRITICAL: Legacy checksum mismatch for {0}!", uuid);
                    return null;
                }
                if (checksum != null
                        && !PlayerData.verifyChecksum(json, checksum, seed)
                        && PlayerData.verifyChecksum(json, checksum, null)
                        && name != null) {
                    plugin.getLogger().log(java.util.logging.Level.INFO, "Migrating data for {0} to salted checksum format", name);
                }

                PlayerData data = GSON.fromJson(json, PlayerData.class);
                if (data != null) {
                    // Save to component tables and null out legacy column
                    savePlayerDataComponents(plugin, data, uuid);
                    String sqlClearLegacy = UPDATE_SQL_PREFIX + tableName + " SET data = NULL" + WHERE_UUID_SQL;
                    try (PreparedStatement clearStmt = connection.prepareStatement(sqlClearLegacy)) {
                        clearStmt.setString(1, uuid.toString());
                        clearStmt.executeUpdate();
                    }
                }
                return data;
            }
        }
    }

    private boolean loadInventoryComponent(Connection connection, PlayerData data, UUID uuid) throws SQLException {
        String sqlInv = "SELECT inventory_blob, armor_blob, ender_chest_blob FROM " + inventoriesTable + WHERE_UUID_SQL;
        try (PreparedStatement stmt = connection.prepareStatement(sqlInv)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                byte[] invBlob = rs.getBytes("inventory_blob");
                byte[] armorBlob = rs.getBytes("armor_blob");
                byte[] ecBlob = rs.getBytes("ender_chest_blob");
                if (invBlob != null) data.setInventoryContentsNBT(deserializeListFromBlob(invBlob));
                if (armorBlob != null) data.setArmorContentsNBT(deserializeListFromBlob(armorBlob));
                if (ecBlob != null) data.setEnderChestContentsNBT(deserializeListFromBlob(ecBlob));
                return true;
            }
        }
    }

    private boolean loadStatisticsComponent(Connection connection, PlayerData data, UUID uuid) throws SQLException {
        String sqlStats = "SELECT " + COL_HEALTH + ", food_level, xp_level, xp_exp, xp_total, saturation, exhaustion, vanilla_stats_json FROM " + statisticsTable + WHERE_UUID_SQL;
        try (PreparedStatement stmt = connection.prepareStatement(sqlStats)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                data.setHealth(rs.getDouble(COL_HEALTH));
                data.setFoodLevel(rs.getInt("food_level"));
                data.setLevel(rs.getInt("xp_level"));
                data.setExp(rs.getFloat("xp_exp"));
                data.setTotalExperience(rs.getInt("xp_total"));
                data.setSaturation(rs.getFloat("saturation"));
                data.setExhaustion(rs.getFloat("exhaustion"));
                String json = rs.getString("vanilla_stats_json");
                if (json != null && !json.isEmpty() && !"{}".equals(json)) {
                    try {
                        applyVanillaStats(data, GSON.fromJson(json, PlayerData.class));
                    } catch (Exception e) {
                        LOGGER.log(java.util.logging.Level.WARNING, "Failed to parse vanilla stats JSON for {0}: {1}", new Object[]{uuid, e.getMessage()});
                    }
                }
                return true;
            }
        }
    }

    private static void applyVanillaStats(PlayerData data, PlayerData temp) {
        if (temp == null) return;
        if (temp.getPotionEffects() != null) data.setPotionEffects(temp.getPotionEffects());
        if (temp.getDiscoveredRecipes() != null) data.setDiscoveredRecipes(temp.getDiscoveredRecipes());
        if (temp.getGameMode() != null) data.setGameMode(temp.getGameMode());
        data.setFlying(temp.isFlying());
        data.setAllowFlight(temp.isAllowFlight());
        if (temp.getWorld() != null) {
            data.setWorld(temp.getWorld());
            data.setX(temp.getX());
            data.setY(temp.getY());
            data.setZ(temp.getZ());
            data.setYaw(temp.getYaw());
            data.setPitch(temp.getPitch());
        }
        if (temp.getStatistics() != null) data.setStatistics(temp.getStatistics());
    }

    @SuppressWarnings({"unchecked", "null"})
    private boolean loadMetadataComponent(Connection connection, PlayerData data, UUID uuid) throws SQLException {
        String sqlMeta = "SELECT pdc_data, " + COL_ADVANCEMENTS + " FROM " + metadataTable + WHERE_UUID_SQL;
        try (PreparedStatement stmt = connection.prepareStatement(sqlMeta)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                String pdc = rs.getString("pdc_data");
                String advancementsStr = rs.getString(COL_ADVANCEMENTS);
                if (pdc != null) data.setPdcNBT(pdc);
                if (advancementsStr != null) {
                    try {
                        Map<String, List<String>> advMap = GSON.fromJson(advancementsStr, Map.class);
                        data.setAdvancements(advMap);
                    } catch (Exception e) {
                        LOGGER.log(java.util.logging.Level.WARNING, "Failed to parse advancements JSON for {0}: {1}", new Object[]{uuid, e.getMessage()});
                    }
                }
                return true;
            }
        }
    }

    private boolean loadCompanionComponent(Connection connection, com.digitalserverhost.plugins.MCDataBridge plugin, PlayerData data, UUID uuid) throws SQLException {
        String mode = MODE_FOLLOW;
        try {
            mode = plugin.getConfig().getString("companions.mode", MODE_FOLLOW).toLowerCase();
        } catch (Exception _) { /* default to follow if config is missing */ }
        if (mode.equals("untracked") || mode.equals("off")) return false;

        String sqlComp = "SELECT companions_nbt FROM " + companionsTable + WHERE_UUID_SQL;
        try (PreparedStatement stmt = connection.prepareStatement(sqlComp)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return false;
                String companionsNbt = rs.getString("companions_nbt");
                if (companionsNbt != null && !companionsNbt.isEmpty()) {
                    List<CompanionSnapshot> toSpawn = new ArrayList<>();
                    List<CompanionSnapshot> toKeep = new ArrayList<>();
                    
                    parseAndFilterCompanions(companionsNbt, mode, plugin.getServerId(), toSpawn, toKeep);

                    if (!toSpawn.isEmpty()) {
                        data.setCompanionsNBT(GSON.toJson(toSpawn));
                        // Do NOT update database companions on load to prevent sync-erase race conditions!
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private void parseAndFilterCompanions(String companionsNbt, String mode, String currentServer, List<CompanionSnapshot> toSpawn, List<CompanionSnapshot> toKeep) {
        try {
            CompanionSnapshot[] snapshots = GSON.fromJson(companionsNbt, CompanionSnapshot[].class);
            if (snapshots != null) {
                filterCompanionSnapshots(snapshots, mode, currentServer, toSpawn, toKeep);
            }
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Failed to parse companion snapshots during spawn check: {0}", e.getMessage());
            toSpawn.clear();
            toKeep.clear();
            CompanionSnapshot[] fallback = GSON.fromJson(companionsNbt, CompanionSnapshot[].class);
            if (fallback != null) {
                for (CompanionSnapshot snap : fallback) {
                    if (snap != null) {
                        toSpawn.add(snap);
                    }
                }
            }
        }
    }

    private void filterCompanionSnapshots(CompanionSnapshot[] snapshots, String mode, String currentServer, List<CompanionSnapshot> toSpawn, List<CompanionSnapshot> toKeep) {
        for (CompanionSnapshot snap : snapshots) {
            if (snap != null) {
                boolean shouldSpawn = shouldSpawnCompanion(snap, mode, currentServer);
                if (shouldSpawn) {
                    toSpawn.add(snap);
                } else {
                    toKeep.add(snap);
                }
            }
        }
    }

    private boolean shouldSpawnCompanion(CompanionSnapshot snap, String mode, String currentServer) {
        if (mode.equals(MODE_FOLLOW)) {
            return true;
        }
        if (mode.equals(MODE_RETURN)) {
            return snap.getSourceServerId() == null || snap.getSourceServerId().equalsIgnoreCase(currentServer);
        }
        return false;
    }

    private int executeMigrateQuery(Connection connection, String table, UUID oldUuid, UUID newUuid) throws SQLException {
        String sql = UPDATE_SQL_PREFIX + table + MIGRATE_UUID_SQL;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, newUuid.toString());
            stmt.setString(2, oldUuid.toString());
            return stmt.executeUpdate();
        }
    }

    public boolean migrateData(UUID oldUuid, UUID newUuid) throws SQLException {
        if (oldUuid == null || newUuid == null) return false;
        
        try (Connection connection = getConnection()) {
            // 1. Check if newUuid already has data in the main table (don't overwrite)
            String checkSql = "SELECT uuid FROM " + tableName + WHERE_UUID_SQL;
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, newUuid.toString());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        return false; // Target UUID already has data
                    }
                }
            }

            connection.setAutoCommit(false);
            try {
                boolean migrated = executeMigrateQuery(connection, tableName, oldUuid, newUuid) > 0;
                executeMigrateQuery(connection, inventoriesTable, oldUuid, newUuid);
                executeMigrateQuery(connection, statisticsTable, oldUuid, newUuid);
                executeMigrateQuery(connection, metadataTable, oldUuid, newUuid);
                executeMigrateQuery(connection, companionsTable, oldUuid, newUuid);

                connection.commit();
                return migrated;
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
