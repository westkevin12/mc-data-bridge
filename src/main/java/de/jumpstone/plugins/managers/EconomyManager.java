package de.jumpstone.plugins.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;

public class EconomyManager {
    private Economy economy;
    private boolean isEnabled;

    public EconomyManager() {
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            isEnabled = false;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            isEnabled = false;
            return;
        }

        economy = rsp.getProvider();
        isEnabled = true;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public double getBalance(Player player) {
        if (!isEnabled || economy == null) {
            return 0.0;
        }
        try {
            return economy.getBalance(player);
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    public void setBalance(Player player, double balance) {
        if (!isEnabled || economy == null) {
            return;
        }
        try {
            double currentBalance = economy.getBalance(player);
            double difference = balance - currentBalance;

            if (difference > 0) {
                economy.depositPlayer(player, difference);
            } else if (difference < 0) {
                economy.withdrawPlayer(player, Math.abs(difference));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String format(double amount) {
        if (!isEnabled || economy == null) {
            return String.valueOf(amount);
        }
        try {
            return economy.format(amount);
        } catch (Exception e) {
            return String.valueOf(amount);
        }
    }
}