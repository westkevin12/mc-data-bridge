# Release Notes - MC Data Bridge `1.21.8.3`

This is a key performance and stability patch. This update refactors both the player saving and loading processes to be more robust, performant, and secure.

## ⚡ Performance Improvements

* **Fully Asynchronous Player Saving:** The entire player data save process, including NBT item serialization and JSON conversion, has been moved off the main server thread and onto an asynchronous task.
* **Reduced Main-Thread Work (on Quit):** This change prevents the `PlayerQuitEvent` from performing heavy serialization operations. This solves potential server lag spikes that could occur during events with mass player logouts (e.g., during a server restart).

## ✅ Stability & Robustness

* **Player Data Pre-loading (Login Gate):** Player data is now loaded and validated *synchronously* during the `PlayerLoginEvent` (before the player enters the world). This is a critical data-integrity fix that prevents players from joining with empty data while their real data is still being loaded.
* **Robust Pre-Join Kicking:** Because data is loaded pre-join, players with locked data (from switching servers too fast) or corrupt data (deserialization errors) are now safely kicked *before* they ever enter the world.
* **Database Startup Guard:** A new safety check has been added. The plugin will now safely disable itself on startup if it fails to create or update the required `player_data` tables, preventing it from running in a broken state.
* **Improved Deserialization Handling:** Refactored the data loading logic to use a custom `ItemDeserializationException`. This makes the error-handling logic cleaner and more reliable.

## ⚙️ Internal Refactoring & Configuration

* **SSL & Custom JDBC Support:** Added support for high-security database connections, including those requiring **SSL**. The database connection no longer uses hardcoded URL parameters (like `useSSL=false`). You can now specify any custom JDBC properties under the new `database.properties` section in `config.yml` to meet your host's requirements.
* **Centralized Gson Instance:** Consolidated the `Gson` instance into a single static object within the main plugin class for better code management.
* **Code Cleanup:** Removed unused classes (`PluginManager.java`, `Utils.java`) to streamline the codebase.

## ⚠️ Important Notes for Upgrading

* **Configuration Change:** A new `database.properties` section has been added to `config.yml` to support custom JDBC settings. Your existing config file will be updated automatically with the new settings. No manual changes are required *unless* you need to customize your connection (e.g., to **enable SSL** for a secure database).
* This version is a drop-in replacement for `v1.21.8.2`.