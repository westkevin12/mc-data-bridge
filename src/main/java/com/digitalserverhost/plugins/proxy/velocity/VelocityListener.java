package com.digitalserverhost.plugins.proxy.velocity;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;

public class VelocityListener {

    private final VelocityMCDataBridge plugin;

    public VelocityListener(VelocityMCDataBridge plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(server -> {
            plugin.getLogger().info("Player " + player.getUsername() + " is switching from " + server.getServerInfo().getName() + ". Requesting data save.");
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SaveAndRelease");
            out.writeUTF(player.getUniqueId().toString());
            server.sendPluginMessage(plugin.getChannel(), out.toByteArray());
        });
    }
}
