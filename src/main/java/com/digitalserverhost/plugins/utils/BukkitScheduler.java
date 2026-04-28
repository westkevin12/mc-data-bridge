package com.digitalserverhost.plugins.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class BukkitScheduler implements TaskScheduler {

    private final Map<UUID, BukkitTask> activeLockTasks = new ConcurrentHashMap<>();

    @Override
    public void runLater(Plugin plugin, Runnable runnable, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
    }

    @Override
    public void runAsync(Plugin plugin, Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    @Override
    public void runOnEntity(Plugin plugin, Player player, Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    @Override
    public void runOnEntityLater(Plugin plugin, Player player, Runnable runnable, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
    }

    @Override
    public void startHeartbeat(Plugin plugin, Player player, UUID uuid, String serverId, long heartbeatTicks, Consumer<UUID> onTick) {
        BukkitTask lockTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (player.isOnline()) {
                onTick.accept(uuid);
            } else {
                cancelHeartbeat(uuid);
            }
        }, heartbeatTicks, heartbeatTicks);
        activeLockTasks.put(uuid, lockTask);
    }

    @Override
    public void cancelHeartbeat(UUID uuid) {
        BukkitTask task = activeLockTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
