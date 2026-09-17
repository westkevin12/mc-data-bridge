# MC Data Bridge - Formal Security Model & System Invariants Specification

This document details the normative security model, system invariants, trust boundaries, threat model, and cryptographic guarantees enforced by **MC Data Bridge**.

---

## 1. Purpose & Security Objectives

MC Data Bridge manages authoritative distributed player state across Minecraft proxy networks (BungeeCord, Velocity) and multi-server backend architectures.

The system's core objective is to ensure that player data transitions are:
1. **Authoritative**: At most one backend server maintains active write authority over a player's profile at any given time.
2. **Atomic**: State persistence occurs in a single database transaction; partial/fragmented state saves are impossible.
3. **Fenced**: Stale write attempts resulting from network partitions, thread stalls, or long Garbage Collection (GC) pauses are rejected at the database boundary.
4. **Verifiable**: State snapshots and player identity tokens are protected against unauthorized modification and identity spoofing.
5. **Fail-Closed**: Operational or cryptographic failures default to denying access rather than dropping to weaker security controls.

---

## 2. Trust Boundaries

MC Data Bridge operates across three distinct execution boundaries:

```text
┌─────────────────────────────────────────────────────────────┐
│                      PROXY NETWORK BOUNDARY                 │
│                 (Velocity / BungeeCord Proxy)               │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               │ Plugin Message Signal        │ Plugin Message Signal
               ▼                              ▼
┌─────────────────────────────┐┌─────────────────────────────┐
│  BACKEND SERVER BOUNDARY 1  ││  BACKEND SERVER BOUNDARY 2  │
│  (Paper / Folia Server A)   ││  (Paper / Folia Server B)   │
└──────────────┬──────────────┘└──────────────┬──────────────┘
               │                              │
               │ JDBC Transaction             │ JDBC Transaction
               ▼                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   DATABASE STORAGE BOUNDARY                 │
│                 (MySQL / MariaDB / SQLite)                  │
└─────────────────────────────────────────────────────────────┘
```

1. **Proxy Boundary**: Responsible for player routing and orchestrating high-speed lock release notifications (`LockReleased`). The proxy is *trusted for routing notifications*, but is *not the authority for data persistence*.
2. **Backend Server Boundary**: Responsible for loaded player state runtime, inventory modification, and issuing SQL persistence/lock statements. Backend servers are *untrusted across nodes* and must demonstrate write authority via valid fencing tokens.
3. **Database Boundary**: The *single authoritative source of truth*. Enforces atomicity, transactional isolation, lock states, and lock version fencing.

---

## 3. Threat Model

MC Data Bridge explicitly protects against the following threat vectors:

| Threat Vector | Description | System Countermeasure |
| :--- | :--- | :--- |
| **Stale Writer / Split-Brain** | Server A stalls (e.g. GC pause); Server B acquires lock. Server A resumes and attempts to save old state. | Monotonic fencing tokens (`lock_version`). SQL updates check `WHERE lock_version = N`. |
| **Partial Persistence Failure** | Server crashes after saving inventory but before saving ender chest or releasing lock. | Single-transaction `BEGIN...COMMIT` block for component tables and lock status. |
| **Concurrent Session Race** | Player logs into Server A and Server B simultaneously ("Double Login"). | Database-level row locking (`is_locked=1`, `locking_server`). |
| **Identity Impersonation** | Malicious actor manipulates offline UUID / username mapping. | Keyed HMAC-SHA256 identity hashing (`identity_hash`). |
| **State Tampering** | Out-of-band manipulation of database component rows. | Snapshot checksum validation (`snapshot_checksum`). |
| **Silent Downgrade** | Cryptographic initialization error silently dropping to plain unkeyed hashes. | Fail-closed policy: runtime crypto initialization failures abort operations. |

### 3.1 Security Regression Test Matrix

The following test matrix defines mandatory verification scenarios enforced by unit and integration tests:

