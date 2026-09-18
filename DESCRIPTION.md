# MC Data Bridge

MC Data Bridge is a high-performance hybrid plugin for **PaperMC** (and forks like **Purpur**), **Folia**, **Spigot**, **Bukkit**, **BungeeCord** (and forks like **Waterfall**), and **Velocity**. It seamlessly synchronizes player data across linked Minecraft servers with enterprise-grade distributed locks, single-transaction atomic persistence, and instant event-driven messaging.

## Compatibility

- **Minecraft Version:** `1.21.x` and `26.3.x`
- **Server Platforms:** PaperMC, Purpur, Spigot, Bukkit, **Folia**
- **Proxy Platforms:** BungeeCord, Waterfall, Velocity
- **Java Version:** 25+

This plugin is a hybrid single-JAR build that operates across all supported server and proxy platforms.

---

## Quick Start

1. **Deploy Plugin**: Place the single `mc-data-bridge-*.jar` file into the `plugins/` folder of each Minecraft server (Paper/Folia/Spigot) and your proxy (Velocity/Bungee).
2. **Configure Backend**: Edit `plugins/mc-data-bridge/config.yml` on each backend server:
   - Set a unique `server-id` (e.g. `server-id: "survival-1"`).
   - Configure **MySQL** or **MariaDB** credentials (**Required** for multi-server networks. Need help setting up a database? See [**DATABASE_SETUP.md**](DATABASE_SETUP.md)).
   - Set `security.seed` to a secret random string.
3. **Restart Servers**: Restart your proxy and backend servers. Data synchronization runs automatically.

---

## Features

- **Comprehensive Cross-Server Data Synchronization**: Synchronizes health, food level, saturation, exhaustion, experience, inventory, armor, potion effects, ender chests, advancements, recipes, vanilla statistics, persistent data container (PDC) metadata, flight and gamemode status, companion pets, cross-server maps, and separate gamemode inventory profiles.
- **Item Duplication Exploit Protection**: Automatically closes open inventory views during server transfers and cancels container clicks, drags, item drops, pickups, and interactions while transfer locks are held.
- **Interactive Inventory & Ender Chest Inspector (`invsee` / `endersee`)**: Inspect offline or cross-server player inventories and ender chests in real time. Features safe view-only mode by default and interactive edit mode (`--edit` flag or Right-Click) for admins with automatic database persistence upon GUI close.
- **Hybrid Single-JAR Architecture**: A single JAR file operates on your PaperMC, Folia, Spigot, BungeeCord, and Velocity servers, automatically executing the appropriate platform handlers.
- **Identity Modes & Authentication Auto-Migration**: Toggle between strict `PREMIUM` UUID enforcement or flexible `HYBRID` identity shifts. Automatically migrates player data from offline to premium UUIDs upon verification by FastLogin (PreLogin) or AuthMe (Login, including AuthMe native TOTP/2FA).
- **Identity History & Security Tracking**: Tracks `last_known_name` and a secure `identity_hash` (Keyed HMAC-SHA256) to detect identity hijacking, UUID collisions, and manual database tampering.
- **Distributed Lock Fencing Tokens (`lock_version`)**: Employs monotonic generation counters to fence off stale server writes, ensuring delayed servers (e.g., post-GC pauses or network hiccups) can never overwrite active player sessions.
- **Event-Driven Lock Release Messaging**: Source servers dispatch an instant `LockReleased` signal upon saving player state, waking destination pre-login threads immediately and reducing server-switch lock wait latency to ~50ms (with automatic fallback to database polling).
- **Single-Transaction Atomic Persistence**: Combines normalized component updates (`inventories`, `statistics`, `metadata`, `companions`, `maps`) and lock releases into a single database connection and transaction (`setAutoCommit(false)` ... `commit()`).
- **Normalized Data Integrity Checksums**: Computes canonical SHA-256 integrity hashes across normalized player state components salted with `security.seed` to verify data integrity before loading.
- **Folia Multi-Threading Compatibility**: Uses regionalized schedulers (`GlobalRegionScheduler`, `EntityScheduler`) to guarantee thread safety across Folia's regionalized threading model.
- **Prometheus Metrics Exporter**: Built-in HTTP server exposing real-time synchronization performance metrics (sync latency, cache hit ratios, HikariCP pool status) for Grafana dashboards.
- **Granular Sync & Blacklist Control**: Enable or disable synchronization for any specific data type or exclude specific servers and worlds via `config.yml`.

---

## Commands & Permissions

| Command | Description | Required Permission |
| :--- | :--- | :--- |
| `/databridge inspect <player> [inv\|ender] [--edit]` | Open GUI to view or edit saved player data | `databridge.inspect` (`.edit` for `--edit`) |
| `/databridge invsee <player> [--edit]` | Directly open player inventory view or edit GUI | `databridge.inspect` (`.edit` for `--edit`) |
| `/databridge endersee <player> [--edit]` | Directly open player ender chest view or edit GUI | `databridge.inspect` (`.edit` for `--edit`) |
| `/databridge unlock <player>` | Release a stuck data lock (Works on Spigot, Folia, Bungee, Velocity) | `databridge.admin` |
| `/databridge forceunlock <player>` | Proxy command: force immediate lock drop signal | `databridge.admin` |
| `/databridge migrate <source> <target>` | Move player data between UUIDs or names | `databridge.admin` |
| `/databridge reload` | Reload configuration and reconnect DB pool | `databridge.admin` |

**Permission Nodes:**
- `databridge.inspect`: Permission to view player data, inventories, and ender chests in safe read-only mode.
- `databridge.inspect.edit`: Elevated permission required to interactively edit inventories and ender chests via `--edit` or Right-Click.
- `databridge.admin`: Full administrative access for all commands and functions.

---

## Documentation References

For full technical diagrams, relational database schemas, and SpigotMC wiki pages:
- [**DATABASE_SETUP.md**](DATABASE_SETUP.md) — MySQL & MariaDB Installation Guide (Docker, Linux Host, Firewall & Security Best Practices).
- [**ARCHITECTURE.md**](ARCHITECTURE.md) — Architectural Deep Dive, Complete Sequence Diagrams, and Database Schemas.
- [**DOCUMENTATION.bbcode**](DOCUMENTATION.bbcode) — BBCode Wiki Documentation.
