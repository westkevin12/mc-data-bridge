package com.digitalserverhost.plugins.proxy.bungee;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
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
            plugin.getLogger().info("Player " + player.getName() + " is switching from " + event.getFrom().getName() + ". Requesting data save.");
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SaveAndRelease");
            out.writeUTF(player.getUniqueId().toString());
            event.getFrom().sendData("mc-data-bridge:main", out.toByteArray());
        }
    }
}
