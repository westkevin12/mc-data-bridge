# Release Notes - MC Data Bridge v2.0.3

## 🆕 New Features
*   **Granular Data Synchronization**: You can now toggle exactly what data is synced in `config.yml`. Added `sync-data` section.
*   **Ender Chest Sync**: Added support for synchronizing Ender Chest contents! (Disabled by default, enable in config).
*   **Advancements Integration**: Added support for standard Advancements and Recipes! (Disabled by default).
*   **Admin Unlock Command**: Added `/databridge unlock <player>` to manually release a stuck lock.
*   **Blacklist**: Added `sync-blacklist` to disable synchronization on specific servers or worlds.

## 🛠 Improvements & Optimizations
*   **Configurable Heartbeat**: Added `lock-heartbeat-seconds` to control how often the data lock is refreshed.
*   **Better Messages**: Updated message handling to use Adventure API for full modern chat component support.
*   **Database Check**: Added a startup check to warn if your database schema for `data` is `LONGTEXT` instead of the recommended `MEDIUMBLOB`.

## ⚠️ Important Notes
*   **Configuration Update**: The plugin now features a **Smart Auto-Updater**. On startup, it will detect any missing configuration keys (like the new `sync-data` section) and safely append them to your existing `config.yml`. **You do NOT need to reset your config.**
*   **Schema Optimization**: The plugin now supports `LONGBLOB` for the `data` column, which is more efficient than `LONGTEXT`.
    *   **Automatic Migration**: A new check will run on startup. If you are using the old `LONGTEXT` format, the plugin will automatically attempt to migrate your table to `LONGBLOB` if the config option `auto-update-schema` is set to `true` (which is the default for updated configs).
    *   **Manual**: If you prefer, you can manually run `ALTER TABLE player_data MODIFY COLUMN data LONGBLOB NULL;`.
