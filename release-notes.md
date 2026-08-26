# MC Data Bridge - Release Notes (v2.1.9)

## Overview

Version 2.1.9 delivers **Proxy Subcommand Auto-Forwarding** across proxy platforms (Velocity, BungeeCord, and Waterfall) and **Real-Time Online Player Live Inventory Sync & Active Edit Lock Protection**.

---

## Key Changes

### 🔄 Proxy Subcommand Auto-Forwarding (#34)

- **Proxy-to-Backend Command Routing:** Non-`unlock` subcommands (e.g. `/databridge invsee`, `endersee`, `inspect`, `reload`, `sync`, `migrate`) executed by players on proxy servers (**Velocity**, **BungeeCord**, and **Waterfall**) are now automatically routed downstream to their connected backend Paper/Spigot server without requiring explicit namespace prefixes (`/mc-data-bridge:databridge`).
- **Namespace Interception Fix:** Fixes command hijack and usage rejection issues when running shorthand `/databridge` subcommands across proxy setups.

### ⚡ Online Player Live Inventory Sync & Active Edit Lock (#31, #32)

- **Real-Time In-Memory Update:** Edits made to an online player's inventory or ender chest via `/databridge invsee --edit` or `/databridge endersee --edit` now immediately update the target player's live, in-memory inventory on their active server instance.
- **Cross-Server Live Sync:** Dispatches a `LiveInventorySync` message over the `mc-data-bridge:main` plugin channel when editing online players on other servers across the proxy network, forcing an instant live memory reload without requiring player relog.
- **Pre-Fetch Memory Snapshot:** Opening an inspection GUI for a locally online player automatically snapshots their live memory state first, preventing recent item pickups or drops from being overwritten by older database data.
- **Active Edit Lock Protection:** Protects online target players from inventory desync or duplicate item exploits while an admin is actively modifying their inventory in an edit GUI.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
