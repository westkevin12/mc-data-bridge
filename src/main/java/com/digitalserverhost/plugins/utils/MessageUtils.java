package com.digitalserverhost.plugins.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Utility for sending messages in a platform-agnostic way.
 */
public class MessageUtils {

    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        // We use legacy section symbol for color codes
        sender.sendMessage(message.replace("&", "§"));
    }

    public static @NotNull String serialize(@NotNull Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
