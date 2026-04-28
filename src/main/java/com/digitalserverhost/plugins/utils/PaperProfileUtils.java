package com.digitalserverhost.plugins.utils;

import org.bukkit.Bukkit;
import java.util.UUID;

/**
 * Isolated class for Paper-specific profile API to prevent NoClassDefFoundError on Spigot.
 */
public class PaperProfileUtils {
    
    public static UUID resolveUuid(String targetName) {
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(targetName);
            profile.complete();
            return profile.getId();
        } catch (Exception e) {
            return null;
        }
    }
}
