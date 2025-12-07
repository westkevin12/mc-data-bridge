package com.digitalserverhost.plugins.commands;

import com.digitalserverhost.plugins.managers.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class UnlockCommand implements CommandExecutor {

    private final DatabaseManager databaseManager;

    public UnlockCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("databridge.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /databridge unlock <player>", NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];

        // Run async to avoid blocking main thread with DB lookup
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("mc-data-bridge"), () -> {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName); // Deprecated but necessary for name lookup if
                                                                        // UUID unknown

            if (target == null || target.getUniqueId() == null) { // Should not happen with getOfflinePlayer but
                                                                  // checking sanity
                sender.sendMessage(Component.text("Could not resolve player " + targetName, NamedTextColor.RED));
                return;
            }

            UUID uuid = target.getUniqueId();
            boolean success = databaseManager.releaseLock(uuid);

            if (success) {
                sender.sendMessage(
                        Component.text("Successfully released lock for player " + targetName + " (" + uuid + ")",
                                NamedTextColor.GREEN));
            } else {
                sender.sendMessage(
                        Component.text("Failed to release lock for " + targetName + ". Check console for errors.",
                                NamedTextColor.RED));
            }
        });

        return true;
    }
}
