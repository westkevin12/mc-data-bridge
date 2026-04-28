package com.digitalserverhost.plugins.commands;

import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class BridgeCommand implements CommandExecutor {

    private static final String PLUGIN_NAME = "mc-data-bridge";
    private final DatabaseManager databaseManager;

    public BridgeCommand(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("databridge.admin")) {
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cUsage: /databridge <unlock|inspect|migrate> <args>");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "unlock":
                handleUnlock(sender, args);
                break;
            case "inspect":
                handleInspect(sender, args);
                break;
            case "migrate":
                handleMigrate(sender, args);
                break;
            default:
                return false;
        }
        return true;
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cUsage: /databridge unlock <player>");
            return;
        }
        String targetName = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin(PLUGIN_NAME), () -> {
            UUID uuid = resolveUuid(targetName);
            if (uuid == null) {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cCould not resolve player " + targetName);
                return;
            }

            boolean success = databaseManager.releaseLock(uuid);
            if (success) {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, 
                        "&aSuccessfully released lock for player " + targetName + " (" + uuid + ")");
            } else {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, 
                        "&cFailed to release lock for " + targetName + ". Check console for errors.");
            }
        });
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }
        if (args.length < 2) {
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cUsage: /databridge inspect <player>");
            return;
        }
        org.bukkit.entity.Player admin = (org.bukkit.entity.Player) sender;
        String targetName = args[1];
        
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin(PLUGIN_NAME), () -> {
            UUID uuid = resolveUuid(targetName);
            if (uuid == null) {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(admin, "&cCould not resolve player " + targetName);
                return;
            }

            com.digitalserverhost.plugins.MCDataBridge plugin = (com.digitalserverhost.plugins.MCDataBridge) Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            new com.digitalserverhost.plugins.utils.DataManagementGUI(plugin, databaseManager).openPlayerInspector(admin, uuid, targetName);
        });
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cUsage: /databridge migrate <source> <target>");
            return;
        }
        String sourceInput = args[1];
        String targetInput = args[2];

        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin(PLUGIN_NAME), () -> {
            UUID sourceUuid = resolveUuid(sourceInput);
            UUID targetUuid = resolveUuid(targetInput);

            if (sourceUuid == null) {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cCould not resolve source player: " + sourceInput);
                return;
            }
            if (targetUuid == null) {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cCould not resolve target player: " + targetInput);
                return;
            }

            try {
                boolean success = databaseManager.migrateData(sourceUuid, targetUuid);
                if (success) {
                    com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&aSuccessfully migrated data from " + sourceInput + " to " + targetInput);
                } else {
                    com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cMigration failed. Ensure the source player has data.");
                }
            } catch (Exception e) {
                com.digitalserverhost.plugins.utils.MessageUtils.sendMessage(sender, "&cError during migration: " + e.getMessage());
            }
        });
    }

    private UUID resolveUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException _) {
            // Try DB first
            UUID dbUuid = databaseManager.getUuidByName(input);
            if (dbUuid != null) return dbUuid;

            if (com.digitalserverhost.plugins.utils.SchedulerUtils.isPaper()) {
                return com.digitalserverhost.plugins.utils.PaperProfileUtils.resolveUuid(input);
            } else {
                return org.bukkit.Bukkit.getOfflinePlayer(input).getUniqueId();
            }
        }
    }
}
