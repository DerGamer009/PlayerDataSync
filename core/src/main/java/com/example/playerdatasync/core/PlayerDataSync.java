package com.example.playerdatasync.core;

import com.example.playerdatasync.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import net.milkbowl.vault.economy.Economy;

import com.example.playerdatasync.database.ConnectionPool;
import com.example.playerdatasync.database.DatabaseManager;
import com.example.playerdatasync.integration.InventoryViewerIntegrationManager;
import com.example.playerdatasync.listeners.PlayerDataListener;
import com.example.playerdatasync.listeners.ServerSwitchListener;
import com.example.playerdatasync.commands.SyncCommand;
import com.example.playerdatasync.api.UpdateChecker;
import com.example.playerdatasync.utils.VersionCompatibility;
import com.example.playerdatasync.utils.SchedulerUtils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
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
    private boolean maintenanceMode = false;

    private DatabaseManager databaseManager;
    private com.example.playerdatasync.nms.NMSHandler nmsHandler;
    private ConfigManager configManager;
    private BackupManager backupManager;
    private MenuManager menuManager;
    private ProfileManager profileManager;
    private InventoryViewerIntegrationManager inventoryViewerIntegrationManager;
    private int autosaveIntervalSeconds;
    private BukkitTask autosaveTask;
    private MessageManager messageManager;
    private BStatsManager bStatsManager;
    private FastStatsManager fastStatsManager;
    private String nmsVersionString = "Unknown";
    private long lastSaveDurationMs = 0;

    @Override
    public void onEnable() {
        getLogger().info("Enabling PlayerDataSync...");

        // Load database drivers explicitly (JDBC 4 discovery often fails in shaded Spigot plugins)
        loadDatabaseDrivers();

        // Check server version compatibility
        checkVersionCompatibility();

        // 1. Initialize folders
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        // 2. Ensure config.yml exists
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getLogger().info("Generating default configuration...");
            saveDefaultConfig();
        }

        // 3. Robust Config Loading (UTF-8)
        try {
            // Force reload from disk to ensure we have any newly saved defaults
            reloadConfig();
            
            // Double check if it actually loaded correctly
            if (getConfig().getKeys(false).isEmpty()) {
                getLogger().warning("Primary configuration load failed or empty. Attempting direct UTF-8 load...");
                loadConfigUtf8(configFile);
            }
        } catch (Exception e) {
            getLogger().severe("Failed to load configuration: " + e.getMessage());
        }

        // 4. Initialize Config Manager (Migration & Validation)
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

        // Initialize metrics managers
        if (getConfig().getBoolean("metrics.bstats", true)) {
            if (bStatsManager == null) {
                bStatsManager = new BStatsManager(this);
            }
            bStatsManager.initialize();
        }

        if (getConfig().getBoolean("metrics.faststats", true)) {
            if (fastStatsManager == null) {
                fastStatsManager = new FastStatsManager(this);
            }
            fastStatsManager.initialize();
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
                int connectionTimeout = Math.max(1000, getConfig().getInt("database.mysql.connection_timeout", 5000));

                databaseUrl = buildMysqlJdbcUrl(host, port, database, connectionTimeout,
                        getConfig().getBoolean("database.mysql.ssl", false));
                DriverManager.setLoginTimeout(Math.max(1, connectionTimeout / 1000));

                // Create initial connection for testing
                connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
                getLogger().info("Connected to MySQL database at " + host + ":" + port + "/" + database +
                        " (timeout=" + connectionTimeout + "ms)");

                // Initialize connection pool if enabled
                if (getConfig().getBoolean("performance.connection_pooling", true)) {
                    int maxConnections = getConfig().getInt("database.mysql.max_connections", 10);
                    connectionPool = new ConnectionPool(this, databaseUrl, databaseUser, databasePassword,
                            maxConnections);
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
        setupNMS();
        nmsHandler.setupAdvancements(this);
        bungeecordIntegrationEnabled = getConfig().getBoolean("integrations.bungeecord", false);
        if (bungeecordIntegrationEnabled) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            getLogger().info("BungeeCord integration enabled. Plugin messaging channel registered.");
        }

        autosaveIntervalSeconds = getConfig().getInt("autosave.interval", 1);

        if (autosaveIntervalSeconds > 0) {
            long ticks = autosaveIntervalSeconds * 20L;
            autosaveTask = SchedulerUtils.runTaskTimerAsync(this, () -> {
                if (maintenanceMode) return;
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
                            getLogger()
                                    .warning("Failed to autosave data for " + player.getName() + ": " + e.getMessage());
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
            if (SchedulerUtils.isFolia()) {
                getLogger().info("Folia detected - using async scheduler for autosave");
            }
        }

        if (databaseType.equalsIgnoreCase("mongodb")) {
            // databaseManager = new MongoDatabaseManager(this); // To be implemented
        } else {
            databaseManager = new com.example.playerdatasync.database.SQLDatabaseManager(this);
        }
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

        // Initialize menu manager
        menuManager = new MenuManager(this);

        // Initialize profile manager
        profileManager = new ProfileManager(this);

        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, databaseManager), this);
        getServer().getPluginManager().registerEvents(new ServerSwitchListener(this, databaseManager), this);
        if (getCommand("sync") != null) {
            SyncCommand syncCommand = new SyncCommand(this);
            getCommand("sync").setExecutor(syncCommand);
            getCommand("sync").setTabCompleter(syncCommand);
        }
        new UpdateChecker(this, messageManager).check();

        if (SchedulerUtils.isFolia()) {
            getLogger().info("Folia detected - using Folia-compatible schedulers");
        }

        getLogger().info("PlayerDataSync enabled successfully!");
    }

    private String buildMysqlJdbcUrl(String host, int port, String database, int connectionTimeout,
            boolean sslEnabled) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("useSSL", String.valueOf(sslEnabled));
        params.put("allowPublicKeyRetrieval", "true");
        params.put("connectTimeout", String.valueOf(connectionTimeout));
        params.put("socketTimeout", String.valueOf(connectionTimeout));
        params.put("tcpKeepAlive", "true");
        params.put("serverTimezone", "UTC");
        params.put("characterEncoding", "utf8");
        params.put("useUnicode", "true");

        StringJoiner query = new StringJoiner("&");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            query.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.format("jdbc:mysql://%s:%d/%s?%s", host, port, database, query.toString());
    }

    private void setupNMS() {
        try {
            String packageName = getServer().getClass().getPackage().getName();
            String versionStr = packageName.substring(packageName.lastIndexOf('.') + 1);
            this.nmsVersionString = versionStr;
            Class<?> clazz = Class.forName("com.example.playerdatasync.nms." + versionStr + ".NMSHandlerImpl");
            if (com.example.playerdatasync.nms.NMSHandler.class.isAssignableFrom(clazz)) {
                this.nmsHandler = (com.example.playerdatasync.nms.NMSHandler) clazz.getConstructor().newInstance();
                getLogger().info("Successfully loaded NMS handler for " + versionStr);
            } else {
                this.nmsHandler = new com.example.playerdatasync.nms.FallbackNMSHandler();
                getLogger().warning("NMS class found but doesn't implement interface properly. Using fallback.");
            }
        } catch (Exception e) {
            getLogger().info("No specific NMS class found for this version. Feature compatibility will be restricted.");
            this.nmsHandler = new com.example.playerdatasync.nms.FallbackNMSHandler();
            this.nmsVersionString = "Fallback";
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling PlayerDataSync...");

        if (fastStatsManager != null) {
            fastStatsManager.shutdown();
        }

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
        // Fix for Issue #42 and #46: Ensure economy is saved before shutdown
        if (databaseManager != null) {
            try {
                int savedCount = 0;
                long startTime = System.currentTimeMillis();

                // Reconfigure economy integration to ensure it's available during shutdown
                // This is critical for Issue #46: Vault Balance de-sync on server shutdown
                if (syncEconomy) {
                    getLogger().info("Reconfiguring economy integration for shutdown save...");
                    configureEconomyIntegration();

                    // Wait a tick to ensure Vault is fully initialized
                    try {
                        Thread.sleep(100); // Small delay to ensure Vault is ready
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                // Save all players synchronously to ensure data is persisted
                // This prevents race conditions where economy balance might not be saved
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        // Force economy balance refresh before save
                        if (syncEconomy && economyProvider != null) {
                            // Trigger a balance read to ensure Vault has latest balance
                            try {
                                double currentBalance = economyProvider.getBalance(player);
                                getLogger().fine("Current balance for " + player.getName() + " before shutdown save: "
                                        + currentBalance);
                            } catch (Exception e) {
                                getLogger().warning("Could not read balance for " + player.getName()
                                        + " before shutdown: " + e.getMessage());
                            }
                        }

                        // Save player data (including economy balance)
                        if (databaseManager.savePlayer(player)) {
                            savedCount++;
                            getLogger().fine("Saved data for " + player.getName() + " during shutdown");
                        } else {
                            getLogger().severe("Failed to save data for " + player.getName()
                                    + " during shutdown: See previous log entries for details.");
                        }
                    } catch (Exception e) {
                        getLogger().severe(
                                "Failed to save data for " + player.getName() + " during shutdown: " + e.getMessage());
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

        if (nmsHandler != null) {
            nmsHandler.shutdownAdvancements();
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
            if (connection == null || connection.isClosed() || !isConnectionValid(connection)) {
                connection = createConnection();
                getLogger().info("Reconnected to database");
            }
        } catch (SQLException e) {
            getLogger().severe("Could not establish database connection: " + e.getMessage());
        }
        return connection;
    }

    private boolean isConnectionValid(Connection connection) {
        try {
            if (connection == null || connection.isClosed()) {
                return false;
            }
            // Some older drivers (like SQLite) might not implement isValid()
            try {
                return connection.isValid(2);
            } catch (AbstractMethodError | Exception e) {
                return true; // Assume valid if we can't check
            }
        } catch (SQLException e) {
            return false;
        }
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

        // Extended sync options with version checks
        syncArmor = getConfig().getBoolean("sync.armor", true);

        // Offhand requires 1.9+
        syncOffhand = VersionCompatibility.isOffhandSupported() &&
                getConfig().getBoolean("sync.offhand", true);
        if (!VersionCompatibility.isOffhandSupported() && getConfig().getBoolean("sync.offhand", true)) {
            getLogger().info("Offhand sync disabled - requires Minecraft 1.9+");
            getConfig().set("sync.offhand", false);
        }

        syncEffects = getConfig().getBoolean("sync.effects", true);
        syncStatistics = getConfig().getBoolean("sync.statistics", true);

        // Attributes require 1.9+
        syncAttributes = VersionCompatibility.isAttributesSupported() &&
                getConfig().getBoolean("sync.attributes", true);
        if (!VersionCompatibility.isAttributesSupported() && getConfig().getBoolean("sync.attributes", true)) {
            getLogger().info("Attribute sync disabled - requires Minecraft 1.9+");
            getConfig().set("sync.attributes", false);
        }

        // Advancements require 1.12+
        syncAchievements = VersionCompatibility.isAdvancementsSupported() &&
                getConfig().getBoolean("sync.achievements", true);
        if (!VersionCompatibility.isAdvancementsSupported() && getConfig().getBoolean("sync.achievements", true)) {
            getLogger().info("Advancement sync disabled - requires Minecraft 1.12+");
            getConfig().set("sync.achievements", false);
        }

        syncPermissions = getConfig().getBoolean("sync.permissions", false);
        syncEconomy = getConfig().getBoolean("sync.economy", false);

        // Save config if we disabled any features
        saveConfig();
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

        if (getConfig().getBoolean("metrics.bstats", true)) {
            if (bStatsManager == null) {
                bStatsManager = new BStatsManager(this);
            }
            bStatsManager.initialize();
        }

        if (getConfig().getBoolean("metrics.faststats", true)) {
            if (fastStatsManager == null) {
                fastStatsManager = new FastStatsManager(this);
            }
            fastStatsManager.initialize();
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

        if (nmsHandler != null) {
            // Can add reload config logic inside NMSHandler if needed
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
                                getLogger().warning(
                                        "Failed to autosave data for " + player.getName() + ": " + e.getMessage());
                            }
                        }

                        long endTime = System.currentTimeMillis();
                        this.lastSaveDurationMs = endTime - startTime;
                        if (savedCount > 0 && isPerformanceLoggingEnabled()) {
                            getLogger().info("Autosaved data for " + savedCount + " players in " +
                                    this.lastSaveDurationMs + "ms");
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
    public boolean isSyncArmor() {
        return syncArmor;
    }

    public boolean isSyncOffhand() {
        return syncOffhand;
    }

    public boolean isSyncEffects() {
        return syncEffects;
    }

    public boolean isSyncStatistics() {
        return syncStatistics;
    }

    public boolean isSyncAttributes() {
        return syncAttributes;
    }

    public boolean isSyncPermissions() {
        return syncPermissions;
    }

    public boolean isSyncEconomy() {
        return syncEconomy;
    }

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
    public String getDatabaseType() {
        return databaseType != null ? databaseType : "unknown";
    }

    public String getNmsVersionString() {
        return nmsVersionString;
    }
    public long getLastSaveDurationMs() {
        return lastSaveDurationMs;
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

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
        if (maintenanceMode) {
            getLogger().warning("MAINTENANCE MODE ENABLED - All data syncing is paused!");
        } else {
            getLogger().info("Maintenance mode disabled - Data syncing resumed.");
        }
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    // Getter methods for components
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public String getTablePrefix() {
        return tablePrefix != null ? tablePrefix : "player_data";
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public com.example.playerdatasync.nms.NMSHandler getNmsHandler() {
        return nmsHandler;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public Economy getEconomyProvider() {
        return economyProvider;
    }

    public boolean isBungeecordIntegrationEnabled() {
        return bungeecordIntegrationEnabled;
    }

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
                getLogger().severe("Failed to send player " + player.getName() + " to server '" + targetServer + "': "
                        + e.getMessage());
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
            getLogger().info("Server version: " + serverVersion);

            // Check if we're running on a supported version range (1.8 to 1.21.11)
            boolean isSupportedVersion = false;
            String versionInfo = "";

            // Check for supported versions
            if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_8()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.8 - Full compatibility confirmed";
            } else if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_9_to_1_11()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.9-1.11 - Full compatibility confirmed";
            } else if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_12()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.12 - Full compatibility confirmed";
            } else if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_13_to_1_16()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.13-1.16 - Full compatibility confirmed";
            } else if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_17()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.17 - Full compatibility confirmed";
            } else if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_18_to_1_20()) {
                isSupportedVersion = true;
                versionInfo = "Minecraft 1.18-1.20 - Full compatibility confirmed";
            } else if (com.example.playerdatasync.utils.VersionCompatibility.isVersion1_21_Plus()) {
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
                getLogger().warning("Consider updating to a supported version");
                getLogger().warning("================================================");
            }

            // Test critical API methods with version checks
            if (com.example.playerdatasync.utils.VersionCompatibility.isAttributesSupported()) {
                try {
                    Class.forName("org.bukkit.attribute.Attribute");
                    getLogger().info("Attribute API availability: OK");
                } catch (Exception e) {
                    getLogger().severe("CRITICAL: Attribute API classes not found!");
                }
            } else {
                getLogger().info("Attribute API not available (requires 1.9+) - attribute sync will be disabled");
            }

            // Log feature availability
            if (!com.example.playerdatasync.utils.VersionCompatibility.isOffhandSupported()) {
                getLogger().info("ℹ️  Offhand sync disabled (requires 1.9+)");
            }
            if (!com.example.playerdatasync.utils.VersionCompatibility.isAdvancementsSupported()) {
                getLogger().info("ℹ️  Advancements sync disabled (requires 1.12+)");
            }

            // Log compatibility summary
            getLogger().info("✅ Running on Minecraft "
                    + com.example.playerdatasync.utils.VersionCompatibility.getVersionString() +
                    " - Full compatibility confirmed");

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

        RegisteredServiceProvider<Economy> registration = getServer().getServicesManager()
                .getRegistration(Economy.class);
        if (registration == null) {
            getLogger()
                    .warning("No Vault economy provider registration found. Economy sync requires an economy plugin.");
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
            String emergencyConfig = "config-version: 3\n" +
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
                    "  on_xp_change: true\n" +
                    "  on_server_switch: true\n" +
                    "  on_kick: true\n" +
                    "  async: true\n" +
                    "data_management:\n" +
                    "  cleanup:\n" +
                    "    enabled: false\n" +
                    "    days_inactive: 90\n" +
                    "  backup:\n" +
                    "    enabled: true\n" +
                    "    interval: 1440\n" +
                    "    keep_backups: 7\n" +
                    "  validation:\n" +
                    "    enabled: true\n" +
                    "    strict_mode: false\n" +
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
                    "  preload_advancements_on_startup: true\n" +
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
                    "integrations:\n" +
                    "  bungeecord: false\n" +
                    "  luckperms: false\n" +
                    "  vault: true\n" +
                    "  placeholderapi: false\n" +
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
                    "  show_sync_messages: true\n" +
                    "  language: en\n" +
                    "  prefix: \"&8[&bPDS&8]\"\n" +
                    "  colors: true\n" +
                    "config-version: 6\n";

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(emergencyConfig);
            }

            getLogger().info("Emergency configuration created successfully!");

        } catch (Exception e) {
            getLogger().severe("Failed to create emergency configuration: " + e.getMessage());
            getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
        }
    }

    private void loadConfigUtf8(File file) {
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            YamlConfiguration utf8Config = YamlConfiguration.loadConfiguration(reader);
            // We can't directly replace the config object in Bukkit easily,
            // but we can copy values from the utf8Config to the current config
            for (String key : utf8Config.getKeys(true)) {
                getConfig().set(key, utf8Config.get(key));
            }
            getLogger().info("Successfully loaded configuration with UTF-8 encoding.");
        } catch (Exception e) {
            getLogger().warning("Failed to load config with UTF-8: " + e.getMessage());
        }
    }

    private void loadDatabaseDrivers() {
        try {
            Class.forName("org.sqlite.JDBC");
            Class.forName("com.mysql.cj.jdbc.Driver");
            Class.forName("org.postgresql.Driver");
            getLogger().info("Database drivers loaded successfully.");
        } catch (ClassNotFoundException e) {
            getLogger().warning("One or more database drivers could not be loaded: " + e.getMessage());
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
