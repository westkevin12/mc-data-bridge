# Release Notes - MC Data Bridge `2.0.1`

This is a landmark update, representing a complete architectural overhaul of MC Data Bridge to introduce robust, high-performance, cross-server data synchronization for BungeeCord and Velocity networks.

## 🚀 Major New Features

*   **Cross-Server Synchronization:** The plugin now fully supports synchronizing player data (inventory, health, XP, potion effects, etc.) across multiple servers connected via a BungeeCord or Velocity proxy.
*   **Database Locking System:** A sophisticated, atomic locking mechanism has been implemented to prevent all race conditions and data corruption.
    *   When a player joins a server, the plugin acquires a lock on their data in the database.
    *   This lock is continuously renewed via a "heartbeat" task while the player is online.
    *   If a player switches servers, the new server waits for the old server to save and release the lock.
    *   A configurable `lock-timeout` ensures that if a server crashes, its locks will eventually expire and can be claimed by another server, preventing players from being permanently locked out.
*   **Proxy Integration:** The plugin now listens for a custom `mc-data-bridge:main` plugin message from the proxy. This message tells the server to immediately save a player's data when they are switching servers, ensuring data is persisted before they connect to the next server.
*   **Startup Self-Healing:** On startup, the plugin will now find and release any "orphaned" locks that were held by that same server before a crash or shutdown, ensuring a clean state.

## ⚙️ Configuration & Setup

*   **`server-id` (Critical):** A new, **mandatory** `server-id` has been added to `config.yml`. Each server connected to the same database **must** have a unique ID for the locking system to function correctly.
*   **`lock-timeout`:** A new setting to control how long a lock is considered valid before it can be forcefully acquired by another server. The default is 60 seconds.

## 📞 Compatibility

*   **Minecraft Version:** This version is built for and compatible with Minecraft `1.21.x`.
*   **Platform Support:** The plugin now requires the **Paper API** (for Spigot servers) and includes support for **BungeeCord API** and **Velocity API**.

## ✅ Stability & Performance

*   **New Serialization Format:** Item data is now serialized to **Base64**, which is significantly more robust and reliable than the previous NBT-based string method.
*   **Backward Compatibility:** The new version can still read the old NBT-based item format, ensuring a smooth upgrade path for existing users. Old data will be converted to the new format the next time it is saved.
*   **Optimized Database Logic:** All database interactions have been rewritten to be more efficient and atomic, using HikariCP for best-in-class connection pooling.

## ⚠️ Important Notes for Upgrading

*   **This is a major, breaking update.** It is a drop-in replacement, but its core logic is entirely new.
*   **Proxy Installation Required:** For the new cross-server synchronization to work, you **must** install the same `mc-data-bridge-*.jar` file into the `plugins/` folder of your BungeeCord or Velocity proxy server.
*   **Configuration is Critical:** You **must** define a unique `server-id` in your `config.yml` on every single one of your Spigot/Paper servers. Failure to do so will lead to data corruption in a multi-server environment.
