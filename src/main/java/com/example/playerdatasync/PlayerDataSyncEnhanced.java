package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bstats.bukkit.Metrics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Enhanced PlayerDataSync plugin with expanded features
 * @author DerGamer09
 * @version 1.1.0
 */
public class PlayerDataSyncEnhanced extends JavaPlugin {
    
    // Database connection
    private DatabaseManager databaseManager;
    private Connection connection;
    private String databaseType;
    private String databaseUrl;
    private String databaseUser;
    private String databasePassword;
    
    // Core components
    private MessageManager messageManager;
    private ConfigManager configManager;
    private Metrics metrics;
    
    // Autosave system
    private int autosaveInterval;
    private BukkitTask autosaveTask;
    
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
    
    // Performance and cache settings
    private boolean useConnectionPooling;
    private boolean asyncLoading;
    private int cacheSize;
    private int batchSize;
    
    // Integration flags
    private boolean vaultEnabled;
    private boolean luckPermsEnabled;
    private boolean placeholderAPIEnabled;
    private boolean bungeeCordEnabled;
    
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        
        try {
            // Initialize configuration
            saveDefaultConfig();
            configManager = new ConfigManager(this);
            
            // Initialize message system
            messageManager = new MessageManager(this);
            String lang = getConfig().getString("messages.language", "en");
            messageManager.load(lang);
            
            // Initialize metrics if enabled
            initializeMetrics();
            
            // Setup database connection
            if (!setupDatabase()) {
                getLogger().severe("Failed to setup database connection. Disabling plugin.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            
            // Initialize database manager
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            
            // Load sync settings
            loadSyncSettings();
            
            // Setup autosave system
            setupAutosave();
            
            // Register events and commands
            registerEventsAndCommands();
            
            // Setup integrations
            setupIntegrations();
            
            // Check for updates
            if (getConfig().getBoolean("update_checker.enabled", true)) {
                new UpdateChecker(this, 123166).check();
            }
            
            long loadTime = System.currentTimeMillis() - startTime;
            getLogger().info("PlayerDataSync enabled successfully in " + loadTime + "ms");
            
        } catch (Exception e) {
            getLogger().severe("Failed to enable PlayerDataSync: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        try {
            // Cancel autosave task
            if (autosaveTask != null) {
                autosaveTask.cancel();
            }
            
            // Save all online players
            if (databaseManager != null) {
                getLogger().info("Saving data for " + Bukkit.getOnlinePlayers().size() + " online players...");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        databaseManager.savePlayer(player);
                    } catch (Exception e) {
                        getLogger().warning("Failed to save data for " + player.getName() + ": " + e.getMessage());
                    }
                }
            }
            
            // Close database connection
            closeDatabase();
            
            getLogger().info("PlayerDataSync disabled successfully.");
            
        } catch (Exception e) {
            getLogger().severe("Error during plugin shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initialize metrics system
     */
    private void initializeMetrics() {
        if (getConfig().getBoolean("metrics.bstats", true)) {
            metrics = new Metrics(this, 25037);
            getLogger().info("Metrics collection enabled.");
        } else {
            getLogger().info("Metrics collection disabled.");
        }
    }
    
    /**
     * Setup database connection based on configuration
     */
    private boolean setupDatabase() {
        databaseType = getConfig().getString("database.type", "mysql").toLowerCase();
        
        try {
            switch (databaseType) {
                case "mysql":
                    return setupMySQLConnection();
                case "postgresql":
                    return setupPostgreSQLConnection();
                case "sqlite":
                default:
                    return setupSQLiteConnection();
            }
        } catch (SQLException e) {
            getLogger().severe("Database connection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Setup MySQL database connection
     */
    private boolean setupMySQLConnection() throws SQLException {
        String host = getConfig().getString("database.mysql.host", "localhost");
        int port = getConfig().getInt("database.mysql.port", 3306);
        String database = getConfig().getString("database.mysql.database", "minecraft");
        databaseUser = getConfig().getString("database.mysql.user", "root");
        databasePassword = getConfig().getString("database.mysql.password", "");
        boolean ssl = getConfig().getBoolean("database.mysql.ssl", false);
        int timeout = getConfig().getInt("database.mysql.connection_timeout", 5000);
        
        databaseUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&connectTimeout=%d&useUnicode=true&characterEncoding=utf8",
            host, port, database, ssl, timeout);
        
        connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
        getLogger().info("Connected to MySQL database: " + host + ":" + port + "/" + database);
        return true;
    }
    
    /**
     * Setup PostgreSQL database connection
     */
    private boolean setupPostgreSQLConnection() throws SQLException {
        String host = getConfig().getString("database.postgresql.host", "localhost");
        int port = getConfig().getInt("database.postgresql.port", 5432);
        String database = getConfig().getString("database.postgresql.database", "minecraft");
        databaseUser = getConfig().getString("database.postgresql.user", "postgres");
        databasePassword = getConfig().getString("database.postgresql.password", "");
        boolean ssl = getConfig().getBoolean("database.postgresql.ssl", false);
        
        databaseUrl = String.format("jdbc:postgresql://%s:%d/%s?sslmode=%s",
            host, port, database, ssl ? "require" : "disable");
        
        connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
        getLogger().info("Connected to PostgreSQL database: " + host + ":" + port + "/" + database);
        return true;
    }
    
    /**
     * Setup SQLite database connection
     */
    private boolean setupSQLiteConnection() throws SQLException {
        String file = getConfig().getString("database.sqlite.file", "plugins/PlayerDataSync/playerdata.db");
        databaseUrl = "jdbc:sqlite:" + file;
        databaseUser = null;
        databasePassword = null;
        
        // Ensure directory exists
        java.io.File dbFile = new java.io.File(file);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }
        
        connection = DriverManager.getConnection(databaseUrl);
        getLogger().info("Connected to SQLite database: " + file);
        return true;
    }
    
    /**
     * Close database connection
     */
    private void closeDatabase() {
        if (connection != null) {
            try {
                connection.close();
                getLogger().info("Database connection closed successfully.");
            } catch (SQLException e) {
                getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get database connection with automatic reconnection
     */
    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                getLogger().info("Reconnecting to database...");
                setupDatabase();
            }
        } catch (SQLException e) {
            getLogger().severe("Could not establish database connection: " + e.getMessage());
        }
        return connection;
    }
    
    /**
     * Load synchronization settings from configuration
     */
    private void loadSyncSettings() {
        // Basic sync options
        syncCoordinates = getConfig().getBoolean("sync.coordinates", true);
        syncXp = getConfig().getBoolean("sync.xp", true);
        syncGamemode = getConfig().getBoolean("sync.gamemode", true);
        syncEnderchest = getConfig().getBoolean("sync.enderchest", true);
        syncInventory = getConfig().getBoolean("sync.inventory", true);
        syncHealth = getConfig().getBoolean("sync.health", true);
        syncHunger = getConfig().getBoolean("sync.hunger", true);
        syncPosition = getConfig().getBoolean("sync.position", true);
        syncAchievements = getConfig().getBoolean("sync.achievements", true);
        
        // Extended sync options
        syncArmor = getConfig().getBoolean("sync.armor", true);
        syncOffhand = getConfig().getBoolean("sync.offhand", true);
        syncEffects = getConfig().getBoolean("sync.effects", true);
        syncStatistics = getConfig().getBoolean("sync.statistics", true);
        syncAttributes = getConfig().getBoolean("sync.attributes", true);
        syncPermissions = getConfig().getBoolean("sync.permissions", false);
        syncEconomy = getConfig().getBoolean("sync.economy", false);
        
        // Performance settings
        useConnectionPooling = getConfig().getBoolean("performance.connection_pooling", true);
        asyncLoading = getConfig().getBoolean("performance.async_loading", true);
        cacheSize = getConfig().getInt("performance.cache_size", 100);
        batchSize = getConfig().getInt("performance.batch_size", 50);
        
        getLogger().info("Loaded synchronization settings.");
    }
    
    /**
     * Setup autosave system
     */
    private void setupAutosave() {
        autosaveInterval = getConfig().getInt("autosave.interval", 5);
        boolean autosaveEnabled = getConfig().getBoolean("autosave.enabled", true);
        boolean async = getConfig().getBoolean("autosave.async", true);
        
        if (autosaveEnabled && autosaveInterval > 0) {
            long ticks = autosaveInterval * 1200L; // Convert minutes to ticks
            
            Runnable saveTask = () -> {
                try {
                    int savedCount = 0;
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        databaseManager.savePlayer(player);
                        savedCount++;
                    }
                    if (getConfig().getBoolean("logging.log_performance", false)) {
                        getLogger().info("Autosave completed for " + savedCount + " players.");
                    }
                } catch (Exception e) {
                    getLogger().severe("Error during autosave: " + e.getMessage());
                }
            };
            
            if (async) {
                autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, saveTask, ticks, ticks);
            } else {
                autosaveTask = Bukkit.getScheduler().runTaskTimer(this, saveTask, ticks, ticks);
            }
            
            getLogger().info("Autosave enabled: " + autosaveInterval + " minutes (" + (async ? "async" : "sync") + ")");
        } else {
            getLogger().info("Autosave disabled.");
        }
    }
    
    /**
     * Register events and commands
     */
    private void registerEventsAndCommands() {
        // Register event listener
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, databaseManager), this);
        
        // Register commands
        if (getCommand("sync") != null) {
            getCommand("sync").setExecutor(new SyncCommand(this));
            getCommand("sync").setTabCompleter(new SyncCommand(this));
        }
        
        getLogger().info("Events and commands registered.");
    }
    
