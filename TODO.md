# MC Data Bridge - Future Roadmap & TODOs

These are the planned tasks to be started next week or shortly after the holidays.

### 1. Missing Data Types for Synchronization

While the plugin covers core player stats, it currently does not synchronize several secondary data types:

* [ ] **Player Statistics:** Vanilla Minecraft statistics (e.g., blocks mined, time played, deaths) are not currently tracked or synchronized in `PlayerData.java`.
* [ ] **Persistent Data Container (PDC):** Custom metadata attached to players by other plugins is not serialized. This means data from other plugins (like levels, currency, or quest progress stored in PDC) will not follow the player across servers.
* [ ] **Flight and GameMode State:** The plugin does not appear to save whether a player was flying or their specific GameMode (Creative, Survival, etc.) upon switching.

### 2. Operational Limitations

* [ ] **Non-MySQL Database Support:** The `DatabaseManager` is hardcoded for MySQL/MariaDB connections using the `mysql-connector-j` driver. It does not currently support PostgreSQL, MongoDB, or local SQLite storage.
* [ ] **Automatic Backup System:** While it has a robust "Happy Path" for saving, there is no built-in system for creating periodic backups of the `player_data` table within the plugin itself; it relies on external database management for backups.
* [ ] **In-Game Management GUI:** All management is done via the config file or the `/databridge unlock` command. There is no administrative "viewer" to see a player's synced data in-game without them being online.

### 3. Features "In Progress" or Disabled by Default

Some features exist in the code but are considered "New Features" and are disabled by default in the `config.yml`, requiring manual activation. We need to review/finalize these:

* [ ] **Ender Chest Synchronization:** Disabled by default.
* [ ] **Advancements and Recipes:** Disabled by default.
* [ ] **Location Tracking:** The plugin captures location data but currently notes it is for "Logging/Admin Use Only" and is "NOT APPLIED" to the player upon joining.

### 4. Cross-Platform Parity

* [ ] **Command Parity:** While the Spigot/Paper version has the `/databridge unlock` command, the BungeeCord and Velocity proxy versions are primarily listeners and do not currently have an equivalent administrative command suite on the proxy level.
