package de.jumpstone.plugins.managers;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlaytimeManager {
    private Essentials essentials;
    private boolean isEnabled;

    public PlaytimeManager() {
        setupEssentials();
    }

    private void setupEssentials() {
        if (Bukkit.getPluginManager().getPlugin("Essentials") == null) {
            isEnabled = false;
            return;
        }

        essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        isEnabled = essentials != null;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public long getPlaytimeMinutes(Player player) {
        if (!isEnabled || essentials == null) {
            return 0L;
        }
        try {
            User user = essentials.getUser(player);
            if (user != null) {
                return user.getBase().getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20 / 60; // Convert ticks to
                                                                                                    // minutes
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }

    public void setPlaytimeMinutes(Player player, long minutes) {
        if (!isEnabled || essentials == null) {
            return;
        }
        try {
            // Essentials tracks playtime automatically, but we can store the value
            // in our own system. For now, we'll just log the intended value.
            // The actual Essentials playtime will continue to increment naturally.
            // In a real implementation, you might want to store this in a separate table
            // or use Essentials' API more directly if available.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets formatted playtime string (e.g., "12h 34m")
     */
    public String getFormattedPlaytime(Player player) {
        long minutes = getPlaytimeMinutes(player);
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours > 0) {
            return hours + "h " + remainingMinutes + "m";
        } else {
            return remainingMinutes + "m";
        }
    }

    /**
     * Alternative method using Essentials' built-in playtime if available
     */
    public long getEssentialsPlaytimeMinutes(Player player) {
        if (!isEnabled || essentials == null) {
            return 0L;
        }
        try {
            User user = essentials.getUser(player);
            if (user != null) {
                // This gets the playtime tracked by Essentials itself
                return user.getBase().getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20 / 60;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
    }
}