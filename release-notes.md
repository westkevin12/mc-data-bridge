# MC Data Bridge - Release Notes (v2.1.4)

## Overview

Version 2.1.4 represents a major modernization and feature expansion of the MC Data Bridge plugin. This release focuses on full platform parity, a consolidated command architecture, and enhanced security tools for production environments. All administrative functions have been unified under the `/databridge` command hub, simplifying network management.

## New Features & Enhancements

### 🛡️ Administrative Inspector GUI

- Added `/databridge inspect <player>`: A visual GUI for administrators to inspect saved player data.
- Displays real-time stats including health, food, experience, inventory size, and last known location.
- View Persistent Data Container (PDC) status and metadata at a glance.

### 🛡️ Advanced Security & Integrity

- **SHA-256 Checksums:** Implemented data integrity verification to detect and prevent manual database tampering or corruption.
- **Identity History Tracking:** Automatically tracks `last_known_name` and a secure `identity_hash` (SHA-256) to enable secure UUID change detection and prevent identity hijacking in hybrid (Cracked/Premium) environments.
- **Identity Modes (PREMIUM/HYBRID):** Introduced selectable identity modes. `PREMIUM` mode strictly enforces UUID consistency and blocks data load on collisions, while `HYBRID` mode allows for flexible identity shifts common in mixed networks.
- **FastLogin & AuthMe Auto-Migration:** Seamlessly handles Cracked -> Premium account upgrades. Supports both **FastLogin** (pre-login verification) and **AuthMe** (post-login verification), including support for **TOTP/2FA** flows.
- **Server-Side Salting:** Introduced `security.seed` to salt all identity hashes and data checksums, providing protection against rainbow table attacks and pre-computation. Includes automatic legacy migration for existing data.
- **Automatic Lock Recovery:** The plugin now automatically identifies and releases orphaned locks held by the local server on startup, preventing stuck sessions after a crash.
- **New Migration System:** Added `/databridge migrate <source> <target>` to securely move data between player identities (UUID or Name).
- **Proxy Parity:** The `/databridge unlock` command is now available on **BungeeCord**, **Waterfall**, and **Velocity**.
- **ForceUnlock Relay:** Proxy commands automatically relay "ForceUnlock" signals to backend Spigot/Paper/Folia servers.

### 🚀 Folia & Modern API Support

- **Folia Compatibility:** Implemented `SchedulerUtils` to handle regionalized threading requirements, ensuring safe execution on Folia clusters.
- **Adventure API Migration:** Replaced all legacy chat and UI logic with the Adventure API for high-fidelity text and cross-platform consistency.
- **NBT API Modernization:** Fully migrated to the latest NBT API static utility methods, improving performance and future-proofing data serialization.

### 💾 Data Integrity & Backups

- **SQLite Support:** Added support for local SQLite storage as an alternative to MySQL/MariaDB.
- **Local Redundancy System:** Optional JSON-based redundancy system for local data exports (disabled by default).
- **Professional Backup Guidance:** Updated `config.yml` with comprehensive instructions for implementing "True Offsite Backups" using professional tools like `mysqldump` and `rclone`.

### 🔧 Refinements & Logic Improvements

- **Modern Player Resolution:** Switched to the Paper `PlayerProfile` API for safer, non-blocking offline player lookups.
- **Delayed Flight Application:** Implemented a 5-tick override for flight status on join, ensuring compatibility with plugins like EssentialsX that may reset flight.
- **Auto-Update Schema:** Added `auto-update-schema` configuration to allow the plugin to automatically migrate database columns (e.g., from TEXT to LONGBLOB).
- **Exhaustion Sync:** Expanded food synchronization to include exhaustion levels for 100% hunger parity.
- **Null-Safety Audit:** Resolved all persistent IDE warnings and type-safety issues across the entire codebase.
- **Teleport-on-Join:** Refined location restoration logic to ensure players are accurately placed when returning to a server.
- **Standalone Sync Parity:** Improved data flush logic during server shutdown, ensuring data integrity even for servers operating without a proxy coordinator.

## Technical Details

- **Minimum Java Version:** 25
- **Supported Platforms:** Paper (1.21.x and 26.1.x), Purpur, Folia, Spigot/Bukkit, BungeeCord/Waterfall, Velocity.
- **Dependencies Updated:** NBTAPI (2.13.x+), HikariCP, Adventure.
- **CI/CD Modernization:** Migrated to Node.js 24 and updated GitHub Actions to latest v4 versions for improved build reliability and long-term support.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
