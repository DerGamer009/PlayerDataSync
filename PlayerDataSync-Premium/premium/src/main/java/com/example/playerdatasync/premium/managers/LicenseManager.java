package com.example.playerdatasync.premium.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.playerdatasync.premium.api.LicenseValidator;
import com.example.playerdatasync.premium.api.LicenseValidator.LicenseValidationResult;
import com.example.playerdatasync.premium.utils.SchedulerUtils;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * License Manager for PlayerDataSync Premium
 * Handles license validation, caching, and periodic re-validation
 */
public class LicenseManager {
    private final JavaPlugin plugin;
    private final LicenseValidator validator;
    private String licenseKey;
    private boolean licenseValid = false;
    private boolean licenseChecked = false;
    private long lastValidationTime = 0;
    private static final long REVALIDATION_INTERVAL_MS = 24 * 60 * 60 * 1000; // Revalidate every 24 hours
    private int validationTaskId = -1;
    
    public LicenseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.validator = new LicenseValidator(plugin);
    }
    
    /**
     * Initialize license manager and validate license from config
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        licenseKey = config.getString("license.key", null);
        
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            plugin.getLogger().severe("================================================");
            plugin.getLogger().severe("PlayerDataSync Premium - NO LICENSE KEY FOUND!");
            plugin.getLogger().severe("Please enter your license key in config.yml:");
            plugin.getLogger().severe("license:");
            plugin.getLogger().severe("  key: YOUR-LICENSE-KEY-HERE");
            plugin.getLogger().severe("================================================");
            plugin.getLogger().severe("The plugin will be disabled until a valid license is configured.");
            SchedulerUtils.runTask(plugin, () -> {
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            });
            return;
        }
        
        // Validate license on startup
        validateLicense();
        
        // Schedule periodic re-validation (every 24 hours)
        long intervalTicks = 20 * 60 * 60; // 1 hour in ticks
        validationTaskId = SchedulerUtils.runTaskTimerAsync(plugin, () -> {
            if (shouldRevalidate()) {
                plugin.getLogger().info("[LicenseManager] Performing scheduled license re-validation...");
                validateLicense();
            }
        }, intervalTicks, intervalTicks).getTaskId();
    }
    
    /**
     * Validate the license key
     */
    public void validateLicense() {
        if (licenseKey == null || licenseKey.trim().isEmpty()) {
            licenseValid = false;
            licenseChecked = true;
            return;
        }
        
        plugin.getLogger().info("[LicenseManager] Validating license key...");
        
        validator.validateLicenseAsync(licenseKey).thenAccept(result -> {
            SchedulerUtils.runTask(plugin, () -> {
                licenseValid = result.isValid();
                licenseChecked = true;
                lastValidationTime = System.currentTimeMillis();
                
                if (licenseValid) {
                    plugin.getLogger().info("================================================");
                    plugin.getLogger().info("PlayerDataSync Premium - LICENSE VALIDATED!");
                    if (result.getPurchase() != null) {
                        plugin.getLogger().info("Purchase ID: " + result.getPurchase().getId());
                        plugin.getLogger().info("User ID: " + result.getPurchase().getUserId());
                    }
                    plugin.getLogger().info("================================================");
                } else {
                    plugin.getLogger().severe("================================================");
                    plugin.getLogger().severe("PlayerDataSync Premium - LICENSE VALIDATION FAILED!");
                    plugin.getLogger().severe("Reason: " + (result.getMessage() != null ? result.getMessage() : "Unknown error"));
                    plugin.getLogger().severe("Please check your license key in config.yml");
                    plugin.getLogger().severe("License key: " + maskLicenseKey(licenseKey));
                    plugin.getLogger().severe("================================================");
                    plugin.getLogger().severe("The plugin will be disabled in 30 seconds if the license is not valid.");
                    
                    // Disable plugin after 30 seconds if license is invalid
                    SchedulerUtils.runTaskLater(plugin, () -> {
                        if (!isLicenseValid()) {
                            plugin.getLogger().severe("License is still invalid. Disabling plugin...");
                            plugin.getServer().getPluginManager().disablePlugin(plugin);
                        }
                    }, 600L); // 30 seconds
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("[LicenseManager] License validation error: " + throwable.getMessage());
            plugin.getLogger().log(Level.FINE, "License validation exception", throwable);
            SchedulerUtils.runTask(plugin, () -> {
                licenseValid = false;
                licenseChecked = true;
            });
            return null;
        });
    }
    
    /**
     * Check if license should be revalidated
     */
    private boolean shouldRevalidate() {
        return (System.currentTimeMillis() - lastValidationTime) >= REVALIDATION_INTERVAL_MS;
    }
    
    /**
     * Check if license is valid
     */
    public boolean isLicenseValid() {
        return licenseValid && licenseChecked;
    }
    
    /**
     * Check if license has been checked
     */
    public boolean isLicenseChecked() {
        return licenseChecked;
    }
    
    /**
     * Get the license key (masked for security)
     */
    public String getLicenseKey() {
        return licenseKey != null ? maskLicenseKey(licenseKey) : null;
    }
    
    /**
     * Set a new license key and validate it
     */
    public void setLicenseKey(String newLicenseKey) {
        this.licenseKey = newLicenseKey;
        plugin.getConfig().set("license.key", newLicenseKey);
        plugin.saveConfig();
        validator.clearCache();
        validateLicense();
    }
    
    /**
     * Mask license key for logging (show only first and last 4 characters)
     */
    private String maskLicenseKey(String key) {
        if (key == null || key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
    
    /**
     * Shutdown license manager
     */
    public void shutdown() {
        if (validationTaskId != -1) {
            // Note: On Folia, tasks are cancelled automatically when plugin disables
            if (validationTaskId != -1) {
                try {
                    Bukkit.getScheduler().cancelTask(validationTaskId);
                } catch (Exception e) {
                    // Ignore errors on shutdown
                }
            }
            validationTaskId = -1;
        }
    }
    
    /**
     * Force revalidation of license
     */
    public CompletableFuture<LicenseValidationResult> revalidateLicense() {
        validator.clearCache();
        return validator.validateLicenseAsync(licenseKey).thenApply(result -> {
            SchedulerUtils.runTask(plugin, () -> {
                licenseValid = result.isValid();
                lastValidationTime = System.currentTimeMillis();
                
                if (licenseValid) {
                    plugin.getLogger().info("[LicenseManager] License re-validation successful!");
                } else {
                    plugin.getLogger().warning("[LicenseManager] License re-validation failed: " + result.getMessage());
                }
            });
            return result;
        });
    }
}
