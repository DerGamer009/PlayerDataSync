package com.example.playerdatasync.managers;

import com.example.playerdatasync.core.PlayerDataSync;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MenuManager implements Listener {

    private final PlayerDataSync plugin;
    private final String menuTitle = "§8PlayerDataSync Management";

    public MenuManager(PlayerDataSync plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, menuTitle);

        // Sync Options (Slots 0-15)
        addOptionItem(inv, 0, "Coordinates", plugin.isSyncCoordinates(), getMaterial("COMPASS", "COMPASS"));
        addOptionItem(inv, 1, "Position", plugin.isSyncPosition(), getMaterial("MAP", "MAP"));
        addOptionItem(inv, 2, "XP", plugin.isSyncXp(), getMaterial("EXPERIENCE_BOTTLE", "EXP_BOTTLE"));
        addOptionItem(inv, 3, "Gamemode", plugin.isSyncGamemode(), getMaterial("GRASS_BLOCK", "GRASS"));
        addOptionItem(inv, 4, "Inventory", plugin.isSyncInventory(), getMaterial("CHEST", "CHEST"));
        addOptionItem(inv, 5, "Enderchest", plugin.isSyncEnderchest(), getMaterial("ENDER_CHEST", "ENDER_CHEST"));
        addOptionItem(inv, 6, "Armor", plugin.isSyncArmor(), getMaterial("IRON_CHESTPLATE", "IRON_CHESTPLATE"));
        addOptionItem(inv, 7, "Offhand", plugin.isSyncOffhand(), getMaterial("SHIELD", "IRON_INGOT"));
        addOptionItem(inv, 8, "Health", plugin.isSyncHealth(), getMaterial("APPLE", "APPLE"));
        addOptionItem(inv, 9, "Hunger", plugin.isSyncHunger(), getMaterial("COOKED_BEEF", "COOKED_BEEF"));
        addOptionItem(inv, 10, "Effects", plugin.isSyncEffects(), getMaterial("POTION", "POTION"));
        addOptionItem(inv, 11, "Achievements", plugin.isSyncAchievements(), getMaterial("EMERALD", "EMERALD"));
        addOptionItem(inv, 12, "Statistics", plugin.isSyncStatistics(), getMaterial("WRITABLE_BOOK", "BOOK_AND_QUILL"));
        addOptionItem(inv, 13, "Attributes", plugin.isSyncAttributes(), getMaterial("DIAMOND_SWORD", "DIAMOND_SWORD"));
        addOptionItem(inv, 14, "Permissions", plugin.isSyncPermissions(), getMaterial("PAPER", "PAPER"));
        addOptionItem(inv, 15, "Economy", plugin.isSyncEconomy(), getMaterial("GOLD_INGOT", "GOLD_INGOT"));

        // Management Actions
        addMaintenanceItem(inv, 31);
        addInfoItem(inv, 35);

        player.openInventory(inv);
    }

    private void addOptionItem(Inventory inv, int slot, String name, boolean enabled, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bSync: " + name);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Status: " + (enabled ? "§aEnabled" : "§cDisabled"));
            lore.add("");
            lore.add("§eClick to toggle");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private void addMaintenanceItem(Inventory inv, int slot) {
        boolean active = plugin.isMaintenanceMode();
        ItemStack item = new ItemStack(active ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lMaintenance Mode");
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Current Status: " + (active ? "§c§lACTIVE" : "§aInactive"));
            lore.add("§7When active, all data syncing is paused.");
            lore.add("");
            lore.add("§eClick to " + (active ? "disable" : "enable"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private void addInfoItem(Inventory inv, int slot) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Plugin Information");
            List<String> lore = new ArrayList<>();
            lore.add("§7Version: §f" + plugin.getDescription().getVersion());
            lore.add("§7Author: §f" + String.join(", ", plugin.getDescription().getAuthors()));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(menuTitle)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String displayName = meta.getDisplayName();

        if (displayName.startsWith("§bSync: ")) {
            String option = displayName.substring(8).toLowerCase();
            toggleSyncOption(option);
            openMenu(player); // Refresh
        } else if (displayName.equals("§c§lMaintenance Mode")) {
            plugin.setMaintenanceMode(!plugin.isMaintenanceMode());
            openMenu(player); // Refresh
        }
    }

    private void toggleSyncOption(String option) {
        switch (option) {
            case "coordinates": plugin.setSyncCoordinates(!plugin.isSyncCoordinates()); break;
            case "position": plugin.setSyncPosition(!plugin.isSyncPosition()); break;
            case "xp": plugin.setSyncXp(!plugin.isSyncXp()); break;
            case "gamemode": plugin.setSyncGamemode(!plugin.isSyncGamemode()); break;
            case "inventory": plugin.setSyncInventory(!plugin.isSyncInventory()); break;
            case "enderchest": plugin.setSyncEnderchest(!plugin.isSyncEnderchest()); break;
            case "armor": plugin.setSyncArmor(!plugin.isSyncArmor()); break;
            case "offhand": plugin.setSyncOffhand(!plugin.isSyncOffhand()); break;
            case "health": plugin.setSyncHealth(!plugin.isSyncHealth()); break;
            case "hunger": plugin.setSyncHunger(!plugin.isSyncHunger()); break;
            case "effects": plugin.setSyncEffects(!plugin.isSyncEffects()); break;
            case "achievements": plugin.setSyncAchievements(!plugin.isSyncAchievements()); break;
            case "statistics": plugin.setSyncStatistics(!plugin.isSyncStatistics()); break;
            case "attributes": plugin.setSyncAttributes(!plugin.isSyncAttributes()); break;
            case "permissions": plugin.setSyncPermissions(!plugin.isSyncPermissions()); break;
            case "economy": plugin.setSyncEconomy(!plugin.isSyncEconomy()); break;
        }
    }
    private Material getMaterial(String modern, String legacy) {
        Material m = Material.getMaterial(modern);
        if (m == null) m = Material.getMaterial(legacy);
        return m != null ? m : Material.STONE;
    }
}
