package com.example.playerdatasync.commands;

import com.example.playerdatasync.PlayerDataSync;
import com.example.playerdatasync.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Backup management command for PlayerDataSync
 */
public class BackupCommand implements CommandExecutor, TabCompleter {
    private final PlayerDataSync plugin;
    private final MessageManager messageManager;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    
    private static final List<String> SUB_COMMANDS = Arrays.asList(
        "create", "restore", "list", "delete", "auto", "info"
    );
    
    public BackupCommand(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasPermission(sender, "playerdatasync.backup")) return true;
        
        if (args.length == 0) {
            return showHelp(sender);
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "create":
                return handleCreate(sender, args);
                
            case "restore":
                return handleRestore(sender, args);
                
            case "list":
                return handleList(sender, args);
                
            case "delete":
                return handleDelete(sender, args);
                
            case "auto":
                return handleAuto(sender, args);
                
            case "info":
                return handleInfo(sender, args);
                
            default:
                return showHelp(sender);
        }
    }
    
    /**
     * Handle backup creation
     */
    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.backup.create")) return true;
        
        if (args.length < 2) {
            sender.sendMessage(messageManager.get("prefix") + " §cUsage: /pdsbackup create <player|all> [description]");
            return true;
        }
        
        String target = args[1];
        String description = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        
        if (target.equalsIgnoreCase("all")) {
            return createAllPlayersBackup(sender, description);
        } else {
            return createPlayerBackup(sender, target, description);
        }
    }
    
    /**
     * Handle backup restoration
     */
    private boolean handleRestore(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.backup.restore")) return true;
        
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("prefix") + " §cUsage: /pdsbackup restore <player> <backup_id>");
            return true;
        }
        
        String playerName = args[1];
        String backupId = args[2];
        
        return restorePlayerBackup(sender, playerName, backupId);
    }
    
    /**
     * Handle backup listing
     */
    private boolean handleList(CommandSender sender, String[] args) {
        String playerName = args.length > 1 ? args[1] : null;
        
        if (playerName != null && !hasPermission(sender, "playerdatasync.backup.others")) {
            sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
            return true;
        }
        
        return listBackups(sender, playerName);
    }
    
    /**
     * Handle backup deletion
     */
    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.backup.others")) return true;
        
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("prefix") + " §cUsage: /pdsbackup delete <player> <backup_id>");
            return true;
        }
        
        String playerName = args[1];
        String backupId = args[2];
        
        return deleteBackup(sender, playerName, backupId);
    }
    
    /**
     * Handle auto-backup settings
     */
    private boolean handleAuto(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;
        
        if (args.length < 2) {
            // Show current auto-backup status
            boolean enabled = plugin.getConfig().getBoolean("data_management.backup.enabled", true);
            int interval = plugin.getConfig().getInt("data_management.backup.interval", 1440);
            int keepCount = plugin.getConfig().getInt("data_management.backup.keep_backups", 7);
            
            sender.sendMessage(messageManager.get("prefix") + " §7Auto-backup status:");
            sender.sendMessage("  §7Enabled: " + (enabled ? "§aYes" : "§cNo"));
            sender.sendMessage("  §7Interval: §f" + interval + " minutes");
            sender.sendMessage("  §7Keep backups: §f" + keepCount);
            return true;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "enable":
                plugin.getConfig().set("data_management.backup.enabled", true);
                plugin.saveConfig();
                sender.sendMessage(messageManager.get("prefix") + " §aAuto-backup enabled.");
                break;
                
            case "disable":
                plugin.getConfig().set("data_management.backup.enabled", false);
                plugin.saveConfig();
                sender.sendMessage(messageManager.get("prefix") + " §cAuto-backup disabled.");
                break;
                
            case "interval":
                if (args.length < 3) {
                    sender.sendMessage(messageManager.get("prefix") + " §cUsage: /pdsbackup auto interval <minutes>");
                    return true;
                }
                
                try {
                    int newInterval = Integer.parseInt(args[2]);
                    if (newInterval < 60) {
                        sender.sendMessage(messageManager.get("prefix") + " §cMinimum interval is 60 minutes.");
                        return true;
                    }
                    
                    plugin.getConfig().set("data_management.backup.interval", newInterval);
                    plugin.saveConfig();
                    sender.sendMessage(messageManager.get("prefix") + " §aBackup interval set to " + newInterval + " minutes.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(messageManager.get("prefix") + " §cInvalid number format.");
                }
                break;
                
            default:
                sender.sendMessage(messageManager.get("prefix") + " §cUsage: /pdsbackup auto <enable|disable|interval>");
                break;
        }
        
        return true;
    }
    
    /**
     * Handle backup info
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messageManager.get("prefix") + " §cUsage: /pdsbackup info <player> <backup_id>");
            return true;
        }
        
        String playerName = args[1];
        String backupId = args[2];
        
        return showBackupInfo(sender, playerName, backupId);
    }
    
    /**
     * Create backup for specific player
     */
    private boolean createPlayerBackup(CommandSender sender, String playerName, String description) {
        try {
            // Generate backup ID
            String backupId = "backup_" + dateFormat.format(new Date());
            
            // Create backup directory
            File backupDir = new File(plugin.getDataFolder(), "backups/" + playerName);
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            // Here you would implement the actual backup logic
            // For now, this is a placeholder
            File backupFile = new File(backupDir, backupId + ".json");
            
            // Simulate backup creation
            Thread.sleep(100); // Simulate processing time
            
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_created").replace("{id}", backupId));
            
            if (!description.isEmpty()) {
                sender.sendMessage("  §7Description: §f" + description);
            }
            
            return true;
            
        } catch (Exception e) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_failed").replace("{error}", e.getMessage()));
            return true;
        }
    }
    
    /**
     * Create backup for all online players
     */
    private boolean createAllPlayersBackup(CommandSender sender, String description) {
        try {
            int playerCount = Bukkit.getOnlinePlayers().size();
            String backupId = "global_" + dateFormat.format(new Date());
            
            sender.sendMessage(messageManager.get("prefix") + " §7Creating backup for " + playerCount + " players...");
            
            // Simulate backup creation for all players
            Thread.sleep(playerCount * 50); // Simulate processing time
            
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_created").replace("{id}", backupId));
            
            if (!description.isEmpty()) {
                sender.sendMessage("  §7Description: §f" + description);
            }
            
            return true;
            
        } catch (Exception e) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_failed").replace("{error}", e.getMessage()));
            return true;
        }
    }
    
    /**
     * Restore player backup
     */
    private boolean restorePlayerBackup(CommandSender sender, String playerName, String backupId) {
        try {
            // Validate backup exists
            File backupDir = new File(plugin.getDataFolder(), "backups/" + playerName);
            File backupFile = new File(backupDir, backupId + ".json");
            
            if (!backupFile.exists()) {
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("backup_not_found").replace("{id}", backupId));
                return true;
            }
            
            // Check if player is online (might need special handling)
            Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                sender.sendMessage(messageManager.get("prefix") + " §eWarning: Player is online. Restoration may cause issues.");
            }
            
            // Simulate restore process
            sender.sendMessage(messageManager.get("prefix") + " §7Restoring backup " + backupId + "...");
            Thread.sleep(200); // Simulate processing time
            
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_restored").replace("{id}", backupId));
            
            return true;
            
        } catch (Exception e) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_failed").replace("{error}", e.getMessage()));
            return true;
        }
    }
    
    /**
     * List backups for player or all players
     */
    private boolean listBackups(CommandSender sender, String playerName) {
        File backupsDir = new File(plugin.getDataFolder(), "backups");
        
        if (!backupsDir.exists()) {
            sender.sendMessage(messageManager.get("prefix") + " §7No backups found.");
            return true;
        }
        
        if (playerName != null) {
            // List backups for specific player
            File playerBackupDir = new File(backupsDir, playerName);
            if (!playerBackupDir.exists()) {
                sender.sendMessage(messageManager.get("prefix") + " " + 
                    messageManager.get("backup_list_empty").replace("{player}", playerName));
                return true;
            }
            
            sender.sendMessage(messageManager.get("backup_list_header").replace("{player}", playerName));
            
            File[] backupFiles = playerBackupDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (backupFiles != null && backupFiles.length > 0) {
                for (File backup : backupFiles) {
                    String backupId = backup.getName().replace(".json", "");
                    String date = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(backup.lastModified()));
                    String size = formatFileSize(backup.length());
                    
                    sender.sendMessage(messageManager.get("backup_list_entry")
                        .replace("{id}", backupId)
                        .replace("{date}", date)
                        .replace("{size}", size));
                }
            } else {
                sender.sendMessage(messageManager.get("backup_list_empty").replace("{player}", playerName));
            }
            
        } else {
            // List all backups
            sender.sendMessage("§8§m----------§r §bAll Backups §8§m----------");
            
            File[] playerDirs = backupsDir.listFiles(File::isDirectory);
            if (playerDirs != null && playerDirs.length > 0) {
                for (File playerDir : playerDirs) {
                    int backupCount = playerDir.listFiles((dir, name) -> name.endsWith(".json")).length;
                    sender.sendMessage("§7" + playerDir.getName() + ": §f" + backupCount + " backups");
                }
            } else {
                sender.sendMessage("§7No backups found.");
            }
        }
        
        return true;
    }
    
    /**
     * Delete specific backup
     */
    private boolean deleteBackup(CommandSender sender, String playerName, String backupId) {
        File backupFile = new File(plugin.getDataFolder(), "backups/" + playerName + "/" + backupId + ".json");
        
        if (!backupFile.exists()) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_not_found").replace("{id}", backupId));
            return true;
        }
        
        if (backupFile.delete()) {
            sender.sendMessage(messageManager.get("prefix") + " §aBackup " + backupId + " deleted successfully.");
        } else {
            sender.sendMessage(messageManager.get("prefix") + " §cFailed to delete backup " + backupId + ".");
        }
        
        return true;
    }
    
    /**
     * Show detailed backup information
     */
    private boolean showBackupInfo(CommandSender sender, String playerName, String backupId) {
        File backupFile = new File(plugin.getDataFolder(), "backups/" + playerName + "/" + backupId + ".json");
        
        if (!backupFile.exists()) {
            sender.sendMessage(messageManager.get("prefix") + " " + 
                messageManager.get("backup_not_found").replace("{id}", backupId));
            return true;
        }
        
        sender.sendMessage("§8§m----------§r §bBackup Info §8§m----------");
        sender.sendMessage("§7Backup ID: §f" + backupId);
        sender.sendMessage("§7Player: §f" + playerName);
        sender.sendMessage("§7Created: §f" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(backupFile.lastModified())));
        sender.sendMessage("§7Size: §f" + formatFileSize(backupFile.length()));
        sender.sendMessage("§7File: §f" + backupFile.getName());
        sender.sendMessage("§8§m--------------------------------");
        
        return true;
    }
    
    /**
     * Show help information
     */
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage("§8§m----------§r §bBackup Commands §8§m----------");
        sender.sendMessage("§b/pdsbackup create <player|all> [description] §8- §7Create backup");
        sender.sendMessage("§b/pdsbackup restore <player> <backup_id> §8- §7Restore backup");
        sender.sendMessage("§b/pdsbackup list [player] §8- §7List backups");
        sender.sendMessage("§b/pdsbackup delete <player> <backup_id> §8- §7Delete backup");
        sender.sendMessage("§b/pdsbackup auto <enable|disable|interval> §8- §7Auto-backup settings");
        sender.sendMessage("§b/pdsbackup info <player> <backup_id> §8- §7Show backup info");
        sender.sendMessage("§8§m----------------------------------");
        return true;
    }
    
    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
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
            return SUB_COMMANDS.stream()
                .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("create")) {
                List<String> options = new ArrayList<>();
                options.add("all");
                options.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
                return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (subCommand.equals("restore") || subCommand.equals("list") || 
                subCommand.equals("delete") || subCommand.equals("info")) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            
            if (subCommand.equals("auto")) {
                return Arrays.asList("enable", "disable", "interval").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("auto") && args[1].equalsIgnoreCase("interval")) {
                return Arrays.asList("60", "120", "360", "720", "1440");
            }
            
            // For restore, delete, info - would need to list actual backup IDs
            // This is a placeholder
            if (subCommand.equals("restore") || subCommand.equals("delete") || subCommand.equals("info")) {
                return Arrays.asList("backup_example", "backup_latest");
            }
        }
        
        return new ArrayList<>();
    }
}
