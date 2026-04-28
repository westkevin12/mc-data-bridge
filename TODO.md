# MC Data Bridge - Roadmap & TODOs

### 1. Synchronization Data Types (COMPLETED)

- [x] **Player Statistics:** Vanilla statistics are now synchronized across servers.
- [x] **Persistent Data Container (PDC):** Custom metadata is serialized via NBT API, allowing other plugins' data to persist cross-server.
- [x] **Flight and GameMode State:** Players maintain their flight status and GameMode when switching servers.

### 2. Operational Enhancements (COMPLETED)

- [x] **Non-MySQL Database Support:** Added support for local **SQLite** storage.
- [x] **Local Redundancy System:** Added an optional JSON export system for local backups.
- [x] **True Backup Documentation:** Added professional guidance for offsite SQL backups in the configuration.
- [x] **In-Game Management GUI:** Admins can now use `/databridge inspect <player>` to view data in a visual interface.

### 3. Feature Finalization (COMPLETED)

- [x] **Location Restoration:** Implemented optional "Teleport on Join" functionality in the configuration.

### 4. Cross-Platform & Compatibility (COMPLETED)

- [x] **Command Parity:** Added `/databridge unlock` to **BungeeCord** and **Velocity** proxy versions.
- [x] **Folia Support:** Implemented a compatibility layer for regionalized threading, ensuring safety on high-performance multi-threaded servers.

### 5. Security & Identity (COMPLETED - v2.1.4-RC3)

- [x] **Data Integrity Verification:** SHA-256 checksums prevent manual database tampering.
- [x] **Identity History Tracking:** Tracking `last_known_name` and SHA-256 `identity_hash` enables automatic UUID change detection and secure hybrid support.
- [x] **Manual Migration System:** Admins can securely link different UUIDs via `/databridge migrate`.
- [x] **Standalone Sync Parity:** Improved data flush logic for servers running without proxies.

---

### 6. Future Roadmap

- [ ] **External Auth Addons:** Modular support for linking accounts via FastLogin, AuthMe, or TOTP.
- [ ] **Global Locking Service:** Dedicated micro-service for ultra-fast locking in massive networks.
- [ ] **Additional Database Drivers:** PostgreSQL and MongoDB support.

