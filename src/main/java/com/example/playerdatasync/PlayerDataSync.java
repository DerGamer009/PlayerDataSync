package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.example.playerdatasync.MessageManager;

import com.example.playerdatasync.DatabaseManager;
import com.example.playerdatasync.PlayerDataListener;
import com.example.playerdatasync.SyncCommand;
import com.example.playerdatasync.UpdateChecker;
import org.bstats.bukkit.Metrics;
import net.milkbowl.vault.economy.Economy;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

public class PlayerDataSync extends JavaPlugin {
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
    private EditorIntegrationManager editorIntegrationManager;
    private InventoryViewerIntegrationManager inventoryViewerIntegrationManager;
    private int autosaveIntervalSeconds;
    private BukkitTask autosaveTask;
    private MessageManager messageManager;
    private Metrics metrics;

    @Override
    public void onEnable() {
        getLogger().info("Enabling PlayerDataSync...");
        
        // Check server version compatibility
        checkVersionCompatibility();
        
        // Initialize configuration first
        getLogger().info("Saving default configuration...");
        saveDefaultConfig();
        
        // Debug: Check if config file exists and has content
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) {
            getLogger().info("Config file exists. Size: " + configFile.length() + " bytes");
            if (configFile.length() == 0) {
                getLogger().warning("Config file is empty (0 bytes)! This indicates a problem with the JAR file or resource loading.");
            }
        } else {
            getLogger().warning("Config file does not exist after saveDefaultConfig()!");
            getLogger().warning("This usually means the config.yml resource is not properly embedded in the JAR file.");
        }
        
        // Ensure config file exists and is not empty
        if (getConfig().getKeys(false).isEmpty()) {
            getLogger().warning("Configuration file is empty! Recreating from defaults...");
            reloadConfig();
            saveDefaultConfig();
            
            // Double-check that config is now loaded
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
        
        // If config is still empty after ConfigManager initialization, force initialize defaults
        if (getConfig().getKeys(false).isEmpty()) {
            getLogger().warning("Configuration still empty after ConfigManager init. Force initializing defaults...");
            configManager.initializeDefaultConfig();
            reloadConfig();
            
            // Final check - if still empty, create a minimal config manually
            if (getConfig().getKeys(false).isEmpty()) {
                getLogger().severe("CRITICAL: All configuration loading methods failed!");
                getLogger().severe("Creating emergency minimal configuration...");
                createEmergencyConfig();
                reloadConfig();
            }
        }
        
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

        editorIntegrationManager = new EditorIntegrationManager(this);
        editorIntegrationManager.start();
        // Initialize database connection
        databaseType = getConfig().getString("database.type", "mysql");
        try {
            if (databaseType.equalsIgnoreCase("mysql")) {
                String host = getConfig().getString("database.mysql.host", "localhost");
                int port = getConfig().getInt("database.mysql.port", 3306);
                String database = getConfig().getString("database.mysql.database", "minecraft");
                databaseUser = getConfig().getString("database.mysql.user", "root");
                databasePassword = getConfig().getString("database.mysql.password", "");
                
                // Add connection parameters for better reliability
                databaseUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=%s&autoReconnect=true&failOverReadOnly=false&maxReconnects=3", 
                    host, port, database, getConfig().getBoolean("database.mysql.ssl", false));
                
                // Create initial connection for testing
                connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
                getLogger().info("Connected to MySQL database at " + host + ":" + port + "/" + database);
                
                // Initialize connection pool if enabled
                if (getConfig().getBoolean("performance.connection_pooling", true)) {
                    int maxConnections = getConfig().getInt("database.mysql.max_connections", 10);
                    connectionPool = new ConnectionPool(this, databaseUrl, databaseUser, databasePassword, maxConnections);
                    connectionPool.initialize();
                }
            } else if (databaseType.equalsIgnoreCase("sqlite")) {
                String file = getConfig().getString("database.sqlite.file", "plugins/PlayerDataSync/playerdata.db");
                // Ensure directory exists
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
            autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
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
        new UpdateChecker(this, 123166, messageManager).check();
        
        getLogger().info("PlayerDataSync enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PlayerDataSync...");

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
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        if (databaseManager.savePlayer(player)) {
                            savedCount++;
                        } else {
                            getLogger().severe("Failed to save data for " + player.getName()
                                + " during shutdown: See previous log entries for details.");
                        }
                    } catch (Exception e) {
                        getLogger().severe("Failed to save data for " + player.getName() + " during shutdown: " + e.getMessage());
                    }
                }
                
