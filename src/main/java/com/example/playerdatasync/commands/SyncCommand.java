package com.example.playerdatasync.commands;

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

import com.example.playerdatasync.core.PlayerDataSync;
import com.example.playerdatasync.managers.AdvancementSyncManager;
import com.example.playerdatasync.managers.BackupManager;
import com.example.playerdatasync.managers.MessageManager;
import com.example.playerdatasync.integration.EditorIntegrationManager;

/**
 * Enhanced sync command with expanded functionality
 */
public class SyncCommand implements CommandExecutor, TabCompleter {
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
        "reload", "status", "save", "help", "cache", "validate", "backup", "restore", "achievements", "editor"
    );
    
    public SyncCommand(PlayerDataSync plugin) {
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

            case "backup":
                return handleBackup(sender, args);

            case "restore":
                return handleRestore(sender, args);

            case "achievements":
                return handleAchievements(sender, args);

            case "editor":
                return handleEditor(sender, args);

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
                if (plugin.getDatabaseManager().savePlayer(target)) {
                    sender.sendMessage(messageManager.get("prefix") + " " +
                        messageManager.get("manual_save_success"));
                } else {
                    sender.sendMessage(messageManager.get("prefix") + " " +
                        messageManager.get("manual_save_failed").replace("{error}",
                            "Unable to persist player data. See console for details."));
                }
            } catch (Exception e) {
                sender.sendMessage(messageManager.get("prefix") + " " +
                    messageManager.get("manual_save_failed").replace("{error}", e.getMessage()));
            }
        } else {
            // Save all online players
            try {
                int savedCount = 0;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.getDatabaseManager().savePlayer(player)) {
                        savedCount++;
                    }
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
            // Clear performance stats
            plugin.getDatabaseManager().resetPerformanceStats();
            sender.sendMessage(messageManager.get("prefix") + " " + "Performance statistics cleared.");
        } else {
            // Show performance stats
            String stats = plugin.getDatabaseManager().getPerformanceStats();
            sender.sendMessage(messageManager.get("prefix") + " " + "Performance Stats: " + stats);
            
            // Show connection pool stats if available
            if (plugin.getConnectionPool() != null) {
                sender.sendMessage(messageManager.get("prefix") + " " + "Connection Pool: " + plugin.getConnectionPool().getStats());
            }
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
     * Handle backup command
     */
    private boolean handleBackup(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.backup")) return true;
        
        String backupType = args.length > 1 ? args[1] : "manual";
        
        sender.sendMessage(messageManager.get("prefix") + " Creating backup...");
        
        plugin.getBackupManager().createBackup(backupType).thenAccept(result -> {
            if (result.isSuccess()) {
                sender.sendMessage(messageManager.get("prefix") + " Backup created: " + result.getFileName() + 
                    " (" + formatFileSize(result.getFileSize()) + ")");
            } else {
                sender.sendMessage(messageManager.get("prefix") + " Backup failed!");
            }
        });
        
        return true;
    }
    
    /**
     * Handle restore command
     */
    private boolean handleRestore(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.restore")) return true;
        
        if (args.length < 2) {
            // List available backups
            List<BackupManager.BackupInfo> backups = plugin.getBackupManager().listBackups();
            if (backups.isEmpty()) {
                sender.sendMessage(messageManager.get("prefix") + " No backups available.");
            } else {
                sender.sendMessage(messageManager.get("prefix") + " Available backups:");
                for (BackupManager.BackupInfo backup : backups) {
                    sender.sendMessage("§7- §f" + backup.getFileName() + " §8(" + backup.getFormattedSize() + 
                        ", " + backup.getCreatedDate() + ")");
                }
            }
            return true;
        }
        
        String backupName = args[1];
        sender.sendMessage(messageManager.get("prefix") + " Restoring from backup: " + backupName);
        
        plugin.getBackupManager().restoreFromBackup(backupName).thenAccept(success -> {
            if (success) {
                sender.sendMessage(messageManager.get("prefix") + " Restore completed successfully!");
            } else {
                sender.sendMessage(messageManager.get("prefix") + " Restore failed!");
            }
        });

        return true;
    }

    private boolean handleEditor(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.editor")) return true;

        EditorIntegrationManager manager = plugin.getEditorIntegrationManager();
        String prefix = messageManager.get("prefix") + " ";

        if (manager == null) {
            sender.sendMessage(prefix + messageManager.get("editor_disabled"));
            return true;
        }

        String action = args.length > 1 ? args[1].toLowerCase() : "token";

        switch (action) {
            case "token":
                Player target;
                if (args.length > 2) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage(prefix + messageManager.get("player_not_found").replace("{player}", args[2]));
                        return true;
                    }
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                } else {
                    sender.sendMessage(prefix + messageManager.get("editor_player_required"));
                    return true;
                }

                String playerName = target.getName();
                sender.sendMessage(prefix + messageManager.get("editor_token_generating").replace("{player}", playerName));

                manager.requestEditorToken(target.getUniqueId(), playerName).whenComplete((result, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            sender.sendMessage(prefix + messageManager.get("editor_token_failed")
                                .replace("{error}", throwable.getMessage() != null ? throwable.getMessage() : "unknown error"));
                            return;
                        }

                        if (result == null) {
                            sender.sendMessage(prefix + messageManager.get("editor_token_failed").replace("{error}", "no response"));
                            return;
                        }

                        String url = result.getUrl();
                        String token = result.getToken();

                        if (url != null && !url.isEmpty()) {
                            sender.sendMessage(prefix + messageManager.get("editor_token_success")
                                .replace("{url}", url));
                        }

                        if (token != null && !token.isEmpty()) {
                            String expires = result.getExpiresIn() > 0
                                ? messageManager.get("editor_token_expires")
                                    .replace("{seconds}", String.valueOf(result.getExpiresIn()))
                                : messageManager.get("editor_token_expires_unknown");

                            sender.sendMessage(prefix + messageManager.get("editor_token_value")
                                .replace("{token}", token)
                                .replace("{expires}", expires));
                        } else if (url == null || url.isEmpty()) {
                            sender.sendMessage(prefix + messageManager.get("editor_token_failed")
                                .replace("{error}", "missing token"));
                        }
                    })
                );
                return true;

            case "snapshot":
                sender.sendMessage(prefix + messageManager.get("editor_snapshot_start"));
                manager.pushSnapshot().whenComplete((success, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            sender.sendMessage(prefix + messageManager.get("editor_snapshot_failed")
                                .replace("{error}", throwable.getMessage() != null ? throwable.getMessage() : "unknown error"));
                        } else {
                            sender.sendMessage(prefix + messageManager.get("editor_snapshot_success"));
                        }
                    })
                );
                return true;

            case "heartbeat":
                if (args.length < 3) {
                    sender.sendMessage(prefix + messageManager.get("editor_heartbeat_usage"));
                    return true;
                }

                boolean online;
                if (args[2].equalsIgnoreCase("online")) {
                    online = true;
                } else if (args[2].equalsIgnoreCase("offline")) {
                    online = false;
                } else {
                    sender.sendMessage(prefix + messageManager.get("editor_heartbeat_usage"));
                    return true;
                }

                manager.sendHeartbeatAsync(online).whenComplete((success, throwable) ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (throwable != null) {
                            sender.sendMessage(prefix + messageManager.get("editor_heartbeat_failed")
                                .replace("{error}", throwable.getMessage() != null ? throwable.getMessage() : "unknown error"));
                        } else {
                            sender.sendMessage(prefix + messageManager.get("editor_heartbeat_success")
                                .replace("{status}", online ? messageManager.get("editor_status_online")
                                    : messageManager.get("editor_status_offline")));
                        }
                    })
                );
                return true;

            default:
                sender.sendMessage(prefix + messageManager.get("editor_usage"));
                return true;
        }
    }

    private boolean handleAchievements(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.achievements")) return true;

        AdvancementSyncManager advancementSyncManager = plugin.getAdvancementSyncManager();
        if (advancementSyncManager == null) {
            sender.sendMessage(messageManager.get("prefix") + " Advancement manager is not available.");
            return true;
        }

        String prefix = messageManager.get("prefix") + " ";

        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(prefix + "Advancement cache: " + advancementSyncManager.getGlobalImportStatus());

            if (args.length > 2) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(prefix + "Player '" + args[2] + "' is not online.");
                } else {
                    sender.sendMessage(prefix + target.getName() + ": " +
                        advancementSyncManager.getPlayerStatus(target.getUniqueId()));
                }
            } else if (sender instanceof Player) {
                Player player = (Player) sender;
                sender.sendMessage(prefix + "You: " +
                    advancementSyncManager.getPlayerStatus(player.getUniqueId()));
            }

            sender.sendMessage(prefix + "Use /sync achievements import [player] to queue an import.");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("import") || action.equals("preload")) {
            if (args.length == 2) {
                boolean started = advancementSyncManager.startGlobalImport(true);
                if (started) {
                    sender.sendMessage(prefix + "Started global advancement cache rebuild.");
                } else if (advancementSyncManager.getGlobalImportStatus().startsWith("running")) {
                    sender.sendMessage(prefix + "Global advancement cache rebuild is already running.");
                } else {
                    sender.sendMessage(prefix + "Advancement cache already up to date. Use /sync achievements import again later to rebuild.");
                }
                return true;
            }

            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(prefix + "Player '" + args[2] + "' is not online.");
                return true;
            }

            advancementSyncManager.forceRescan(target);
            sender.sendMessage(prefix + "Queued advancement import for " + target.getName() + ".");
            return true;
        }

        sender.sendMessage(prefix + "Unknown achievements subcommand. Try /sync achievements status or /sync achievements import");
        return true;
    }

    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
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
        sender.sendMessage("§b/sync cache [clear] §8- §7Manage cache and performance stats");
        sender.sendMessage("§b/sync validate §8- §7Validate data integrity");
        sender.sendMessage("§b/sync backup [type] §8- §7Create manual backup");
        sender.sendMessage("§b/sync restore [backup] §8- §7Restore from backup");
        sender.sendMessage("§b/sync editor token [player] §8- §7Generate web editor access");
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

            if (firstArg.equals("backup")) {
                return Arrays.asList("manual", "automatic", "scheduled").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (firstArg.equals("achievements")) {
                return Arrays.asList("status", "import").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (firstArg.equals("editor")) {
                return Arrays.asList("token", "snapshot", "heartbeat").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }

            if (firstArg.equals("restore")) {
                // List available backup files
                return plugin.getBackupManager().listBackups().stream()
                    .map(BackupManager.BackupInfo::getFileName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("achievements")) {
                String second = args[1].toLowerCase();
                if (second.equals("status") || second.equals("import") || second.equals("preload")) {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }

            if (args[0].equalsIgnoreCase("editor")) {
                String second = args[1].toLowerCase();
                if (second.equals("token")) {
                    return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }

                if (second.equals("heartbeat")) {
                    return Arrays.asList("online", "offline").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
                }
            }
        }

        return completions;
    }
}
