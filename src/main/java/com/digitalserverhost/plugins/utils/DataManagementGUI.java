package com.digitalserverhost.plugins.utils;

import com.digitalserverhost.plugins.MCDataBridge;
import com.digitalserverhost.plugins.managers.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class DataManagementGUI implements Listener {

    private static final String TITLE_INSPECT = "§8Inspecting: §1";
    private static final String TITLE_INVENTORY_VIEW = "§8Inventory: §1";
    private static final String TITLE_INVENTORY_EDIT = "§cEdit Inventory: §1";
    private static final String TITLE_ENDERCHEST_VIEW = "§8EnderChest: §1";
    private static final String TITLE_ENDERCHEST_EDIT = "§cEdit EnderChest: §1";

    public enum ViewType { OVERVIEW, INVENTORY, ENDERCHEST }

    public static class DataBridgeGUIHolder implements InventoryHolder {
        private final UUID targetUuid;
        private final String targetName;
        private final ViewType viewType;
        private final boolean isEditable;
        private Inventory inventory;

        public DataBridgeGUIHolder(UUID targetUuid, String targetName, ViewType viewType, boolean isEditable) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
            this.viewType = viewType;
            this.isEditable = isEditable;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public UUID getTargetUuid() { return targetUuid; }
        public String getTargetName() { return targetName; }
        public ViewType getViewType() { return viewType; }
        public boolean isEditable() { return isEditable; }
    }

    private final MCDataBridge plugin;
    private final DatabaseManager databaseManager;

    public DataManagementGUI(MCDataBridge plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public void openPlayerInspector(Player admin, UUID targetUuid, String targetName, boolean isEditable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerData data = databaseManager.loadPlayerDataComponents(plugin, targetUuid, targetName);
                if (data != null) {
                    SchedulerUtils.runOnEntity(plugin, admin, () -> buildAndOpenGUI(admin, targetUuid, targetName, data, isEditable));
                } else {
                    MessageUtils.sendMessage(admin, "§cNo player data found for: " + targetName);
                }
            } catch (Exception e) {
                MessageUtils.sendMessage(admin, "§cError loading player data: " + e.getMessage());
            }
        });
    }

    public void openPlayerInspector(Player admin, UUID targetUuid, String targetName) {
        openPlayerInspector(admin, targetUuid, targetName, false);
    }

    public void openInventoryView(Player admin, UUID targetUuid, String targetName, boolean isEditable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerData data = databaseManager.loadPlayerDataComponents(plugin, targetUuid, targetName);
                if (data != null) {
                    SchedulerUtils.runOnEntity(plugin, admin, () -> buildAndOpenInventoryGUI(admin, targetUuid, targetName, data, isEditable));
                } else {
                    MessageUtils.sendMessage(admin, "§cNo inventory data found for: " + targetName);
                }
            } catch (Exception e) {
                MessageUtils.sendMessage(admin, "§cError loading inventory data: " + e.getMessage());
            }
        });
    }

    public void openInventoryView(Player admin, UUID targetUuid, String targetName) {
        openInventoryView(admin, targetUuid, targetName, false);
    }

    public void openEnderChestView(Player admin, UUID targetUuid, String targetName, boolean isEditable) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerData data = databaseManager.loadPlayerDataComponents(plugin, targetUuid, targetName);
                if (data != null) {
                    SchedulerUtils.runOnEntity(plugin, admin, () -> buildAndOpenEnderChestGUI(admin, targetUuid, targetName, data, isEditable));
                } else {
                    MessageUtils.sendMessage(admin, "§cNo ender chest data found for: " + targetName);
                }
            } catch (Exception e) {
                MessageUtils.sendMessage(admin, "§cError loading ender chest data: " + e.getMessage());
            }
        });
    }

    public void openEnderChestView(Player admin, UUID targetUuid, String targetName) {
        openEnderChestView(admin, targetUuid, targetName, false);
    }

    @SuppressWarnings("deprecation")
    private void buildAndOpenGUI(@NotNull Player admin, @NotNull UUID targetUuid, @NotNull String targetName, @NotNull PlayerData data, boolean isEditable) {
        String title = TITLE_INSPECT + targetName;
        DataBridgeGUIHolder holder = new DataBridgeGUIHolder(targetUuid, targetName, ViewType.OVERVIEW, isEditable);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        boolean canEdit = admin.hasPermission("databridge.inspect.edit") || admin.hasPermission("databridge.admin");

        inv.setItem(10, createInfoItem(Material.PLAYER_HEAD, "§b§lPlayer Stats",
                "§7Health: §f" + String.format("%.1f", data.getHealth()),
                "§7Level: §f" + data.getLevel(),
                "§7Exp: §f" + String.format("%.2f", data.getExp()),
                "§7Food: §f" + data.getFoodLevel()));

        int invSize = data.getInventoryContents() != null ? data.getInventoryContents().length : 0;
        List<String> invLore = new ArrayList<>();
        invLore.add("§7Items Tracked: §f" + invSize);
        invLore.add("§eLeft-Click: §fView Inventory (Read-Only)");
        if (canEdit) {
            invLore.add("§cRight-Click: §fEdit Inventory (Interactive)");
        }
        inv.setItem(12, createInfoItem(Material.CHEST, "§6§lInventory", invLore.toArray(new String[0])));

        int ecSize = data.getEnderChestContents() != null ? data.getEnderChestContents().length : 0;
        List<String> ecLore = new ArrayList<>();
        ecLore.add("§7Items Tracked: §f" + ecSize);
        ecLore.add("§eLeft-Click: §fView Ender Chest (Read-Only)");
        if (canEdit) {
            ecLore.add("§cRight-Click: §fEdit Ender Chest (Interactive)");
        }
        inv.setItem(13, createInfoItem(Material.ENDER_CHEST, "§d§lEnder Chest", ecLore.toArray(new String[0])));

        inv.setItem(14, createInfoItem(Material.COMPASS, "§e§lLast Known Location",
                "§7World: §f" + data.getWorld(),
                "§7X: §f" + (int) data.getX(),
                "§7Y: §f" + (int) data.getY(),
                "§7Z: §f" + (int) data.getZ()));

        inv.setItem(16, createInfoItem(Material.BOOK, "§5§lMetadata (PDC)",
                "§7Custom Data: §f" + (data.getPdcNBT() != null ? "Present" : "None")));

        admin.openInventory(inv);
    }

    @SuppressWarnings("deprecation")
    private void buildAndOpenInventoryGUI(@NotNull Player admin, @NotNull UUID targetUuid, @NotNull String targetName, @NotNull PlayerData data, boolean isEditable) {
        String title = (isEditable ? TITLE_INVENTORY_EDIT : TITLE_INVENTORY_VIEW) + targetName;
        DataBridgeGUIHolder holder = new DataBridgeGUIHolder(targetUuid, targetName, ViewType.INVENTORY, isEditable);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        ItemStack[] items = data.getInventoryContents();
        if (items != null) {
            for (int i = 0; i < Math.min(items.length, 36); i++) {
                if (items[i] != null && items[i].getType() != Material.AIR) {
                    inv.setItem(i, items[i]);
                }
            }
        }

        ItemStack[] armor = data.getArmorContents();
        if (armor != null) {
            for (int i = 0; i < Math.min(armor.length, 4); i++) {
                if (armor[i] != null && armor[i].getType() != Material.AIR) {
                    inv.setItem(36 + i, armor[i]);
                }
            }
        }

        inv.setItem(49, createInfoItem(Material.ARROW, "§c§lBack to Overview", "§7Click to return to overview"));
        admin.openInventory(inv);
    }

    @SuppressWarnings("deprecation")
    private void buildAndOpenEnderChestGUI(@NotNull Player admin, @NotNull UUID targetUuid, @NotNull String targetName, @NotNull PlayerData data, boolean isEditable) {
        String title = (isEditable ? TITLE_ENDERCHEST_EDIT : TITLE_ENDERCHEST_VIEW) + targetName;
        DataBridgeGUIHolder holder = new DataBridgeGUIHolder(targetUuid, targetName, ViewType.ENDERCHEST, isEditable);
        Inventory inv = Bukkit.createInventory(holder, 36, title);
        holder.setInventory(inv);

        ItemStack[] items = data.getEnderChestContents();
        if (items != null) {
            for (int i = 0; i < Math.min(items.length, 27); i++) {
                if (items[i] != null && items[i].getType() != Material.AIR) {
                    inv.setItem(i, items[i]);
                }
            }
        }

        inv.setItem(31, createInfoItem(Material.ARROW, "§c§lBack to Overview", "§7Click to return to overview"));
        admin.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (!(event.getInventory().getHolder() instanceof DataBridgeGUIHolder holder)) return;

        int rawSlot = event.getRawSlot();

        if (holder.getViewType() == ViewType.OVERVIEW) {
            handleOverviewClick(event, admin, holder, rawSlot);
        } else if (holder.getViewType() == ViewType.INVENTORY) {
            handleViewClick(event, admin, holder, rawSlot, 49);
        } else if (holder.getViewType() == ViewType.ENDERCHEST) {
            handleViewClick(event, admin, holder, rawSlot, 31);
        }
    }

    private void handleOverviewClick(InventoryClickEvent event, Player admin, DataBridgeGUIHolder holder, int rawSlot) {
        event.setCancelled(true);
        boolean isRightClick = event.isRightClick();
        boolean canEdit = admin.hasPermission("databridge.inspect.edit") || admin.hasPermission("databridge.admin");

        if (rawSlot == 12) {
            openTargetView(admin, holder, true, isRightClick, canEdit);
        } else if (rawSlot == 13) {
            openTargetView(admin, holder, false, isRightClick, canEdit);
        }
    }

    private void openTargetView(Player admin, DataBridgeGUIHolder holder, boolean isInventory, boolean isRightClick, boolean canEdit) {
        if (isRightClick) {
            if (canEdit) {
                if (isInventory) {
                    openInventoryView(admin, holder.getTargetUuid(), holder.getTargetName(), true);
                } else {
                    openEnderChestView(admin, holder.getTargetUuid(), holder.getTargetName(), true);
                }
            } else {
                String typeName = isInventory ? "player inventory" : "ender chest";
                MessageUtils.sendMessage(admin, "&cYou do not have permission to edit " + typeName + " (databridge.inspect.edit required).");
            }
        } else {
            if (isInventory) {
                openInventoryView(admin, holder.getTargetUuid(), holder.getTargetName(), false);
            } else {
                openEnderChestView(admin, holder.getTargetUuid(), holder.getTargetName(), false);
            }
        }
    }

    private void handleViewClick(InventoryClickEvent event, Player admin, DataBridgeGUIHolder holder, int rawSlot, int backSlot) {
        if (rawSlot == backSlot) {
            event.setCancelled(true);
            openPlayerInspector(admin, holder.getTargetUuid(), holder.getTargetName(), holder.isEditable());
        } else if (!holder.isEditable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player admin)) return;
        if (!(event.getInventory().getHolder() instanceof DataBridgeGUIHolder holder)) return;
        if (!holder.isEditable()) return;

        Inventory inv = event.getInventory();
        UUID targetUuid = holder.getTargetUuid();
        String targetName = holder.getTargetName();
        ViewType viewType = holder.getViewType();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveEditedInventory(admin, targetUuid, targetName, viewType, inv));
    }

    private void saveEditedInventory(Player admin, UUID targetUuid, String targetName, ViewType viewType, Inventory inv) {
        try {
            PlayerData data = databaseManager.loadPlayerDataComponents(plugin, targetUuid, targetName);
            if (data == null) data = new PlayerData();

            if (viewType == ViewType.INVENTORY) {
                ItemStack[] mainItems = new ItemStack[36];
                for (int i = 0; i < 36; i++) mainItems[i] = inv.getItem(i);

                ItemStack[] armorItems = new ItemStack[4];
                for (int i = 0; i < 4; i++) armorItems[i] = inv.getItem(36 + i);

                data.setInventoryContentsNBT(serializeItems(mainItems));
                data.setArmorContentsNBT(serializeItems(armorItems));
            } else if (viewType == ViewType.ENDERCHEST) {
                ItemStack[] ecItems = new ItemStack[27];
                for (int i = 0; i < 27; i++) ecItems[i] = inv.getItem(i);

                data.setEnderChestContentsNBT(serializeItems(ecItems));
            }

            boolean success = databaseManager.saveInventoryComponent(plugin, data, targetUuid);
            if (success) {
                MessageUtils.sendMessage(admin, "&a[DataBridge] Saved modified " + viewType.name().toLowerCase() + " for " + targetName + " to database.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save edited inventory for {0}: {1}", new Object[]{targetName, e.getMessage()});
            MessageUtils.sendMessage(admin, "&cError saving modified inventory: " + e.getMessage());
        }
    }

    private List<String> serializeItems(ItemStack[] items) {
        List<String> list = new ArrayList<>();
        com.google.gson.Gson gson = MCDataBridge.getGson();
        if (items != null) {
            for (ItemStack item : items) {
                if (item == null || item.getType() == Material.AIR) {
                    list.add(null);
                } else {
                    try {
                        PlayerData.SerializableItemStack serializable = new PlayerData.SerializableItemStack(item);
                        list.add(gson.toJson(serializable));
                    } catch (Exception _) {
                        list.add(null);
                    }
                }
            }
        }
        return list;
    }

    @SuppressWarnings("deprecation")
    private @NotNull ItemStack createInfoItem(@NotNull Material material, @NotNull String name, @NotNull String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> loreList = new ArrayList<>(List.of(lore));
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }
}
