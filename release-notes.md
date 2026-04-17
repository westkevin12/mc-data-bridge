# Release Notes - v2.1.3

**MC Data Bridge v2.1.3** - **API Modernization & Strict Null Safety**

This release focuses on updating core infrastructure to align with the latest 1.21.2+ ecosystems.

---

### 🚀 Modernization & Technical Debt

- **API Modernization (Paper/Velocity):**
  - Updated `paper-api` to `26.1.2.build.7-alpha`.
  - Updated `velocity-api` to `3.5.0-SNAPSHOT` (Build #592).
  - Updated `item-nbt-api` to `2.15.7`.
  - Updated testing dependencies (`mockito`) to `5.23.0`.
- **Bukkit / Paper Refactors:**
  - Migrated health sync properties to utilize `Attribute.MAX_HEALTH`, fully deprecating legacy `GENERIC_` prefix requirements for modern servers.
  - Restructured `PlayerQuitEvent` logic in our flow suites to adopt the modern `Component` adventure text system, erasing legacy string deprecations natively.
  - Upgraded internal plugin message events to correctly route their legacy event parameters to accommodate transfer flags natively.

### 🛡 Zero-Suppression Defensive Null Safety

- Fully phased out `@SuppressWarnings` for null validation loops across the entire bridge logic constraint map, replacing them with mechanically enforced `java.util.Objects.requireNonNull` validation layers cleanly.
- Resolved BungeeCord and Velocity `ByteArrayDataOutput.writeUTF` structural inference problems around UUID String bounds natively to conform to `Guava` expectations.
- Safely rewired ambiguous core Bukkit states (`getLocation`, `advancementIterator`, `getCommand`) to aggressively trap and seal implicit analyzer NPE leakages directly inside the event pipeline.

### 🏗 Build Pipeline Modernization

- **Java 25 Architecture:**
  - Upgraded compiler compliance matrix to strictly target Java 25 (`<release>25</release>`) accommodating PaperMC 26's bleeding-edge bytecode limits.
  - Bumped `maven-shade-plugin` natively to `3.6.2`, and injected cutting edge `org.ow2.asm` `9.9.1` dependencies to successfully resolve shade failures against Java 25's new major bytecode signatures.
- **Velocity Integration Fixes:**
  - Patched breaking compiler regressions across Maven Annotation Processors that suppressed proxy integration endpoints. 
  - Explicilty routed the Velocity API annotation processor (`@Plugin`) directly into the compiler arguments array, successfully recovering the `velocity-plugin.json` initialization file.
