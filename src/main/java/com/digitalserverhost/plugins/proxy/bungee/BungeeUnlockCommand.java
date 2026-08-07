package com.digitalserverhost.plugins.proxy.bungee;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;

public class BungeeUnlockCommand extends Command {

    private final BungeeMCDataBridge plugin;

    public BungeeUnlockCommand(BungeeMCDataBridge plugin) {
        super("databridge", "databridge.admin", "db");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0 && !args[0].equalsIgnoreCase("unlock")) {
            if (sender instanceof net.md_5.bungee.api.connection.ProxiedPlayer player) {
                player.chat("/mc-data-bridge:databridge " + String.join(" ", args));
                return;
            }
            sender.sendMessage(new net.md_5.bungee.api.chat.ComponentBuilder("Usage: /databridge unlock <player>").color(net.md_5.bungee.api.ChatColor.RED).create());
            return;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("unlock")) {
            sender.sendMessage(new net.md_5.bungee.api.chat.ComponentBuilder("Usage: /databridge unlock <player>").color(net.md_5.bungee.api.ChatColor.RED).create());
            return;
        }

        String targetName = args[1];
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            // Since we don't have direct DB access on proxy easily, we relay to Spigot servers
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("ForceUnlock");
            
            // We use name lookup if possible, otherwise we assume it's a UUID string
            String uuidStr = targetName;
            net.md_5.bungee.api.connection.ProxiedPlayer player = plugin.getProxy().getPlayer(targetName);
            if (player != null) {
                uuidStr = player.getUniqueId().toString();
            }

            if (uuidStr != null) {
                out.writeUTF(uuidStr);
            } else {
                out.writeUTF("");
            }

            boolean sent = false;
            for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
                if (!server.getPlayers().isEmpty()) {
                    server.sendData("mc-data-bridge:main", out.toByteArray());
                    sent = true;
                }
            }

            if (sent) {
                sender.sendMessage(new net.md_5.bungee.api.chat.ComponentBuilder("Relayed unlock request for " + targetName + " to backend servers.").color(net.md_5.bungee.api.ChatColor.GREEN).create());
            } else {
                sender.sendMessage(new net.md_5.bungee.api.chat.ComponentBuilder("No online servers available to process the unlock request.").color(net.md_5.bungee.api.ChatColor.RED).create());
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BungeeUnlockCommand that = (BungeeUnlockCommand) o;
        return java.util.Objects.equals(plugin, that.plugin);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(super.hashCode(), plugin);
    }
}
