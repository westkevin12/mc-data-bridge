package com.digitalserverhost.plugins.proxy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

@Plugin(id = "mc-data-bridge", name = "mc-data-bridge", version = "2.0.3", description = "A data bridge for Minecraft servers.", authors = {
        "DigitalServerHost" })
public class VelocityMCDataBridge {

    private final ProxyServer server;
    private final Logger logger;
    private final MinecraftChannelIdentifier channel = MinecraftChannelIdentifier.create("mc-data-bridge", "main");

    @Inject
    public VelocityMCDataBridge(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(channel);
        server.getEventManager().register(this, new VelocityListener(this));
        logger.info("mc-data-bridge has been enabled on Velocity!");
    }

    public ProxyServer getServer() {
        return server;
    }

    public MinecraftChannelIdentifier getChannel() {
        return channel;
    }

    public Logger getLogger() {
        return logger;
    }
}
