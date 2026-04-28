package com.digitalserverhost.plugins.listeners;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.PlayerData;
import fr.xephi.authme.events.LoginEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class AuthMeListener implements Listener {

    private final MCDataBridge plugin;
    private final DatabaseManager databaseManager;

    public AuthMeListener(MCDataBridge plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        if (!plugin.isAutoMigrateAuthMe()) return;

        Player player = event.getPlayer();
        UUID currentUuid = player.getUniqueId();
        String name = player.getName();

        // Check if this name was previously owned by a different UUID
        UUID oldUuid = databaseManager.getUuidByName(name);

        if (oldUuid != null && !oldUuid.equals(currentUuid)) {
            plugin.getLogger().log(java.util.logging.Level.INFO, "AuthMe verified login for {0}. Checking for auto-migration from {1} to {2}", new Object[]{name, oldUuid, currentUuid});
            
            try {
                if (databaseManager.migrateData(oldUuid, currentUuid)) {
                    plugin.getLogger().log(java.util.logging.Level.INFO, "Auto-migration successful for {0} via AuthMe.", name);
                    
                    // Hot-reload: Since the player is already on the server, we fetch their (now migrated) 
                    // data and apply it to their current session.
                    PlayerData data = plugin.getPlayerListener().loadPlayerData(currentUuid);
                    if (data != null) {
                        plugin.getPlayerListener().applyPlayerData(player, data);
                        plugin.getLogger().log(java.util.logging.Level.INFO, "Hot-reloaded migrated data for {0}", name);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to perform AuthMe auto-migration for {0}: {1}", new Object[]{name, e.getMessage()});
            }
        }
    }
}