                long endTime = System.currentTimeMillis();
                if (savedCount > 0) {
                    getLogger().info("Saved data for " + savedCount + " players during shutdown in " + 
                        (endTime - startTime) + "ms");
                }
            } catch (Exception e) {
                getLogger().severe("Error saving players during shutdown: " + e.getMessage());
            }
        }
        
        // Stop backup manager
        if (backupManager != null) {
            backupManager.stopAutomaticBackups();
            backupManager = null;
        }

        if (editorIntegrationManager != null) {
            editorIntegrationManager.shutdown();
            editorIntegrationManager = null;
        }

        if (advancementSyncManager != null) {
            advancementSyncManager.shutdown();
            advancementSyncManager = null;
        }

        if (inventoryViewerIntegrationManager != null) {
            inventoryViewerIntegrationManager.shutdown();
            inventoryViewerIntegrationManager = null;
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
        
        getLogger().info("PlayerDataSync disabled successfully");
    }

    private Connection createConnection() throws SQLException {
        if (databaseType.equalsIgnoreCase("mysql")) {
            return DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
        }
        return DriverManager.getConnection(databaseUrl);
    }

    public synchronized Connection getConnection() {
        try {
            // Use connection pool if available
            if (connectionPool != null) {
                return connectionPool.getConnection();
            }
            
            // Fallback to single connection
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connection = createConnection();
                getLogger().info("Reconnected to database");
            }
        } catch (SQLException e) {
            getLogger().severe("Could not establish database connection: " + e.getMessage());
        }
        return connection;
    }
    
    /**
     * Return a connection to the pool (if pooling is enabled)
     */
    public void returnConnection(Connection conn) {
        if (connectionPool != null && conn != null) {
            connectionPool.returnConnection(conn);
        }
    }

    public boolean isSyncCoordinates() {
        return syncCoordinates;
    }

    public boolean isSyncXp() {
        return syncXp;
    }

    public boolean isSyncGamemode() {
        return syncGamemode;
    }

    public boolean isSyncEnderchest() {
        return syncEnderchest;
    }

    public boolean isSyncInventory() {
        return syncInventory;
    }

    public boolean isSyncHealth() {
        return syncHealth;
    }

    public boolean isSyncHunger() {
        return syncHunger;
    }

    public boolean isSyncPosition() {
        return syncPosition;
    }

    public boolean isSyncAchievements() {
        return syncAchievements;
    }

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
    }

    public void reloadPlugin() {
        reloadConfig();

        if (configManager != null) {
            configManager.reloadConfig();
            tablePrefix = configManager.getTablePrefix();
        }

        // Always use messages.language path for reload
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
            getLogger().info("BungeeCord integration enabled after reload. Plugin messaging channel registered.");
        } else if (!bungeecordIntegrationEnabled && wasBungeeEnabled) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this);
            getLogger().info("BungeeCord integration disabled after reload. Plugin messaging channel unregistered.");
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
                autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
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

    // Getter methods for extended sync options
    public boolean isSyncArmor() { return syncArmor; }
    public boolean isSyncOffhand() { return syncOffhand; }
    public boolean isSyncEffects() { return syncEffects; }
    public boolean isSyncStatistics() { return syncStatistics; }
    public boolean isSyncAttributes() { return syncAttributes; }
    public boolean isSyncPermissions() { return syncPermissions; }
    public boolean isSyncEconomy() { return syncEconomy; }
    
    // Setter methods for extended sync options
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
    
    /**
     * Manually trigger economy sync for a player
     * This can be called by other plugins when server switching is detected
     */
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
    
    // Getter methods for components
    public ConfigManager getConfigManager() { return configManager; }
    public String getTablePrefix() { return tablePrefix != null ? tablePrefix : "player_data"; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AdvancementSyncManager getAdvancementSyncManager() { return advancementSyncManager; }
    public BackupManager getBackupManager() { return backupManager; }
    public ConnectionPool getConnectionPool() { return connectionPool; }
    public MessageManager getMessageManager() { return messageManager; }
    public EditorIntegrationManager getEditorIntegrationManager() { return editorIntegrationManager; }

    public Economy getEconomyProvider() { return economyProvider; }

    public boolean isBungeecordIntegrationEnabled() { return bungeecordIntegrationEnabled; }

    /**
     * API method for other plugins to trigger economy sync
     * This is useful when detecting server switches via BungeeCord or other methods
     */
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

        Bukkit.getScheduler().runTask(this, () -> {
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

    /**
     * Check server version compatibility and log warnings if needed
     */
    private void checkVersionCompatibility() {
        // Check if version checking is enabled in config
        if (!getConfig().getBoolean("compatibility.version_check", true)) {
            getLogger().info("Version compatibility checking is disabled in config");
            return;
        }
        
        try {
            String serverVersion = Bukkit.getServer().getBukkitVersion();
            String pluginApiVersion = getDescription().getAPIVersion();
            
            getLogger().info("Server version: " + serverVersion);
            getLogger().info("Plugin API version: " + pluginApiVersion);
            
            // Check if we're running on a supported version range (1.20.4 to 1.21.9)
            boolean isSupportedVersion = false;
            String versionInfo = "";
            
            // Check for 1.20.4+ versions
            if (serverVersion.contains("1.20.4") || serverVersion.contains("1.20.5") || serverVersion.contains("1.20.6")) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.20.4+ - Full compatibility confirmed";
            }
            
            // Check for 1.21.x versions
            if (serverVersion.contains("1.21") || serverVersion.contains("1.21.1") || serverVersion.contains("1.21.2") ||
                serverVersion.contains("1.21.3") || serverVersion.contains("1.21.4") || serverVersion.contains("1.21.5") ||
                serverVersion.contains("1.21.6") || serverVersion.contains("1.21.7") || serverVersion.contains("1.21.8") ||
                serverVersion.contains("1.21.9") || serverVersion.contains("1.21.10")) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.21.x - Full compatibility confirmed";
            }
            
            if (isSupportedVersion) {
                getLogger().info("✅ " + versionInfo);
            } else {
                getLogger().warning("================================================");
                getLogger().warning("VERSION COMPATIBILITY WARNING:");
                getLogger().warning("This plugin supports Minecraft 1.20.4 to 1.21.10");
                getLogger().warning("Current server version: " + serverVersion);
                getLogger().warning("Some features may not work correctly");
                getLogger().warning("Consider updating to a supported version");
                getLogger().warning("================================================");
            }
            
            // Test critical API methods
            try {
                org.bukkit.attribute.Attribute.values();
                getLogger().info("Attribute API compatibility: OK");
            } catch (Exception e) {
                getLogger().severe("CRITICAL: Attribute API compatibility issue detected!");
                getLogger().severe("This may cause crashes when saving player data");
                getLogger().severe("Error: " + e.getMessage());
                
                // Suggest enabling safe attribute sync
                getLogger().warning("Consider setting 'compatibility.safe_attribute_sync: true' in config.yml");
            }
            
            // Log compatibility summary
            if (serverVersion.contains("1.20.4") || serverVersion.contains("1.20.5") || serverVersion.contains("1.20.6")) {
                getLogger().info("✅ Full compatibility with Minecraft 1.20.4+ confirmed");
            } else if (serverVersion.contains("1.21") || serverVersion.contains("1.21.1") || serverVersion.contains("1.21.2") || 
                      serverVersion.contains("1.21.3") || serverVersion.contains("1.21.4") || serverVersion.contains("1.21.5") ||
                      serverVersion.contains("1.21.6") || serverVersion.contains("1.21.7") || serverVersion.contains("1.21.8") ||
                      serverVersion.contains("1.21.9") || serverVersion.contains("1.21.10")) {
                getLogger().info("✅ Full compatibility with Minecraft 1.21.x confirmed");
            } else {
                getLogger().info("ℹ️  Running on version: " + serverVersion + " - compatibility status unknown");
            }
            
        } catch (Exception e) {
            getLogger().warning("Could not perform version compatibility check: " + e.getMessage());
        }
    }

    private void configureEconomyIntegration() {
        logDebug("Economy sync setting from config: " + getConfig().getBoolean("sync.economy", false));
        logDebug("Economy sync variable: " + syncEconomy);

        if (!syncEconomy) {
            economyProvider = null;
            logDebug("Economy sync is disabled in configuration");
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

    /**
     * Create emergency minimal configuration when all other methods fail
     */
    private void createEmergencyConfig() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            
            // Create minimal working configuration
            String emergencyConfig =
                "config-version: 3\n" +
                "server:\n" +
                "  id: default\n" +
                "database:\n" +
                "  type: sqlite\n" +
                "  table_prefix: player_data\n" +
                "  sqlite:\n" +
                "    file: plugins/PlayerDataSync/playerdata.db\n" +
                "sync:\n" +
                "  coordinates: true\n" +
                "  position: true\n" +
                "  xp: true\n" +
                "  gamemode: true\n" +
                "  inventory: true\n" +
                "  enderchest: true\n" +
                "  armor: true\n" +
                "  offhand: true\n" +
                "  health: true\n" +
                "  hunger: true\n" +
                "  effects: true\n" +
                "  achievements: true\n" +
                "  statistics: true\n" +
                "  attributes: true\n" +
                "  permissions: false\n" +
                "  economy: false\n" +
                "autosave:\n" +
                "  enabled: true\n" +
                "  interval: 1\n" +
                "  on_world_change: true\n" +
                "  on_death: true\n" +
                "  async: true\n" +
                "performance:\n" +
                "  batch_size: 50\n" +
                "  cache_size: 100\n" +
                "  cache_ttl: 300000\n" +
                "  cache_compression: true\n" +
                "  connection_pooling: true\n" +
                "  async_loading: true\n" +
                "  disable_achievement_sync_on_large_amounts: true\n" +
                "  achievement_batch_size: 50\n" +
                "  achievement_timeout_ms: 5000\n" +
                "  max_achievements_per_player: 2000\n" +
                "compatibility:\n" +
                "  safe_attribute_sync: true\n" +
                "  disable_attributes_on_error: false\n" +
                "  version_check: true\n" +
                "  legacy_1_20_support: true\n" +
                "  modern_1_21_support: true\n" +
                "  disable_achievements_on_critical_error: true\n" +
                "security:\n" +
                "  encrypt_data: false\n" +
                "  hash_uuids: false\n" +
                "  audit_log: true\n" +
                "logging:\n" +
                "  level: INFO\n" +
                "  log_database: false\n" +
                "  log_performance: false\n" +
                "  debug_mode: false\n" +
                "update_checker:\n" +
                "  enabled: true\n" +
                "  notify_ops: true\n" +
                "  auto_download: false\n" +
                "  timeout: 10000\n" +
                "metrics:\n" +
                "  bstats: true\n" +
                "  custom_metrics: true\n" +
                "messages:\n" +
                "  enabled: true\n" +
                "  language: en\n" +
                "  prefix: \"&8[&bPDS&8]\"\n" +
                "  colors: true\n";
            
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(emergencyConfig);
            }
            
            getLogger().info("Emergency configuration created successfully!");
            
        } catch (Exception e) {
            getLogger().severe("Failed to create emergency configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
