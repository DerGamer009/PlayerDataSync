package com.example.playerdatasync.premium.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.example.playerdatasync.premium.core.PlayerDataSyncPremium;
import com.example.playerdatasync.premium.managers.LicenseManager;
import com.example.playerdatasync.premium.api.PremiumUpdateChecker;
import com.example.playerdatasync.premium.api.LicenseValidator.LicenseValidationResult;

/**
 * Premium command handler for license and update commands
 * 
 * This class should be integrated into SyncCommand.java
 */
public class PremiumCommandHandler {
    private final PlayerDataSyncPremium plugin;
    
    public PremiumCommandHandler(PlayerDataSyncPremium plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Handle license commands
     * Usage: /sync license [validate|info]
     */
    public boolean handleLicense(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerdatasync.premium.admin.license")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        LicenseManager licenseManager = plugin.getLicenseManager();
        if (licenseManager == null) {
            sender.sendMessage("§cLicense manager is not initialized.");
            return true;
        }
        
        String action = args.length > 2 ? args[2].toLowerCase() : "info";
        
        switch (action) {
            case "validate":
            case "revalidate":
                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Validating license...");
                licenseManager.revalidateLicense().thenAccept(result -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (result.isValid()) {
                            sender.sendMessage("§8[§6PlayerDataSync Premium§8] §aLicense is valid!");
                            if (result.getPurchase() != null) {
                                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Purchase ID: §f" + result.getPurchase().getId());
                                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7User ID: §f" + result.getPurchase().getUserId());
                            }
                        } else {
                            sender.sendMessage("§8[§6PlayerDataSync Premium§8] §cLicense validation failed!");
                            sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Reason: §f" + 
                                (result.getMessage() != null ? result.getMessage() : "Unknown error"));
                        }
                    });
                });
                return true;
                
            case "info":
                sender.sendMessage("§8[§m----------§r §6License Information §8§m----------");
                sender.sendMessage("§7License Key: §f" + licenseManager.getLicenseKey());
                sender.sendMessage("§7Status: " + (licenseManager.isLicenseValid() ? "§aValid" : "§cInvalid"));
                sender.sendMessage("§7Checked: " + (licenseManager.isLicenseChecked() ? "§aYes" : "§cNo"));
                sender.sendMessage("§8§m----------------------------------------");
                return true;
                
            default:
                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Usage: /sync license [validate|info]");
                return true;
        }
    }
    
    /**
     * Handle update commands
     * Usage: /sync update [check]
     */
    public boolean handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("playerdatasync.premium.admin.update")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        
        PremiumUpdateChecker updateChecker = plugin.getUpdateChecker();
        if (updateChecker == null) {
            sender.sendMessage("§cUpdate checker is not initialized.");
            return true;
        }
        
        String action = args.length > 2 ? args[2].toLowerCase() : "check";
        
        switch (action) {
            case "check":
                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Checking for updates...");
                updateChecker.check();
                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Update check initiated. Check console for results.");
                return true;
                
            default:
                sender.sendMessage("§8[§6PlayerDataSync Premium§8] §7Usage: /sync update check");
                return true;
        }
    }
}
