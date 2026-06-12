# MC Data Bridge - Release Notes (v2.1.6)

## Overview

Version 2.1.6 is a comprehensive feature and security update that introduces multi-server **Companion & Pet Synchronization**, completes database schema normalization, adds compressed binary NBT serialization, embeds a **Prometheus Metrics Exporter**, and hardens overall network security with strict seed verification.

---

## Key Features & Improvements

### 🐾 Companion & Pet Synchronization
- **Tamed Pet Persistence:** Synchronizes tamed wolves, cats, parrots, and other pets across server switches.
- **State Preservation:** Restores ownership, custom names, health/max health, and sitting status accurately.
- **NBT-Level Preservation:** Utilizes NBTAPI metadata merging to preserve custom pet attributes, items, and collars.
- **Duplication Prevention:** Despawns source entities synchronously upon snapshot generation to prevent duplication exploits.

### 🗄️ Relational Component Schema Normalization
- **Companions Component Table:** Introduced the `databridge_companions` table for row-level normalized storage.
- **Optimized SQL Transactions:** Avoids write-amplification by target-updating specific components (inventories, statistics, metadata, companions) independently.

### ⚡ Binary NBT Serialization Option
- **Compressed NBT Storage:** Adds support for native binary compression (`serialization-format: binary`) to dramatically reduce database footprint, storage I/O, and CPU serialization overhead.

### 🛡️ Production Security Hardening
- **Fails-Strict Safe Mode:** Prevents startup if the default cryptographic seed is detected and no secure seed is configured, mitigating identity hijacking risks.
- **DDL Column Whitelists:** Enforces SQL parameter and column sanitation to secure automated schema migration queries.

### 📊 Embedded Prometheus Metrics Exporter
- **Live Server Metrics:** Provides an optional HTTP `/metrics` scrape endpoint for Grafana/Prometheus tracking, exposing cache efficiency, database connection pool statistics, active transaction counts, and synchronization latencies.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
