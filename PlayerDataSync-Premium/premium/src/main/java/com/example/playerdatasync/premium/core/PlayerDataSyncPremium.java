package com.example.playerdatasync.premium.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import net.milkbowl.vault.economy.Economy;

import com.example.playerdatasync.premium.database.ConnectionPool;
import com.example.playerdatasync.premium.database.DatabaseManager;
import com.example.playerdatasync.premium.integration.InventoryViewerIntegrationManager;
import com.example.playerdatasync.premium.listeners.PlayerDataListener;
import com.example.playerdatasync.premium.listeners.ServerSwitchListener;
import com.example.playerdatasync.premium.managers.AdvancementSyncManager;
import com.example.playerdatasync.premium.managers.BackupManager;
import com.example.playerdatasync.premium.managers.ConfigManager;
import com.example.playerdatasync.premium.managers.MessageManager;
import com.example.playerdatasync.premium.commands.SyncCommand;
import com.example.playerdatasync.premium.api.PremiumUpdateChecker;
import com.example.playerdatasync.premium.api.LicenseValidator;
import com.example.playerdatasync.premium.managers.LicenseManager;
import com.example.playerdatasync.premium.utils.VersionCompatibility;
import com.example.playerdatasync.premium.utils.SchedulerUtils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;
import java.io.FileWriter;
import java.util.logging.Level;

/**
 * PlayerDataSync Premium - Premium version with license validation
 * 
 * This is the premium version of PlayerDataSync that requires a valid license key
 * from CraftingStudio Pro to function.
 */
public class PlayerDataSyncPremium extends JavaPlugin {
    private Connection connection;
    private ConnectionPool connectionPool;
    private String databaseType;
    private String databaseUrl;
    private String databaseUser;
    private String databasePassword;
    private String tablePrefix;
    
    // Basic sync options
    private boolean syncCoordinates;
    private boolean syncXp;
    private boolean syncGamemode;
    private boolean syncEnderchest;
    private boolean syncInventory;
    private boolean syncHealth;
    private boolean syncHunger;
    private boolean syncPosition;
    private boolean syncAchievements;
    
    // Extended sync options
    private boolean syncArmor;
    private boolean syncOffhand;
    private boolean syncEffects;
    private boolean syncStatistics;
    private boolean syncAttributes;
    private boolean syncPermissions;
    private boolean syncEconomy;
    private Economy economyProvider;

    private boolean bungeecordIntegrationEnabled;

    private DatabaseManager databaseManager;
    private AdvancementSyncManager advancementSyncManager;
    private ConfigManager configManager;
    private BackupManager backupManager;
    private InventoryViewerIntegrationManager inventoryViewerIntegrationManager;
    private int autosaveIntervalSeconds;
    private BukkitTask autosaveTask;
    private MessageManager messageManager;
    private Metrics metrics;
    
    // Premium components
    private LicenseManager licenseManager;
    private PremiumUpdateChecker updateChecker;

