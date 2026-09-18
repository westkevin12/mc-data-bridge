# MC Data Bridge - Release Notes (v2.2.3)

## Overview

Version 2.2.3 adds Minecraft 26.3 compatibility, updating core dependencies and Paper API target versions for seamless cross-server item stack synchronization on Minecraft 26.3+ servers.

---

## Key Changes

### 📦 Minecraft 26.3 Compatibility
- **Dependency Upgrades:** Upgraded `de.tr7zw:item-nbt-api` to `2.16.1` (including PR #360 item stack mirror resolution), `io.papermc.paper:paper-api` to `26.3-pre-2.build.0-alpha`, and `net.kyori` Adventure libraries to `5.2.0`.
- **Full 26.3 Item & Component Support:** Tested and verified full cross-server item synchronization for Minecraft 26.3 (including new Poplar wood items, cushions, beds, and wool/concrete stairs and slabs).

---

_For installation instructions and configuration details, please refer to [README.md](README.md), [ARCHITECTURE.md](ARCHITECTURE.md), and [DATABASE_SETUP.md](DATABASE_SETUP.md)._
