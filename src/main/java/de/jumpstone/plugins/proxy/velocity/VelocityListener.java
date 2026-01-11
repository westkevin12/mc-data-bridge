package de.jumpstone.plugins.proxy.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import de.jumpstone.plugins.managers.RedisManager;

public class VelocityListener {

    private final VelocityHMTSync plugin;

    public VelocityListener(VelocityHMTSync plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(server -> {
            String playerName = player.getUsername();
            String fromServer = server.getServerInfo().getName();

            plugin.getLogger().info("Player " + playerName + " is switching from " + fromServer
                    + ". Handling data transfer...");

            // If Redis is enabled, cache player data immediately
            if (plugin.isRedisEnabled() && plugin.getRedisManager() != null
                    && plugin.getRedisManager().isAvailable()) {
                RedisManager redisManager = plugin.getRedisManager();

                // Send immediate cache request to source server
                ByteArrayDataOutput cacheOut = ByteStreams.newDataOutput();
                cacheOut.writeUTF("CachePlayerData");
                cacheOut.writeUTF(player.getUniqueId().toString());
                server.sendPluginMessage(plugin.getChannel(), cacheOut.toByteArray());

                plugin.getLogger().info("[HMTSync-Redis] Cached player data for " + playerName + " in Redis");
            }

            // Send traditional SaveAndRelease request
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SaveAndRelease");
            out.writeUTF(player.getUniqueId().toString());
            server.sendPluginMessage(plugin.getChannel(), out.toByteArray());
        });
    }
}