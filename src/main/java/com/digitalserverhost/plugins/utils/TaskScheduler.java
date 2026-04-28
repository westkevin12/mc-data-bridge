package com.digitalserverhost.plugins.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface for platform-agnostic task scheduling.
 */
public interface TaskScheduler {
    
    void runLater(Plugin plugin, Runnable runnable, long ticks);
    
    void runAsync(Plugin plugin, Runnable runnable);
    
    void runOnEntity(Plugin plugin, Player player, Runnable runnable);
    
    void runOnEntityLater(Plugin plugin, Player player, Runnable runnable, long ticks);
    
    /**
     * Starts a heartbeat task that updates the player data lock.
     * 
     * @param plugin The plugin instance
     * @param player The player to track
     * @param uuid The player's UUID
     * @param serverId The ID of the current server
     * @param heartbeatTicks The interval between heartbeats in ticks
     * @param onTick A callback to execute every tick (typically databaseManager.updateLock)
     */
    void startHeartbeat(Plugin plugin, Player player, UUID uuid, String serverId, long heartbeatTicks, Consumer<UUID> onTick);
    
    void cancelHeartbeat(UUID uuid);
}
