# MC Data Bridge

MC Data Bridge is a robust, high-performance hybrid plugin for PaperMC (Spigot), **Folia**, BungeeCord, and Velocity. It is designed to seamlessly synchronize player data across multiple Minecraft servers, ensuring that players have a consistent experience by retaining their health, hunger, experience, inventory, **Ender Chests, Advancements, Statistics, Companions & Pets, and Persistent Data** as they move between linked servers.

## Compatibility

- **Minecraft Version:** `1.21.x` and `26.2.x`
- **Server Platforms:** PaperMC (Purpur), **Folia**, Spigot
- **Proxy Platforms:** BungeeCord, Waterfall, Velocity
- **Java Version:** 25+

This plugin is a hybrid build and the same JAR file works on all supported platforms, automatically activating the correct functionality for each platform.

## Features

- **Hybrid Plugin:** A single JAR file works on your PaperMC/Spigot/Folia servers and your BungeeCord/Velocity proxy.
- **Companion & Pet Synchronization:** Fully synchronizes tamed wolves, cats, parrots, and other pets with support for shoulder-perched states, ownership, names, health, and metadata. Offers configurable modes (`follow`, `return`, `untracked`) with robust state merging and duplication prevention.
- **Relational Component Schema Normalization:** Divides player data into normalized, targeted tables to optimize write performance and prevent write-amplification.
- **Binary NBT Serialization Option:** Choose between JSON and compressed binary NBT format (`database.serialization-format: binary`) for highly optimized CPU serialization and database storage.
- **Embedded Prometheus Metrics Exporter:** Provides an optional HTTP endpoint (`/metrics`) to monitor active transactions, cache efficiency, connection pool health, and sync latencies.
- **SHA-256 Data Integrity:** Implements cryptographic checksums with server-side salting to detect and prevent manual database tampering or corruption.
- **Industrial Identity Protection:** Tracks `last_known_name` and secure identity hashes to prevent identity hijacking and manage UUID transitions in hybrid (Cracked/Premium) environments.
- **Zero-Tick Identity Verification:** Database locks and identity checks are performed during the `AsyncPlayerPreLoginEvent`, ensuring that player state is verified and ready before they even reach the server's "Join" state.
- **Regionalized Folia Support:** Built with `SchedulerUtils` to handle regionalized threading, ensuring safe execution on Folia's multi-threaded clusters.
- **Interactive Inventory & Ender Chest Inspector (`invsee` / `endersee`):**
  - **Overview GUI Controls:** **Left-Click** inventory or ender chest icons to view in Safe Read-Only Mode (`databridge.inspect`); **Right-Click** icons to open in Interactive Edit Mode (`databridge.inspect.edit`).
  - **Safe View-Only Mode (Default):** View player inventory and ender chest snapshots in read-only mode (`databridge.inspect`).
  - **Interactive Edit Mode (`--edit` flag or Right-Click):** Admins with elevated permissions (`databridge.inspect.edit`) can interactively modify offline or cross-server player inventories with automatic database synchronization on GUI close.
- **Full Command Tab Completion:** Comprehensive auto-completion for subcommands, player names, view options, and flags.
- **Administrative Inspector GUI:** Visual interface for administrators to inspect saved player data (inventories, stats, PDC) even when the player is offline.
- **Proxy-Initiated Saves:** The proxy (BungeeCord/Velocity) orchestrates the data saving process, ensuring that a player's data is saved from their source server _before_ they connect to the destination server. This eliminates race conditions.
- **Fully Asynchronous:** All database and serialization operations are performed on a separate thread, ensuring that your server's main thread is never blocked.
- **Robust Locking Mechanism:** A database-level locking mechanism with an automatic timeout and heartbeats prevents data corruption.
- **Version-Independent Item Serialization:** Player inventories are serialized using native binary methods (Paper/Folia) or NBT-API fallback (Spigot), ensuring 100% integrity across updates.
- **Cross-Server Player Data Sync (Granular Control):**
  - Health (Attribute-aware)
  - Food Level, Saturation, & **Exhaustion**
  - Experience (Total XP, current XP, and Level)
  - Inventory & Armor Contents
  - Active Potion Effects
  - **Ender Chest Contents**
  - **Advancements & Recipes**
  - **Player Statistics** (Vanilla stats)
  - **Persistent Data Container (PDC)** (Custom metadata)
  - **Flight & GameMode** status
  - **Companions & Pets** (Tamed wolves, cats, parrots, etc.)
  - **Map Items & Canvas Sync** (Cross-server map stashing via `mode: return` or canvas sync via `global`)
  - **Separate Gamemode Inventories** (Opt-in separate profiles for Survival, Creative, Adventure, Spectator)
