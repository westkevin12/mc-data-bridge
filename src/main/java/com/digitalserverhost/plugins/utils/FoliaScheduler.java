package com.digitalserverhost.plugins.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class FoliaScheduler implements TaskScheduler {

    private final Map<UUID, ScheduledTask> activeLockTasks = new ConcurrentHashMap<>();

    @Override
    public void runLater(Plugin plugin, Runnable runnable, long ticks) {
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> runnable.run(), ticks);
    }

    @Override
    public void runAsync(Plugin plugin, Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, (t) -> runnable.run());
    }

    @Override
    public void runOnEntity(Plugin plugin, Player player, Runnable runnable) {
        player.getScheduler().run(plugin, (t) -> runnable.run(), null);
    }

    @Override
    public void runOnEntityLater(Plugin plugin, Player player, Runnable runnable, long ticks) {
        player.getScheduler().runDelayed(plugin, (t) -> runnable.run(), null, ticks);
    }

    @Override
    public void startHeartbeat(Plugin plugin, Player player, UUID uuid, String serverId, long heartbeatTicks, Consumer<UUID> onTick) {
        // Folia runAtFixedRate uses absolute time (ms), not ticks.
        // Convert ticks to ms (20 ticks = 1000ms)
        long delayMs = heartbeatTicks * 50;
        ScheduledTask task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (t) -> {
            if (player.isOnline()) {
                onTick.accept(uuid);
            } else {
                t.cancel();
                activeLockTasks.remove(uuid);
            }
        }, delayMs, delayMs, TimeUnit.MILLISECONDS);
        activeLockTasks.put(uuid, task);
    }

    @Override
    public void cancelHeartbeat(UUID uuid) {
        ScheduledTask task = activeLockTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }
}
