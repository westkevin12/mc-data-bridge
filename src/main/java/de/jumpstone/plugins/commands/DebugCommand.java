package de.jumpstone.plugins.commands;

import de.jumpstone.plugins.HMTSync;
import de.jumpstone.plugins.managers.EconomyManager;
import de.jumpstone.plugins.managers.PlaytimeManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DebugCommand implements CommandExecutor {
    private final HMTSync plugin;

    public DebugCommand(HMTSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("hmtsync.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                showStatus(player);
                break;
            case "test-economy":
                testEconomy(player);
                break;
            case "test-playtime":
                testPlaytime(player);
                break;
            case "test-enderchest":
                testEnderChest(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== HMTSync Debug Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/hmtsync debug status" + ChatColor.WHITE + " - Show plugin status");
        player.sendMessage(ChatColor.YELLOW + "/hmtsync debug test-economy" + ChatColor.WHITE + " - Test economy sync");
        player.sendMessage(
                ChatColor.YELLOW + "/hmtsync debug test-playtime" + ChatColor.WHITE + " - Test playtime sync");
        player.sendMessage(
                ChatColor.YELLOW + "/hmtsync debug test-enderchest" + ChatColor.WHITE + " - Test ender chest sync");
    }

    private void showStatus(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== HMTSync Status ===");

        // Config status
        player.sendMessage(ChatColor.YELLOW + "Config Values:");
        player.sendMessage("  Ender Chest Sync: " + ChatColor.GREEN + plugin.isSyncEnabledNewFeature("ender-chest"));
        player.sendMessage("  Economy Sync: " + ChatColor.GREEN + plugin.isSyncEnabledNewFeature("economy"));
        player.sendMessage("  Playtime Sync: " + ChatColor.GREEN + plugin.isSyncEnabledNewFeature("playtime"));
        player.sendMessage("  Debug Mode: " + ChatColor.GREEN + plugin.isDebugMode());

        // Manager status
        EconomyManager econManager = plugin.getEconomyManager();
        PlaytimeManager playManager = plugin.getPlaytimeManager();

        player.sendMessage(ChatColor.YELLOW + "Manager Status:");
        player.sendMessage("  Economy Manager Enabled: " +
                (econManager.isEnabled() ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
        player.sendMessage("  Playtime Manager Enabled: " +
                (playManager.isEnabled() ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

        if (econManager.isEnabled()) {
            player.sendMessage(
                    "  Current Balance: " + ChatColor.AQUA + econManager.format(econManager.getBalance(player)));
        }

        if (playManager.isEnabled()) {
            player.sendMessage("  Current Playtime: " + ChatColor.AQUA + playManager.getFormattedPlaytime(player));
        }

        // Redis status
        player.sendMessage("  Redis Enabled: " +
                (plugin.isRedisEnabled() ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));
    }

    private void testEconomy(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Economy Test ===");

        EconomyManager econManager = plugin.getEconomyManager();
        if (!econManager.isEnabled()) {
            player.sendMessage(ChatColor.RED + "Economy manager is not enabled!");
            player.sendMessage(ChatColor.RED + "Make sure Vault plugin is installed and an economy plugin is loaded.");
            return;
        }

        double currentBalance = econManager.getBalance(player);
        player.sendMessage("Current balance: " + ChatColor.AQUA + econManager.format(currentBalance));

        // Test setting balance
        double testAmount = currentBalance + 100.0;
        econManager.setBalance(player, testAmount);

        double newBalance = econManager.getBalance(player);
        player.sendMessage("After setting to " + econManager.format(testAmount) + ": " + ChatColor.AQUA
                + econManager.format(newBalance));

        if (Math.abs(newBalance - testAmount) < 0.01) {
            player.sendMessage(ChatColor.GREEN + "✓ Economy test passed!");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Economy test failed!");
        }
    }

    private void testPlaytime(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Playtime Test ===");

        PlaytimeManager playManager = plugin.getPlaytimeManager();
        if (!playManager.isEnabled()) {
            player.sendMessage(ChatColor.RED + "Playtime manager is not enabled!");
            player.sendMessage(ChatColor.RED + "Make sure Essentials plugin is installed.");
            return;
        }

        long currentPlaytime = playManager.getPlaytimeMinutes(player);
        player.sendMessage("Current playtime: " + ChatColor.AQUA + playManager.getFormattedPlaytime(player));
        player.sendMessage("(Raw minutes: " + currentPlaytime + ")");

        player.sendMessage(ChatColor.GREEN + "Playtime test completed (read-only)");
    }

    private void testEnderChest(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Ender Chest Test ===");

        boolean enabled = plugin.isSyncEnabledNewFeature("ender-chest");
        player.sendMessage("Ender chest sync enabled: " + (enabled ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"));

        if (!enabled) {
            player.sendMessage(ChatColor.RED + "Enable ender-chest sync in config.yml first!");
            return;
        }

        // Count items in ender chest
        int itemCount = 0;
        for (int i = 0; i < player.getEnderChest().getSize(); i++) {
            if (player.getEnderChest().getItem(i) != null) {
                itemCount++;
            }
        }

        player.sendMessage("Items in ender chest: " + ChatColor.AQUA + itemCount);
        player.sendMessage(ChatColor.GREEN + "Ender chest test completed!");
    }
}