# MC Data Bridge - Release Notes (v2.1.7)

## Overview

Version 2.1.7 is a targeted bugfix release that upgrades the `vanilla_stats_json` column type in the statistics component table to `LONGTEXT` (for MySQL/MariaDB), preventing potential data truncation issues when synchronizing large volumes of vanilla player statistics. It also introduces automatic schema migration for existing databases.

---

## Changes in v2.1.7

### 🗄️ Database Schema Upgrade & Migration

- **Upgrade to LONGTEXT:** Upgraded the `vanilla_stats_json` column from `TEXT` to `LONGTEXT` in the `databridge_statistics` table to support larger JSON payloads without risk of truncation.
- **Automated Migration:** Added support for automatically executing the `ALTER TABLE ... MODIFY COLUMN ...` statement when `auto-update-schema` is set to `true` in the configuration.
- **Admin Warning Notice:** If `auto-update-schema` is disabled, the plugin will now log a prominent warning suggesting the manual execution of the schema migration command:
  ```sql
  ALTER TABLE `table_prefix_databridge_statistics` MODIFY COLUMN vanilla_stats_json LONGTEXT DEFAULT NULL;
  ```

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
