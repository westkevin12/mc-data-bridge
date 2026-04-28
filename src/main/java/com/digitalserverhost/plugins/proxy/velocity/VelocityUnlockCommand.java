package com.digitalserverhost.plugins.proxy.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;


public class VelocityUnlockCommand implements SimpleCommand {

    private final VelocityMCDataBridge plugin;

    public VelocityUnlockCommand(VelocityMCDataBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2 || !args[0].equalsIgnoreCase("unlock")) {
            invocation.source().sendMessage(Component.text("Usage: /databridge unlock <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[1];
        
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("ForceUnlock");

        String resolvedUuid = plugin.getServer().getPlayer(targetName)
                .map(p -> p.getUniqueId().toString())
                .orElse(targetName);

        out.writeUTF(java.util.Objects.requireNonNull(resolvedUuid));

        boolean sent = false;
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(plugin.getChannel(), out.toByteArray());
                sent = true;
            }
        }

        if (sent) {
            invocation.source().sendMessage(Component.text("Relayed unlock request for " + targetName + " to backend servers.", NamedTextColor.GREEN));
        } else {
            invocation.source().sendMessage(Component.text("No online servers available to process the unlock request.", NamedTextColor.RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("databridge.admin");
    }
}