- **Resilient Connection Pooling:** Uses HikariCP with optimized settings for resilience against network jitter.
- **Flexible Storage:** Support for **MySQL/MariaDB** or local **SQLite** databases.
- **Granular Sync Control**: Enable or disable synchronization for any specific data type via `config.yml`.
- **Server/World Blacklist**: Prevent synchronization on specific servers or worlds.

## Installation

1.  **Download the Plugin JAR:**
    - Download the latest release JAR (`mc-data-bridge-*.jar`) from this page (Modrinth) or the official GitHub Releases.
2.  **Deploy to Servers:**
    - Copy the single `mc-data-bridge-*.jar` file into the `plugins/` folder of **each PaperMC/Folia server** you wish to synchronize.
    - Copy the **same JAR file** into the `plugins/` folder of your **BungeeCord or Velocity proxy server**.
3.  **Optional: Manual Build (For Developers)**
    - To build from source, clone the repository and run `mvn clean package`.

## Commands & Permissions

| Command                                                         | Description                                               | Permission                                                       |
| :-------------------------------------------------------------- | :-------------------------------------------------------- | :--------------------------------------------------------------- |
| `/databridge inspect <player> [inventory\|enderchest] [--edit]` | Opens GUI to view or edit saved player data/inventory.    | `databridge.inspect`<br>`databridge.inspect.edit` (for `--edit`) |
| `/databridge invsee <player> [--edit]`                          | Directly opens saved player inventory view or edit GUI.   | `databridge.inspect`<br>`databridge.inspect.edit` (for `--edit`) |
| `/databridge endersee <player> [--edit]`                        | Directly opens saved player ender chest view or edit GUI. | `databridge.inspect`<br>`databridge.inspect.edit` (for `--edit`) |
| `/databridge migrate <src> <dest>`                              | Securely move data between two identities.                | `databridge.admin`                                               |
| `/databridge unlock <player>`                                   | Manually release a stuck data lock.                       | `databridge.admin`                                               |
| `/databridge reload`                                            | Reloads configuration and reconnects to database pool.    | `databridge.admin`                                               |

**Proxy Commands:**

- `/databridge unlock <player>` (Bungee/Velocity): Relays a signal to the backend Spigot server to release the lock across the network.
- `/databridge forceunlock <player>` (Bungee/Velocity): Relays an immediate signal to the backend server to drop the lock.

**Permission Nodes:**

- `databridge.inspect` - Permission to view player data, inventories, and ender chests in safe read-only mode.
- `databridge.inspect.edit` - Elevated permission required to interactively edit inventories and ender chests via `--edit` or Right-Click.
- `databridge.admin` - Master administrative access (includes unlock, migrate, reload, forceunlock, inspect, edit).

## Configuration

A `config.yml` file will be generated in the `plugins/mc-data-bridge/` folder after the first run. **Existing configs will be safely auto-updated.**

```yaml
# Database Configuration
database:
  type: mysql # "mysql" or "sqlite"
  host: localhost
  port: 3306
  database: minecraft
  username: user
  password: password
  sqlite-file: "player_data.db"
  serialization-format: "json" # "json" or "binary"

  # A list of JDBC properties to apply.
  properties:
    useSSL: false
    allowPublicKeyRetrieval: true

  # HikariCP Connection Pool Settings
  pool-settings:
    maximum-pool-size: 10
    minimum-idle: 10
    max-lifetime: 1800000 # 30 minutes
    connection-timeout: 5000 # 5 seconds
    idle-timeout: 600000 # 10 minutes

# A unique name for this server. This is CRITICAL for data locking.
server-id: "default-server"

# Prefix for all database tables
table-prefix: ""

# Toggle specific data to sync
sync-data:
  health: true
  food-level: true
  experience: true
  inventory: true
  armor: true
  potion-effects: true
  ender-chest: true
  location: false
  advancements: true
  statistics: true
  pdc: true
  flight-gamemode: true
  companions: true
  maps: true
  separate-gamemode-inventories: false

# Map Synchronization Settings
maps:
  mode: "return" # "return", "global", or "untracked"

# Security & Identity
security:
  verify-data-integrity: true
  seed: "change-me-to-a-long-random-string" # SALT FOR CHECKSUMS

identity:
  mode: PREMIUM # PREMIUM or HYBRID
  auto-migrate-fastlogin: false
  auto-migrate-authme: false

# Companion/Pet Synchronization Settings
companions:
  scan-radius: 32
  mode: "follow" # "follow", "return", or "untracked"

# Prometheus Metrics Exporter
metrics:
  enabled: false
  port: 8080
  path: "/metrics"

# Server/World Blacklist
sync-blacklist:
  servers:
    - "example-blacklisted-server"
  worlds:
    - "example_world_nether"
```

