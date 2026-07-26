# MC Data Bridge - Release Notes (v2.1.8)

## Overview

Version 2.1.8 introduces **Item Duplication Exploit Protection**, **Full Tab Completion**, **Interactive Inventory & Ender Chest Inspection (`invsee` & `endersee`)** with read-only and edit modes, **Granular Permissions**, **Database Schema Auto-Migration**, and **Paper 26.2 Platform Parity**.

---

## Key Changes

### 🛡 Item Duplication Exploit Protection (#30)

- **Special Thanks:** Special thanks to **@AllenLinong** for discovering and reporting this critical exploit ([#30](https://github.com/westkevin12/mc-data-bridge/issues/30))!
- **Safe Inventory Closure:** Open inventory views are automatically closed before player data snapshots are captured during server switches.
- **Transfer Lock Enforcement:** Cancels item drops, item pickups, container clicks, inventory drags, and block/entity interactions while a player transfer or database save is in progress.

### 🔍 Interactive Inventory & Ender Chest Inspector (`invsee` / `endersee`)

- **New Subcommands & Aliases:**
  - `/databridge inspect <player> [inventory|enderchest] [--edit]`
  - `/databridge invsee <player> [--edit]`
  - `/databridge endersee <player> [--edit]`
- **Safe View-Only Mode (Default):** Inspection GUIs open in read-only mode by default (**Left-Click** in Overview GUI) to prevent accidental item modifications or unintended edits.
- **Interactive Edit Mode (`--edit` flag or Right-Click):** Admins with `databridge.inspect.edit` permission can interactively edit items (**Right-Click** in Overview GUI or using `--edit` flag) in offline/cross-server player inventories or ender chests directly inside the GUI.
- **Database Auto-Save:** Closing an editable GUI (`InventoryCloseEvent`) automatically serializes modified item stacks back to the database so changes apply when the player logs in.

### 🔑 Granular Permission Nodes

- `databridge.inspect` (or `databridge.admin`): Permission to view player data, inventories, and ender chests in safe read-only mode.
- `databridge.inspect.edit` (or `databridge.admin`): Elevated permission required to open interactive edit mode using the `--edit` flag.
- `databridge.admin`: Full administrative access to all commands and subcommands.

### ⌨️ Full Command Tab Completion

- **TabExecutor / Brigadier Integration:** Added dynamic auto-completion for subcommands (`unlock`, `inspect`, `invsee`, `endersee`, `migrate`), online player names, view types (`inventory`, `enderchest`), and the `--edit` flag.

### 🗄 Database Schema Maintenance & Upgrades

- **Auto-Update Schema:** Added `auto-update-schema` configuration toggle (default `true`) to automatically manage table upgrades.
- **Column Expansion:** Upgraded `vanilla_stats_json` column to `LONGTEXT` (MySQL/MariaDB) / `TEXT` (SQLite) to accommodate large recipe books, advancements, and vanilla statistics.

### ⚡ Paper 26.2 & Dependency Upgrades

- **Updated Target API:** Compiled and tested against Paper API `26.2` and verified on Paper 26.2+ servers.
- **HikariCP:** Upgraded connection pool library to `7.1.0`.
- **Platform Reflection:** Retains full backward compatibility for pre-26.2 Spigot/Paper versions via dynamic reflection.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
