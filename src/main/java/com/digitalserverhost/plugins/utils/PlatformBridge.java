package com.digitalserverhost.plugins.utils;

import org.jetbrains.annotations.NotNull;

public interface PlatformBridge {
    void disallowPlayer(@NotNull org.bukkit.event.player.AsyncPlayerPreLoginEvent event, @NotNull org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result result, @NotNull String message);
    void kickPlayer(@NotNull org.bukkit.entity.Player player, @NotNull String message);
}
