# HMT Sync Build Guide

This guide explains how to build the HMT Sync plugin for different Minecraft server platforms.

## Building for Different Platforms

### 1. Building for Spigot/Paper Servers (Recommended for game servers)

```bash
mvn clean package -Pspigot
```

This creates: `target/hmt-sync-2.1.1-SNAPSHOT-spigot.jar`

**Use this JAR for:**
- Spigot servers
- Paper servers
- Any Bukkit-compatible server

### 2. Building for Velocity Proxies

```bash
mvn clean package -Pvelocity
```

This creates: `target/hmt-sync-2.1.1-SNAPSHOT-velocity.jar`

**Use this JAR for:**
- Velocity proxy servers

### 3. Building for BungeeCord Proxies

```bash
mvn clean package -Pbungee
```

This creates: `target/hmt-sync-2.1.1-SNAPSHOT-bungee.jar`

**Use this JAR for:**
- BungeeCord proxy servers
- Waterfall servers

### 4. Building Universal JAR (Development only)

```bash
mvn clean package -Puniversal
```

This creates: `target/hmt-sync-2.1.1-SNAPSHOT-universal.jar`

**Warning:** This JAR contains all metadata files and should only be used for development/testing. It may cause conflicts on production servers.

## Deployment Instructions

### For Game Servers (Spigot/Paper):
1. Build using `-Pspigot` profile
2. Copy `hmt-sync-*-spigot.jar` to your server's `plugins/` folder
3. Restart the server

### For Proxy Servers:
1. **For Velocity:** Build using `-Pvelocity` profile
2. **For BungeeCord:** Build using `-Pbungee` profile
3. Copy the appropriate JAR to your proxy's `plugins/` folder
4. Restart the proxy

## Troubleshooting

### "Unable to load plugin - appears to be a Bukkit or BungeeCord plugin"

This error occurs when you try to load a Bukkit-formatted plugin on Velocity, or vice versa.

**Solution:**
- Make sure you're using the correct build profile for your platform
- Don't use the universal JAR on production servers
- Check that you built with the correct Maven profile

### Checking Which JAR You Have

You can verify which platform a JAR is built for by examining its contents:

```bash
# Check if it's a Spigot JAR (should contain plugin.yml)
jar -tf target/hmt-sync-*-spigot.jar | grep plugin.yml

# Check if it's a BungeeCord JAR (should contain bungee.yml)
jar -tf target/hmt-sync-*-bungee.jar | grep bungee.yml

# Velocity JARs should NOT contain plugin.yml or bungee.yml
jar -tf target/hmt-sync-*-velocity.jar | grep -E "(plugin\.yml|bungee\.yml)"
```

## Why This Structure?

The hybrid approach requires different metadata files because:

- **Spigot/Paper** uses `plugin.yml` format
- **BungeeCord** uses `bungee.yml` format  
- **Velocity** uses annotation-based plugin definition (no YAML file needed)

Each platform's loader checks for its specific metadata format, which is why mixing them causes loading errors.