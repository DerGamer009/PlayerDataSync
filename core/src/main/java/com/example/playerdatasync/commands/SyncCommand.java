package com.example.playerdatasync.commands;

import com.example.playerdatasync.api.UpdateChecker;
import com.example.playerdatasync.core.PlayerDataSync;
import com.example.playerdatasync.managers.BackupManager;
import com.example.playerdatasync.managers.MessageManager;
import com.example.playerdatasync.utils.InventoryUtils;
import com.example.playerdatasync.utils.VersionCompatibility;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import su.nightexpress.excellentenchants.api.enchantment.CustomEnchantment;
import su.nightexpress.excellentenchants.enchantment.EnchantRegistry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SyncCommand implements CommandExecutor, TabCompleter {

    private final PlayerDataSync plugin;
    private final MessageManager messageManager;

    private static final List<String> SYNC_OPTIONS = Arrays.asList(
            "coordinates", "position", "xp", "gamemode", "inventory", "enderchest",
            "armor", "offhand", "health", "hunger", "effects", "achievements",
            "statistics", "attributes", "permissions", "economy"
    );

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "reload", "status", "save", "help", "cache", "validate", "backup", "restore", "achievements", "checkupdate", "maintenance", "menu", "profile"
    );

    public SyncCommand(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return showStatus(sender);

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload": return handleReload(sender);
            case "status": return handleStatus(sender, args);
            case "save": return handleSave(sender, args);
            case "help": return showHelp(sender);
            case "cache": return handleCache(sender, args);
            case "validate": return handleValidate(sender, args);
            case "backup": return handleBackup(sender, args);
            case "restore": return handleRestore(sender, args);
            case "achievements": return handleAchievements(sender, args);
            case "checkupdate": return handleCheckUpdate(sender);
            case "maintenance": return handleMaintenance(sender, args);
            case "menu": return handleMenu(sender);
            case "profile": return handleProfile(sender, args);
            default:
                if (args.length == 2) return handleSyncOption(sender, args[0], args[1]);
                else return showHelp(sender);
        }
    }

    private boolean showStatus(CommandSender sender) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;

        sender.sendMessage(messageManager.get("status_header"));
        sender.sendMessage(messageManager.get("status_version").replace("{version}", plugin.getDescription().getVersion()));

        for (String option : SYNC_OPTIONS) {
            boolean enabled = getSyncOptionValue(option);
            String status = enabled ? messageManager.get("sync_status_enabled") : messageManager.get("sync_status_disabled");
            sender.sendMessage(messageManager.get("sync_status").replace("{option}", option).replace("{status}", status));
        }

        sender.sendMessage(messageManager.get("status_footer"));
        return true;
    }

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

    private boolean handleStatus(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.status")) return true;

        Player target;
        if (args.length > 1) {
            if (!hasPermission(sender, "playerdatasync.status.others")) return true;
            target = Bukkit.getPlayerExact(args[1]);
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

    private boolean handleSave(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.save")) return true;

        if (args.length > 1) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(messageManager.get("prefix") + " " +
                        messageManager.get("player_not_found").replace("{player}", args[1]));
                return true;
            }
            try {
                boolean saved = plugin.getDatabaseManager().savePlayer(target);
                sender.sendMessage(messageManager.get("prefix") + " " +
                        (saved ? messageManager.get("manual_save_success")
                                : messageManager.get("manual_save_failed").replace("{error}", "Unable to persist player data.")));
            } catch (Exception e) {
                sender.sendMessage(messageManager.get("prefix") + " " +
                        messageManager.get("manual_save_failed").replace("{error}", e.getMessage()));
            }
        } else {
            int savedCount = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.getDatabaseManager().savePlayer(player)) savedCount++;
            }
            sender.sendMessage(messageManager.get("prefix") + " Saved data for " + savedCount + " players.");
        }
        return true;
    }

    private boolean handleCache(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;

        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
            plugin.getDatabaseManager().resetPerformanceStats();
            InventoryUtils.resetDeserializationStats();
            sender.sendMessage(messageManager.get("prefix") + " Performance and deserialization statistics cleared.");
        } else {
            sender.sendMessage(messageManager.get("prefix") + " Performance Stats: " + plugin.getDatabaseManager().getPerformanceStats());
            if (plugin.getConnectionPool() != null) {
                sender.sendMessage(messageManager.get("prefix") + " Connection Pool: " + plugin.getConnectionPool().getStats());
            }
            String deserializationStats = InventoryUtils.getDeserializationStats();
            sender.sendMessage(messageManager.get("prefix") + " Deserialization Stats: " + deserializationStats);

            // Enchantment plugin checks
            Plugin eePlugin = Bukkit.getPluginManager().getPlugin("ExcellentEnchants");
            Plugin ecoEnchantsPlugin = Bukkit.getPluginManager().getPlugin("EcoEnchants");
            boolean hasCustomEnchantmentFailures = deserializationStats.contains("Custom Enchantments:") &&
                    !deserializationStats.contains("Custom Enchantments: 0");
            if (eePlugin != null && eePlugin.isEnabled()) {
                Set<CustomEnchantment> customs = EnchantRegistry.getRegistered();
                if (customs.isEmpty() && hasCustomEnchantmentFailures) {
                    sender.sendMessage("§e⚠ ExcellentEnchants loaded but no custom enchantments found!");
                }
            }

            if (!((eePlugin != null && eePlugin.isEnabled()) || (ecoEnchantsPlugin != null && ecoEnchantsPlugin.isEnabled()))
                    && hasCustomEnchantmentFailures) {
                sender.sendMessage("§e⚠ Custom enchantment failures detected. Ensure plugins like ExcellentEnchants/EcoEnchants are loaded.");
            }
        }
        return true;
    }

    private boolean handleValidate(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;
        sender.sendMessage(messageManager.get("prefix") + " Data validation completed.");
        return true;
    }

    private boolean handleBackup(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.backup")) return true;
        String backupType = args.length > 1 ? args[1] : "manual";
        sender.sendMessage(messageManager.get("prefix") + " Creating backup...");

        CompletableFuture<BackupManager.BackupResult> future = plugin.getBackupManager().createBackup(backupType);
        future.thenAccept(result -> {
            if (result.isSuccess()) {
                sender.sendMessage(messageManager.get("prefix") + " Backup created: " + result.getFileName() +
                        " (" + formatFileSize(result.getFileSize()) + ")");
            } else {
                sender.sendMessage(messageManager.get("prefix") + " Backup failed!");
            }
        });
        return true;
    }

    private boolean handleRestore(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.restore")) return true;

        if (args.length < 2) {
            List<BackupManager.BackupInfo> backups = plugin.getBackupManager().listBackups();
            if (backups.isEmpty()) {
                sender.sendMessage(messageManager.get("prefix") + " No backups available.");
            } else {
                sender.sendMessage(messageManager.get("prefix") + " Available backups:");
                for (BackupManager.BackupInfo backup : backups) {
                    sender.sendMessage("§7- §f" + backup.getFileName() + " §8(" + backup.getFormattedSize() + ", " + backup.getCreatedDate() + ")");
                }
            }
            return true;
        }

        String backupName = args[1];
        sender.sendMessage(messageManager.get("prefix") + " Restoring from backup: " + backupName);
        plugin.getBackupManager().restoreFromBackup(backupName).thenAccept(success -> {
            sender.sendMessage(messageManager.get("prefix") + (success ? " Restore completed successfully!" : " Restore failed!"));
        });
        return true;
    }

    private boolean handleAchievements(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.achievements")) return true;

        String prefix = messageManager.get("prefix") + " ";
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(prefix + "Advancement sync is delegated to NMSHandler.");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("import") || action.equals("preload")) {
            if (args.length == 2) {
                sender.sendMessage(prefix + "Global cache rebuild not supported in NMS mode.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(prefix + "Player '" + args[2] + "' not online.");
                return true;
            }

            if (plugin.getNmsHandler() != null) {
                plugin.getNmsHandler().queueAdvancementImport(target, true);
                sender.sendMessage(prefix + "Queued advancement import for " + target.getName() + ".");
            } else {
                sender.sendMessage(prefix + "NMSHandler is not loaded.");
            }
            return true;
        }

        sender.sendMessage(prefix + "Unknown achievements subcommand. Use /sync achievements status/import.");
        return true;
    }

    private boolean handleCheckUpdate(CommandSender sender) {
        if (!hasPermission(sender, "playerdatasync.admin.update")) return true;
        new UpdateChecker(plugin, messageManager).check(sender, true);
        return true;
    }

    private boolean handleMaintenance(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.maintenance")) return true;

        if (args.length < 2) {
            sender.sendMessage(messageManager.get("prefix") + " §7Maintenance Mode: " +
                    (plugin.isMaintenanceMode() ? "§cEnabled" : "§aDisabled"));
            sender.sendMessage(messageManager.get("prefix") + " §7Use §f/sync maintenance <on|off> §7to toggle.");
            return true;
        }

        boolean enable = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        plugin.setMaintenanceMode(enable);

        String status = enable ? "§cENABLED" : "§aDISABLED";
        sender.sendMessage(messageManager.get("prefix") + " §7Maintenance Mode has been " + status + ".");
        return true;
    }

    private boolean handleMenu(CommandSender sender) {
        if (!hasPermission(sender, "playerdatasync.admin")) return true;
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.get("prefix") + " §cThis command can only be used by players.");
            return true;
        }
        plugin.getMenuManager().openMenu((Player) sender);
        return true;
    }

    private boolean handleProfile(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "playerdatasync.admin.profile")) return true;

        if (args.length > 1 && args[1].equalsIgnoreCase("reset")) {
            plugin.getProfileManager().reset();
            sender.sendMessage(messageManager.get("prefix") + " §7Performance profile data reset.");
            return true;
        }

        plugin.getProfileManager().showProfile(sender);
        return true;
    }

    private boolean handleSyncOption(CommandSender sender, String option, String value) {
        if (!hasPermission(sender, "playerdatasync.admin." + option)) return true;
        if (!SYNC_OPTIONS.contains(option.toLowerCase())) {
            sender.sendMessage(messageManager.get("prefix") + " Unknown sync option: " + option);
            return true;
        }

        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            sender.sendMessage(messageManager.get("prefix") + " " +
                    messageManager.get("invalid_syntax").replace("{usage}", "/sync <option> <true|false>"));
            return true;
        }

        boolean enabled = Boolean.parseBoolean(value);
        setSyncOptionValue(option, enabled);
        String msg = enabled ? messageManager.get("sync_enabled") : messageManager.get("sync_disabled");
        sender.sendMessage(messageManager.get("prefix") + " " + msg.replace("{option}", option));
        return true;
    }

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
        sender.sendMessage("§b/sync checkupdate §8- §7Manually check for updates");
        sender.sendMessage("§b/sync maintenance <on/off> §8- §7Toggle Maintenance Mode");
        sender.sendMessage("§b/sync menu §8- §7Open management GUI");
        sender.sendMessage("§b/sync profile [reset] §8- §7Show performance profiling");
        sender.sendMessage("§b/sync help §8- §7Show this help");
        sender.sendMessage(messageManager.get("help_footer"));
        return true;
    }

    private void showPlayerStatus(CommandSender sender, Player player) {
        sender.sendMessage("§8§m----------§r §bPlayer Status: " + player.getName() + " §8§m----------");
        sender.sendMessage("§7Online: §aYes");
        sender.sendMessage("§7World: §f" + player.getWorld().getName());
        sender.sendMessage("§7Location: §f" + String.format("%.1f, %.1f, %.1f",
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ()));

        double maxHealth = 20.0;
        try {
            if (plugin.getNmsHandler() != null) {
                maxHealth = plugin.getNmsHandler().getGenericMaxHealth(player);
            }
        } catch (Exception ignored) {}

        sender.sendMessage("§7Health: §f" + String.format("%.1f/%.1f", player.getHealth(), maxHealth));
        sender.sendMessage("§7Food Level: §f" + player.getFoodLevel() + "/20");
        sender.sendMessage("§7XP Level: §f" + player.getLevel());
        sender.sendMessage("§7Game Mode: §f" + player.getGameMode());
        sender.sendMessage("§8§m----------------------------------------");
    }

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

    private void setSyncOptionValue(String option, boolean value) {
        switch (option.toLowerCase()) {
            case "coordinates": plugin.setSyncCoordinates(value); break;
            case "position": plugin.setSyncPosition(value); break;
            case "xp": plugin.setSyncXp(value); break;
            case "gamemode": plugin.setSyncGamemode(value); break;
            case "inventory": plugin.setSyncInventory(value); break;
            case "enderchest": plugin.setSyncEnderchest(value); break;
            case "armor": plugin.setSyncArmor(value); break;
            case "offhand": plugin.setSyncOffhand(value); break;
            case "health": plugin.setSyncHealth(value); break;
            case "hunger": plugin.setSyncHunger(value); break;
            case "effects": plugin.setSyncEffects(value); break;
            case "achievements": plugin.setSyncAchievements(value); break;
            case "statistics": plugin.setSyncStatistics(value); break;
            case "attributes": plugin.setSyncAttributes(value); break;
            case "permissions": plugin.setSyncPermissions(value); break;
            case "economy": plugin.setSyncEconomy(value); break;
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("playerdatasync.admin.*")) return true;
        sender.sendMessage(messageManager.get("prefix") + " " + messageManager.get("no_permission"));
        return false;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(SUB_COMMANDS);
            completions.addAll(SYNC_OPTIONS);
        } else if (args.length == 2 && SYNC_OPTIONS.contains(args[0].toLowerCase())) {
            completions.add("true");
            completions.add("false");
        }
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }
}
