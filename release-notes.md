# MC Data Bridge - Release Notes (v2.2.0)

## Overview

Version 2.2.0 introduces **Map Sync Between Servers** and **Separate Gamemode Inventories**, allowing players and staff members to maintain isolated inventory profiles per gamemode and stash maps per-server across proxy networks without data conflicts, alongside core dependency updates.

---

## Key Changes

### 🗺️ Map Sync Between Servers (#36)

- **Server-Isolated Map Stashing (`mode: return`):** Filled map items created on Server A are tagged with origin metadata (`databridge:origin_server` and `databridge:original_map_id`). When transferring to Server B, foreign maps are safely stashed in the database (`databridge_maps`) and automatically restored to their inventory slots when returning to Server A.
- **Configurable Map Synchronization Modes:** Added a new `maps:` config section supporting three modes:
  - `return` (default): Stashes and restores server-specific map items per server.
  - `global`: Synchronizes map canvas pixel data across all network servers.
  - `untracked`: Legacy vanilla map handling.
- **Component Database Storage:** Added `{table-prefix}databridge_maps` table in MySQL and SQLite for atomic map persistence and cross-server UUID data migration.

### 🎒 Separate Gamemode Inventories (#33)

- **Opt-In Gamemode Inventory Separation (`sync-data.separate-gamemode-inventories`):** Staff members and players can maintain separate inventory, armor, and Ender Chest profiles for Survival, Creative, Adventure, and Spectator modes.
- **Live Gamemode Swapping Hook:** Automatically snapshots and restores gamemode-specific inventory profiles when switching gamemodes on the fly (`PlayerGameModeChangeEvent`).
- **Isolated Component Table:** Stores gamemode inventory profiles in `{table-prefix}databridge_gamemode_inventories` using `(uuid, gamemode)` composite key.

### 📦 Dependency Updates

- **MySQL Connector/J:** Updated `com.mysql:mysql-connector-j` from `9.7.0` to `26.7.0`.
- **Item NBT API:** Updated `de.tr7zw:item-nbt-api` from `2.15.7` to `2.16.0`.
- **JUnit Jupiter:** Updated `org.junit.jupiter:junit-jupiter` from `6.1.0` to `6.1.3`.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
