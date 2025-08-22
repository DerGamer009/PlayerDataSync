package com.example.playerdatasync.commands;

import com.example.playerdatasync.PlayerDataSync;
import com.example.playerdatasync.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Status command for checking PlayerDataSync status and statistics
 */
public class StatusCommand implements CommandExecutor, TabCompleter {
    private final PlayerDataSync plugin;
    private final MessageManager messageManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public StatusCommand(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender, "playerdatasync.status")) return true;
        
        if (args.length == 0) {
            return showGeneralStatus(sender);
        } else {
            String playerName = args[0];
            return showPlayerStatus(sender, playerName);
        }
    }
    
    /**
     * Show general plugin status
     */
    private boolean showGeneralStatus(CommandSender sender) {
        sender.sendMessage(messageManager.get("status_header"));
        
        // Version information
        sender.sendMessage(messageManager.get("status_version")
            .replace("{version}", plugin.getDescription().getVersion()));
        
        // Database status
        String dbType = plugin.getConfig().getString("database.type", "unknown");
        String dbStatus = isDatabaseConnected() ? "§aConnected" : "§cDisconnected";
        sender.sendMessage(messageManager.get("status_database")
            .replace("{type}", dbType.toUpperCase())
            .replace("{status}", dbStatus));
        
        // Connected players
        int playerCount = Bukkit.getOnlinePlayers().size();
        sender.sendMessage(messageManager.get("status_connected_players")
            .replace("{count}", String.valueOf(playerCount)));
        
        // Total records (placeholder - would need database query)
        sender.sendMessage(messageManager.get("status_total_records")
            .replace("{count}", "Unknown"));
        
        // Last backup (placeholder)
        sender.sendMessage(messageManager.get("status_last_backup")
            .replace("{time}", "Never"));
        
        // Autosave status
        boolean autosaveEnabled = plugin.getConfig().getBoolean("autosave.enabled", true);
        int interval = plugin.getConfig().getInt("autosave.interval", 5);
        String autosaveStatus = autosaveEnabled ? "§aEnabled" : "§cDisabled";
        sender.sendMessage(messageManager.get("status_autosave")
            .replace("{status}", autosaveStatus)
            .replace("{interval}", String.valueOf(interval)));
        
        // Performance metrics
        showPerformanceMetrics(sender);
        
        // Integration status
        showIntegrationStatus(sender);
        
        sender.sendMessage(messageManager.get("status_footer"));
        return true;
    }
    
    /**
     * Show specific player status
     */
    private boolean showPlayerStatus(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        
        if (target == null) {
            // Check if we have permission to view offline players
            if (!hasPermission(sender, "playerdatasync.status.others")) {
                sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
                return true;
            }
            
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("player_not_found").replace("{player}", playerName));
            return true;
        }
        
        // Check permission for viewing other players
        if (!target.equals(sender) && !hasPermission(sender, "playerdatasync.status.others")) {
            sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
            return true;
        }
        
        showDetailedPlayerStatus(sender, target);
        return true;
    }
    
    /**
     * Show detailed player status
     */
    private void showDetailedPlayerStatus(CommandSender sender, Player player) {
        sender.sendMessage("§8§m----------§r §bPlayer Status: " + player.getName() + " §8§m----------");
        
        // Basic information
        sender.sendMessage("§7UUID: §f" + player.getUniqueId().toString());
        sender.sendMessage("§7Online: §aYes");
        sender.sendMessage("§7World: §f" + player.getWorld().getName());
        
        // Location
        sender.sendMessage("§7Location: §f" + 
            String.format("%.1f, %.1f, %.1f", 
                player.getLocation().getX(), 
                player.getLocation().getY(), 
                player.getLocation().getZ()));
        
        // Health and hunger
        sender.sendMessage("§7Health: §f" + 
            String.format("%.1f/%.1f", player.getHealth(), player.getMaxHealth()));
        sender.sendMessage("§7Food Level: §f" + player.getFoodLevel() + "/20");
        sender.sendMessage("§7Saturation: §f" + String.format("%.1f", player.getSaturation()));
        
        // Experience
        sender.sendMessage("§7XP Level: §f" + player.getLevel());
        sender.sendMessage("§7Total XP: §f" + player.getTotalExperience());
        
        // Game mode
        sender.sendMessage("§7Game Mode: §f" + player.getGameMode().toString());
        
        // Flying status
        if (player.getAllowFlight()) {
            sender.sendMessage("§7Flying: §f" + (player.isFlying() ? "Yes" : "No") + " (Allowed)");
        } else {
            sender.sendMessage("§7Flying: §cNot Allowed");
        }
        
        // Inventory items
        int inventoryItems = 0;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (player.getInventory().getItem(i) != null) {
                inventoryItems++;
            }
        }
        sender.sendMessage("§7Inventory Items: §f" + inventoryItems + "/36");
        
        // Ender chest items
        int enderChestItems = 0;
        for (int i = 0; i < player.getEnderChest().getSize(); i++) {
            if (player.getEnderChest().getItem(i) != null) {
                enderChestItems++;
            }
        }
        sender.sendMessage("§7Ender Chest Items: §f" + enderChestItems + "/27");
        
        // Effects
        int activeEffects = player.getActivePotionEffects().size();
        sender.sendMessage("§7Active Effects: §f" + activeEffects);
        
        // Sync status for this player
        showPlayerSyncStatus(sender, player);
        
        sender.sendMessage("§8§m----------------------------------------");
    }
    
    /**
     * Show sync status for specific player
     */
    private void showPlayerSyncStatus(CommandSender sender, Player player) {
        sender.sendMessage("§7§lSync Status:");
        
        // Check which features are enabled and show status
        if (plugin.isSyncCoordinates()) {
            sender.sendMessage("  §7Coordinates: §aTracked");
        }
        if (plugin.isSyncInventory()) {
            sender.sendMessage("  §7Inventory: §aTracked");
        }
        if (plugin.isSyncHealth()) {
            sender.sendMessage("  §7Health: §aTracked");
        }
        if (plugin.isSyncXp()) {
            sender.sendMessage("  §7Experience: §aTracked");
        }
        if (plugin.isSyncGamemode()) {
            sender.sendMessage("  §7Game Mode: §aTracked");
        }
        
        // Last sync time (placeholder - would need to be stored)
        sender.sendMessage("  §7Last Sync: §fOn Join");
    }
    
    /**
     * Show performance metrics
     */
    private void showPerformanceMetrics(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("logging.log_performance", false)) {
            return;
        }
        
        sender.sendMessage("§7§lPerformance:");
        
        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        sender.sendMessage("  §7Memory Usage: §f" + usedMemory + "MB / " + totalMemory + "MB");
        
        // Database metrics (placeholder)
        sender.sendMessage("  §7Database Queries: §fN/A");
        sender.sendMessage("  §7Cache Hit Rate: §fN/A");
        sender.sendMessage("  §7Avg Response Time: §fN/A");
    }
    
    /**
     * Show integration status
     */
    private void showIntegrationStatus(CommandSender sender) {
        sender.sendMessage("§7§lIntegrations:");
        
        // Vault
        boolean vaultEnabled = plugin.getServer().getPluginManager().getPlugin("Vault") != null;
        sender.sendMessage("  §7Vault: " + (vaultEnabled ? "§aAvailable" : "§cNot Found"));
        
        // LuckPerms
        boolean luckPermsEnabled = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
        sender.sendMessage("  §7LuckPerms: " + (luckPermsEnabled ? "§aAvailable" : "§cNot Found"));
        
        // PlaceholderAPI
        boolean papiEnabled = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        sender.sendMessage("  §7PlaceholderAPI: " + (papiEnabled ? "§aAvailable" : "§cNot Found"));
        
        // BungeeCord
        boolean bungeeCord = plugin.getConfig().getBoolean("integrations.bungeecord", false);
        sender.sendMessage("  §7BungeeCord Mode: " + (bungeeCord ? "§aEnabled" : "§cDisabled"));
    }
    
    /**
     * Check if database is connected
     */
    private boolean isDatabaseConnected() {
        try {
            return plugin.getConnection() != null && !plugin.getConnection().isClosed();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if sender has permission
     */
    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("playerdatasync.admin.*")) {
            return true;
        }
        sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
        return false;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Return online player names
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}