    @Override
    public void onEnable() {
        getLogger().info("================================================");
        getLogger().info("Enabling PlayerDataSync Premium...");
        getLogger().info("================================================");
        
        // Check server version compatibility
        checkVersionCompatibility();
        
        // Initialize configuration first
        getLogger().info("Saving default configuration...");
        saveDefaultConfig();
        
        // Ensure config file exists and is not empty
        if (getConfig().getKeys(false).isEmpty()) {
            getLogger().warning("Configuration file is empty! Recreating from defaults...");
            reloadConfig();
            saveDefaultConfig();
            
            if (getConfig().getKeys(false).isEmpty()) {
                getLogger().severe("CRITICAL: Failed to load configuration! Plugin will be disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        
        configManager = new ConfigManager(this);
        tablePrefix = configManager.getTablePrefix();

        Level configuredLevel = configManager.getLoggingLevel();
        if (configManager.isDebugMode() && configuredLevel.intValue() > Level.FINE.intValue()) {
            configuredLevel = Level.FINE;
        }
        getLogger().setLevel(configuredLevel);
        
        // Initialize message manager
        messageManager = new MessageManager(this);
        String lang = getConfig().getString("messages.language", "en");
        try {
            messageManager.load(lang);
        } catch (Exception e) {
            getLogger().warning("Failed to load messages for language " + lang + ", falling back to English");
            try {
                messageManager.load("en");
            } catch (Exception e2) {
                getLogger().severe("Failed to load any message files: " + e2.getMessage());
            }
        }

        if (getConfig().getBoolean("metrics", true)) {
            if (metrics == null) {
                metrics = new Metrics(this, 25037);
            }
        } else {
            metrics = null;
        }

        // ========================================
        // PREMIUM: Initialize License Validation
        // ========================================
        getLogger().info("Initializing license validation...");
        licenseManager = new LicenseManager(this);
        licenseManager.initialize();
        
        // Wait a bit for license validation to complete (async)
        try {
            Thread.sleep(3000); // Wait 3 seconds for initial validation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check if license is valid
        if (!licenseManager.isLicenseValid()) {
            getLogger().severe("================================================");
            getLogger().severe("PlayerDataSync Premium - LICENSE VALIDATION FAILED!");
            getLogger().severe("The plugin requires a valid license key to function.");
            getLogger().severe("Please configure your license key in config.yml:");
            getLogger().severe("license:");
            getLogger().severe("  key: YOUR-LICENSE-KEY-HERE");
            getLogger().severe("================================================");
            getLogger().severe("The plugin will be disabled in 30 seconds if the license is not valid.");
            
            // Disable plugin after 30 seconds if license is still invalid
            SchedulerUtils.runTaskLater(this, () -> {
                if (!licenseManager.isLicenseValid()) {
                    getLogger().severe("License is still invalid. Disabling plugin...");
                    getServer().getPluginManager().disablePlugin(this);
                }
            }, 600L); // 30 seconds
            
            // Don't continue initialization if license is invalid
            // But allow some time for validation to complete
            return;
        }
        
        getLogger().info("License validated successfully! Continuing initialization...");
        
        // Initialize database connection
        databaseType = getConfig().getString("database.type", "mysql");
        try {
            if (databaseType.equalsIgnoreCase("mysql")) {
                String host = getConfig().getString("database.mysql.host", "localhost");
                int port = getConfig().getInt("database.mysql.port", 3306);
                String database = getConfig().getString("database.mysql.database", "minecraft");
                databaseUser = getConfig().getString("database.mysql.user", "root");
                databasePassword = getConfig().getString("database.mysql.password", "");
                
                databaseUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=true&failOverReadOnly=false&maxReconnects=3", 
                    host, port, database, getConfig().getBoolean("database.mysql.ssl", false));
                
                connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
                getLogger().info("Connected to MySQL database at " + host + ":" + port + "/" + database);
                
                if (getConfig().getBoolean("performance.connection_pooling", true)) {
                    int maxConnections = getConfig().getInt("database.mysql.max_connections", 10);
                    connectionPool = new ConnectionPool(this, databaseUrl, databaseUser, databasePassword, maxConnections);
                    connectionPool.initialize();
                }
            } else if (databaseType.equalsIgnoreCase("sqlite")) {
                String file = getConfig().getString("database.sqlite.file", "plugins/PlayerDataSync-Premium/playerdata.db");
                java.io.File dbFile = new java.io.File(file);
                if (!dbFile.getParentFile().exists()) {
                    dbFile.getParentFile().mkdirs();
                }
                databaseUrl = "jdbc:sqlite:" + file;
                databaseUser = null;
                databasePassword = null;
                connection = DriverManager.getConnection(databaseUrl);
                getLogger().info("Connected to SQLite database at " + file);
            } else {
                getLogger().severe("Unsupported database type: " + databaseType + ". Supported types: mysql, sqlite");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } catch (SQLException e) {
            getLogger().severe("Could not connect to " + databaseType + " database: " + e.getMessage());
            getLogger().severe("Please check your database configuration and ensure the database server is running");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadSyncSettings();
        advancementSyncManager = new AdvancementSyncManager(this);
        bungeecordIntegrationEnabled = getConfig().getBoolean("integrations.bungeecord", false);
        if (bungeecordIntegrationEnabled) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            getLogger().info("BungeeCord integration enabled. Plugin messaging channel registered.");
        }

        autosaveIntervalSeconds = getConfig().getInt("autosave.interval", 1);

        if (autosaveIntervalSeconds > 0) {
            long ticks = autosaveIntervalSeconds * 20L;
            autosaveTask = SchedulerUtils.runTaskTimerAsync(this, () -> {
                try {
                    int savedCount = 0;
                    long startTime = System.currentTimeMillis();
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        try {
                            if (databaseManager.savePlayer(player)) {
                                savedCount++;
                            } else {
                                getLogger().warning("Failed to autosave data for " + player.getName()
                                    + ": See previous log entries for details.");
                            }
                        } catch (Exception e) {
                            getLogger().warning("Failed to autosave data for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                    
                    long endTime = System.currentTimeMillis();
                    if (savedCount > 0 && isPerformanceLoggingEnabled()) {
                        getLogger().info("Autosaved data for " + savedCount + " players in " +
                            (endTime - startTime) + "ms");
                    }
                } catch (Exception e) {
                    getLogger().severe("Error during autosave: " + e.getMessage());
                }
            }, ticks, ticks);
            getLogger().info("Autosave task scheduled with interval: " + autosaveIntervalSeconds + " seconds");
        }

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        boolean invSeeIntegration = getConfig().getBoolean("integrations.invsee", true);
        boolean openInvIntegration = getConfig().getBoolean("integrations.openinv", true);
        if (invSeeIntegration || openInvIntegration) {
            inventoryViewerIntegrationManager = new InventoryViewerIntegrationManager(this, databaseManager,
                invSeeIntegration, openInvIntegration);
        }

        configureEconomyIntegration();
        
        // Initialize backup manager
        backupManager = new BackupManager(this);
        backupManager.startAutomaticBackups();
        
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, databaseManager), this);
        getServer().getPluginManager().registerEvents(new ServerSwitchListener(this, databaseManager), this);
        if (getCommand("sync") != null) {
            SyncCommand syncCommand = new SyncCommand(this);
            getCommand("sync").setExecutor(syncCommand);
            getCommand("sync").setTabCompleter(syncCommand);
        }
        
        // ========================================
        // PREMIUM: Initialize Update Checker
        // ========================================
        updateChecker = new PremiumUpdateChecker(this);
        updateChecker.check();
        
        if (SchedulerUtils.isFolia()) {
            getLogger().info("Folia detected - using Folia-compatible schedulers");
        }
        
        getLogger().info("================================================");
        getLogger().info("PlayerDataSync Premium enabled successfully!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("License: Valid");
        getLogger().info("================================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PlayerDataSync Premium...");

        // Cancel autosave task
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
            getLogger().info("Autosave task cancelled");
        }

        if (bungeecordIntegrationEnabled) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        }
        
        // Save all online players before shutdown
        if (databaseManager != null) {
            try {
                int savedCount = 0;
                long startTime = System.currentTimeMillis();
                
                if (syncEconomy) {
                    getLogger().info("Reconfiguring economy integration for shutdown save...");
                    configureEconomyIntegration();
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        if (syncEconomy && economyProvider != null) {
                            try {
                                double currentBalance = economyProvider.getBalance(player);
                                getLogger().fine("Current balance for " + player.getName() + " before shutdown save: " + currentBalance);
                            } catch (Exception e) {
                                getLogger().warning("Could not read balance for " + player.getName() + " before shutdown: " + e.getMessage());
                            }
                        }
                        
                        if (databaseManager.savePlayer(player)) {
                            savedCount++;
                            getLogger().fine("Saved data for " + player.getName() + " during shutdown");
                        } else {
                            getLogger().severe("Failed to save data for " + player.getName()
                                + " during shutdown: See previous log entries for details.");
                        }
                    } catch (Exception e) {
                        getLogger().severe("Failed to save data for " + player.getName() + " during shutdown: " + e.getMessage());
                        getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                    }
                }
                
                long endTime = System.currentTimeMillis();
                if (savedCount > 0) {
                    getLogger().info("Saved data for " + savedCount + " players during shutdown in " + 
                        (endTime - startTime) + "ms (including economy balances)");
                } else {
                    getLogger().warning("No players were saved during shutdown - this may cause data loss!");
                }
            } catch (Exception e) {
                getLogger().severe("Error saving players during shutdown: " + e.getMessage());
                getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
            }
        }
        
        // Stop backup manager
        if (backupManager != null) {
            backupManager.stopAutomaticBackups();
            backupManager = null;
        }

        if (advancementSyncManager != null) {
            advancementSyncManager.shutdown();
            advancementSyncManager = null;
        }

        if (inventoryViewerIntegrationManager != null) {
            inventoryViewerIntegrationManager.shutdown();
            inventoryViewerIntegrationManager = null;
        }

        // Shutdown license manager
        if (licenseManager != null) {
            licenseManager.shutdown();
            licenseManager = null;
        }

        // Shutdown connection pool
        if (connectionPool != null) {
            connectionPool.shutdown();
            connectionPool = null;
        }
        
        // Close database connection
        if (connection != null) {
            try {
                connection.close();
                if (databaseType.equalsIgnoreCase("mysql")) {
                    getLogger().info("MySQL connection closed");
                } else {
                    getLogger().info("SQLite connection closed");
                }
            } catch (SQLException e) {
                getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        }
        
        getLogger().info("PlayerDataSync Premium disabled successfully");
    }

    // ... (Copy all other methods from PlayerDataSync.java)
    // For brevity, I'll include the essential methods and refer to the original file for the rest
    
    private Connection createConnection() throws SQLException {
        if (databaseType.equalsIgnoreCase("mysql")) {
            return DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
        }
        return DriverManager.getConnection(databaseUrl);
    }

    public synchronized Connection getConnection() {
        try {
            if (connectionPool != null) {
                return connectionPool.getConnection();
            }
            
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connection = createConnection();
                getLogger().info("Reconnected to database");
            }
        } catch (SQLException e) {
            getLogger().severe("Could not establish database connection: " + e.getMessage());
        }
        return connection;
    }
    
    public void returnConnection(Connection conn) {
        if (connectionPool != null && conn != null) {
            connectionPool.returnConnection(conn);
        }
    }

    // ... (Include all sync option getters/setters and other methods from PlayerDataSync.java)
    // For now, I'll create a simplified version - you'll need to copy the full implementation
    
    private void loadSyncSettings() {
        syncCoordinates = getConfig().getBoolean("sync.coordinates", true);
        syncXp = getConfig().getBoolean("sync.xp", true);
        syncGamemode = getConfig().getBoolean("sync.gamemode", true);
        syncEnderchest = getConfig().getBoolean("sync.enderchest", true);
        syncInventory = getConfig().getBoolean("sync.inventory", true);
        syncHealth = getConfig().getBoolean("sync.health", true);
        syncHunger = getConfig().getBoolean("sync.hunger", true);
        syncPosition = getConfig().getBoolean("sync.position", true);
        
        syncArmor = getConfig().getBoolean("sync.armor", true);
        
        syncOffhand = VersionCompatibility.isOffhandSupported() && 
                     getConfig().getBoolean("sync.offhand", true);
        if (!VersionCompatibility.isOffhandSupported() && getConfig().getBoolean("sync.offhand", true)) {
            getLogger().info("Offhand sync disabled - requires Minecraft 1.9+");
            getConfig().set("sync.offhand", false);
        }
        
        syncEffects = getConfig().getBoolean("sync.effects", true);
        syncStatistics = getConfig().getBoolean("sync.statistics", true);
        
        syncAttributes = VersionCompatibility.isAttributesSupported() && 
                        getConfig().getBoolean("sync.attributes", true);
        if (!VersionCompatibility.isAttributesSupported() && getConfig().getBoolean("sync.attributes", true)) {
            getLogger().info("Attribute sync disabled - requires Minecraft 1.9+");
            getConfig().set("sync.attributes", false);
        }
        
        syncAchievements = VersionCompatibility.isAdvancementsSupported() && 
                          getConfig().getBoolean("sync.achievements", true);
        if (!VersionCompatibility.isAdvancementsSupported() && getConfig().getBoolean("sync.achievements", true)) {
            getLogger().info("Advancement sync disabled - requires Minecraft 1.12+");
            getConfig().set("sync.achievements", false);
        }
        
        syncPermissions = getConfig().getBoolean("sync.permissions", false);
        syncEconomy = getConfig().getBoolean("sync.economy", false);
        
        saveConfig();
    }

    private void checkVersionCompatibility() {
        if (!getConfig().getBoolean("compatibility.version_check", true)) {
            getLogger().info("Version compatibility checking is disabled in config");
            return;
        }
        
        try {
            String serverVersion = Bukkit.getServer().getBukkitVersion();
            String pluginApiVersion = getDescription().getAPIVersion();
            
            getLogger().info("Server version: " + serverVersion);
            getLogger().info("Plugin API version: " + pluginApiVersion);
            
            boolean isSupportedVersion = false;
            String versionInfo = "";
            
            if (VersionCompatibility.isVersion1_8()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.8 - Full compatibility confirmed";
            } else if (VersionCompatibility.isVersion1_9_to_1_11()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.9-1.11 - Full compatibility confirmed";
            } else if (VersionCompatibility.isVersion1_12()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.12 - Full compatibility confirmed";
            } else if (VersionCompatibility.isVersion1_13_to_1_16()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.13-1.16 - Full compatibility confirmed";
            } else if (VersionCompatibility.isVersion1_17()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.17 - Full compatibility confirmed";
            } else if (VersionCompatibility.isVersion1_18_to_1_20()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.18-1.20 - Full compatibility confirmed";
            } else if (VersionCompatibility.isVersion1_21_Plus()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.21+ - Full compatibility confirmed";
            }
            
            if (isSupportedVersion) {
                getLogger().info("✅ " + versionInfo);
            } else {
                getLogger().warning("================================================");
                getLogger().warning("VERSION COMPATIBILITY WARNING:");
                getLogger().warning("This plugin supports Minecraft 1.8 to 1.21.11");
                getLogger().warning("Current server version: " + serverVersion);
                getLogger().warning("Some features may not work correctly");
                getLogger().warning("================================================");
            }
            
            if (VersionCompatibility.isAttributesSupported()) {
                try {
                    org.bukkit.attribute.Attribute.values();
                    getLogger().info("Attribute API compatibility: OK");
                } catch (Exception e) {
                    getLogger().severe("CRITICAL: Attribute API compatibility issue detected!");
                }
            } else {
                getLogger().info("Attribute API not available (requires 1.9+) - attribute sync will be disabled");
            }
            
            if (!VersionCompatibility.isOffhandSupported()) {
                getLogger().info("ℹ️  Offhand sync disabled (requires 1.9+)");
            }
            if (!VersionCompatibility.isAdvancementsSupported()) {
                getLogger().info("ℹ️  Advancements sync disabled (requires 1.12+)");
            }
            
            getLogger().info("✅ Running on Minecraft " + VersionCompatibility.getVersionString() + 
                " - Full compatibility confirmed");
            
        } catch (Exception e) {
            getLogger().warning("Could not perform version compatibility check: " + e.getMessage());
        }
    }

    private void configureEconomyIntegration() {
        if (!syncEconomy) {
            economyProvider = null;
            return;
        }

        if (setupEconomyIntegration()) {
            getLogger().info("Vault integration enabled for economy sync.");
        } else {
            economyProvider = null;
            syncEconomy = false;
            getConfig().set("sync.economy", false);
            saveConfig();
            getLogger().warning("Economy sync has been disabled because Vault or an economy provider is unavailable.");
        }
    }

    private boolean setupEconomyIntegration() {
        economyProvider = null;

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault plugin not found! Economy sync requires Vault.");
            return false;
        }

        RegisteredServiceProvider<Economy> registration =
            getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            getLogger().warning("No Vault economy provider registration found. Economy sync requires an economy plugin.");
            return false;
        }

        Economy provider = registration.getProvider();
        if (provider == null) {
            getLogger().warning("Vault returned a null economy provider. Economy sync cannot continue.");
            return false;
        }

        economyProvider = provider;
        getLogger().info("Hooked into Vault economy provider: " + provider.getName());
        return true;
    }

