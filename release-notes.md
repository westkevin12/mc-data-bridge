# Release Notes - v2.1.1-SNAPSHOT

**MC Data Bridge v2.1.1** - **Critical Bug Fix & Stability Update**

This patch release addresses a critical issue reported where players with boosted health (e.g., via Health Boost potion effects or attributes) were being kicked upon switching servers.

---

### 🐛 Bug Fixes
*   **Fixed Kick on Server Switch with High Health:** Resolved an issue where players with health greater than 20.0 (Health Boost) would be kicked with an `IllegalArgumentException`. The plugin now safely handles this by loading potions and attributes from the database before setting the max health attribute.

