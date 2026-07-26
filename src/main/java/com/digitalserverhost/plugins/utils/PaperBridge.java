package com.digitalserverhost.plugins.utils;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class PaperBridge implements PlatformBridge {
    @Override
    @SuppressWarnings("null")
    public void disallowPlayer(@NotNull org.bukkit.event.player.AsyncPlayerPreLoginEvent event, @NotNull org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result result, @NotNull String message) {
        // Modern Paper/Folia API expects native net.kyori.adventure.text.Component
        // We use the provided LegacyComponentSerializer to convert our string
        event.disallow(result, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(Objects.requireNonNull(message)));
    }

    @Override
    @SuppressWarnings("null")
    public void kickPlayer(@NotNull org.bukkit.entity.Player player, @NotNull String message) {
        player.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(Objects.requireNonNull(message)));
    }
}
