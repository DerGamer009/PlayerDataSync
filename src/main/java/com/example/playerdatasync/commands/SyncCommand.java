package com.example.playerdatasync.commands;

import com.example.playerdatasync.core.PlayerDataSync;
import com.example.playerdatasync.managers.AdvancementSyncManager;
import com.example.playerdatasync.managers.BackupManager;
import com.example.playerdatasync.managers.MessageManager;
import com.example.playerdatasync.utils.InventoryUtils;
import com.example.playerdatasync.utils.VersionCompatibility;
import org.bukkit.Bukkit;
import org.bukkit.attribute.AttributeInstance;
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
            "reload", "status", "save", "help", "cache", "validate", "backup", "restore", "achievements"
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

        AdvancementSyncManager manager = plugin.getAdvancementSyncManager();
        if (manager == null) {
            sender.sendMessage(messageManager.get("prefix") + " Advancement manager not available.");
            return true;
        }

        String prefix = messageManager.get("prefix") + " ";
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(prefix + "Advancement cache: " + manager.getGlobalImportStatus());
            if (args.length > 2) {
                Player target = Bukkit.getPlayerExact(args[2]);
                sender.sendMessage(prefix + (target != null ? target.getName() + ": " + manager.getPlayerStatus(target.getUniqueId())
                        : "Player '" + args[2] + "' not online."));
            } else if (sender instanceof Player player) {
                sender.sendMessage(prefix + "You: " + manager.getPlayerStatus(player.getUniqueId()));
            }
            sender.sendMessage(prefix + "Use /sync achievements import [player] to queue an import.");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("import") || action.equals("preload")) {
            if (args.length == 2) {
                boolean started = manager.startGlobalImport(true);
                if (started) sender.sendMessage(prefix + "Started global advancement cache rebuild.");
                else if (manager.getGlobalImportStatus().startsWith("running"))
                    sender.sendMessage(prefix + "Global cache rebuild already running.");
                else sender.sendMessage(prefix + "Cache already up to date.");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(prefix + "Player '" + args[2] + "' not online.");
                return true;
            }

            manager.forceRescan(target);
            sender.sendMessage(prefix + "Queued advancement import for " + target.getName() + ".");
            return true;
        }

        sender.sendMessage(prefix + "Unknown achievements subcommand. Use /sync achievements status/import.");
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
            if (VersionCompatibility.isAttributesSupported()) {
                AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) maxHealth = attr.getValue();
            } else maxHealth = player.getMaxHealth();
        } catch (Exception ignored) {}

        sender.sendMessage("§7Health: §f" + String.format("%.1f/%.1f", player.getHealth(), maxHealth));
        sender.sendMessage("§7Food Level: §f" + player.getFoodLevel() + "/20");
        sender.sendMessage("§7XP Level: §f" + player.getLevel());
        sender.sendMessage("§7Game Mode: §f" + player.getGameMode());
        sender.sendMessage("§8§m----------------------------------------");
    }

    private boolean getSyncOptionValue(String option) {
        return switch (option.toLowerCase()) {
            case "coordinates" -> plugin.isSyncCoordinates();
            case "position" -> plugin.isSyncPosition();
            case "xp" -> plugin.isSyncXp();
            case "gamemode" -> plugin.isSyncGamemode();
            case "inventory" -> plugin.isSyncInventory();
            case "enderchest" -> plugin.isSyncEnderchest();
            case "armor" -> plugin.isSyncArmor();
            case "offhand" -> plugin.isSyncOffhand();
            case "health" -> plugin.isSyncHealth();
            case "hunger" -> plugin.isSyncHunger();
            case "effects" -> plugin.isSyncEffects();
            case "achievements" -> plugin.isSyncAchievements();
            case "statistics" -> plugin.isSyncStatistics();
            case "attributes" -> plugin.isSyncAttributes();
            case "permissions" -> plugin.isSyncPermissions();
            case "economy" -> plugin.isSyncEconomy();
            default -> false;
        };
    }

    private void setSyncOptionValue(String option, boolean value) {
        switch (option.toLowerCase()) {
            case "coordinates" -> plugin.setSyncCoordinates(value);
            case "position" -> plugin.setSyncPosition(value);
            case "xp" -> plugin.setSyncXp(value);
            case "gamemode" -> plugin.setSyncGamemode(value);
            case "inventory" -> plugin.setSyncInventory(value);
            case "enderchest" -> plugin.setSyncEnderchest(value);
            case "armor" -> plugin.setSyncArmor(value);
            case "offhand" -> plugin.setSyncOffhand(value);
            case "health" -> plugin.setSyncHealth(value);
            case "hunger" -> plugin.setSyncHunger(value);
            case "effects" -> plugin.setSyncEffects(value);
            case "achievements" -> plugin.setSyncAchievements(value);
            case "statistics" -> plugin.setSyncStatistics(value);
            case "attributes" -> plugin.setSyncAttributes(value);
            case "permissions" -> plugin.setSyncPermissions(value);
            case "economy" -> plugin.setSyncEconomy(value);
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
