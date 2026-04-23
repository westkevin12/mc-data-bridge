package com.digitalserverhost.plugins.commands;

import com.digitalserverhost.plugins.managers.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, Component.text("Usage: /databridge <unlock|inspect> <player>", NamedTextColor.RED));
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String targetName = args[1];

        if (subCommand.equals("unlock")) {
            // Run async to avoid blocking main thread with DB lookup
            Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("mc-data-bridge"), () -> {
                UUID uuid;
                try {
                    uuid = UUID.fromString(targetName);
                } catch (IllegalArgumentException e) {
                    // Resolve name to UUID
                    if (com.digitalserverhost.plugins.utils.SchedulerUtils.isPaper()) {
                        // Use modern PaperProfile API (safe async)
                        uuid = com.digitalserverhost.plugins.utils.PaperProfileUtils.resolveUuid(targetName);
                    } else {
                        // Fallback for Spigot/Bukkit (Warning: getOfflinePlayer is blocking)
                        //noinspection deprecation
                        uuid = org.bukkit.Bukkit.getOfflinePlayer(targetName).getUniqueId();
                    }
                }

                if (uuid == null) {
                    com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, Component.text("Could not resolve player " + targetName, NamedTextColor.RED));
                    return;
                }

                boolean success = databaseManager.releaseLock(uuid);

                if (success) {
                    com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, 
                            Component.text("Successfully released lock for player " + targetName + " (" + uuid + ")",
                                    NamedTextColor.GREEN));
                } else {
                    com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, 
                            Component.text("Failed to release lock for " + targetName + ". Check console for errors.",
                                    NamedTextColor.RED));
                }
            });
            return true;
        }

        if (subCommand.equals("inspect")) {
            if (!(sender instanceof org.bukkit.entity.Player)) {
                sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                return true;
            }
            org.bukkit.entity.Player admin = (org.bukkit.entity.Player) sender;
            
            Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("mc-data-bridge"), () -> {
                UUID uuid;
                try {
                    uuid = UUID.fromString(targetName);
                } catch (IllegalArgumentException e) {
                    if (com.digitalserverhost.plugins.utils.SchedulerUtils.isPaper()) {
                        uuid = com.digitalserverhost.plugins.utils.PaperProfileUtils.resolveUuid(targetName);
                    } else {
                        //noinspection deprecation
                        uuid = org.bukkit.Bukkit.getOfflinePlayer(targetName).getUniqueId();
                    }
                }

                if (uuid == null) {
                    com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(admin, Component.text("Could not resolve player " + targetName, NamedTextColor.RED));
                    return;
                }

                com.digitalserverhost.plugins.MCDataBridge plugin = (com.digitalserverhost.plugins.MCDataBridge) Bukkit.getPluginManager().getPlugin("mc-data-bridge");
                new com.digitalserverhost.plugins.utils.DataManagementGUI(plugin, databaseManager).openPlayerInspector(admin, uuid, targetName);
            });
            return true;
        }

        com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, Component.text("Unknown subcommand. Usage: /databridge <unlock|inspect> <player>", NamedTextColor.RED));
        return true;
    }
}
