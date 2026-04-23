package com.digitalserverhost.plugins.utils;

public interface PlatformBridge {
    void disallowPlayer(org.bukkit.event.player.AsyncPlayerPreLoginEvent event, org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result result, String message);
    void kickPlayer(org.bukkit.entity.Player player, String message);
}
