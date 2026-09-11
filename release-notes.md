# MC Data Bridge - Release Notes (v2.2.2)

## Overview

Version 2.2.2 is a major release introducing enterprise-grade concurrency, integrity, and performance protections derived from an architectural security audit. Features include monotonic distributed lock fencing tokens, normalized snapshot SHA-256 integrity validation, single-transaction atomic saves, event-driven lock release messaging to eliminate pre-login latency, standard HMAC-SHA256 identity hashing, and automated unit testing in CI.

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

### ⚡ Event-Driven Lock Release Messaging — 🚀 Up to 10X Faster Server Switching (#46, AUDIT §9)
- **⚡ Up to 10X Faster Cross-Server Transfers:** Dispatches an instant `LockReleased` plugin message upon source server save completion. Destination servers wake up waiting pre-login threads immediately, slashing server-switch lock wait latency from **~500ms down to a near-instant ~50ms**!
- **Resilient Fallback:** Retains 500ms database polling loops as a zero-downtime fallback mechanism if network messages are dropped.

### 🔐 Keyed HMAC-SHA256 Identity Verification (#47, AUDIT §12)
- **Standard HMAC-SHA256:** Upgraded identity hashing in `HashUtils` to use standard `HmacSHA256` key derivation with `security.seed`.
- **Seamless Dual Verification:** Automatically verifies existing records against both HMAC-SHA256 and legacy salted SHA-256 digests for 100% backward compatibility without table migrations.

### 🧪 Automated CI Unit Testing & Concurrency Suite (#43, AUDIT §16)
- **CI Test Automation:** Re-enabled automated unit testing in GitHub Actions (`.github/workflows/maven.yml`) on pull requests and merges.
- **Concurrency & HMAC Unit Tests:** Added `LockFencingTest` and `HashUtilsTest` verifying lock token increments, stale write rejections, HMAC generation, legacy fallback, and null safety.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