| Attack / Threat Scenario | Invariant Tested | Required Outcome |
| :--- | :--- | :--- |
| **Active Lock Write** | Lock Ownership (§4.1) | Server holding active unexpired lock successfully persists state. |
| **Unheld Lock Write** | Lock Ownership (§4.1) | Server attempting write without lock is rejected. |
| **GC Stall / Stale Writer** | Lock Fencing (§4.2) | Resumed server with $V_{\text{server}} < V_{\text{db}}$ updates 0 rows; save aborts. |
| **Lock Steal / Monotonic Generation** | Lock Fencing (§4.2) | New lock acquisition increments lock version ($V_{\text{db}} \leftarrow V_{\text{db}} + 1$). |
| **Component Field Tampering** | State Integrity (§4.5) | Modifying inventory, XP, or health alters `snapshot_checksum`. |
| **Identity Hash Tampering** | Identity Verification (§4.4) | Altering stored `identity_hash` fails verification. |
| **HMAC Provider Failure** | Fail-Closed Policy (§7) | Provider initialization failure throws exception; does NOT downgrade. |
| **Legacy Identity Hash** | Legacy Migration (§6) | Unmigrated legacy SHA-256 hash verifies and migrates on next save. |
| **Invalid Credential Format** | Legacy Migration (§6) | Bad credential fails HMAC and legacy check; join rejected. |
| **Interrupted Persistence** | Atomic Persistence (§4.3) | SQL exception during multi-table update rolls back entire transaction. |

---

## 4. Normative Security Invariants

The security model of MC Data Bridge is defined by five mandatory invariants. Implementation details across versions MUST satisfy these invariants.

### 4.1 Lock Ownership Invariant
> **Invariant**: *At any given timestamp $T$, at most one backend server MAY possess active write authority over a specific player UUID.*

- An active lock requires `is_locked = TRUE` and a non-expired `lock_timestamp`.
- Lock acquisition fails if another server holds an unexpired lock.

### 4.2 Fencing Invariant
> **Invariant**: *A server possessing an obsolete lock generation token ($V_{\text{server}} < V_{\text{db}}$) MUST NOT be capable of persisting player state or modifying lock state.*

- Every lock acquisition increments `lock_version` monotonically ($V_{\text{db}} \leftarrow V_{\text{db}} + 1$).
- All write operations execute with a strict conditional guard:
  ```sql
  UPDATE databridge_inventories 
  SET ... 
  WHERE uuid = ? AND lock_version = ?;
  ```
- If an expected persistence update affects `0` rows, the transaction MUST abort immediately; where the component row is known to exist, this is treated as a fencing/stale-writer violation.

### 4.3 Atomic Persistence Invariant
> **Invariant**: *A state update operation MUST either commit all normalized component tables and update lock status atomically, or rollback completely.*

- No intermediate state (e.g., inventory updated but statistics uncommitted) shall ever be visible to other servers or persisted to storage.
- Executed via database transaction isolation:
  ```sql
  START TRANSACTION;
  UPDATE databridge_inventories ...;
  UPDATE databridge_statistics ...;
  UPDATE databridge_metadata ...;
  UPDATE player_data SET is_locked = 0 ... WHERE lock_version = ?;
  COMMIT;
  ```

### 4.4 Identity Verification Invariant
> **Invariant**: *A player identity digest MUST be verified using the configured secret key before granting state access.*

- Identity digests use Keyed HMAC-SHA256:
  $$\text{HMAC-SHA256}(K = \text{security.seed}, M = \text{lowercase(name)} \mathbin{\Vert} \text{":"} \mathbin{\Vert} \text{UUID})$$
- Verification verifies exact byte equality against stored `identity_hash`.

### 4.5 State Integrity Invariant
> **Invariant**: *A loaded state snapshot MUST match its canonical snapshot checksum before being applied to a player entity.*

- If `verify-data-integrity` is enabled, calculating `snapshot_checksum` over canonicalized fields must match the DB record.
- Mismatched checksums prevent corrupt/tampered state from entering server memory.

---

## 5. Cryptographic Model

### 5.1 Identity Hashing Construction
- **Algorithm**: `HmacSHA256`
- **Key ($K$)**: UTF-8 bytes of `security.seed`.
- **Message ($M$)**: `name.toLowerCase(Locale.ROOT) + ":" + uuid.toString()`
- **Output**: 64-character lowercase hexadecimal string.

