package de.jumpstone.plugins.proxy.bungee;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.jumpstone.plugins.managers.RedisManager;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class BungeeListener implements Listener {

    private final Plugin plugin;

    public BungeeListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (event.getFrom() != null) {
            String playerName = player.getName();
            String fromServer = event.getFrom().getName();

            plugin.getLogger().info("Player " + playerName + " is switching from " + fromServer
                    + ". Handling data transfer...");

            // If Redis is enabled, cache player data immediately
            if (plugin instanceof BungeeHMTSync && ((BungeeHMTSync) plugin).isRedisEnabled()) {
                RedisManager redisManager = ((BungeeHMTSync) plugin).getRedisManager();
                if (redisManager != null && redisManager.isAvailable()) {
                    // Send immediate cache request to source server
                    ByteArrayDataOutput cacheOut = ByteStreams.newDataOutput();
                    cacheOut.writeUTF("CachePlayerData");
                    cacheOut.writeUTF(player.getUniqueId().toString());
                    event.getFrom().sendData("hmt-sync:main", cacheOut.toByteArray());

                    plugin.getLogger().info("[HMTSync-Redis] Cached player data for " + playerName + " in Redis");
                }
            }

            // Send traditional SaveAndRelease request
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SaveAndRelease");
            out.writeUTF(player.getUniqueId().toString());
            event.getFrom().sendData("hmt-sync:main", out.toByteArray());
        }
    }
}