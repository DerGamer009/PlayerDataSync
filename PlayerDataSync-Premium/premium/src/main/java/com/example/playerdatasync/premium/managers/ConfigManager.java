package com.example.playerdatasync.premium.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

import com.example.playerdatasync.premium.core.PlayerDataSyncPremium;

/**
 * Configuration manager for PlayerDataSync
 * Handles validation, migration, and advanced configuration features
 */
public class ConfigManager {
    private final PlayerDataSyncPremium plugin;
    private FileConfiguration config;
    private File configFile;
    
    // Configuration version for migration
    private static final int CURRENT_CONFIG_VERSION = 6;
    
    public ConfigManager(PlayerDataSyncPremium plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        
        validateAndMigrateConfig();
    }
    
    /**
     * Validate and migrate configuration if needed
     */
    private void validateAndMigrateConfig() {
        // Check if config is completely empty
        if (config.getKeys(false).isEmpty()) {
            plugin.getLogger().severe("Configuration file is completely empty! This indicates a serious problem.");
            plugin.getLogger().severe("Please check if the plugin JAR file is corrupted or if there are permission issues.");
            return;
        }
        
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
            if (fromVersion < 2) {
                migrateFromV1ToV2();
                fromVersion = 2;
            }

            if (fromVersion < 3) {
                migrateFromV2ToV3();
                fromVersion = 3;
            }

            if (fromVersion < 4) {
                migrateFromV3ToV4();
                fromVersion = 4;
            }

            if (fromVersion < 5) {
                migrateFromV4ToV5();
                fromVersion = 5;
            }

            if (fromVersion < 6) {
                migrateFromV5ToV6();
            }

            plugin.getLogger().info("Configuration migration completed successfully.");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Configuration migration failed: " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
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

    private void migrateFromV2ToV3() {
        addDefaultIfMissing("database.table_prefix", "player_data");

        String prefix = config.getString("database.table_prefix", "player_data");
        String sanitized = sanitizeTablePrefix(prefix);
        if (!sanitized.equals(prefix)) {
            config.set("database.table_prefix", sanitized);
            plugin.getLogger().info("Sanitized database.table_prefix from '" + prefix + "' to '" + sanitized + "'.");
        }
    }

    private void migrateFromV3ToV4() {
        // No longer required. Previous versions introduced editor configuration defaults
        // that have since been removed.
    }

    private void migrateFromV4ToV5() {
        if (config.contains("editor")) {
            plugin.getLogger().info("Removing deprecated editor.* configuration entries.");
            config.set("editor", null);
        }
    }

    private void migrateFromV5ToV6() {
        addDefaultIfMissing("integrations.invsee", true);
        addDefaultIfMissing("integrations.openinv", true);
    }
    
    /**
     * Initialize default configuration if completely missing
     */
    public void initializeDefaultConfig() {
        plugin.getLogger().info("Initializing default configuration...");
        
        // Add all essential configuration sections
        addDefaultIfMissing("config-version", CURRENT_CONFIG_VERSION);
        
        // Server configuration
        addDefaultIfMissing("server.id", "default");
        
        // Database configuration
        addDefaultIfMissing("database.type", "mysql");
        addDefaultIfMissing("database.mysql.host", "localhost");
        addDefaultIfMissing("database.mysql.port", 3306);
        addDefaultIfMissing("database.mysql.database", "minecraft");
        addDefaultIfMissing("database.mysql.user", "root");
        addDefaultIfMissing("database.mysql.password", "password");
        addDefaultIfMissing("database.mysql.ssl", false);
        addDefaultIfMissing("database.mysql.connection_timeout", 5000);
        addDefaultIfMissing("database.mysql.max_connections", 10);
        addDefaultIfMissing("database.table_prefix", "player_data");
        addDefaultIfMissing("database.sqlite.file", "plugins/PlayerDataSync/playerdata.db");
        
        // Sync configuration
        addDefaultIfMissing("sync.coordinates", true);
        addDefaultIfMissing("sync.position", true);
        addDefaultIfMissing("sync.xp", true);
        addDefaultIfMissing("sync.gamemode", true);
        addDefaultIfMissing("sync.inventory", true);
        addDefaultIfMissing("sync.enderchest", true);
        addDefaultIfMissing("sync.armor", true);
        addDefaultIfMissing("sync.offhand", true);
        addDefaultIfMissing("sync.health", true);
        addDefaultIfMissing("sync.hunger", true);
        addDefaultIfMissing("sync.effects", true);
        addDefaultIfMissing("sync.achievements", true);
        addDefaultIfMissing("sync.statistics", true);
        addDefaultIfMissing("sync.attributes", true);
        addDefaultIfMissing("sync.permissions", false);
        addDefaultIfMissing("sync.economy", false);
        
        // Autosave configuration
        addDefaultIfMissing("autosave.enabled", true);
        addDefaultIfMissing("autosave.interval", 1);
        addDefaultIfMissing("autosave.on_world_change", true);
        addDefaultIfMissing("autosave.on_death", true);
        addDefaultIfMissing("autosave.async", true);
        
        // Performance configuration
        addDefaultIfMissing("performance.batch_size", 50);
        addDefaultIfMissing("performance.cache_size", 100);
        addDefaultIfMissing("performance.cache_ttl", 300000);
        addDefaultIfMissing("performance.cache_compression", true);
        addDefaultIfMissing("performance.connection_pooling", true);
        addDefaultIfMissing("performance.async_loading", true);
        addDefaultIfMissing("performance.disable_achievement_sync_on_large_amounts", true);
        addDefaultIfMissing("performance.achievement_batch_size", 50);
        addDefaultIfMissing("performance.achievement_timeout_ms", 5000);
        addDefaultIfMissing("performance.max_achievements_per_player", 2000);
        addDefaultIfMissing("performance.preload_advancements_on_startup", true);
        addDefaultIfMissing("performance.advancement_import_batch_size", 250);
        addDefaultIfMissing("performance.player_advancement_import_batch_size", 150);
        addDefaultIfMissing("performance.automatic_player_advancement_import", true);
        
        // Compatibility configuration
        addDefaultIfMissing("compatibility.safe_attribute_sync", true);
        addDefaultIfMissing("compatibility.disable_attributes_on_error", false);
        addDefaultIfMissing("compatibility.version_check", true);
        addDefaultIfMissing("compatibility.legacy_1_20_support", true);
        addDefaultIfMissing("compatibility.modern_1_21_support", true);
        addDefaultIfMissing("compatibility.disable_achievements_on_critical_error", true);
        
        // Security configuration
        addDefaultIfMissing("security.encrypt_data", false);
        addDefaultIfMissing("security.hash_uuids", false);
        addDefaultIfMissing("security.audit_log", true);
        
        // Logging configuration
        addDefaultIfMissing("logging.level", "INFO");
        addDefaultIfMissing("logging.log_database", false);
        addDefaultIfMissing("logging.log_performance", false);
        addDefaultIfMissing("logging.debug_mode", false);
        
        // Update checker configuration
        addDefaultIfMissing("update_checker.enabled", true);
        addDefaultIfMissing("update_checker.notify_ops", true);
        addDefaultIfMissing("update_checker.auto_download", false);
        addDefaultIfMissing("update_checker.timeout", 10000);
        
        // Metrics configuration
        addDefaultIfMissing("metrics.bstats", true);
        addDefaultIfMissing("metrics.custom_metrics", true);

        addDefaultIfMissing("integrations.invsee", true);
        addDefaultIfMissing("integrations.openinv", true);

        // Editor integration defaults
        // Messages configuration
        addDefaultIfMissing("messages.enabled", true);
        addDefaultIfMissing("messages.show_sync_messages", true);
        addDefaultIfMissing("messages.language", "en");
        addDefaultIfMissing("messages.prefix", "&8[&bPDS&8]");
        addDefaultIfMissing("messages.colors", true);
        
        plugin.getLogger().info("Default configuration initialized successfully!");
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

        String tablePrefix = config.getString("database.table_prefix", "player_data");
        String sanitizedPrefix = sanitizeTablePrefix(tablePrefix);
        if (sanitizedPrefix.isEmpty()) {
            warnings.add("database.table_prefix is empty or invalid. Using default 'player_data'.");
            sanitizedPrefix = "player_data";
        }
        if (!sanitizedPrefix.equals(tablePrefix)) {
            warnings.add("Sanitized database.table_prefix from '" + tablePrefix + "' to '" + sanitizedPrefix + "'.");
            config.set("database.table_prefix", sanitizedPrefix);
        }

        // Validate autosave interval
        int interval = config.getInt("autosave.interval", 1);
        if (interval < 0) {
            warnings.add("Invalid autosave interval: " + interval + ". Using 1 second as default.");
            config.set("autosave.interval", 1);
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
        String logLevelRaw = config.getString("logging.level", "INFO");
        Level resolvedLevel = parseLogLevel(logLevelRaw);
        if (resolvedLevel == null) {
            warnings.add("Invalid logging level: " + logLevelRaw + ". Using INFO as default.");
            config.set("logging.level", "INFO");
        } else {
            config.set("logging.level", resolvedLevel.getName());
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

    public String getTablePrefix() {
        return sanitizeTablePrefix(config.getString("database.table_prefix", "player_data"));
    }
    
    /**
     * Check if database logging is enabled
     */
    public boolean isDatabaseLoggingEnabled() {
        return config.getBoolean("logging.log_database", false);
    }

    private String sanitizeTablePrefix(String prefix) {
        if (prefix == null) {
            return "player_data";
        }

        String sanitized = prefix.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty()) {
            return "player_data";
        }
        return sanitized;
    }

    /**
     * Check if performance logging is enabled
     */
    public boolean isPerformanceLoggingEnabled() {
        return config.getBoolean("logging.log_performance", false);
    }

    public String getServerId() {
        return config.getString("server.id", "default");
    }
    
    /**
     * Get logging level
     */
    public Level getLoggingLevel() {
        Level level = parseLogLevel(config.getString("logging.level", "INFO"));
        return level != null ? level : Level.INFO;
    }

    private Level parseLogLevel(String levelStr) {
        if (levelStr == null) {
            return null;
        }

        String normalized = levelStr.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "WARN":
            case "WARNING":
                return Level.WARNING;
            case "ERROR":
            case "SEVERE":
                return Level.SEVERE;
            case "DEBUG":
            case "FINE":
                return Level.FINE;
            case "TRACE":
            case "FINER":
                return Level.FINER;
            case "FINEST":
                return Level.FINEST;
            case "CONFIG":
                return Level.CONFIG;
            case "ALL":
                return Level.ALL;
            case "OFF":
                return Level.OFF;
            case "INFO":
                return Level.INFO;
            default:
                try {
                    return Level.parse(normalized);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
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
     * Check if sync messages should be shown to players
     */
    public boolean shouldShowSyncMessages() {
        return config.getBoolean("messages.show_sync_messages", true);
    }
    
    /**
     * Get the underlying configuration
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