    // Getter methods
    public boolean isSyncCoordinates() { return syncCoordinates; }
    public boolean isSyncXp() { return syncXp; }
    public boolean isSyncGamemode() { return syncGamemode; }
    public boolean isSyncEnderchest() { return syncEnderchest; }
    public boolean isSyncInventory() { return syncInventory; }
    public boolean isSyncHealth() { return syncHealth; }
    public boolean isSyncHunger() { return syncHunger; }
    public boolean isSyncPosition() { return syncPosition; }
    public boolean isSyncAchievements() { return syncAchievements; }
    public boolean isSyncArmor() { return syncArmor; }
    public boolean isSyncOffhand() { return syncOffhand; }
    public boolean isSyncEffects() { return syncEffects; }
    public boolean isSyncStatistics() { return syncStatistics; }
    public boolean isSyncAttributes() { return syncAttributes; }
    public boolean isSyncPermissions() { return syncPermissions; }
    public boolean isSyncEconomy() { return syncEconomy; }
    
    // Setter methods
    public void setSyncCoordinates(boolean value) {
        this.syncCoordinates = value;
        getConfig().set("sync.coordinates", value);
        saveConfig();
    }

    public void setSyncXp(boolean value) {
        this.syncXp = value;
        getConfig().set("sync.xp", value);
        saveConfig();
    }

