package com.example.playerdatasync;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Configuration manager for PlayerDataSync
 * Handles validation, migration, and advanced configuration features
 */
public class ConfigManager {
    private final PlayerDataSync plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Configuration version for migration
    private static final int CURRENT_CONFIG_VERSION = 2;
    
    public ConfigManager(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        
        validateAndMigrateConfig();
    }
    
    /**
     * Validate and migrate configuration if needed
     */
    private void validateAndMigrateConfig() {
        int configVersion = config.getInt("config-version", 1);
        
        if (configVersion < CURRENT_CONFIG_VERSION) {
            plugin.getLogger().info("Migrating configuration from version " + configVersion + " to " + CURRENT_CONFIG_VERSION);
            migrateConfig(configVersion);
        }
        
        // Validate configuration values
        validateConfiguration();
        
        // Set current version
        config.set("config-version", CURRENT_CONFIG_VERSION);
        saveConfig();
    }
    
    /**
     * Migrate configuration from older versions
     */
    private void migrateConfig(int fromVersion) {
        try {
            if (fromVersion == 1) {
                // Migrate from v1 to v2
                migrateFromV1ToV2();
            }
            
            plugin.getLogger().info("Configuration migration completed successfully.");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Configuration migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Migrate configuration from version 1 to version 2
     */
    private void migrateFromV1ToV2() {
        // Move language setting to messages section
        if (config.contains("language")) {
            String language = config.getString("language", "en");
            config.set("messages.language", language);
            config.set("language", null);
        }
        
        // Move metrics setting to metrics section
        if (config.contains("metrics")) {
            boolean metrics = config.getBoolean("metrics", true);
            config.set("metrics.bstats", metrics);
            config.set("metrics", null);
        }
        
        // Add new sections with default values
        addDefaultIfMissing("autosave.enabled", true);
        addDefaultIfMissing("autosave.on_world_change", true);
        addDefaultIfMissing("autosave.on_death", true);
        addDefaultIfMissing("autosave.async", true);
        
        addDefaultIfMissing("performance.batch_size", 50);
        addDefaultIfMissing("performance.cache_size", 100);
        addDefaultIfMissing("performance.connection_pooling", true);
        addDefaultIfMissing("performance.async_loading", true);
        
        addDefaultIfMissing("security.encrypt_data", false);
        addDefaultIfMissing("security.hash_uuids", false);
        addDefaultIfMissing("security.audit_log", true);
        
        addDefaultIfMissing("logging.level", "INFO");
        addDefaultIfMissing("logging.log_database", false);
        addDefaultIfMissing("logging.log_performance", false);
        addDefaultIfMissing("logging.debug_mode", false);
    }
    
    /**
     * Add default value if key is missing
     */
    private void addDefaultIfMissing(String path, Object defaultValue) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
        }
    }
    
    /**
     * Validate configuration values
     */
    private void validateConfiguration() {
        List<String> warnings = new ArrayList<>();
        
        // Validate database settings
        String dbType = config.getString("database.type", "mysql").toLowerCase();
        if (!dbType.equals("mysql") && !dbType.equals("sqlite") && !dbType.equals("postgresql")) {
            warnings.add("Invalid database type: " + dbType + ". Using MySQL as default.");
            config.set("database.type", "mysql");
        }
        
        // Validate autosave interval
        int interval = config.getInt("autosave.interval", 5);
        if (interval < 0) {
            warnings.add("Invalid autosave interval: " + interval + ". Using 5 minutes as default.");
            config.set("autosave.interval", 5);
        }
        
        // Validate cache size
        int cacheSize = config.getInt("performance.cache_size", 100);
        if (cacheSize < 10 || cacheSize > 10000) {
            warnings.add("Invalid cache size: " + cacheSize + ". Using 100 as default.");
            config.set("performance.cache_size", 100);
        }
        
        // Validate batch size
        int batchSize = config.getInt("performance.batch_size", 50);
        if (batchSize < 1 || batchSize > 1000) {
            warnings.add("Invalid batch size: " + batchSize + ". Using 50 as default.");
            config.set("performance.batch_size", 50);
        }
        
        // Validate logging level
        String logLevel = config.getString("logging.level", "INFO").toUpperCase();
        try {
            Level.parse(logLevel);
        } catch (IllegalArgumentException e) {
            warnings.add("Invalid logging level: " + logLevel + ". Using INFO as default.");
            config.set("logging.level", "INFO");
        }
        
        // Report warnings
        if (!warnings.isEmpty()) {
            plugin.getLogger().warning("Configuration validation found issues:");
            for (String warning : warnings) {
                plugin.getLogger().warning("- " + warning);
            }
        }
    }
    
    /**
     * Save configuration to file
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save configuration: " + e.getMessage());
        }
    }
    
    /**
     * Reload configuration from file
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        validateAndMigrateConfig();
    }
    
    /**
     * Get configuration value with type safety
     */
    public <T> T get(String path, T defaultValue, Class<T> type) {
        Object value = config.get(path, defaultValue);
        
        if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            plugin.getLogger().warning("Configuration value at '" + path + "' is not of expected type " + type.getSimpleName());
            return defaultValue;
        }
    }
    
    /**
     * Check if debugging is enabled
     */
    public boolean isDebugMode() {
        return config.getBoolean("logging.debug_mode", false);
    }
    
    /**
     * Check if database logging is enabled
     */
    public boolean isDatabaseLoggingEnabled() {
        return config.getBoolean("logging.log_database", false);
    }
    
    /**
     * Check if performance logging is enabled
     */
    public boolean isPerformanceLoggingEnabled() {
        return config.getBoolean("logging.log_performance", false);
    }
    
    /**
     * Get logging level
     */
    public Level getLoggingLevel() {
        String levelStr = config.getString("logging.level", "INFO");
        try {
            return Level.parse(levelStr);
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }
    
    /**
     * Check if a feature is enabled
     */
    public boolean isFeatureEnabled(String feature) {
        return config.getBoolean("sync." + feature, true);
    }
    
    /**
     * Check if data encryption is enabled
     */
    public boolean isEncryptionEnabled() {
        return config.getBoolean("security.encrypt_data", false);
    }
    
    /**
     * Check if UUID hashing is enabled
     */
    public boolean isUuidHashingEnabled() {
        return config.getBoolean("security.hash_uuids", false);
    }
    
    /**
     * Check if audit logging is enabled
     */
    public boolean isAuditLogEnabled() {
        return config.getBoolean("security.audit_log", true);
    }
    
    /**
     * Get cleanup settings
     */
    public boolean isCleanupEnabled() {
        return config.getBoolean("data_management.cleanup.enabled", false);
    }
    
    public int getCleanupDays() {
        return config.getInt("data_management.cleanup.days_inactive", 90);
    }
    
    /**
     * Get backup settings
     */
    public boolean isBackupEnabled() {
        return config.getBoolean("data_management.backup.enabled", true);
    }
    
    public int getBackupInterval() {
        return config.getInt("data_management.backup.interval", 1440);
    }
    
    public int getBackupsToKeep() {
        return config.getInt("data_management.backup.keep_backups", 7);
    }
    
    /**
     * Get validation settings
     */
    public boolean isValidationEnabled() {
        return config.getBoolean("data_management.validation.enabled", true);
    }
    
    public boolean isStrictValidation() {
        return config.getBoolean("data_management.validation.strict_mode", false);
    }
    
    /**
     * Get the underlying configuration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
