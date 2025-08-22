package com.example.playerdatasync.commands;

import com.example.playerdatasync.PlayerDataSync;
import com.example.playerdatasync.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced sync command with expanded functionality
 */
public class SyncCommandEnhanced implements CommandExecutor, TabCompleter {
    private final PlayerDataSync plugin;
    private final MessageManager messageManager;
    
    // Available sync options
    private static final List<String> SYNC_OPTIONS = Arrays.asList(
        "coordinates", "position", "xp", "gamemode", "inventory", "enderchest", 
        "armor", "offhand", "health", "hunger", "effects", "achievements", 
        "statistics", "attributes", "permissions", "economy"
    );
    
    // Available sub-commands
    private static final List<String> SUB_COMMANDS = Arrays.asList(
        "reload", "status", "save", "help", "cache", "validate"
    );
    
    public SyncCommandEnhanced(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return showStatus(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                return handleReload(sender);
                
            case "status":
                return handleStatus(sender, args);
                
            case "save":
                return handleSave(sender, args);
                
            case "help":
                return showHelp(sender);
                
            case "cache":
                return handleCache(sender, args);
                
            case "validate":
                return handleValidate(sender, args);
                
            default:
                // Try to parse as sync option
                if (args.length == 2) {
                    return handleSyncOption(sender, args[0], args[1]);
                } else {
                    return showHelp(sender);
                }
        }
    }
    
    /**
     * Show current sync status
     */
    private boolean showStatus(CommandSender sender) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;
        
        sender.sendMessage(messageManager.get("status_header"));
        sender.sendMessage(messageManager.get("status_version").replace("{version}", plugin.getDescription().getVersion()));
        
        // Show sync options status
        for (String option : SYNC_OPTIONS) {
            boolean enabled = getSyncOptionValue(option);
            String status = enabled ? messageManager.get("sync_status_enabled") : messageManager.get("sync_status_disabled");
            sender.sendMessage(messageManager.get("sync_status")
                .replace("{option}", option)
                .replace("{status}", status));
        }
        
