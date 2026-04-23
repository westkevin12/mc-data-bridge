package com.digitalserverhost.plugins.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

/**
 * Utility for sending messages in a platform-agnostic way.
 */
public class MessageUtils {

    public static void sendMessage(CommandSender sender, Component component) {
        if (SchedulerUtils.isPaper()) {
            sender.sendMessage(component);
        } else {
            // Fallback for Spigot/Bukkit
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
        }
    }

    public static String serialize(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }
}
