package com.digitalserverhost.plugins.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SchedulerUtils {

    private static Boolean isFolia = null;

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

    public static void runLater(Plugin plugin, Runnable runnable, long ticks) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> runnable.run(), ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
        }
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, (t) -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runOnEntity(Plugin plugin, Player player, Runnable runnable) {
        if (isFolia()) {
            player.getScheduler().run(plugin, (t) -> runnable.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }
}
