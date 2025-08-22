package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.Statistic;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced database manager with expanded synchronization features
 */
public class DatabaseManagerEnhanced {
    private final PlayerDataSync plugin;
    private final Gson gson;
    private final Map<UUID, PlayerDataCache> cache;
    private final Set<UUID> currentlySaving;
    
    // Database schema version for migrations
    private static final int SCHEMA_VERSION = 3;
    
    public DatabaseManagerEnhanced(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();
        this.cache = new ConcurrentHashMap<>();
        this.currentlySaving = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Initialize database tables and perform migrations
     */
    public void initialize() {
        createTables();
        performMigrations();
        createIndexes();
    }
    
    /**
     * Create database tables
     */
    private void createTables() {
        String playerDataTable = """
            CREATE TABLE IF NOT EXISTS player_data (
                uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16),
                world VARCHAR(255),
                x DOUBLE, y DOUBLE, z DOUBLE,
                yaw FLOAT, pitch FLOAT,
                xp INT, xp_level INT, xp_progress FLOAT,
                gamemode VARCHAR(20),
                enderchest TEXT,
                inventory TEXT,
                armor TEXT,
                offhand TEXT,
                health DOUBLE, max_health DOUBLE,
                hunger INT, saturation FLOAT,
                effects TEXT,
                advancements TEXT,
                statistics TEXT,
                attributes TEXT,
                last_login TIMESTAMP,
                last_logout TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """;
        
        String metadataTable = """
            CREATE TABLE IF NOT EXISTS pds_metadata (
                key_name VARCHAR(255) PRIMARY KEY,
                value_data TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """;
        
        String backupsTable = """
            CREATE TABLE IF NOT EXISTS pds_backups (
                backup_id VARCHAR(255) PRIMARY KEY,
                player_uuid VARCHAR(36),
                backup_data LONGTEXT,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX idx_player_uuid (player_uuid),
                INDEX idx_created_at (created_at)
            )
            """;
        
        Connection connection = plugin.getConnection();
        if (connection == null) {
            plugin.getLogger().severe("Database connection unavailable");
            return;
        }
        
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(playerDataTable);
            stmt.executeUpdate(metadataTable);
            stmt.executeUpdate(backupsTable);
            
            plugin.getLogger().info("Database tables initialized successfully");
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Perform database migrations
     */
    private void performMigrations() {
        int currentVersion = getDatabaseVersion();
        
        if (currentVersion < SCHEMA_VERSION) {
            plugin.getLogger().info("Migrating database schema from version " + currentVersion + " to " + SCHEMA_VERSION);
            
            try {
                for (int version = currentVersion + 1; version <= SCHEMA_VERSION; version++) {
                    performMigration(version);
                }
                
                setDatabaseVersion(SCHEMA_VERSION);
                plugin.getLogger().info("Database migration completed successfully");
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Database migration failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Perform specific migration version
     */
    private void performMigration(int version) throws SQLException {
        Connection connection = plugin.getConnection();
        
        try (Statement stmt = connection.createStatement()) {
            switch (version) {
                case 2:
                    // Add new columns for enhanced features
                    addColumnIfNotExists(stmt, "player_data", "armor", "TEXT");
                    addColumnIfNotExists(stmt, "player_data", "offhand", "TEXT");
                    addColumnIfNotExists(stmt, "player_data", "effects", "TEXT");
                    addColumnIfNotExists(stmt, "player_data", "statistics", "TEXT");
                    addColumnIfNotExists(stmt, "player_data", "attributes", "TEXT");
                    addColumnIfNotExists(stmt, "player_data", "max_health", "DOUBLE");
                    addColumnIfNotExists(stmt, "player_data", "xp_level", "INT");
                    addColumnIfNotExists(stmt, "player_data", "xp_progress", "FLOAT");
                    break;
                    
                case 3:
                    // Add metadata and backup tracking
                    addColumnIfNotExists(stmt, "player_data", "player_name", "VARCHAR(16)");
                    addColumnIfNotExists(stmt, "player_data", "last_login", "TIMESTAMP");
                    addColumnIfNotExists(stmt, "player_data", "last_logout", "TIMESTAMP");
                    addColumnIfNotExists(stmt, "player_data", "created_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                    addColumnIfNotExists(stmt, "player_data", "updated_at", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                    break;
            }
        }
    }
    
    /**
     * Add column if it doesn't exist
     */
    private void addColumnIfNotExists(Statement stmt, String table, String column, String type) throws SQLException {
        DatabaseMetaData meta = stmt.getConnection().getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (!rs.next()) {
                stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                plugin.getLogger().info("Added column " + column + " to table " + table);
            }
        }
    }
    
    /**
     * Create database indexes for performance
     */
    private void createIndexes() {
        Connection connection = plugin.getConnection();
        
        try (Statement stmt = connection.createStatement()) {
            // Create indexes for better query performance
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_name ON player_data(player_name)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_last_login ON player_data(last_login)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_updated_at ON player_data(updated_at)");
            
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not create database indexes: " + e.getMessage());
        }
    }
    
    /**
     * Save player data to database
     */
    public CompletableFuture<Void> savePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Prevent concurrent saves for the same player
        if (currentlySaving.contains(uuid)) {
            return CompletableFuture.completedFuture(null);
        }
        
        currentlySaving.add(uuid);
        
        return CompletableFuture.runAsync(() -> {
            try {
                savePlayerSync(player);
                
                // Update cache
                PlayerDataCache cacheEntry = new PlayerDataCache(player);
                cache.put(uuid, cacheEntry);
                
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to save data for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                currentlySaving.remove(uuid);
            }
        });
    }
    
    /**
     * Synchronous player save (internal)
     */
    private void savePlayerSync(Player player) throws SQLException {
        String sql = """
            REPLACE INTO player_data (
                uuid, player_name, world, x, y, z, yaw, pitch,
                xp, xp_level, xp_progress, gamemode,
                enderchest, inventory, armor, offhand,
                health, max_health, hunger, saturation,
                effects, advancements, statistics, attributes,
                last_logout, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            """;
        
        Connection connection = plugin.getConnection();
        if (connection == null) {
            throw new SQLException("Database connection unavailable");
        }
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.setString(2, player.getName());
            
            // Location data
            if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
                Location loc = player.getLocation();
                ps.setString(3, loc.getWorld().getName());
                ps.setDouble(4, loc.getX());
                ps.setDouble(5, loc.getY());
                ps.setDouble(6, loc.getZ());
                ps.setFloat(7, loc.getYaw());
                ps.setFloat(8, loc.getPitch());
            } else {
                ps.setNull(3, Types.VARCHAR);
                ps.setDouble(4, 0);
                ps.setDouble(5, 0);
                ps.setDouble(6, 0);
                ps.setFloat(7, 0);
                ps.setFloat(8, 0);
            }
            
            // Experience data
            if (plugin.isSyncXp()) {
                ps.setInt(9, player.getTotalExperience());
                ps.setInt(10, player.getLevel());
                ps.setFloat(11, player.getExp());
            } else {
                ps.setInt(9, 0);
                ps.setInt(10, 0);
                ps.setFloat(11, 0);
            }
            
            // Game mode
            ps.setString(12, plugin.isSyncGamemode() ? player.getGameMode().name() : null);
            
            // Inventory data
            try {
                ps.setString(13, plugin.isSyncEnderchest() ? 
                    InventoryUtils.itemStackArrayToBase64(player.getEnderChest().getContents()) : null);
                ps.setString(14, plugin.isSyncInventory() ? 
                    InventoryUtils.itemStackArrayToBase64(player.getInventory().getContents()) : null);
                ps.setString(15, plugin.isSyncArmor() ? 
                    InventoryUtils.itemStackArrayToBase64(player.getInventory().getArmorContents()) : null);
                ps.setString(16, plugin.isSyncOffhand() ? 
                    InventoryUtils.itemStackToBase64(player.getInventory().getItemInOffHand()) : null);
            } catch (Exception e) {
                plugin.getLogger().severe("Error serializing inventory for " + player.getName() + ": " + e.getMessage());
                ps.setNull(13, Types.LONGVARCHAR);
                ps.setNull(14, Types.LONGVARCHAR);
                ps.setNull(15, Types.LONGVARCHAR);
                ps.setNull(16, Types.LONGVARCHAR);
            }
            
            // Health and hunger
            ps.setDouble(17, plugin.isSyncHealth() ? player.getHealth() : player.getMaxHealth());
            ps.setDouble(18, plugin.isSyncHealth() ? player.getMaxHealth() : 20.0);
            ps.setInt(19, plugin.isSyncHunger() ? player.getFoodLevel() : 20);
            ps.setFloat(20, plugin.isSyncHunger() ? player.getSaturation() : 5.0f);
            
            // Effects
            ps.setString(21, plugin.isSyncEffects() ? serializeEffects(player) : null);
            
            // Advancements
            ps.setString(22, plugin.isSyncAchievements() ? serializeAdvancements(player) : null);
            
            // Statistics
            ps.setString(23, plugin.isSyncStatistics() ? serializeStatistics(player) : null);
            
            // Attributes
            ps.setString(24, plugin.isSyncAttributes() ? serializeAttributes(player) : null);
            
            // Last logout timestamp
            ps.setTimestamp(25, new Timestamp(System.currentTimeMillis()));
            
            ps.executeUpdate();
            
            if (plugin.getConfigManager().isDatabaseLoggingEnabled()) {
                plugin.getLogger().info("Saved data for player: " + player.getName());
            }
            
        }
    }
    
    /**
     * Load player data from database
     */
    public CompletableFuture<Void> loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        
        // Check cache first
        PlayerDataCache cached = cache.get(uuid);
        if (cached != null && cached.isValid()) {
            return CompletableFuture.runAsync(() -> {
                try {
                    applyPlayerData(player, cached);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to apply cached data for " + player.getName() + ": " + e.getMessage());
                }
            });
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                loadPlayerSync(player);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load data for " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Synchronous player load (internal)
     */
    private void loadPlayerSync(Player player) throws SQLException {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        
        Connection connection = plugin.getConnection();
        if (connection == null) {
            throw new SQLException("Database connection unavailable");
        }
        
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            applyPlayerDataFromResultSet(player, rs);
                            
                            // Update cache
                            PlayerDataCache cacheEntry = new PlayerDataCache(player);
                            cache.put(player.getUniqueId(), cacheEntry);
                            
                            // Update last login timestamp
                            updateLastLogin(player.getUniqueId());
                            
                        } catch (Exception e) {
                            plugin.getLogger().severe("Failed to apply data for " + player.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } else {
                    // First time player - create record
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        try {
                            createFirstTimePlayer(player);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Failed to create first-time player record: " + e.getMessage());
                        }
                    });
                }
            }
        }
    }
    
    /**
     * Apply player data from result set
     */
    private void applyPlayerDataFromResultSet(Player player, ResultSet rs) throws SQLException {
        // Location and position
        if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
            String worldName = rs.getString("world");
            if (worldName != null && !worldName.isEmpty()) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world,
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
                    player.teleport(loc);
                } else {
                    plugin.getLogger().warning("World " + worldName + " not found for player " + player.getName());
                }
            }
        }
        
        // Experience
        if (plugin.isSyncXp()) {
            int totalXp = rs.getInt("xp");
            if (totalXp > 0) {
                player.setTotalExperience(0);
                player.setLevel(0);
                player.setExp(0);
                player.giveExp(totalXp);
            }
        }
        
        // Game mode
        if (plugin.isSyncGamemode()) {
            String gmString = rs.getString("gamemode");
            if (gmString != null) {
                try {
                    GameMode gameMode = GameMode.valueOf(gmString);
                    player.setGameMode(gameMode);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid gamemode for " + player.getName() + ": " + gmString);
                }
            }
        }
        
        // Inventory data
        loadInventoryData(player, rs);
        
        // Health and hunger
        if (plugin.isSyncHealth()) {
            double health = rs.getDouble("health");
            double maxHealth = rs.getDouble("max_health");
            if (maxHealth > 0) {
                player.setMaxHealth(maxHealth);
            }
            if (health > 0) {
                player.setHealth(Math.min(health, player.getMaxHealth()));
            }
        }
        
        if (plugin.isSyncHunger()) {
            int hunger = rs.getInt("hunger");
            float saturation = rs.getFloat("saturation");
            player.setFoodLevel(hunger);
            player.setSaturation(saturation);
        }
        
        // Effects
        if (plugin.isSyncEffects()) {
            String effectsData = rs.getString("effects");
            if (effectsData != null) {
                loadEffects(player, effectsData);
            }
        }
        
        // Advancements
        if (plugin.isSyncAchievements()) {
            String advData = rs.getString("advancements");
            if (advData != null) {
                loadAdvancements(player, advData);
            }
        }
        
        // Statistics
        if (plugin.isSyncStatistics()) {
            String statsData = rs.getString("statistics");
            if (statsData != null) {
                loadStatistics(player, statsData);
            }
        }
        
        // Attributes
        if (plugin.isSyncAttributes()) {
            String attributesData = rs.getString("attributes");
            if (attributesData != null) {
                loadAttributes(player, attributesData);
            }
        }
    }
    
    /**
     * Load inventory data for player
     */
    private void loadInventoryData(Player player, ResultSet rs) throws SQLException {
        try {
            // Ender chest
            if (plugin.isSyncEnderchest()) {
                String enderChestData = rs.getString("enderchest");
                if (enderChestData != null) {
                    ItemStack[] items = InventoryUtils.itemStackArrayFromBase64(enderChestData);
                    player.getEnderChest().setContents(items);
                }
            }
            
            // Main inventory
            if (plugin.isSyncInventory()) {
                String inventoryData = rs.getString("inventory");
                if (inventoryData != null) {
                    ItemStack[] items = InventoryUtils.itemStackArrayFromBase64(inventoryData);
                    player.getInventory().setContents(items);
                }
            }
            
            // Armor
            if (plugin.isSyncArmor()) {
                String armorData = rs.getString("armor");
                if (armorData != null) {
                    ItemStack[] armor = InventoryUtils.itemStackArrayFromBase64(armorData);
                    player.getInventory().setArmorContents(armor);
                }
            }
            
            // Offhand
            if (plugin.isSyncOffhand()) {
                String offhandData = rs.getString("offhand");
                if (offhandData != null) {
                    ItemStack offhandItem = InventoryUtils.itemStackFromBase64(offhandData);
                    if (offhandItem != null) {
                        player.getInventory().setItemInOffHand(offhandItem);
                    }
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading inventory data for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    // Additional serialization methods...
    
    /**
     * Serialize potion effects
     */
    private String serializeEffects(Player player) {
        if (player.getActivePotionEffects().isEmpty()) {
            return null;
        }
        
        JsonArray effectsArray = new JsonArray();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            JsonObject effectObj = new JsonObject();
            effectObj.addProperty("type", effect.getType().getName());
            effectObj.addProperty("duration", effect.getDuration());
            effectObj.addProperty("amplifier", effect.getAmplifier());
            effectObj.addProperty("ambient", effect.isAmbient());
            effectObj.addProperty("particles", effect.hasParticles());
            effectObj.addProperty("icon", effect.hasIcon());
            effectsArray.add(effectObj);
        }
        
        return gson.toJson(effectsArray);
    }
    
    /**
     * Load potion effects
     */
    private void loadEffects(Player player, String effectsData) {
        try {
            // Clear existing effects
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            
            JsonArray effectsArray = JsonParser.parseString(effectsData).getAsJsonArray();
            for (int i = 0; i < effectsArray.size(); i++) {
                JsonObject effectObj = effectsArray.get(i).getAsJsonObject();
                
                // Parse effect data and apply
                // Implementation would depend on specific requirements
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load effects for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    // Additional methods for statistics, attributes, etc. would be implemented similarly...
    
    /**
     * Get database version
     */
    private int getDatabaseVersion() {
        try (Connection conn = plugin.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT value_data FROM pds_metadata WHERE key_name = 'schema_version'");
             ResultSet rs = ps.executeQuery()) {
            
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value_data"));
            }
        } catch (Exception e) {
            // Table might not exist yet
        }
        return 1; // Default version
    }
    
    /**
     * Set database version
     */
    private void setDatabaseVersion(int version) throws SQLException {
        try (Connection conn = plugin.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "REPLACE INTO pds_metadata (key_name, value_data) VALUES ('schema_version', ?)")) {
            ps.setString(1, String.valueOf(version));
            ps.executeUpdate();
        }
    }
    
    /**
     * Player data cache entry
     */
    private static class PlayerDataCache {
        private final long timestamp;
        private final String playerName;
        // Cache data would be stored here
        
        public PlayerDataCache(Player player) {
            this.timestamp = System.currentTimeMillis();
            this.playerName = player.getName();
        }
        
        public boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < 300000; // 5 minutes
        }
    }
    
    // Additional helper methods...
    private String serializeAdvancements(Player player) { return null; }
    private String serializeStatistics(Player player) { return null; }
    private String serializeAttributes(Player player) { return null; }
    private void loadAdvancements(Player player, String data) {}
    private void loadStatistics(Player player, String data) {}
    private void loadAttributes(Player player, String data) {}
    private void applyPlayerData(Player player, PlayerDataCache cache) {}
    private void updateLastLogin(UUID uuid) {}
    private void createFirstTimePlayer(Player player) {}
}
