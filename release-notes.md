# MC Data Bridge - Release Notes (v2.1.5)

## Overview

Version 2.1.5 is a stability patch focused on maintaining compatibility with the bleeding-edge Paper 26.1 API (Minecraft 1.21.4+). This release addresses a specific serialization issue with modern Adventure text objects and optimizes the plugin's internal dependency structure.

## Changes & Fixes

### 🧩 Adventure Text Serialization Patch

- Resolved a `NoClassDefFoundError` and serialization failure affecting niche Adventure text components, specifically those used in modern **Player Head** metadata.
- Aligned internal dependencies with **Adventure v4.26.1** to support the latest Minecraft 1.21.4 features.

### 🚀 Modern API & Build Stability

- **Paper 26.1 Support:** Officially verified compatibility with the latest Paper alpha builds.
- **Dependency Optimization:** Transitioned Adventure libraries to `provided` scope, maintaining a lightweight JAR (~3.0M) while ensuring native performance on Paper/Velocity.

---

_For installation instructions and configuration details, please refer to the [README.md](README.md) and [config.yml](src/main/resources/config.yml)._
