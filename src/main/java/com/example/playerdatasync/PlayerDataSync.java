package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.example.playerdatasync.MessageManager;

import com.example.playerdatasync.DatabaseManager;
import com.example.playerdatasync.PlayerDataListener;
import com.example.playerdatasync.SyncCommand;
import com.example.playerdatasync.UpdateChecker;
import org.bstats.bukkit.Metrics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PlayerDataSync extends JavaPlugin {
    private Connection connection;
    private ConnectionPool connectionPool;
    private String databaseType;
    private String databaseUrl;
    private String databaseUser;
    private String databasePassword;
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

    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private int autosaveInterval;
    private BukkitTask autosaveTask;
    private MessageManager messageManager;
    private Metrics metrics;

    @Override
    public void onEnable() {
        getLogger().info("Enabling PlayerDataSync...");
        
        // Initialize configuration first
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        
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

        autosaveInterval = getConfig().getInt("autosave.interval", 5);

        if (autosaveInterval > 0) {
            long ticks = autosaveInterval * 1200L;
            autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                try {
                    int savedCount = 0;
                    long startTime = System.currentTimeMillis();
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        try {
                            databaseManager.savePlayer(player);
                            savedCount++;
                        } catch (Exception e) {
                            getLogger().warning("Failed to autosave data for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                    
                    long endTime = System.currentTimeMillis();
                    if (savedCount > 0) {
                        getLogger().info("Autosaved data for " + savedCount + " players in " + 
                            (endTime - startTime) + "ms");
                    }
                } catch (Exception e) {
                    getLogger().severe("Error during autosave: " + e.getMessage());
                }
            }, ticks, ticks);
        }

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, databaseManager), this);
        if (getCommand("sync") != null) {
            SyncCommand syncCommand = new SyncCommand(this);
            getCommand("sync").setExecutor(syncCommand);
            getCommand("sync").setTabCompleter(syncCommand);
        }
        new UpdateChecker(this, 123166).check();
        
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
        
        // Save all online players before shutdown
        if (databaseManager != null) {
            try {
                int savedCount = 0;
                long startTime = System.currentTimeMillis();
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        databaseManager.savePlayer(player);
                        savedCount++;
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

    public MessageManager getMessageManager() {
        return messageManager;
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

        String lang = getConfig().getString("language", "en");
        messageManager.load(lang);

        if (getConfig().getBoolean("metrics", true)) {
            if (metrics == null) {
                metrics = new Metrics(this, 25037);
            }
        } else {
            metrics = null;
        }

        loadSyncSettings();

        int newInterval = getConfig().getInt("autosave.interval", 5);
        if (newInterval != autosaveInterval) {
            autosaveInterval = newInterval;
            if (autosaveTask != null) {
                autosaveTask.cancel();
                autosaveTask = null;
            }
            if (autosaveInterval > 0) {
                long ticks = autosaveInterval * 1200L;
                autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                    try {
                        int savedCount = 0;
                        long startTime = System.currentTimeMillis();
                        
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            try {
                                databaseManager.savePlayer(player);
                                savedCount++;
                            } catch (Exception e) {
                                getLogger().warning("Failed to autosave data for " + player.getName() + ": " + e.getMessage());
                            }
                        }
                        
                        long endTime = System.currentTimeMillis();
                        if (savedCount > 0) {
                            getLogger().info("Autosaved data for " + savedCount + " players in " + 
                                (endTime - startTime) + "ms");
                        }
                    } catch (Exception e) {
                        getLogger().severe("Error during autosave: " + e.getMessage());
                    }
                }, ticks, ticks);
                getLogger().info("Autosave task restarted with interval: " + autosaveInterval + " minutes");
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
        saveConfig(); 
    }
    
    // Getter methods for components
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
}
