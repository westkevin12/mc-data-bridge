package com.digitalserverhost.plugins.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Utility for sending messages in a platform-agnostic way.
 */
public class MessageUtils {
    
    private MessageUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        // We use legacy section symbol for color codes
        sender.sendMessage(message.replace("&", "§"));
    }

    @SuppressWarnings("null")
    public static @NotNull String serialize(@NotNull Component component) {
        return Objects.requireNonNull(LegacyComponentSerializer.legacySection().serialize(Objects.requireNonNull(component)));
    }
}