### Detailed Configuration Breakdown

- **`database.type`**: Choose between `mysql` (external) or `sqlite` (local file).
- **`database.serialization-format`**: Select `binary` for compressed NBT data or `json` for backward-compatible Base64 NBT.
- **`server-id` (Required):** You **must** set a unique name for each backend server.
- **`security.seed`**: A secret string used to salt all SHA-256 hashes. Change this immediately!
- **`identity.mode`**: Choose between `PREMIUM` (strictly enforces UUID consistency) or `HYBRID` (allows flexible identity shifts). Note: **`HYBRID` mode is required** to enable auto-migration features like `auto-migrate-fastlogin`.
- **`sync-data`**: Individual toggles for every data type.
- **`companions`**: Set the detection radius and choose how pets behave when swapping servers.
- **`metrics`**: Configure the embedded Prometheus HTTP server port and route.
- **`sync-blacklist`**: Define servers or worlds where synchronization should be skipped.

## 📊 Technical: Database Schema

In addition to the primary tracking table (`player_data`), MC Data Bridge uses component tables to minimize database load.

### Core Locking & Integrity Table: `player_data`

| Column              | Type         | Description                                            |
| :------------------ | :----------- | :----------------------------------------------------- |
| `uuid`              | VARCHAR(36)  | The player's Unique ID (Primary Key).                  |
| `data`              | LONGBLOB     | The serialized binary data snapshot (legacy fallback). |
| `data_checksum`     | VARCHAR(64)  | SHA-256 integrity hash of the data packet.             |
| `is_locked`         | BOOLEAN      | Prevents concurrent writes from multiple servers.      |
| `locking_server`    | VARCHAR(255) | The ID of the server holding the lock.                 |
| `lock_timestamp`    | BIGINT       | Heartbeat to detect and recover from crashes.          |
| `last_known_name`   | VARCHAR(16)  | Used for identity tracking and migration.              |
| `identity_hash`     | VARCHAR(64)  | Salted hash of Name+UUID+Seed for verification.        |
| `name_last_updated` | BIGINT       | Timestamp of when the player's name was last updated.  |
| `last_updated`      | TIMESTAMP    | Auto-updated timestamp of the last lock state change.  |

### Inventories Component: `databridge_inventories`

- Holds `inventory_blob` (LONGBLOB/TEXT), `armor_blob` (LONGBLOB/TEXT), and `ender_chest_blob` (LONGBLOB/TEXT) snapshots.
- Includes a `last_updated` auto-timestamp.

### Statistics Component: `databridge_statistics`

- Holds basic stats (`health` DOUBLE/REAL, `food_level` INT/INTEGER, `xp_level` INT/INTEGER, `xp_exp` FLOAT/REAL, `xp_total` INT/INTEGER, `saturation` FLOAT/REAL, `exhaustion` FLOAT/REAL).
- Holds `vanilla_stats_json` (TEXT) containing dynamic statistics, locations, and recipe states.
- Includes a `last_updated` auto-timestamp.

### Metadata Component: `databridge_metadata`

- Holds custom PDC data (`pdc_data` LONGTEXT/TEXT) and player advancement progress (`advancements` LONGTEXT/TEXT).
- Includes a `last_updated` auto-timestamp.

### Companions Component: `databridge_companions` (NEW)

- Holds companion and pet metadata states (`companions_nbt` LONGTEXT/TEXT).
- Includes a `last_updated` auto-timestamp.

---

_For more detailed information, please refer to the [official documentation](https://github.com/westkevin12/mc-data-bridge/wiki)._
