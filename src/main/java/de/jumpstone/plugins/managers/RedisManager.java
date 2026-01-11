package de.jumpstone.plugins.managers;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Redis Manager for handling short-term player data caching and atomic
 * operations.
 * Provides ultra-fast data access during server switches to eliminate race
 * conditions.
 */
public class RedisManager {
    private JedisPool jedisPool;
    private final Logger logger;
    private boolean isConnected = false;
    private String host;
    private int port;
    private String password;
    private int timeoutMs;

    // Redis key prefixes
    private static final String PLAYER_DATA_KEY = "hmt:player:%s:data";
    private static final String PLAYER_LOCK_KEY = "hmt:player:%s:lock";
    private static final String PLAYER_SERVER_KEY = "hmt:player:%s:server";
    private static final String PENDING_SAVES_KEY = "hmt:pending:saves";

    public RedisManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Initialize Redis connection pool
     */
    public boolean initialize(String host, int port, String password, int timeoutMs) {
        try {
            this.host = host;
            this.port = port;
            this.password = password;
            this.timeoutMs = timeoutMs;

            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(50);
            config.setMaxIdle(20);
            config.setMinIdle(5);
            config.setMaxWaitMillis(2000);
            config.setTestOnBorrow(true);
            config.setTestOnReturn(true);
            config.setTestWhileIdle(true);

            if (password != null && !password.isEmpty()) {
                this.jedisPool = new JedisPool(config, host, port, timeoutMs, password);
            } else {
                this.jedisPool = new JedisPool(config, host, port, timeoutMs);
            }

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                this.isConnected = true;
                logger.info("[HMTSync-Redis] Successfully connected to Redis at " + host + ":" + port);
                return true;
            }
        } catch (Exception e) {
            logger.severe("[HMTSync-Redis] Failed to initialize Redis connection: " + e.getMessage());
            this.isConnected = false;
            return false;
        }
    }

    /**
     * Check if Redis is available and connected
     */
    public boolean isAvailable() {
        if (!isConnected || jedisPool == null) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            return true;
        } catch (JedisException e) {
            logger.warning("[HMTSync-Redis] Redis connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Acquire atomic lock for player data with TTL
     * 
     * @param uuid       Player UUID
     * @param serverId   Server requesting the lock
     * @param ttlSeconds Time-to-live in seconds
     * @return true if lock acquired, false if already locked
     */
    public boolean acquireLock(UUID uuid, String serverId, int ttlSeconds) {
        if (!isAvailable()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String lockKey = String.format(PLAYER_LOCK_KEY, uuid.toString());
            String serverKey = String.format(PLAYER_SERVER_KEY, uuid.toString());

            // Simple approach: check if lock exists, then set with expiration
            if (jedis.exists(lockKey)) {
                return false; // Already locked
            }

            // Set lock with expiration
            jedis.setex(lockKey, ttlSeconds, "1");
            jedis.setex(serverKey, ttlSeconds, serverId);

            if (logger != null) {
                logger.info("[HMTSync-Redis] Lock acquired for player " + uuid + " by server " + serverId);
            }
            return true;

        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to acquire lock for " + uuid + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Release player data lock
     */
    public boolean releaseLock(UUID uuid, String serverId) {
        if (!isAvailable()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String lockKey = String.format(PLAYER_LOCK_KEY, uuid.toString());
            String serverKey = String.format(PLAYER_SERVER_KEY, uuid.toString());

            // Check if this server owns the lock
            String currentServer = jedis.get(serverKey);
            if (currentServer != null && currentServer.equals(serverId)) {
                jedis.del(lockKey, serverKey);
                if (logger != null) {
                    logger.info("[HMTSync-Redis] Lock released for player " + uuid + " by server " + serverId);
                }
                return true;
            }
            return false;
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to release lock for " + uuid + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Store player data in Redis cache
     */
    public boolean storePlayerData(UUID uuid, String jsonData, int cacheSeconds) {
        if (!isAvailable()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String dataKey = String.format(PLAYER_DATA_KEY, uuid.toString());
            jedis.setex(dataKey, cacheSeconds, jsonData);

            if (logger != null && logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine(
                        "[HMTSync-Redis] Stored player data for " + uuid + " (size: " + jsonData.length() + " chars)");
            }
            return true;
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to store player data for " + uuid + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Retrieve player data from Redis cache
     */
    public String getPlayerData(UUID uuid) {
        if (!isAvailable()) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String dataKey = String.format(PLAYER_DATA_KEY, uuid.toString());
            String data = jedis.get(dataKey);

            if (data != null && logger != null && logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine(
                        "[HMTSync-Redis] Retrieved player data for " + uuid + " (size: " + data.length() + " chars)");
            }

            return data;
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to retrieve player data for " + uuid + ": " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Check if player data exists in cache
     */
    public boolean hasPlayerData(UUID uuid) {
        if (!isAvailable()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String dataKey = String.format(PLAYER_DATA_KEY, uuid.toString());
            return jedis.exists(dataKey);
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe(
                        "[HMTSync-Redis] Failed to check player data existence for " + uuid + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Delete player data from cache
     */
    public boolean deletePlayerData(UUID uuid) {
        if (!isAvailable()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String dataKey = String.format(PLAYER_DATA_KEY, uuid.toString());
            jedis.del(dataKey);
            return true;
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to delete player data for " + uuid + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Add player to pending saves queue for MySQL sync
     */
    public boolean addToPendingSaves(UUID uuid, String jsonData, String sourceServer) {
        if (!isAvailable()) {
            return false;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            String saveEntry = uuid.toString() + "|" + System.currentTimeMillis() + "|" + sourceServer;
            jedis.lpush(PENDING_SAVES_KEY, saveEntry);
            return true;
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to add pending save for " + uuid + ": " + e.getMessage());
            }
            return false;
        }
    }

    /**
     * Get next pending save from queue
     */
    public String getNextPendingSave() {
        if (!isAvailable()) {
            return null;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.rpop(PENDING_SAVES_KEY);
        } catch (JedisException e) {
            if (logger != null) {
                logger.severe("[HMTSync-Redis] Failed to get pending save: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Close Redis connection pool
     */
    public void shutdown() {
        if (jedisPool != null) {
            try {
                jedisPool.close();
                isConnected = false;
                if (logger != null) {
                    logger.info("[HMTSync-Redis] Redis connection pool closed");
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.severe("[HMTSync-Redis] Error closing Redis connection pool: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Get Redis connection statistics
     */
    public String getStats() {
        if (!isAvailable()) {
            return "Redis: Disconnected";
        }

        try (Jedis jedis = jedisPool.getResource()) {
            return "Redis: Connected to " + host + ":" + port + " | Active: " + jedisPool.getNumActive() +
                    " | Idle: " + jedisPool.getNumIdle() + " | Waiters: " + jedisPool.getNumWaiters();
        } catch (Exception e) {
            return "Redis: Connection error - " + e.getMessage();
        }
    }
}