    /**
     * Setup plugin integrations
     */
    private void setupIntegrations() {
        // Check for Vault
        vaultEnabled = getConfig().getBoolean("integrations.vault", false) && 
                       getServer().getPluginManager().getPlugin("Vault") != null;
        if (vaultEnabled) {
            getLogger().info("Vault integration enabled.");
        }
        
        // Check for LuckPerms
        luckPermsEnabled = getConfig().getBoolean("integrations.luckperms", false) && 
                          getServer().getPluginManager().getPlugin("LuckPerms") != null;
        if (luckPermsEnabled) {
            getLogger().info("LuckPerms integration enabled.");
        }
        
        // Check for PlaceholderAPI
        placeholderAPIEnabled = getConfig().getBoolean("integrations.placeholderapi", false) && 
                               getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (placeholderAPIEnabled) {
            getLogger().info("PlaceholderAPI integration enabled.");
        }
        
        // BungeeCord mode
        bungeeCordEnabled = getConfig().getBoolean("integrations.bungeecord", false);
        if (bungeeCordEnabled) {
            getLogger().info("BungeeCord mode enabled.");
        }
    }
    
    /**
     * Reload plugin configuration and settings
     */
    public void reloadPlugin() {
        try {
            reloadConfig();
            
            // Reload messages
            String lang = getConfig().getString("messages.language", "en");
            messageManager.load(lang);
            
            // Reload metrics
            if (getConfig().getBoolean("metrics.bstats", true)) {
                if (metrics == null) {
                    metrics = new Metrics(this, 25037);
                }
            } else {
                metrics = null;
            }
            
            // Reload sync settings
            loadSyncSettings();
            
            // Reload autosave
            int newInterval = getConfig().getInt("autosave.interval", 5);
            boolean autosaveEnabled = getConfig().getBoolean("autosave.enabled", true);
            
            if (newInterval != autosaveInterval || !autosaveEnabled) {
                if (autosaveTask != null) {
                    autosaveTask.cancel();
                }
                setupAutosave();
            }
            
            // Reload integrations
            setupIntegrations();
            
            getLogger().info("Configuration reloaded successfully.");
            
        } catch (Exception e) {
            getLogger().severe("Error reloading configuration: " + e.getMessage());
            throw new RuntimeException("Failed to reload configuration", e);
        }
    }
    
    // Getter methods for sync options
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
    
    // Setter methods for sync options
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
        saveConfig(); 
    }
    
    // Getter methods for components and settings
    public MessageManager getMessageManager() { return messageManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public boolean isVaultEnabled() { return vaultEnabled; }
    public boolean isLuckPermsEnabled() { return luckPermsEnabled; }
    public boolean isPlaceholderAPIEnabled() { return placeholderAPIEnabled; }
    public boolean isBungeeCordEnabled() { return bungeeCordEnabled; }
    public boolean isAsyncLoading() { return asyncLoading; }
    public int getCacheSize() { return cacheSize; }
    public int getBatchSize() { return batchSize; }
}
