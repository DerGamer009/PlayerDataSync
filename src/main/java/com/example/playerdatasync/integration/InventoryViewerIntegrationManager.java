package com.example.playerdatasync.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.example.playerdatasync.core.PlayerDataSync;
import com.example.playerdatasync.database.DatabaseManager;
import com.example.playerdatasync.managers.MessageManager;
import com.example.playerdatasync.utils.OfflinePlayerData;

/**
 * Provides compatibility with inventory viewing plugins such as InvSee++ and OpenInv.
 * The manager intercepts their commands and serves data directly from the
 * PlayerDataSync database so that editing inventories works across servers.
 */
public class InventoryViewerIntegrationManager implements Listener {
    private static final Set<String> INVSEE_COMMANDS = new HashSet<>(Arrays.asList("invsee", "isee"));
    private static final Set<String> INVSEE_ENDER_COMMANDS = new HashSet<>(Arrays.asList("endersee", "enderinv"));
    private static final Set<String> OPENINV_COMMANDS = new HashSet<>(Arrays.asList("openinv", "oi"));
    private static final Set<String> OPENINV_ENDER_COMMANDS = new HashSet<>(Arrays.asList("openender", "enderchest", "openec", "ec"));

    private final PlayerDataSync plugin;
    private final DatabaseManager databaseManager;
    private final MessageManager messageManager;
    private boolean invSeeEnabled;
    private boolean openInvEnabled;
    private final ItemStack fillerItem;

