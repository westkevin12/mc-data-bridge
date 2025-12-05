# Release Notes

## Version 2.0.2

This maintenance release focuses on API modernization, code cleanup, and continued stability improvements.

### 🛠 Improvements & Fixes

*   **Adventure API:** Switched to the Adventure API (`Component.text`) for handling disconnect and kick messages, replacing legacy string formatting.
*   **Dependency Updates:** Updated `bungeecord-api` to the latest `1.21-R0.4`.
*   **Safety Checks:** Added null checks to prevent errors when kicking players during data application failures.
*   **Legacy Data Compatibility:** Explicitly marked and documented legacy NBT and potion effect handling to ensure older data formats continue to load correctly without generating build warnings.
*   **Code Cleanup:** Removed various unused imports and cleaned up code.

---
