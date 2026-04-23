package com.digitalserverhost.plugins.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SchedulerUtils {

    private static Boolean isFolia = null;
    private static Boolean isPaper = null;
    private static TaskScheduler scheduler = null;

    private static PlatformBridge bridge = null;

    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                org.bukkit.Bukkit.class.getMethod("getAsyncScheduler");
                isFolia = true;
            } catch (NoSuchMethodException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }

    public static boolean isPaper() {
        if (isPaper == null) {
            try {
                Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
                isPaper = true;
            } catch (ClassNotFoundException e) {
                isPaper = false;
            }
        }
        return isPaper;
    }

    public static TaskScheduler getScheduler() {
        if (scheduler == null) {
            if (isFolia()) {
                try {
                    scheduler = (TaskScheduler) Class.forName("com.digitalserverhost.plugins.utils.FoliaScheduler")
                            .getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    org.bukkit.Bukkit.getLogger().severe("[mc-data-bridge] Failed to load FoliaScheduler: " + e.getMessage());
                    scheduler = new BukkitScheduler();
                }
            } else {
                scheduler = new BukkitScheduler();
            }
        }
        return scheduler;
    }

    public static PlatformBridge getBridge() {
        if (bridge == null) {
            if (isPaper()) {
                try {
                    bridge = (PlatformBridge) Class.forName("com.digitalserverhost.plugins.utils.PaperBridge")
                            .getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    bridge = new BukkitBridge();
                }
            } else {
                bridge = new BukkitBridge();
            }
        }
        return bridge;
    }

    public static void runLater(Plugin plugin, Runnable runnable, long ticks) {
        getScheduler().runLater(plugin, runnable, ticks);
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        getScheduler().runAsync(plugin, runnable);
    }

    public static void runOnEntity(Plugin plugin, Player player, Runnable runnable) {
        getScheduler().runOnEntity(plugin, player, runnable);
    }

    public static void runOnEntityLater(Plugin plugin, Player player, Runnable runnable, long ticks) {
        getScheduler().runOnEntityLater(plugin, player, runnable, ticks);
    }
}
