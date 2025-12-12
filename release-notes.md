# Release Notes - v2.0.6

## 🚀 CI/CD & Process Automation

This release focuses on improving the development lifecycle, automation, and project stability. **No changes have been made to the in-game plugin logic.**

### New Features

- **Automated Release Pipeline**: Merging to `main` now automatically builds, verifies, tags (git), and publishes (GitHub Release) the artifacts.
- **Strict Version Control**: Added CI checks that prevent merging if versions in `pom.xml`, `plugin.yml`, `bungee.yml`, and `release-notes.md` do not match or if the version tag already exists.
- **Helper Scripts**: Added `scripts/update-version.sh` to interactively bump versions across all required files.

### Documentation

- Updated `CONTRIBUTING.md` (implied) and workflows to reflect the new strict release process.
