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
    private int autosaveInterval;
    private BukkitTask autosaveTask;
    private MessageManager messageManager;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messageManager = new MessageManager(this);
        String lang = getConfig().getString("language", "en");
        messageManager.load(lang);

        if (getConfig().getBoolean("metrics", true)) {
            if (metrics == null) {
                metrics = new Metrics(this, 25037);
            }
        } else {
            metrics = null;
        }
        databaseType = getConfig().getString("database.type", "mysql");
        try {
            if (databaseType.equalsIgnoreCase("mysql")) {
                String host = getConfig().getString("database.mysql.host", "localhost");
                int port = getConfig().getInt("database.mysql.port", 3306);
                String database = getConfig().getString("database.mysql.database", "minecraft");
                databaseUser = getConfig().getString("database.mysql.user", "root");
                databasePassword = getConfig().getString("database.mysql.password", "");
                databaseUrl = String.format("jdbc:mysql://%s:%d/%s", host, port, database);
                connection = DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
                getLogger().info("Connected to MySQL database");
            } else {
                String file = getConfig().getString("database.sqlite.file", "plugins/PlayerDataSync/playerdata.db");
                databaseUrl = "jdbc:sqlite:" + file;
                databaseUser = null;
                databasePassword = null;
                connection = DriverManager.getConnection(databaseUrl);
                getLogger().info("Connected to SQLite database");
            }
        } catch (SQLException e) {
            getLogger().severe("Could not connect to database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        syncCoordinates = getConfig().getBoolean("sync.coordinates", true);
        syncXp = getConfig().getBoolean("sync.xp", true);
        syncGamemode = getConfig().getBoolean("sync.gamemode", true);
        syncEnderchest = getConfig().getBoolean("sync.enderchest", true);
        syncInventory = getConfig().getBoolean("sync.inventory", true);
        syncHealth = getConfig().getBoolean("sync.health", true);
        syncHunger = getConfig().getBoolean("sync.hunger", true);
        syncPosition = getConfig().getBoolean("sync.position", true);
        syncAchievements = getConfig().getBoolean("sync.achievements", true);
        syncAchievements = getConfig().getBoolean("sync.achievements", true);

        autosaveInterval = getConfig().getInt("autosave.interval", 5);

        if (autosaveInterval > 0) {
            long ticks = autosaveInterval * 1200L;
            autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    databaseManager.savePlayer(player);
                }
            }, ticks, ticks);
        }

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        getServer().getPluginManager().registerEvents(new PlayerDataListener(this, databaseManager), this);
        if (getCommand("sync") != null) {
            getCommand("sync").setExecutor(new SyncCommand(this));
        }
        new UpdateChecker(this, 123166).check();
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (databaseManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                databaseManager.savePlayer(player);
            }
        }
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
    }

    private Connection createConnection() throws SQLException {
        if (databaseType.equalsIgnoreCase("mysql")) {
            return DriverManager.getConnection(databaseUrl, databaseUser, databasePassword);
        }
        return DriverManager.getConnection(databaseUrl);
    }

    public synchronized Connection getConnection() {
        try {
            if (connection == null || connection.isClosed() || !connection.isValid(2)) {
                connection = createConnection();
                getLogger().info("Reconnected to database");
            }
        } catch (SQLException e) {
            getLogger().severe("Could not establish database connection: " + e.getMessage());
        }
        return connection;
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

        syncCoordinates = getConfig().getBoolean("sync.coordinates", true);
        syncXp = getConfig().getBoolean("sync.xp", true);
        syncGamemode = getConfig().getBoolean("sync.gamemode", true);
        syncEnderchest = getConfig().getBoolean("sync.enderchest", true);
        syncInventory = getConfig().getBoolean("sync.inventory", true);
        syncHealth = getConfig().getBoolean("sync.health", true);
        syncHunger = getConfig().getBoolean("sync.hunger", true);
        syncPosition = getConfig().getBoolean("sync.position", true);
        syncAchievements = getConfig().getBoolean("sync.achievements", true);

        int newInterval = getConfig().getInt("autosave.interval", 5);
        if (newInterval != autosaveInterval) {
            autosaveInterval = newInterval;
            if (autosaveTask != null) {
                autosaveTask.cancel();
            }
            if (autosaveInterval > 0) {
                long ticks = autosaveInterval * 1200L;
                autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        databaseManager.savePlayer(player);
                    }
                }, ticks, ticks);
            } else {
                autosaveTask = null;
            }
        }
    }
}
