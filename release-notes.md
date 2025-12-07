# Release Notes - MC Data Bridge v2.0.4

## 🆕 New Features
*   **Configurable Table Prefix**: Added a new `table-prefix` option in `config.yml`.
    *   This allows you to specify a prefix (e.g., `mc_data_bridge_`) for the database table.
    *   Useful for avoiding conflicts with other plugins in the same database that use the same table name or running multiple instances of the plugin.

## 🛠 Improvements
*   **Backward Compatibility**: The default behavior remains unchanged. If `table-prefix` is not set or left empty, the plugin continues to use `player_data` as the table name.
*   **Auto-Configuration**: The new `table-prefix` setting will be automatically added to your existing `config.yml` on startup.
*   **Smart Migration**: If you set a prefix (e.g., `survival_`) and have an existing `player_data` table, the plugin will **automatically rename** your old table to the new name (e.g., `survival_player_data`) on first startup, preserving all your player data. this is only done once from the default table name to the new prefix. You can manually rename the table if you need to change the prefix after setting it.

## ⚠️ Notes
*   **No Schema Changes**: This update does not require any database migrations unless you intend to change the table name by setting a prefix.