    public void setSyncGamemode(boolean value) {
        this.syncGamemode = value;
        getConfig().set("sync.gamemode", value);
        saveConfig();
    }

    public void setSyncEnderchest(boolean value) {
        this.syncEnderchest = value;
        getConfig().set("sync.enderchest", value);
        saveConfig();
    }

    public void setSyncInventory(boolean value) {
        this.syncInventory = value;
        getConfig().set("sync.inventory", value);
        saveConfig();
    }

    public void setSyncHealth(boolean value) {
        this.syncHealth = value;
        getConfig().set("sync.health", value);
        saveConfig();
    }

    public void setSyncHunger(boolean value) {
        this.syncHunger = value;
        getConfig().set("sync.hunger", value);
        saveConfig();
    }

    public void setSyncPosition(boolean value) {
        this.syncPosition = value;
        getConfig().set("sync.position", value);
        saveConfig();
    }

    public void setSyncAchievements(boolean value) {
        this.syncAchievements = value;
        getConfig().set("sync.achievements", value);
        saveConfig();
    }
    
    public void setSyncArmor(boolean value) {
        this.syncArmor = value;
        getConfig().set("sync.armor", value);
        saveConfig();
    }
    
    public void setSyncOffhand(boolean value) {
        this.syncOffhand = value;
        getConfig().set("sync.offhand", value);
        saveConfig();
    }
    
