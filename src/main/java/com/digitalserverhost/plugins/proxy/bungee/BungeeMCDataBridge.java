package com.digitalserverhost.plugins.proxy.bungee;

import net.md_5.bungee.api.plugin.Plugin;

public class BungeeMCDataBridge extends Plugin {

    @Override
    public void onEnable() {
        getLogger().info("mc-data-bridge has been enabled on BungeeCord!");
        getProxy().registerChannel("mc-data-bridge:main");
        getProxy().getPluginManager().registerListener(this, new BungeeListener(this));
    }
}
