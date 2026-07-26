package com.digitalserverhost.plugins.commands;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import com.digitalserverhost.plugins.utils.DataManagementGUI;
import com.digitalserverhost.plugins.utils.MessageUtils;
import com.digitalserverhost.plugins.utils.PaperProfileUtils;
import com.digitalserverhost.plugins.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BridgeCommand implements TabExecutor {

    private static final String PLUGIN_NAME = "mc-data-bridge";
    private static final String CMD_INSPECT = "inspect";
    private static final String CMD_INVSEE = "invsee";
    private static final String CMD_ENDERSEE = "endersee";
    private static final String CMD_MIGRATE = "migrate";
    private static final String CMD_UNLOCK = "unlock";
    private static final String FLAG_EDIT = "--edit";

    private static final String PERM_ADMIN = "databridge.admin";
    private static final String PERM_INSPECT = "databridge.inspect";
    private static final String PERM_INSPECT_EDIT = "databridge.inspect.edit";

    private static final String VIEW_INVENTORY = "inventory";
    private static final String VIEW_ENDERCHEST = "enderchest";

    private static final String MSG_PLAYER_ONLY = "§cThis command can only be used by players.";

    private final DatabaseManager databaseManager;
    private final DataManagementGUI guiManager;

    public BridgeCommand(DatabaseManager databaseManager, DataManagementGUI guiManager) {
        this.databaseManager = databaseManager;
        this.guiManager = guiManager;
    }

    public BridgeCommand(DatabaseManager databaseManager) {
        this(databaseManager, null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length < 1) {
            return false;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case CMD_UNLOCK:
                if (!checkBasePermission(sender)) return true;
                handleUnlock(sender, args);
                return true;
            case CMD_INSPECT:
                if (!checkInspectPermission(sender)) return true;
                handleInspect(sender, args);
                return true;
            case CMD_INVSEE:
                if (!checkInspectPermission(sender)) return true;
                handleInvsee(sender, args);
                return true;
            case CMD_ENDERSEE:
                if (!checkInspectPermission(sender)) return true;
                handleEndersee(sender, args);
                return true;
            case CMD_MIGRATE:
                if (!checkBasePermission(sender)) return true;
                handleMigrate(sender, args);
                return true;
            default:
                return false;
        }
    }

    private boolean checkBasePermission(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            MessageUtils.sendMessage(sender, "&cYou do not have permission to use this command.");
            return false;
        }
        return true;
    }

    private boolean checkInspectPermission(CommandSender sender) {
        if (!sender.hasPermission(PERM_INSPECT) && !sender.hasPermission(PERM_ADMIN)) {
            MessageUtils.sendMessage(sender, "&cYou do not have permission to inspect player data.");
            return false;
        }
        return true;
    }

    private boolean checkEditPermission(CommandSender sender) {
        if (!sender.hasPermission(PERM_INSPECT_EDIT) && !sender.hasPermission(PERM_ADMIN)) {
            MessageUtils.sendMessage(sender, "&cYou do not have permission to edit player data (databridge.inspect.edit required).");
            return false;
        }
        return true;
    }

    private boolean hasEditFlag(String[] args) {
        for (String arg : args) {
            if (FLAG_EDIT.equalsIgnoreCase(arg) || "edit".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission(PERM_INSPECT) && !sender.hasPermission(PERM_ADMIN)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return getArg0Completions(args[0]);
        } else if (args.length == 2) {
            return getOnlinePlayerCompletions(args[1]);
        } else if (args.length == 3) {
            return getArg2Completions(args[0], args[2]);
        } else if (args.length == 4) {
            return getArg3Completions(args[0], args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> getArg0Completions(String input) {
        List<String> completions = new ArrayList<>();
        String partial = input.toLowerCase();
        for (String sub : List.of(CMD_UNLOCK, CMD_INSPECT, CMD_INVSEE, CMD_ENDERSEE, CMD_MIGRATE)) {
            if (sub.startsWith(partial)) {
                completions.add(sub);
            }
        }
        return completions;
    }

    @SuppressWarnings("null")
    private List<String> getOnlinePlayerCompletions(String input) {
        List<String> completions = new ArrayList<>();
        String partial = input.toLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(partial)) {
                completions.add(player.getName());
            }
        }
        return completions;
    }

    private List<String> getArg2Completions(String subCommand, String input) {
        String partial = input.toLowerCase();
        if (CMD_INSPECT.equalsIgnoreCase(subCommand)) {
            List<String> completions = new ArrayList<>();
            for (String option : List.of(VIEW_INVENTORY, VIEW_ENDERCHEST, FLAG_EDIT)) {
                if (option.startsWith(partial)) {
                    completions.add(option);
                }
            }
            return completions;
        } else if (CMD_INVSEE.equalsIgnoreCase(subCommand) || CMD_ENDERSEE.equalsIgnoreCase(subCommand)) {
            List<String> completions = new ArrayList<>();
            if (FLAG_EDIT.startsWith(partial)) {
                completions.add(FLAG_EDIT);
            }
            return completions;
        } else if (CMD_MIGRATE.equalsIgnoreCase(subCommand)) {
            return getOnlinePlayerCompletions(input);
        }
        return Collections.emptyList();
    }

    private List<String> getArg3Completions(String subCommand, String input) {
        String partial = input.toLowerCase();
        if (CMD_INSPECT.equalsIgnoreCase(subCommand)) {
            List<String> completions = new ArrayList<>();
            if (FLAG_EDIT.startsWith(partial)) {
                completions.add(FLAG_EDIT);
            }
            return completions;
        }
        return Collections.emptyList();
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtils.sendMessage(sender, "&cUsage: /databridge unlock <player>");
            return;
        }
        String targetName = args[1];
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            UUID uuid = resolveUuid(targetName);
            if (uuid == null) {
                MessageUtils.sendMessage(sender, "&cCould not resolve player " + targetName);
                return;
            }

            boolean success = databaseManager.releaseLock(uuid);
            if (success) {
                MessageUtils.sendMessage(sender, 
                        "&aSuccessfully released lock for player " + targetName + " (" + uuid + ")");
            } else {
                MessageUtils.sendMessage(sender, 
                        "&cFailed to release lock for " + targetName + ". Check console for errors.");
            }
        });
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(MSG_PLAYER_ONLY);
            return;
        }
        if (args.length < 2) {
            MessageUtils.sendMessage(sender, "&cUsage: /databridge inspect <player> [inventory|enderchest] [--edit]");
            return;
        }
        String targetName = args[1];
        boolean wantEdit = hasEditFlag(args);
        if (wantEdit && !checkEditPermission(admin)) return;

        String viewType = "overview";
        if (args.length >= 3 && !args[2].startsWith("-") && !"edit".equalsIgnoreCase(args[2])) {
            viewType = args[2].toLowerCase();
        }

        openGuiForPlayer(admin, targetName, viewType, wantEdit);
    }

    private void handleInvsee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(MSG_PLAYER_ONLY);
            return;
        }
        if (args.length < 2) {
            MessageUtils.sendMessage(sender, "&cUsage: /databridge invsee <player> [--edit]");
            return;
        }
        boolean wantEdit = hasEditFlag(args);
        if (wantEdit && !checkEditPermission(admin)) return;

        openGuiForPlayer(admin, args[1], VIEW_INVENTORY, wantEdit);
    }

    private void handleEndersee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(MSG_PLAYER_ONLY);
            return;
        }
        if (args.length < 2) {
            MessageUtils.sendMessage(sender, "&cUsage: /databridge endersee <player> [--edit]");
            return;
        }
        boolean wantEdit = hasEditFlag(args);
        if (wantEdit && !checkEditPermission(admin)) return;

        openGuiForPlayer(admin, args[1], VIEW_ENDERCHEST, wantEdit);
    }

    private void openGuiForPlayer(Player admin, String targetName, String viewType, boolean isEditable) {
        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            UUID uuid = resolveUuid(targetName);
            if (uuid == null) {
                MessageUtils.sendMessage(admin, "&cCould not resolve player " + targetName);
                return;
            }

            MCDataBridge plugin = getPlugin();
            DataManagementGUI gui = (guiManager != null) ? guiManager : new DataManagementGUI(plugin, databaseManager);

            if (VIEW_INVENTORY.equals(viewType)) {
                gui.openInventoryView(admin, uuid, targetName, isEditable);
            } else if (VIEW_ENDERCHEST.equals(viewType)) {
                gui.openEnderChestView(admin, uuid, targetName, isEditable);
            } else {
                gui.openPlayerInspector(admin, uuid, targetName, isEditable);
            }
        });
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtils.sendMessage(sender, "&cUsage: /databridge migrate <source> <target>");
            return;
        }
        String sourceInput = args[1];
        String targetInput = args[2];

        Bukkit.getScheduler().runTaskAsynchronously(getPlugin(), () -> {
            UUID sourceUuid = resolveUuid(sourceInput);
            UUID targetUuid = resolveUuid(targetInput);

            if (sourceUuid == null) {
                MessageUtils.sendMessage(sender, "&cCould not resolve source player: " + sourceInput);
                return;
            }
            if (targetUuid == null) {
                MessageUtils.sendMessage(sender, "&cCould not resolve target player: " + targetInput);
                return;
            }

            try {
                boolean success = databaseManager.migrateData(sourceUuid, targetUuid);
                if (success) {
                    MessageUtils.sendMessage(sender, "&aSuccessfully migrated data from " + sourceInput + " to " + targetInput);
                } else {
                    MessageUtils.sendMessage(sender, "&cMigration failed. Ensure the source player has data.");
                }
            } catch (Exception e) {
                MessageUtils.sendMessage(sender, "&cError during migration: " + e.getMessage());
            }
        });
    }

    private MCDataBridge getPlugin() {
        return (MCDataBridge) Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
    }

    private UUID resolveUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException _) {
            UUID dbUuid = databaseManager.getUuidByName(input);
            if (dbUuid != null) return dbUuid;

            if (SchedulerUtils.isPaper()) {
                return PaperProfileUtils.resolveUuid(input);
            } else {
                return Bukkit.getOfflinePlayer(input).getUniqueId();
            }
        }
    }
}
