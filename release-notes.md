# Release Notes - MC Data Bridge `1.21.10.3`

This is a key performance and stability patch. This update refactors the player data saving process to be fully asynchronous, significantly reducing main-thread work and improving data integrity.

## ⚡ Performance Improvements

* **Fully Asynchronous Player Saving:** The entire player data save process, including NBT item serialization and JSON conversion, has been moved off the main server thread and onto an asynchronous task.
* **Reduced Main-Thread Work:** This change prevents the `PlayerQuitEvent` from performing heavy serialization operations. This solves potential server lag spikes that could occur during events with mass player logouts (e.g., during a server restart).

## ✅ Stability & Robustness

* **Database Startup Guard:** A new safety check has been added. The plugin will now safely disable itself on startup if it fails to create or update the required `player_data` tables, preventing it from running in a broken state.
* **Improved Deserialization Handling:** Refactored the data loading logic to use a custom `ItemDeserializationException`. This makes the existing error-handling (kicking a player with corrupt data) more robust and reliable.

## ⚙️ Internal Refactoring

* **Centralized Gson Instance:** Consolidated the `Gson` instance into a single static object within the main plugin class for better code management.
* **Code Cleanup:** Removed unused classes (`PluginManager.java`, `Utils.java`) to streamline the codebase.

## ⚠️ Important Notes for Upgrading

* There are **no configuration or database schema changes** in this update.
* This version is a drop-in replacement for `v1.21.10.2`.