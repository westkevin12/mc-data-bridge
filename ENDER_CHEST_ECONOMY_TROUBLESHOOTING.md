# Ender Chest & Economy Sync Troubleshooting Guide

## Problem Summary
Your config.yml has `ender-chest: true` and `economy: true` enabled, but these features aren't synchronizing between servers.

## Root Causes Identified

### 1. **Dependency Requirements**
Both features require specific plugins to be installed:

#### Economy Sync Requirements:
- **Vault** plugin must be installed
- An **economy plugin** (like EssentialsX, CMI, etc.) must be loaded
- Vault acts as the bridge between HMTSync and the economy plugin

#### Ender Chest Sync Requirements:
- No additional plugins required
- Built into Minecraft/Spigot/Paper

### 2. **Plugin Manager Status**
The managers might be failing silently. Use the debug command to check:

```
/hmtsync-debug status
```

This will show:
- Whether economy/playtime managers are enabled
- Current balance/playtime values
- Redis connection status

### 3. **Config Loading Issue**
There might be a timing issue with config loading.

## Immediate Solutions

### Step 1: Install Required Dependencies
Make sure these plugins are installed on ALL servers:
1. **Vault** - Essential for economy sync
2. **An economy plugin** (EssentialsX, CMI, etc.)

### Step 2: Test with Debug Commands
On each server, run:
```
/hmtsync-debug status
/hmtsync-debug test-economy
/hmtsync-debug test-enderchest
```

### Step 3: Enable Debug Mode
Set `debug: true` in your config.yml to see detailed logs:
```
debug: true
```

Then restart the server and check the console for:
- Economy manager initialization messages
- Playtime manager initialization messages  
- Any error messages during player join/save

### Step 4: Verify Database Schema
Check that your database table has the correct structure:
```sql
DESCRIBE player_data;
```

Should show:
- `uuid` (VARCHAR)
- `data` (LONGBLOB) 
- `is_locked` (BOOLEAN)
- `locking_server` (VARCHAR)
- `lock_timestamp` (BIGINT)

## Common Issues & Fixes

### Issue: "Economy manager is not enabled"
**Cause:** Vault or economy plugin missing/not loaded
**Fix:** Install Vault and an economy plugin

### Issue: Economy sync works but values are 0
**Cause:** Economy plugin not properly initialized
**Fix:** Check that your economy plugin loads before HMTSync

### Issue: Ender chest sync not working
**Cause:** Config not being read properly or serialization issues
**Fix:** 
1. Ensure `ender-chest: true` is in the correct location in config
2. Check debug logs for serialization errors
3. Test with simple items first

## Testing Procedure

### Test Economy Sync:
1. Put some money in your inventory on Server A
2. Switch to Server B
3. Check if money appears on Server B
4. Make a transaction on Server B
5. Switch back to Server A
6. Verify the transaction persisted

### Test Ender Chest Sync:
1. Put items in ender chest on Server A
2. Switch to Server B  
3. Open ender chest - items should appear
4. Add/remove items on Server B
5. Switch back to Server A
6. Verify changes persisted

## Advanced Debugging

If basic troubleshooting doesn't work, enable maximum logging:

```yaml
debug: true
sync-data:
  ender-chest: true
  economy: true
```

Look for these log messages:
- `[HMTSync] Economy Manager enabled: true/false`
- `[HMTSync] Playtime Manager enabled: true/false`
- Serialization/deserialization errors
- Lock acquisition/release messages

## Contact Support
If issues persist after trying all solutions:
1. Share your complete config.yml (with passwords redacted)
2. Provide the output of `/hmtsync-debug status`
3. Share relevant server logs with debug enabled
4. List all plugins installed on your servers

## Quick Checklist
- [ ] Vault plugin installed
- [ ] Economy plugin installed  
- [ ] Both plugins load before HMTSync
- [ ] `ender-chest: true` in config
- [ ] `economy: true` in config
- [ ] Debug mode enabled for testing
- [ ] Database connection working
- [ ] Redis configured (if using hybrid mode)