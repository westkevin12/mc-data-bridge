package com.digitalserverhost.plugins.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Utility for sending messages in a platform-agnostic way.
 */
public class MessageUtils {

    @SuppressWarnings("null")
    public static void sendMessage(@NotNull CommandSender sender, @NotNull Component component) {
        if (SchedulerUtils.isPaper()) {
            sender.sendMessage(component);
        } else {
            // Fallback for Spigot/Bukkit
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
        }
    }

    @SuppressWarnings("null")
    public static @NotNull String serialize(@NotNull Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