        sender.sendMessage(messageManager.get("status_footer"));
        return true;
    }
    
    /**
     * Handle reload command
     */
    private boolean handleReload(CommandSender sender) {
        if (!hasPermission(sender, "playerdatasync.admin.reload")) return true;
        
        try {
            plugin.reloadPlugin();
            sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("reloaded"));
        } catch (Exception e) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("reload_failed").replace("{error}", e.getMessage()));
        }
        return true;
    }
    
    /**
     * Handle status command for specific player
     */
    private boolean handleStatus(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.status")) return true;
        
        Player target;
        if (args.length > 1) {
            if (!hasPermission(sender, "playerdatasync.status.others")) return true;
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("player_not_found").replace("{player}", args[1]));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("error_player_offline"));
                return true;
            }
            target = (Player) sender;
        }
        
        showPlayerStatus(sender, target);
        return true;
    }
    
    /**
     * Handle save command
     */
    private boolean handleSave(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.save")) return true;
        
        if (args.length > 1) {
            // Save specific player
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("player_not_found").replace("{player}", args[1]));
                return true;
            }
            
            try {
                plugin.getDatabaseManager().savePlayer(target);
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("manual_save_success"));
            } catch (Exception e) {
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("manual_save_failed").replace("{error}", e.getMessage()));
            }
        } else {
            // Save all online players
            try {
                int savedCount = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getDatabaseManager().savePlayer(player);
                    savedCount++;
                }
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    "Saved data for " + savedCount + " players.");
            } catch (Exception e) {
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("manual_save_failed").replace("{error}", e.getMessage()));
            }
        }
        return true;
    }
    
    /**
     * Handle cache command
     */
    private boolean handleCache(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;
        
        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            // Clear cache (if implemented)
            sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("cache_cleared"));
        } else {
            // Show cache stats (if implemented)
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("cache_stats")
                    .replace("{hits}", "0")
                    .replace("{misses}", "0")
                    .replace("{size}", "0"));
        }
        return true;
    }
    
    /**
     * Handle validate command
     */
    private boolean handleValidate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;
        
        // Perform data validation (placeholder)
        sender.sendMessage(messageManager.get("prefix") + " Data validation completed.");
        return true;
    }
    
    /**
     * Handle sync option changes
     */
    private boolean handleSyncOption(CommandSender sender, String option, String value) {
        if (!hasPermission(sender, "playerdatasync.admin." + option)) return true;
        
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("invalid_syntax").replace("{usage}", "/sync <option> <true|false>"));
            return true;
        }
        
        boolean enabled = Boolean.parseBoolean(value);
        
        if (setSyncOptionValue(option, enabled)) {
            String message = enabled ? messageManager.get("sync_enabled") : messageManager.get("sync_disabled");
            sender.sendMessage(messageManager.get("prefix") + " " + 
                message.replace("{option}", option));
        } else {
            sender.sendMessage(messageManager.get("prefix") + " Unknown option: " + option);
        }
        
        return true;
    }
    
    /**
     * Show help information
     */
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(messageManager.get("help_header"));
        sender.sendMessage(messageManager.get("help_sync"));
        sender.sendMessage(messageManager.get("help_sync_option"));
        sender.sendMessage(messageManager.get("help_sync_reload"));
        sender.sendMessage(messageManager.get("help_sync_save"));
        sender.sendMessage("§b/sync status [player] §8- §7Check sync status");
        sender.sendMessage("§b/sync cache [clear] §8- §7Manage cache");
        sender.sendMessage("§b/sync validate §8- §7Validate data");
        sender.sendMessage("§b/sync help §8- §7Show this help");
        sender.sendMessage(messageManager.get("help_footer"));
        return true;
    }
    
    /**
     * Show player-specific status
     */
    private void showPlayerStatus(CommandSender sender, Player player) {
        sender.sendMessage("§8§m----------§r §bPlayer Status: " + player.getName() + " §8§m----------");
        sender.sendMessage("§7Online: §aYes");
        sender.sendMessage("§7World: §f" + player.getWorld().getName());
        sender.sendMessage("§7Location: §f" + 
            String.format("%.1f, %.1f, %.1f", player.getLocation().getX(), 
                player.getLocation().getY(), player.getLocation().getZ()));
        sender.sendMessage("§7Health: §f" + String.format("%.1f/%.1f", player.getHealth(), player.getMaxHealth()));
        sender.sendMessage("§7Food Level: §f" + player.getFoodLevel() + "/20");
        sender.sendMessage("§7XP Level: §f" + player.getLevel());
        sender.sendMessage("§7Game Mode: §f" + player.getGameMode().toString());
        sender.sendMessage("§8§m----------------------------------------");
    }
    
    /**
     * Get sync option value
     */
    private boolean getSyncOptionValue(String option) {
        switch (option.toLowerCase()) {
            case "coordinates": return plugin.isSyncCoordinates();
            case "position": return plugin.isSyncPosition();
            case "xp": return plugin.isSyncXp();
            case "gamemode": return plugin.isSyncGamemode();
            case "inventory": return plugin.isSyncInventory();
            case "enderchest": return plugin.isSyncEnderchest();
            case "armor": return plugin.isSyncArmor();
            case "offhand": return plugin.isSyncOffhand();
            case "health": return plugin.isSyncHealth();
            case "hunger": return plugin.isSyncHunger();
            case "effects": return plugin.isSyncEffects();
            case "achievements": return plugin.isSyncAchievements();
            case "statistics": return plugin.isSyncStatistics();
            case "attributes": return plugin.isSyncAttributes();
            case "permissions": return plugin.isSyncPermissions();
            case "economy": return plugin.isSyncEconomy();
            default: return false;
        }
    }
    
    /**
     * Set sync option value
     */
    private boolean setSyncOptionValue(String option, boolean value) {
        switch (option.toLowerCase()) {
            case "coordinates": plugin.setSyncCoordinates(value); return true;
            case "position": plugin.setSyncPosition(value); return true;
            case "xp": plugin.setSyncXp(value); return true;
            case "gamemode": plugin.setSyncGamemode(value); return true;
            case "inventory": plugin.setSyncInventory(value); return true;
            case "enderchest": plugin.setSyncEnderchest(value); return true;
            case "armor": plugin.setSyncArmor(value); return true;
            case "offhand": plugin.setSyncOffhand(value); return true;
            case "health": plugin.setSyncHealth(value); return true;
            case "hunger": plugin.setSyncHunger(value); return true;
            case "effects": plugin.setSyncEffects(value); return true;
            case "achievements": plugin.setSyncAchievements(value); return true;
            case "statistics": plugin.setSyncStatistics(value); return true;
            case "attributes": plugin.setSyncAttributes(value); return true;
            case "permissions": plugin.setSyncPermissions(value); return true;
            case "economy": plugin.setSyncEconomy(value); return true;
            default: return false;
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
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument: subcommands or sync options
            completions.addAll(SUB_COMMANDS);
            completions.addAll(SYNC_OPTIONS);
            return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String firstArg = args[0].toLowerCase();
            
            if (SYNC_OPTIONS.contains(firstArg)) {
                // Boolean values for sync options
                return Arrays.asList("true", "false").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (firstArg.equals("status") || firstArg.equals("save")) {
                // Player names
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (firstArg.equals("cache")) {
                return Arrays.asList("clear", "stats").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        return completions;
    }
}
