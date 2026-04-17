package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManagementGUI {

    private final MCDataBridge plugin;
    private final DatabaseManager databaseManager;

    public DataManagementGUI(MCDataBridge plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void openPlayerInspector(Player admin, UUID targetUuid, String targetName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = databaseManager.getConnection()) {
                String query = "SELECT data FROM " + databaseManager.getTableName() + " WHERE uuid = ?";
                PreparedStatement statement = connection.prepareStatement(query);
                statement.setString(1, targetUuid.toString());
                ResultSet resultSet = statement.executeQuery();

                if (resultSet.next()) {
                    byte[] dataBytes = resultSet.getBytes("data");
                    String json = (dataBytes != null) ? new String(dataBytes, java.nio.charset.StandardCharsets.UTF_8)
                            : null;

                    if (json != null && !json.trim().isEmpty()) {
                        PlayerData data = fromJsonSafe(json, PlayerData.class);
                        java.util.Optional.ofNullable(data)
                                .ifPresentOrElse(
                                        d -> SchedulerUtils.runOnEntity(plugin, admin,
                                                () -> buildAndOpenGUI(admin, targetName, d)),
                                        () -> admin.sendMessage(LegacyComponentSerializer.legacySection()
                                                .deserialize("§cFailed to parse player data for: " + targetName)));
                    } else {
                        admin.sendMessage(LegacyComponentSerializer.legacySection()
                                .deserialize("§cNo data found for player: " + targetName));
                    }
                } else {
                    admin.sendMessage(LegacyComponentSerializer.legacySection()
                            .deserialize("§cPlayer " + targetName + " not found in database."));
                }
            } catch (Exception e) {
                admin.sendMessage(LegacyComponentSerializer.legacySection()
                        .deserialize("§cError loading player data: " + e.getMessage()));
            }
        });
    }

    private void buildAndOpenGUI(Player admin, String targetName, PlayerData data) {
        Component title = LegacyComponentSerializer.legacySection().deserialize("§8Inspecting: §1" + targetName);
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Stats Item
        inv.setItem(10, createInfoItem(Material.PLAYER_HEAD, "§b§lPlayer Stats",
                "§7Health: §f" + String.format("%.1f", data.getHealth()),
                "§7Level: §f" + data.getLevel(),
                "§7Exp: §f" + String.format("%.2f", data.getExp()),
                "§7Food: §f" + data.getFoodLevel()));

        // Inventory Overview
        int invSize = data.getInventoryContents() != null ? data.getInventoryContents().length : 0;
        inv.setItem(12, createInfoItem(Material.CHEST, "§6§lInventory",
                "§7Items Tracked: §f" + invSize));

        // Ender Chest Overview
        int ecSize = data.getEnderChestContents() != null ? data.getEnderChestContents().length : 0;
        inv.setItem(13, createInfoItem(Material.ENDER_CHEST, "§d§lEnder Chest",
                "§7Items Tracked: §f" + ecSize));

        // Location Info
        inv.setItem(14, createInfoItem(Material.COMPASS, "§e§lLast Known Location",
                "§7World: §f" + data.getWorld(),
                "§7X: §f" + (int) data.getX(),
                "§7Y: §f" + (int) data.getY(),
                "§7Z: §f" + (int) data.getZ()));

        // PDC/Metadata Info
        inv.setItem(16, createInfoItem(Material.BOOK, "§5§lMetadata (PDC)",
                "§7Custom Data: §f" + (data.getPdcNBT() != null ? "Present" : "None")));

        admin.openInventory(inv);
    }

    private ItemStack createInfoItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    private @Nullable <T> T fromJsonSafe(String json, Class<T> clazz) {
        return MCDataBridge.getGson().fromJson(json, clazz);
    }
}