    public InventoryViewerIntegrationManager(PlayerDataSync plugin, DatabaseManager databaseManager,
        boolean invSeeEnabled, boolean openInvEnabled) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.messageManager = plugin.getMessageManager();
        this.invSeeEnabled = invSeeEnabled;
        this.openInvEnabled = openInvEnabled;
        this.fillerItem = createFillerItem();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        detectExternalPlugins();
    }

    private void detectExternalPlugins() {
        if (invSeeEnabled && plugin.getServer().getPluginManager().getPlugin("InvSee++") != null) {
            plugin.getLogger().info("InvSee++ detected. Routing inventory viewing through PlayerDataSync storage.");
        }
        if (openInvEnabled && plugin.getServer().getPluginManager().getPlugin("OpenInv") != null) {
            plugin.getLogger().info("OpenInv detected. Routing inventory viewing through PlayerDataSync storage.");
        }
    }

    public void updateSettings(boolean invSeeEnabled, boolean openInvEnabled) {
        this.invSeeEnabled = invSeeEnabled;
        this.openInvEnabled = openInvEnabled;
    }

    public void shutdown() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryCommand(PlayerCommandPreprocessEvent event) {
        if (!invSeeEnabled && !openInvEnabled) {
            return;
        }

        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.isEmpty()) {
            return;
        }

        String trimmed = rawMessage.trim();
        if (!trimmed.startsWith("/")) {
            return;
        }

        String[] parts = trimmed.substring(1).split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String baseCommand = parts[0].toLowerCase(Locale.ROOT);
        boolean inventoryCommand = (invSeeEnabled && INVSEE_COMMANDS.contains(baseCommand))
            || (openInvEnabled && OPENINV_COMMANDS.contains(baseCommand));
        boolean enderCommand = (invSeeEnabled && INVSEE_ENDER_COMMANDS.contains(baseCommand))
            || (openInvEnabled && OPENINV_ENDER_COMMANDS.contains(baseCommand));

        if (!inventoryCommand && !enderCommand) {
            return;
        }

        Player player = event.getPlayer();

        if (inventoryCommand && !player.hasPermission("playerdatasync.integration.invsee")) {
            player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
            event.setCancelled(true);
            return;
        }

        if (enderCommand && !player.hasPermission("playerdatasync.integration.enderchest")) {
            player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
            event.setCancelled(true);
            return;
        }

        if (parts.length < 2) {
            String usageKey = enderCommand ? "inventory_view_usage_ender" : "inventory_view_usage_inventory";
            player.sendMessage(messageManager.get("prefix") + " " + messageManager.get(usageKey));
            event.setCancelled(true);
            return;
        }

        String targetName = parts[1];
        event.setCancelled(true);
        handleInventoryRequest(player, targetName, enderCommand);
    }

    private void handleInventoryRequest(Player viewer, String targetName, boolean enderChest) {
        // Use UUID-based lookup instead of deprecated getOfflinePlayer(String)
        OfflinePlayer offline = null;
        try {
            // Try to find online player first
            Player onlinePlayer = Bukkit.getPlayer(targetName);
            if (onlinePlayer != null) {
                offline = onlinePlayer;
            } else {
                // For offline players, we still need to use deprecated method for compatibility
                // This is necessary for 1.8-1.16 compatibility
                @SuppressWarnings("deprecation")
                OfflinePlayer tempPlayer = Bukkit.getOfflinePlayer(targetName);
                offline = tempPlayer;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not get offline player for " + targetName + ": " + e.getMessage());
            return;
        }
        
        if (offline == null) {
            return;
        }
        UUID targetUuid = offline != null ? offline.getUniqueId() : null;
        String displayName = offline != null && offline.getName() != null ? offline.getName() : targetName;

        viewer.sendMessage(messageManager.get("prefix") + " "
            + messageManager.get("inventory_view_loading").replace("{player}", displayName));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayerData data;
            try {
                data = databaseManager.loadOfflinePlayerData(targetUuid, displayName);
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to load offline data for " + displayName + ": " + ex.getMessage());
                data = null;
            }

            if (data == null) {
                String message = messageManager.get("prefix") + " "
                    + messageManager.get("inventory_view_open_failed").replace("{player}", displayName);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer.isOnline()) {
                        viewer.sendMessage(message);
                    }
                });
                return;
            }

            OfflinePlayerData finalData = data;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }

                if (!finalData.existsInDatabase()) {
                    viewer.sendMessage(messageManager.get("prefix") + " "
                        + messageManager.get("inventory_view_no_data").replace("{player}", finalData.getDisplayName()));
                }

                if (enderChest) {
                    openEnderChestInventory(viewer, finalData);
                } else {
                    openMainInventory(viewer, finalData);
                }
            });
        });
    }

    private void openMainInventory(Player viewer, OfflinePlayerData data) {
        OfflineInventoryHolder holder = new OfflineInventoryHolder(data, false);
        Inventory inventory = Bukkit.createInventory(holder, 45,
            messageManager.get("inventory_view_title_inventory").replace("{player}", data.getDisplayName()));
        holder.setInventory(inventory);

        ItemStack[] main = data.getInventoryContents();
        for (int slot = 0; slot < 36; slot++) {
            inventory.setItem(slot, slot < main.length ? main[slot] : null);
        }

        ItemStack[] armor = data.getArmorContents();
        inventory.setItem(36, armor.length > 3 ? armor[3] : null); // Helmet position
        inventory.setItem(37, armor.length > 2 ? armor[2] : null); // Chestplate
        inventory.setItem(38, armor.length > 1 ? armor[1] : null); // Leggings
        inventory.setItem(39, armor.length > 0 ? armor[0] : null); // Boots

        inventory.setItem(40, data.getOffhandItem());

        for (int slot = 41; slot < 45; slot++) {
            inventory.setItem(slot, fillerItem.clone());
        }

        viewer.openInventory(inventory);
    }

    private void openEnderChestInventory(Player viewer, OfflinePlayerData data) {
        OfflineInventoryHolder holder = new OfflineInventoryHolder(data, true);
        Inventory inventory = Bukkit.createInventory(holder, 27,
            messageManager.get("inventory_view_title_ender").replace("{player}", data.getDisplayName()));
        holder.setInventory(inventory);

        ItemStack[] contents = data.getEnderChestContents();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, i < contents.length ? contents[i] : null);
        }

        viewer.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof OfflineInventoryHolder holder)) {
            return;
        }

        if (!holder.isEnderChest()) {
            int rawSlot = event.getRawSlot();
            int topSize = event.getView().getTopInventory().getSize();
            if (rawSlot < topSize && rawSlot >= 41) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof OfflineInventoryHolder holder)) {
            return;
        }

        if (holder.isEnderChest()) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot >= 41) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof OfflineInventoryHolder holder)) {
            return;
        }

        Inventory inventory = event.getInventory();
        OfflinePlayerData data = holder.getData();
        UUID viewerId = ((Player) event.getPlayer()).getUniqueId();

        if (holder.isEnderChest()) {
            ItemStack[] contents = Arrays.copyOf(inventory.getContents(), inventory.getSize());
            data.setEnderChestContents(contents);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = databaseManager.saveOfflineEnderChestData(data);
                if (!success) {
                    notifySaveFailure(viewerId, data.getDisplayName());
                }
            });
        } else {
            ItemStack[] main = new ItemStack[36];
            for (int i = 0; i < 36; i++) {
                main[i] = inventory.getItem(i);
            }

            ItemStack[] armor = new ItemStack[4];
            armor[3] = inventory.getItem(36); // helmet
            armor[2] = inventory.getItem(37); // chestplate
            armor[1] = inventory.getItem(38); // leggings
            armor[0] = inventory.getItem(39); // boots
            ItemStack offhand = inventory.getItem(40);

            data.setInventoryContents(main);
            data.setArmorContents(armor);
            data.setOffhandItem(offhand);

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                boolean success = databaseManager.saveOfflineInventoryData(data);
                if (!success) {
                    notifySaveFailure(viewerId, data.getDisplayName());
                }
            });
        }
    }

    private void notifySaveFailure(UUID viewerId, String playerName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                viewer.sendMessage(messageManager.get("prefix") + " "
                    + messageManager.get("inventory_view_save_failed").replace("{player}", playerName));
            }
        });
    }

    private ItemStack createFillerItem() {
        Material paneMaterial;
        
        // GRAY_STAINED_GLASS_PANE only exists in 1.13+
        // For 1.8-1.12, use STAINED_GLASS_PANE with durability/data value 7 (gray)
        try {
            if (com.example.playerdatasync.utils.VersionCompatibility.isAtLeast(1, 13, 0)) {
                paneMaterial = Material.GRAY_STAINED_GLASS_PANE;
            } else {
                // For 1.8-1.12, use STAINED_GLASS_PANE
                paneMaterial = Material.valueOf("STAINED_GLASS_PANE");
            }
        } catch (IllegalArgumentException e) {
            // Fallback if STAINED_GLASS_PANE doesn't exist (shouldn't happen, but be safe)
            paneMaterial = Material.GLASS_PANE;
            plugin.getLogger().warning("Could not find STAINED_GLASS_PANE, using GLASS_PANE as fallback");
        }
        
        ItemStack pane = new ItemStack(paneMaterial);
        
        // Set durability/data value for 1.8-1.12 (7 = gray color)
        if (!com.example.playerdatasync.utils.VersionCompatibility.isAtLeast(1, 13, 0)) {
            try {
                // setDurability is deprecated but necessary for 1.8-1.12 compatibility
                pane.setDurability((short) 7); // Gray color
            } catch (Exception e) {
                plugin.getLogger().warning("Could not set glass pane color for filler item: " + e.getMessage());
                // Continue with default material
            }
        }
        
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private static final class OfflineInventoryHolder implements InventoryHolder {
        private final OfflinePlayerData data;
        private final boolean enderChest;
        private Inventory inventory;

        private OfflineInventoryHolder(OfflinePlayerData data, boolean enderChest) {
            this.data = data;
            this.enderChest = enderChest;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private OfflinePlayerData getData() {
            return data;
        }

        private boolean isEnderChest() {
            return enderChest;
        }
    }
}
