# MC Data Bridge - Release Notes (v2.2.1)

## Overview

Version 2.2.1 is a release streamlining map synchronization architecture, introducing configurable map locking enforcement, fixing map canvas color palette rendering issues, and adding automatic config migration from v2.2.0.

---

## Key Changes

### 🗺️ Map Sync Architecture Simplification (#36, #39)

> [!NOTE]
> **Developer Note:** _"I couldn't think of a single valid use case for return mode with held map items. It seemed like a good idea at first, but having a broken, unusable map item on another server would likely never be the server admin's intention."_

- **Streamlined Map Synchronization Model:** Removed legacy `maps.mode` (`return`/`untracked` per-server stashing). Map synchronization is now toggled cleanly via `sync-data.maps: false` (default, vanilla style handling) or `sync-data.maps: true` (global map sync).
- **Configurable Map Locking (`maps.lock-global-maps`):** Added `maps.lock-global-maps: false` (default). When set to `true`, enforces map locking (`locked = 1`) on cross-server maps to prevent target servers from re-scanning terrain or applying Fog of War over custom artwork.
- **Automatic Legacy Config Migration:** Existing `v2.2.0` configuration files are automatically upgraded on startup:
  - Legacy `maps.mode: global` -> Migrated to `sync-data.maps: true`, `maps.lock-global-maps: true`.
  - Legacy `maps.mode: return` / `untracked` / `off` -> Migrated to `sync-data.maps: false`, `maps.lock-global-maps: false`.
  - Obsolete `maps.mode` config entries are automatically removed.
- **Fixed `maps_nbt` Persistence:** Corrected snapshot extraction and database merge logic to ensure map snapshots and canvas pixels are saved non-null to the `{table-prefix}databridge_maps` table.
- **Locked Map Resolution Support:** Improved map ID resolution logic (`resolveMapId`) to resolve map IDs from item components and PDC (`databridge:original_map_id`), preventing locked maps created in Cartography Tables from being skipped during saves.
- **Canvas Palette & Renderer Fix:** Resolved canvas rendering issues where maps rendered as solid brown blocks. Default background world renderers are now cleared (`view.getRenderers().clear()`) before applying custom raw canvas palette byte renderers.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
