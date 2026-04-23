# MC Data Bridge - Release Notes (v2.1.4-RC1)

## Overview
Version 2.1.4 represents a major modernization and feature expansion of the MC Data Bridge plugin. This release focuses on full platform parity, modernization of internal APIs to support the latest server versions (Paper 1.21+, Folia, Velocity 3.3+), and enhanced administrative tools for production environments.

## New Features & Enhancements

### 🛡️ Administrative Inspector GUI
*   Added `/databridge inspect <player>`: A visual GUI for administrators to inspect saved player data.
*   Displays real-time stats including health, food, experience, inventory size, and last known location.
*   View Persistent Data Container (PDC) status and metadata at a glance.

### 🌐 Cross-Platform Parity
*   **Proxy Unlock Command:** The `/databridge unlock` command is now available on both **BungeeCord** and **Velocity**.
*   **ForceUnlock Relay:** Proxy commands automatically relay "ForceUnlock" signals to backend Spigot/Paper servers via dedicated messaging channels, ensuring locks can be cleared from anywhere in the network.

### 🚀 Folia & Modern API Support
*   **Folia Compatibility:** Implemented `SchedulerUtils` to handle regionalized threading requirements, ensuring safe execution on Folia clusters.
*   **Adventure API Migration:** Replaced all legacy chat and UI logic with the Adventure API for high-fidelity text and cross-platform consistency.
*   **NBT API Modernization:** Fully migrated to the latest NBT API static utility methods, improving performance and future-proofing data serialization.

### 💾 Data Integrity & Backups
*   **SQLite Support:** Added support for local SQLite storage as an alternative to MySQL/MariaDB.
*   **Local Redundancy System:** Optional JSON-based redundancy system for local data exports (disabled by default).
*   **Professional Backup Guidance:** Updated `config.yml` with comprehensive instructions for implementing "True Offsite Backups" using professional tools like `mysqldump` and `rclone`.

### 🔧 Bug Fixes & Refinements
*   **Modern Player Resolution:** Switched to the Paper `PlayerProfile` API for safer, non-blocking offline player lookups.
*   **Null-Safety Audit:** Resolved all persistent IDE warnings and type-safety issues across the entire codebase.
*   **Teleport-on-Join:** Refined location restoration logic to ensure players are accurately placed when returning to a server.

## Technical Details
*   **Minimum Java Version:** 25
*   **Supported Platforms:** Paper (1.21.x and 26.1.x), Folia, BungeeCord/Waterfall, Velocity.
*   **Dependencies Updated:** NBTAPI (2.13.x+), HikariCP, Adventure.
*   **CI/CD Modernization:** Migrated to Node.js 24 and updated GitHub Actions to latest v4 versions for improved build reliability and long-term support.

---
*For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml).*