### 5.2 Canonical State Representation
The canonical representation formats player attributes deterministically prior to SHA-256 digest computation:

$$\text{CanonicalString} = \text{hp:}\langle\text{health}\rangle\text{;food:}\langle\text{food,sat,exh}\rangle\text{;xp:}\langle\text{xp,exp,level}\rangle\text{;inv:}\langle\text{inv\_bytes}\rangle\text{;arm:}\langle\text{arm\_bytes}\rangle\text{;ec:}\langle\text{ec\_bytes}\rangle\text{;pdc:}\langle\text{pdc}\rangle\text{;gm:}\langle\text{gamemode}\rangle\text{;}$$

- Numerical fields use deterministic formatting (`Locale.ROOT`).
- Complex components (inventory, armor, ender chest) are represented using deterministic canonical serialization rather than lossy 32-bit Java object/list hash codes (`List.hashCode()`).

---

## 6. Legacy Compatibility & Data Migration Model

To support seamless upgrades without requiring network downtime, MC Data Bridge implements an explicit **Legacy Migration Architecture**:

```text
                  Incoming Verification Request
                                │
                                ▼
                    Try Current HMAC Verification
                                │
                  ┌─────────────┴─────────────┐
                  │                           │
               MATCH                       NO MATCH
                  │                           │
                  ▼                           ▼
           Identity Valid         Try Legacy SHA-256 Verification
                                              │
                                ┌─────────────┴─────────────┐
                                │                           │
                             MATCH                       NO MATCH
                                │                           │
                                ▼                           ▼
                      Identity Valid &           REJECT / RE-AUTHENTICATE
                      Marked for Migration
                                │
                                ▼
                    On Next State Save:
                    Persist New HMAC-SHA256
```

1. **Read Compatibility**: When loading a record, verification attempts `HmacSHA256` first. If verification fails, it attempts legacy concatenated `SHA-256`.
2. **Opportunistic Migration**: Upon successful legacy verification, the record is authenticated, and the server computes the new `HmacSHA256` digest to overwrite `identity_hash` on the next transactional write.
3. **Decoupled Architecture**: Legacy algorithm migration handles existing database profiles. It is functionally distinct from future key rotation lifecycles (`ACTIVE` -> `GRACE` -> `RETIRED`).

---

## 7. Fail-Closed Policy

MC Data Bridge explicitly enforces a **Fail-Closed** security response:

1. **Cryptographic Initialization Failures**: If `HmacSHA256` is unavailable or `SecretKeySpec` initialization throws an exception, the system MUST throw a runtime exception and halt authentication. It MUST NOT fall back to unkeyed SHA-256.
2. **Lock Acquisition Timeout**: If a lock cannot be acquired within `lock-timeout` and the lock has not expired, the player join operation MUST be canceled with a user-facing error message ("Data still syncing...").
3. **Stale Write Detection**: If a persistence query updates 0 rows due to `lock_version` mismatch, the save transaction MUST roll back and log a critical concurrency warning.

---

## 8. Database Security & Operational Recommendations

To meet the security model's assumptions in production deployments:

1. **Secret Seed Configuration**:
   - `security.seed` MUST be changed from the default `change-me-to-a-long-random-string`.
   - All backend servers connected to the same database MUST share identical `security.seed` values.
   - Production seeds MUST NOT be committed to public source repositories.
2. **Database Transport Security**:
   - Connections across public networks MUST enforce SSL/TLS (`useSSL=true`).
   - `allowPublicKeyRetrieval` should be set to `false` in production environments unless restricted to secure internal networks.

---

## 9. Security Invariant Roadmap

Future versions of MC Data Bridge will extend this security model along the following roadmap:

- **v2.2.3**: Strict fail-closed HMAC exceptions, `Locale.ROOT` normalization, canonical checksum string serialization.
- **v2.4.0**: Native State Provenance Commit Chain & Transactional Event Outbox (`databridge_state_commits`, `databridge_state_deltas`, `databridge_provenance_outbox`).
- **v2.5.0**: Cryptographic Key Agility & Lifecycle Management (Multi-key active/grace verification, versioned `key_id` headers, dedicated provenance signing keys).
