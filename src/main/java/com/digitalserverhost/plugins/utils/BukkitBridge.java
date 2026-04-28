package com.digitalserverhost.plugins.utils;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

public class BukkitBridge implements PlatformBridge {
    @Override
    @SuppressWarnings("deprecation")
    public void disallowPlayer(AsyncPlayerPreLoginEvent event, AsyncPlayerPreLoginEvent.Result result, String message) {
        event.disallow(result, message);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void kickPlayer(org.bukkit.entity.Player player, String message) {
        player.kickPlayer(message);
    }
}
