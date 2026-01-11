package de.jumpstone.plugins.proxy.bungee;

import de.jumpstone.plugins.managers.RedisManager;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class BungeeHMTSync extends Plugin {
    private RedisManager redisManager;
    private boolean redisEnabled = false;

    @Override
    public void onEnable() {
        loadConfiguration();
        initializeRedis();
        getLogger().info("HMT Sync has been enabled on BungeeCord!");
        getProxy().registerChannel("hmt-sync:main");
        getProxy().getPluginManager().registerListener(this, new BungeeListener(this));
    }

    private void loadConfiguration() {
        // Configuration loading will be implemented when config.yml is updated
    }

    private void initializeRedis() {
        // Redis initialization will be implemented when config.yml is updated
        // For now, Redis remains disabled
        redisEnabled = false;
        getLogger().info("[HMTSync-Redis] Redis integration not configured, using MySQL-only mode");
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }
}