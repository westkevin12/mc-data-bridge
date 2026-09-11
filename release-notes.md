# MC Data Bridge - Release Notes (v2.2.2)

## Overview

Version 2.2.2 introduces critical enterprise concurrency and integrity protections derived from an architectural security audit, including monotonic distributed lock fencing tokens, normalized snapshot SHA-256 integrity validation, transactional component saves & legacy migrations, and automated unit testing in CI.

---

## Key Changes

### 🔒 Monotonic Distributed Lock Fencing Tokens (#41, AUDIT §2)
- **Epoch Fencing Protection:** Added `lock_version` (`BIGINT DEFAULT 0`) to `player_data`. Every lock acquisition increments `lock_version`, producing a monotonic fencing token.
- **Stale Write Prevention:** Saves, heartbeats, and lock releases evaluate `WHERE uuid = ? AND locking_server = ? AND lock_version = ?`. Delayed servers or un-fenced post-GC pause writes are automatically rejected, protecting database state from stale session overwrites.

### 🛡️ Normalized Data Integrity Checksums (#42, AUDIT §3)
- **Normalized SHA-256 Checksum Verification:** Added `snapshot_checksum` (`VARCHAR(64)`) to `player_data`.
- **Snapshot Integrity Verification:** Computes canonical SHA-256 hashes across health, food, XP, NBT blobs (inventory, armor, ender chest, PDC), and game mode salted with `security.seed`. Protects against database tampering across normalized component tables.

### ⚡ Atomic Single-Transaction Persistence & Migration (#44, #45, AUDIT §5, §6)
- **Single-Transaction Component Saves & Releases (#44):** `saveAndReleaseLockComponents` now executes all component writes (`databridge_inventories`, `statistics`, `metadata`, `companions`, `maps`) and lock releases within a single database connection and transaction (`setAutoCommit(false)` ... `commit()`).
- **Transactional Legacy Migration (#45):** `loadLegacyData` executes component migration and legacy `data = NULL` clearing inside a unified transaction with automatic rollback (`rollback()`) on failure to eliminate dual-source-of-truth conditions.

### 🧪 Automated CI Unit Testing & Concurrency Suite (#43, AUDIT §16)
- **CI Test Automation:** Re-enabled automated unit testing in GitHub Actions (`.github/workflows/maven.yml`) on pull requests and merges.
- **Fencing & Stale Write Unit Tests:** Added `LockFencingTest` verifying lock token increments, stale write rejections, heartbeat fencing, and recovery flow.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
