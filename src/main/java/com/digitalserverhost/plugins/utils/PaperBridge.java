package com.digitalserverhost.plugins.utils;

public class PaperBridge implements PlatformBridge {
    @Override
    public void disallowPlayer(org.bukkit.event.player.AsyncPlayerPreLoginEvent event, org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result result, String message) {
        // Modern Paper/Folia API expects native net.kyori.adventure.text.Component
        // We use the provided LegacyComponentSerializer to convert our string
        event.disallow(result, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }

    @Override
    public void kickPlayer(org.bukkit.entity.Player player, String message) {
        player.kick(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
    }
}