    public void setSyncEffects(boolean value) {
        this.syncEffects = value;
        getConfig().set("sync.effects", value);
        saveConfig();
    }
    
    public void setSyncStatistics(boolean value) {
        this.syncStatistics = value;
        getConfig().set("sync.statistics", value);
        saveConfig();
    }
    
    public void setSyncAttributes(boolean value) {
        this.syncAttributes = value;
        getConfig().set("sync.attributes", value);
        saveConfig();
    }
    
    public void setSyncPermissions(boolean value) {
        this.syncPermissions = value;
        getConfig().set("sync.permissions", value);
        saveConfig();
    }
    
    public void setSyncEconomy(boolean value) {
        this.syncEconomy = value;
        getConfig().set("sync.economy", value);
        configureEconomyIntegration();
        saveConfig();
    }
    
    public void reloadPlugin() {
        reloadConfig();

        if (configManager != null) {
            configManager.reloadConfig();
            tablePrefix = configManager.getTablePrefix();
        }

        String lang = getConfig().getString("messages.language", "en");
        messageManager.load(lang);

        if (getConfig().getBoolean("metrics", true)) {
            if (metrics == null) {
                metrics = new Metrics(this, 25037);
            }
        } else {
            metrics = null;
        }

        boolean wasBungeeEnabled = bungeecordIntegrationEnabled;
        bungeecordIntegrationEnabled = getConfig().getBoolean("integrations.bungeecord", false);
        if (bungeecordIntegrationEnabled && !wasBungeeEnabled) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            getLogger().info("BungeeCord integration enabled after reload.");
        } else if (!bungeecordIntegrationEnabled && wasBungeeEnabled) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this);
            getLogger().info("BungeeCord integration disabled after reload.");
        }

        loadSyncSettings();

        boolean invSeeIntegration = getConfig().getBoolean("integrations.invsee", true);
        boolean openInvIntegration = getConfig().getBoolean("integrations.openinv", true);
        if (inventoryViewerIntegrationManager != null) {
            if (!invSeeIntegration && !openInvIntegration) {
                inventoryViewerIntegrationManager.shutdown();
                inventoryViewerIntegrationManager = null;
            } else {
                inventoryViewerIntegrationManager.updateSettings(invSeeIntegration, openInvIntegration);
            }
        } else if (invSeeIntegration || openInvIntegration) {
            inventoryViewerIntegrationManager = new InventoryViewerIntegrationManager(this, databaseManager,
                invSeeIntegration, openInvIntegration);
        }

        if (advancementSyncManager != null) {
            advancementSyncManager.reloadFromConfig();
        }

        int newIntervalSeconds = getConfig().getInt("autosave.interval", 1);
        if (newIntervalSeconds != autosaveIntervalSeconds) {
            autosaveIntervalSeconds = newIntervalSeconds;
            if (autosaveTask != null) {
                autosaveTask.cancel();
                autosaveTask = null;
            }
            if (autosaveIntervalSeconds > 0) {
                long ticks = autosaveIntervalSeconds * 20L;
                autosaveTask = SchedulerUtils.runTaskTimerAsync(this, () -> {
                    try {
                        int savedCount = 0;
                        long startTime = System.currentTimeMillis();
                        
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            try {
                                if (databaseManager.savePlayer(player)) {
                                    savedCount++;
                                } else {
                                    getLogger().warning("Failed to autosave data for " + player.getName()
                                        + ": See previous log entries for details.");
                                }
                            } catch (Exception e) {
                                getLogger().warning("Failed to autosave data for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                        
                        long endTime = System.currentTimeMillis();
                        if (savedCount > 0 && isPerformanceLoggingEnabled()) {
                            getLogger().info("Autosaved data for " + savedCount + " players in " +
                                (endTime - startTime) + "ms");
                        }
                    } catch (Exception e) {
                        getLogger().severe("Error during autosave: " + e.getMessage());
                    }
                }, ticks, ticks);
                getLogger().info("Autosave task restarted with interval: " + autosaveIntervalSeconds + " seconds");
            }
        }
    }
    
    public void triggerEconomySync(Player player) {
        if (!syncEconomy) {
            logDebug("Economy sync disabled, skipping manual trigger for " + player.getName());
            return;
        }
        
        logDebug("Manual economy sync triggered for " + player.getName());
        
        try {
            long startTime = System.currentTimeMillis();
            databaseManager.savePlayer(player);
            long endTime = System.currentTimeMillis();
            
            logDebug("Manual economy sync completed for " + player.getName() + 
                " in " + (endTime - startTime) + "ms");
            
        } catch (Exception e) {
            getLogger().severe("Failed to manually sync economy for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    public void syncPlayerEconomy(Player player) {
        triggerEconomySync(player);
    }

    public void connectPlayerToServer(Player player, String targetServer) {
        if (!bungeecordIntegrationEnabled) {
            getLogger().warning("Attempted to send player " + player.getName()
                + " to server '" + targetServer + "' while BungeeCord integration is disabled.");
            return;
        }

        if (player == null || targetServer == null || targetServer.trim().isEmpty()) {
            getLogger().warning("Invalid target server specified for player transfer.");
            return;
        }

        SchedulerUtils.runTask(this, player, () -> {
            if (!player.isOnline()) {
                return;
            }

            try {
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(targetServer);
                player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
                getLogger().info("Sent player " + player.getName() + " to server '" + targetServer + "'.");
            } catch (Exception e) {
                getLogger().severe("Failed to send player " + player.getName() + " to server '" + targetServer + "': " + e.getMessage());
            }
        });
    }
    
    public ConfigManager getConfigManager() { return configManager; }
    public String getTablePrefix() { return tablePrefix != null ? tablePrefix : "player_data_premium"; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AdvancementSyncManager getAdvancementSyncManager() { return advancementSyncManager; }
    public BackupManager getBackupManager() { return backupManager; }
    public ConnectionPool getConnectionPool() { return connectionPool; }
    public MessageManager getMessageManager() { return messageManager; }
    public Economy getEconomyProvider() { return economyProvider; }
    public boolean isBungeecordIntegrationEnabled() { return bungeecordIntegrationEnabled; }
    
    // Premium getters
    public LicenseManager getLicenseManager() { return licenseManager; }
    public PremiumUpdateChecker getUpdateChecker() { return updateChecker; }
    
    public void logDebug(String message) {
        if (configManager != null && configManager.isDebugMode()) {
            getLogger().log(Level.FINE, message);
        }
    }

    public boolean isDebugEnabled() {
        return configManager != null && configManager.isDebugMode();
    }

    public boolean isPerformanceLoggingEnabled() {
        return configManager != null && configManager.isPerformanceLoggingEnabled();
    }
}
