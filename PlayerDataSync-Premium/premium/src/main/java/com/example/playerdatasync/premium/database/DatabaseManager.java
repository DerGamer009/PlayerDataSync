package com.example.playerdatasync.premium.database;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;

import java.io.IOException;
import java.sql.*;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import com.example.playerdatasync.premium.core.PlayerDataSyncPremium;
import com.example.playerdatasync.premium.managers.AdvancementSyncManager;
import com.example.playerdatasync.premium.managers.ConfigManager;
import com.example.playerdatasync.premium.utils.InventoryUtils;
import com.example.playerdatasync.premium.utils.SchedulerUtils;
import com.example.playerdatasync.premium.utils.OfflinePlayerData;
import com.example.playerdatasync.premium.utils.PlayerDataCache;

public class DatabaseManager {
    private final PlayerDataSyncPremium plugin;
    // Cache is initialized but not yet used in current implementation
    @SuppressWarnings("unused")
    private final PlayerDataCache cache;
    
    // Performance monitoring
    private long totalSaveTime = 0;
    private long totalLoadTime = 0;
    private int saveCount = 0;
    private int loadCount = 0;
    private long lastPerformanceLog = 0;
    private final long PERFORMANCE_LOG_INTERVAL = 300000; // 5 minutes

    public DatabaseManager(PlayerDataSyncPremium plugin) {
        this.plugin = plugin;
        this.cache = new PlayerDataCache(plugin);
    }

    public void initialize() {
        String tableName = getTableName();
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(255)," +
                "x DOUBLE,y DOUBLE,z DOUBLE," +
                "yaw FLOAT,pitch FLOAT," +
                "xp INT," +
                "gamemode VARCHAR(20)," +
                "enderchest LONGTEXT," +
                "inventory LONGTEXT," +
                "armor LONGTEXT," +
                "offhand LONGTEXT," +
                "effects TEXT," +
                "statistics LONGTEXT," +
                "attributes TEXT," +
                "health DOUBLE," +
                "hunger INT," +
                "saturation FLOAT," +
                "advancements LONGTEXT," +
                "economy DOUBLE DEFAULT 0.0," +
                "last_save TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "server_id VARCHAR(50) DEFAULT 'default'" +
                ")";
        Connection connection = null;
        try {
            connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                return;
            }
            
            try (Statement st = connection.createStatement()) {
                st.executeUpdate(sql);
                // Ensure columns exist for older installations
                DatabaseMetaData meta = connection.getMetaData();
                try (ResultSet rs = meta.getColumns(null, null, tableName, "hunger")) {
                    if (!rs.next()) {
                        st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN hunger INT");
                    }
                }
                try (ResultSet rs = meta.getColumns(null, null, tableName, "saturation")) {
                    if (!rs.next()) {
                        st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN saturation FLOAT");
                    }
                }
                // Upgrade inventory-related columns from TEXT to LONGTEXT to support large inventories
                // with custom enchantments (e.g., ExcellentEnchants)
                upgradeColumnToLongText(meta, st, tableName, "inventory");
                upgradeColumnToLongText(meta, st, tableName, "enderchest");
                upgradeColumnToLongText(meta, st, tableName, "armor");
                upgradeColumnToLongText(meta, st, tableName, "offhand");
                
                try (ResultSet rs = meta.getColumns(null, null, tableName, "advancements")) {
                    if (!rs.next()) {
                        st.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN advancements LONGTEXT");
                    } else {
                        // Check if it's TEXT and upgrade to LONGTEXT
                        String dataType = rs.getString("TYPE_NAME");
                        if ("TEXT".equalsIgnoreCase(dataType)) {
                            try {
                                st.executeUpdate("ALTER TABLE " + tableName + " MODIFY COLUMN advancements LONGTEXT");
                                plugin.getLogger().info("Upgraded advancements column from TEXT to LONGTEXT");
                            } catch (SQLException e) {
                                plugin.getLogger().warning("Could not upgrade advancements column to LONGTEXT: " + e.getMessage());
                            }
                        }
                    }
                }

                // Add new columns for extended features
                addColumnIfNotExists(meta, st, tableName, "armor", "LONGTEXT");
                addColumnIfNotExists(meta, st, tableName, "offhand", "LONGTEXT");
                addColumnIfNotExists(meta, st, tableName, "effects", "TEXT");
                addColumnIfNotExists(meta, st, tableName, "statistics", "LONGTEXT");
                addColumnIfNotExists(meta, st, tableName, "attributes", "TEXT");
                addColumnIfNotExists(meta, st, tableName, "economy", "DOUBLE DEFAULT 0.0");
                addColumnIfNotExists(meta, st, tableName, "last_save", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                addColumnIfNotExists(meta, st, tableName, "server_id", "VARCHAR(50) DEFAULT 'default'");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create table: " + e.getMessage());
        } finally {
            plugin.returnConnection(connection);
        }
    }
    
    /**
     * Helper method to add column if it doesn't exist
     */
    private void addColumnIfNotExists(DatabaseMetaData meta, Statement st, String table, String columnName, String columnType) throws SQLException {
        try (ResultSet rs = meta.getColumns(null, null, table, columnName)) {
            if (!rs.next()) {
                st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + columnName + " " + columnType);
                plugin.getLogger().info("Added new column: " + columnName);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not add column " + columnName + ": " + e.getMessage());
        }
    }
    
    /**
     * Helper method to upgrade column from TEXT to LONGTEXT
     * This is needed for large inventories with custom enchantments (e.g., ExcellentEnchants)
     */
    private void upgradeColumnToLongText(DatabaseMetaData meta, Statement st, String table, String columnName) {
        try (ResultSet rs = meta.getColumns(null, null, table, columnName)) {
            if (rs.next()) {
                String dataType = rs.getString("TYPE_NAME");
                if ("TEXT".equalsIgnoreCase(dataType)) {
                    try {
                        st.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + columnName + " LONGTEXT");
                        plugin.getLogger().info("Upgraded " + columnName + " column from TEXT to LONGTEXT to support large inventories");
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Could not upgrade " + columnName + " column to LONGTEXT: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().fine("Could not check " + columnName + " column type: " + e.getMessage());
        }
    }
    
    /**
     * Try to upgrade a column immediately when a truncation error occurs
     */
    private void upgradeColumnToLongTextImmediate(Connection connection, String table, String columnName) {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, columnName)) {
                if (rs.next()) {
                    String dataType = rs.getString("TYPE_NAME");
                    if ("TEXT".equalsIgnoreCase(dataType)) {
                        try (Statement st = connection.createStatement()) {
                            st.executeUpdate("ALTER TABLE " + table + " MODIFY COLUMN " + columnName + " LONGTEXT");
                            plugin.getLogger().info("Successfully upgraded " + columnName + " column to LONGTEXT during runtime");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().fine("Could not upgrade " + columnName + " column immediately: " + e.getMessage());
        }
    }
    
    /**
     * Extract column name from SQL error message
     */
    private String extractColumnName(String errorMessage) {
        // Try to extract column name from error message like "Data too long for column 'inventory'"
        if (errorMessage.contains("'")) {
            int start = errorMessage.indexOf("'");
            int end = errorMessage.indexOf("'", start + 1);
            if (end > start) {
                return errorMessage.substring(start + 1, end);
            }
        }
        return "unknown";
    }

    private String getTableName() {
        return plugin.getTablePrefix();
    }

    public boolean savePlayer(Player player) {
        long startTime = System.currentTimeMillis();
        String tableName = getTableName();
        String sql = "REPLACE INTO " + tableName + " (uuid, world, x, y, z, yaw, pitch, xp, gamemode, enderchest, inventory, armor, offhand, effects, statistics, attributes, health, hunger, saturation, advancements, economy, last_save, server_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try {
            PlayerSnapshot snapshot;
            if (SchedulerUtils.isPrimaryThread()) {
                snapshot = capturePlayerSnapshot(player);
            } else {
                snapshot = SchedulerUtils.callSyncMethod(plugin, () -> capturePlayerSnapshot(player));
            }

            if (snapshot == null) {
                plugin.getLogger().warning("Skipping save for player " + player.getName() + " because snapshot creation failed");
                return false;
            }

            Connection connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                return false;
            }

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, snapshot.uuid.toString());
                ps.setString(2, snapshot.worldName);
                ps.setDouble(3, snapshot.x);
                ps.setDouble(4, snapshot.y);
                ps.setDouble(5, snapshot.z);
                ps.setFloat(6, snapshot.yaw);
                ps.setFloat(7, snapshot.pitch);
                ps.setInt(8, snapshot.totalExperience);
                ps.setString(9, snapshot.gamemode);
                ps.setString(10, snapshot.enderChestData);
                ps.setString(11, snapshot.inventoryData);
                ps.setString(12, snapshot.armorData);
                ps.setString(13, snapshot.offhandData);
                ps.setString(14, snapshot.effectsData);
                ps.setString(15, snapshot.statisticsData);
                ps.setString(16, snapshot.attributesData);
                ps.setDouble(17, snapshot.health);
                ps.setInt(18, snapshot.hunger);
                ps.setFloat(19, snapshot.saturation);
                ps.setString(20, snapshot.advancementsData);
                ps.setDouble(21, snapshot.economyBalance);
                ps.setTimestamp(22, new Timestamp(System.currentTimeMillis()));
                ps.setString(23, plugin.getConfig().getString("server.id", "default"));

                ps.executeUpdate();

                long saveTime = System.currentTimeMillis() - startTime;
                totalSaveTime += saveTime;
                saveCount++;

                if (saveTime > 1000) {
                    plugin.getLogger().warning("Slow save detected for " + snapshot.playerName + ": " + saveTime + "ms");
                }

                logPerformanceStats();

                return true;

            } catch (SQLException e) {
                if (e.getMessage().contains("Data too long for column")) {
                    String columnName = extractColumnName(e.getMessage());
                    plugin.getLogger().severe("Data truncation error for " + snapshot.playerName +
                        ": " + e.getMessage());
                    plugin.getLogger().severe("The " + columnName + " column is too small. " +
                        "The plugin will automatically upgrade TEXT columns to LONGTEXT on next restart. " +
                        "If this error persists, please restart the server to trigger the database upgrade.");
                    
                    // Try to upgrade the column immediately if possible
                    try {
                        upgradeColumnToLongTextImmediate(connection, getTableName(), columnName);
                    } catch (Exception upgradeError) {
                        plugin.getLogger().warning("Could not upgrade column immediately: " + upgradeError.getMessage());
                    }
                } else {
                    plugin.getLogger().severe("Could not save data for " + snapshot.playerName + ": " + e.getMessage());
                }
                return false;
            } finally {
                plugin.returnConnection(connection);
            }
        } catch (InterruptedException e) {
            plugin.getLogger().severe("Failed to capture data for player " + player.getName() + ": " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            plugin.getLogger().severe("Failed to capture data for player " + player.getName() + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error saving player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private PlayerSnapshot capturePlayerSnapshot(Player player) {
        PlayerSnapshot snapshot = new PlayerSnapshot(player.getUniqueId(), player.getName());

        if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
            Location loc = player.getLocation();
            World world = loc.getWorld();
            snapshot.worldName = world != null ? world.getName() : null;
            snapshot.x = loc.getX();
            snapshot.y = loc.getY();
            snapshot.z = loc.getZ();
            snapshot.yaw = loc.getYaw();
            snapshot.pitch = loc.getPitch();
        }

        snapshot.totalExperience = plugin.isSyncXp() ? player.getTotalExperience() : 0;
        snapshot.gamemode = plugin.isSyncGamemode() ? player.getGameMode().name() : null;

        try {
            snapshot.enderChestData = plugin.isSyncEnderchest()
                ? InventoryUtils.itemStackArrayToBase64(player.getEnderChest().getContents())
                : null;
            snapshot.inventoryData = plugin.isSyncInventory()
                ? InventoryUtils.itemStackArrayToBase64(player.getInventory().getContents())
                : null;
            snapshot.armorData = plugin.isSyncArmor()
                ? InventoryUtils.itemStackArrayToBase64(player.getInventory().getArmorContents())
                : null;
            // Offhand requires 1.9+
            snapshot.offhandData = null;
            if (plugin.isSyncOffhand() && com.example.playerdatasync.premium.utils.VersionCompatibility.isOffhandSupported()) {
                try {
                    snapshot.offhandData = InventoryUtils.itemStackToBase64(player.getInventory().getItemInOffHand());
                } catch (NoSuchMethodError e) {
                    plugin.getLogger().warning("Offhand not supported on this version");
                }
            }
            snapshot.effectsData = plugin.isSyncEffects() ? serializeEffects(player) : null;
            snapshot.statisticsData = plugin.isSyncStatistics() ? serializeStatistics(player) : null;
            snapshot.attributesData = plugin.isSyncAttributes() ? serializeAttributes(player) : null;
        } catch (Exception e) {
            plugin.getLogger().severe("Error serializing data for " + player.getName() + ": " + e.getMessage());
            snapshot.enderChestData = null;
            snapshot.inventoryData = null;
            snapshot.armorData = null;
            snapshot.offhandData = null;
            snapshot.effectsData = null;
            snapshot.statisticsData = null;
            snapshot.attributesData = null;
        }

        // Get max health safely (getMaxHealth() is deprecated but required for 1.8 compatibility)
        double maxHealth = 20.0;
        try {
            if (com.example.playerdatasync.premium.utils.VersionCompatibility.isAttributesSupported()) {
                org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) {
                    maxHealth = attr.getValue();
                }
            } else {
                @SuppressWarnings("deprecation")
                double tempMax = player.getMaxHealth();
                maxHealth = tempMax;
            }
        } catch (Exception e) {
            // Fallback to default
            maxHealth = 20.0;
        }
        snapshot.health = plugin.isSyncHealth() ? player.getHealth() : maxHealth;
        snapshot.hunger = plugin.isSyncHunger() ? player.getFoodLevel() : 20;
        snapshot.saturation = plugin.isSyncHunger() ? player.getSaturation() : 5f;

        snapshot.advancementsData = null;
        if (plugin.isSyncAchievements()) {
            try {
                long achievementStartTime = System.currentTimeMillis();
                snapshot.advancementsData = serializeAdvancements(player);

                long achievementTime = System.currentTimeMillis() - achievementStartTime;
                if (achievementTime > 2000) {
                    plugin.getLogger().warning("Slow achievement serialization for " + player.getName() +
                        ": " + achievementTime + "ms. Consider disabling achievement sync for better performance.");
                }

                if (snapshot.advancementsData != null && snapshot.advancementsData.length() > 16777215) {
                    plugin.getLogger().warning("Advancement data for " + player.getName() + " is too large (" +
                        snapshot.advancementsData.length() + " characters), skipping advancement sync to prevent database errors");
                    snapshot.advancementsData = null;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("CRITICAL: Achievement serialization failed for " + player.getName() +
                    ". Disabling achievement sync to prevent server freeze: " + e.getMessage());
                snapshot.advancementsData = null;

                if (plugin.getConfig().getBoolean("compatibility.disable_achievements_on_critical_error", true)) {
                    plugin.getLogger().severe("CRITICAL: Automatically disabling achievement sync for " + player.getName() +
                        " due to critical error. Set 'compatibility.disable_achievements_on_critical_error: false' to prevent this.");
                }
            }
        }

        if (plugin.isSyncEconomy()) {
            double balance = getPlayerBalance(player);
            snapshot.economyBalance = balance;
            plugin.logDebug("Saving economy balance for " + player.getName() + ": " + balance);
        } else {
            snapshot.economyBalance = 0.0;
            plugin.logDebug("Economy sync disabled, setting balance to 0.0 for " + player.getName());
        }

        return snapshot;
    }

    public void loadPlayer(Player player) {
        long startTime = System.currentTimeMillis();
        String tableName = getTableName();
        String sql = "SELECT * FROM " + tableName + " WHERE uuid = ?";

        
        Connection connection = null;
        try {
            connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                return;
            }
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
                            String worldName = rs.getString("world");
                            if (worldName != null && !worldName.isEmpty()) {
                                World world = Bukkit.getWorld(worldName);
                                if (world != null) {
                                    Location loc = new Location(world,
                                            rs.getDouble("x"),
                                            rs.getDouble("y"),
                                            rs.getDouble("z"),
                                            rs.getFloat("yaw"),
                                            rs.getFloat("pitch"));
                                    SchedulerUtils.runTask(plugin, player, () -> player.teleport(loc));
                                } else {
                                    plugin.getLogger().warning("World " + worldName + " not found when loading data for " + player.getName());
                                }
                            }
                        }
                        if (plugin.isSyncXp()) {
                            int xp = rs.getInt("xp");
                            SchedulerUtils.runTask(plugin, player, () -> applyExperience(player, xp));
                        }
                        if (plugin.isSyncGamemode()) {
                            String gm = rs.getString("gamemode");
                            if (gm != null) {
                                GameMode mode = GameMode.valueOf(gm);
                                SchedulerUtils.runTask(plugin, player, () -> player.setGameMode(mode));
                            }
                        }
                        if (plugin.isSyncEnderchest()) {
                            String data = rs.getString("enderchest");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    // Validate enderchest size (standard enderchest is 27 slots)
                                    if (items.length > 27) {
                                        ItemStack[] validEnderchest = new ItemStack[27];
                                        System.arraycopy(items, 0, validEnderchest, 0, 27);
                                        items = validEnderchest;
                                    } else if (items.length < 27) {
                                        ItemStack[] extendedEnderchest = new ItemStack[27];
                                        System.arraycopy(items, 0, extendedEnderchest, 0, items.length);
                                        items = extendedEnderchest;
                                    }
                                    final ItemStack[] finalItems = items;
                                    SchedulerUtils.runTask(plugin, player, () -> {
                                        try {
                                            // Set enderchest contents - this preserves all NBT data including custom enchantments
                                            player.getEnderChest().setContents(finalItems);
                                            
                                            // Force refresh of enderchest to ensure custom enchantments are recognized
                                            SchedulerUtils.runTaskLater(plugin, player, () -> {
                                                if (player.isOnline()) {
                                                    ItemStack[] currentItems = player.getEnderChest().getContents();
                                                    player.getEnderChest().setContents(currentItems);
                                                    plugin.logDebug("Successfully loaded enderchest for " + player.getName() + 
                                                        " (" + InventoryUtils.countItems(finalItems) + " items)");
                                                }
                                            }, 2L); // 2 ticks delay to allow plugins to process custom enchantments
                                        } catch (Exception e) {
                                            plugin.getLogger().severe("Error setting enderchest for " + player.getName() + ": " + e.getMessage());
                                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                        }
                                    });
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing enderchest for " + player.getName() + ": " + e.getMessage());
                                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                }
                            }
                        }
                        if (plugin.isSyncInventory()) {
                            String data = rs.getString("inventory");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    // Validate inventory size (standard inventory is 36 slots)
                                    if (items.length > 36) {
                                        // Extract only main inventory slots (0-35)
                                        ItemStack[] mainInventory = new ItemStack[36];
                                        System.arraycopy(items, 0, mainInventory, 0, Math.min(36, items.length));
                                        items = mainInventory;
                                    } else if (items.length < 36) {
                                        // Extend to 36 slots if smaller
                                        ItemStack[] extendedInventory = new ItemStack[36];
                                        System.arraycopy(items, 0, extendedInventory, 0, items.length);
                                        items = extendedInventory;
                                    }
                                    final ItemStack[] finalItems = items;
                                    SchedulerUtils.runTask(plugin, player, () -> {
                                        try {
                                            // Set inventory contents - this preserves all NBT data including custom enchantments
                                            player.getInventory().setContents(finalItems);
                                            // Critical: Update inventory to sync with client
                                            player.updateInventory();
                                            
                                            // Force refresh of items to ensure custom enchantments (e.g., ExcellentEnchants) are recognized
                                            // Some plugins need a tick delay to process custom NBT data
                                            SchedulerUtils.runTaskLater(plugin, player, () -> {
                                                if (player.isOnline()) {
                                                    // Refresh inventory by re-setting items to trigger plugin processing
                                                    ItemStack[] currentItems = player.getInventory().getContents();
                                                    player.getInventory().setContents(currentItems);
                                                    player.updateInventory();
                                                    
                                                    plugin.logDebug("Successfully loaded inventory for " + player.getName() + 
                                                        " (" + InventoryUtils.countItems(finalItems) + " items)");
                                                    
                                                    // Debug: Check for custom enchantments (e.g., ExcellentEnchants)
                                                    if (plugin.isDebugEnabled()) {
                                                        int customEnchantCount = 0;
                                                        for (ItemStack item : currentItems) {
                                                            if (item != null && item.hasItemMeta()) {
                                                                // Check if item has enchantments (including custom ones)
                                                                if (item.getItemMeta().hasEnchants() || 
                                                                    (item.getItemMeta().getPersistentDataContainer() != null && 
                                                                     !item.getItemMeta().getPersistentDataContainer().getKeys().isEmpty())) {
                                                                    customEnchantCount++;
                                                                }
                                                            }
                                                        }
                                                        if (customEnchantCount > 0) {
                                                            plugin.logDebug("Detected " + customEnchantCount + " items with enchantments/metadata in inventory for " + player.getName());
                                                        }
                                                    }
                                                }
                                            }, 2L); // 2 ticks delay to allow plugins to process custom enchantments
                                        } catch (Exception e) {
                                            plugin.getLogger().severe("Error setting inventory for " + player.getName() + ": " + e.getMessage());
                                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                        }
                                    });
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing inventory for " + player.getName() + ": " + e.getMessage());
                                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                }
                            }
                        }
                        if (plugin.isSyncHealth()) {
                            double health = rs.getDouble("health");
                            SchedulerUtils.runTask(plugin, player, () -> {
                                // Get max health safely
                                double maxHealth = 20.0;
                                try {
                                    if (com.example.playerdatasync.premium.utils.VersionCompatibility.isAttributesSupported()) {
                                        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                                        if (attr != null) {
                                            maxHealth = attr.getValue();
                                        }
                                    } else {
                                        @SuppressWarnings("deprecation")
                                        double tempMax = player.getMaxHealth();
                                        maxHealth = tempMax;
                                    }
                                } catch (Exception e) {
                                    maxHealth = 20.0;
                                }
                                player.setHealth(Math.min(health, maxHealth));
                            });
                        }
                        if (plugin.isSyncHunger()) {
                            int hunger = rs.getInt("hunger");
                            float saturation = rs.getFloat("saturation");
                            SchedulerUtils.runTask(plugin, player, () -> {
                                player.setFoodLevel(hunger);
                                player.setSaturation(saturation);
                            });
                        }
                        if (plugin.isSyncArmor()) {
                            String armorData = rs.getString("armor");
                            if (armorData != null) {
                                try {
                                    ItemStack[] armor = InventoryUtils.safeItemStackArrayFromBase64(armorData);
                                    // Normalize armor array to exactly 4 slots (boots, leggings, chestplate, helmet)
                                    armor = normalizeArmorArray(armor);
                                    final ItemStack[] finalArmor = armor;
                                    SchedulerUtils.runTask(plugin, player, () -> {
                                        try {
                                            // Set armor contents - this preserves all NBT data including custom enchantments
                                            player.getInventory().setArmorContents(finalArmor);
                                            // Update inventory to sync armor with client
                                            player.updateInventory();
                                            
                                            // Force refresh of armor to ensure custom enchantments are recognized
                                            SchedulerUtils.runTaskLater(plugin, player, () -> {
                                                if (player.isOnline()) {
                                                    ItemStack[] currentArmor = player.getInventory().getArmorContents();
                                                    player.getInventory().setArmorContents(currentArmor);
                                                    player.updateInventory();
                                                    plugin.logDebug("Successfully loaded armor for " + player.getName());
                                                }
                                            }, 2L); // 2 ticks delay to allow plugins to process custom enchantments
                                        } catch (Exception e) {
                                            plugin.getLogger().severe("Error setting armor for " + player.getName() + ": " + e.getMessage());
                                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                        }
                                    });
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing armor for " + player.getName() + ": " + e.getMessage());
                                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                }
                            }
                        }
                        if (plugin.isSyncOffhand() && com.example.playerdatasync.premium.utils.VersionCompatibility.isOffhandSupported()) {
                            String offhandData = rs.getString("offhand");
                            if (offhandData != null) {
                                try {
                                    ItemStack offhand = InventoryUtils.safeItemStackFromBase64(offhandData);
                                    final ItemStack finalOffhand = offhand;
                                    SchedulerUtils.runTask(plugin, player, () -> {
                                        try {
                                            // Set offhand item - this preserves all NBT data including custom enchantments
                                            player.getInventory().setItemInOffHand(finalOffhand);
                                            // Update inventory to sync offhand with client
                                            player.updateInventory();
                                            
                                            // Force refresh of offhand to ensure custom enchantments are recognized
                                            SchedulerUtils.runTaskLater(plugin, player, () -> {
                                                if (player.isOnline()) {
                                                    ItemStack currentOffhand = player.getInventory().getItemInOffHand();
                                                    player.getInventory().setItemInOffHand(currentOffhand);
                                                    player.updateInventory();
                                                    plugin.logDebug("Successfully loaded offhand for " + player.getName());
                                                }
                                            }, 2L); // 2 ticks delay to allow plugins to process custom enchantments
                                        } catch (NoSuchMethodError e) {
                                            plugin.getLogger().warning("Offhand not supported on this version");
                                        } catch (Exception e) {
                                            plugin.getLogger().severe("Error setting offhand for " + player.getName() + ": " + e.getMessage());
                                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                        }
                                    });
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing offhand for " + player.getName() + ": " + e.getMessage());
                                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
                                }
                            }
                        }
                        if (plugin.isSyncEffects()) {
                            String effectsData = rs.getString("effects");
                            if (effectsData != null) {
                                // Fix for Issue #41: Don't restore effects immediately after death
                                // Check if player just died (has no health or is in respawn state)
                                SchedulerUtils.runTask(plugin, player, () -> {
                                    // Only restore effects if player is not in death/respawn state
                                    if (player.getHealth() > 0 && !player.isDead()) {
                                        loadEffects(player, effectsData);
                                    } else {
                                        plugin.logDebug("Skipping effect restoration for " + player.getName() + 
                                            " - player appears to be dead or respawning");
                                    }
                                });
                            }
                        }
                        if (plugin.isSyncStatistics()) {
                            String statsData = rs.getString("statistics");
                            if (statsData != null) {
                                SchedulerUtils.runTask(plugin, player, () -> loadStatistics(player, statsData));
                            }
                        }
                        if (plugin.isSyncAttributes()) {
                            String attributesData = rs.getString("attributes");
                            if (attributesData != null) {
                                SchedulerUtils.runTask(plugin, player, () -> loadAttributes(player, attributesData));
                            }
                        }
                        if (plugin.isSyncAchievements()) {
                            String advData = rs.getString("advancements");
                            AdvancementSyncManager advancementSyncManager = plugin.getAdvancementSyncManager();
                            if (advancementSyncManager != null) {
                                advancementSyncManager.seedFromDatabase(player.getUniqueId(), advData);
                                if (advData == null && plugin.getConfig().getBoolean("performance.automatic_player_advancement_import", true)) {
                                    SchedulerUtils.runTask(plugin, player, () ->
                                        advancementSyncManager.queuePlayerImport(player, false));
                                }
                            }

                            if (advData != null && !advData.isEmpty()) {
                                // Check if there are too many achievements to prevent lag
                                String[] achievementKeys = advData.split(",");
                                if (achievementKeys.length > 200) {
                                    plugin.getLogger().warning("Large amount of achievements detected for " + player.getName() + 
                                        " (" + achievementKeys.length + "). Loading in background to prevent server lag.");
                                    // Load achievements asynchronously in background
                                    SchedulerUtils.runTaskAsync(plugin, () -> {
                                        try {
                                            loadAdvancements(player, advData);
                                        } catch (Exception e) {
                                            plugin.getLogger().severe("Error loading achievements for " + player.getName() + ": " + e.getMessage());
                                        }
                                    });
                                } else {
                                    // Load achievements normally for smaller amounts
                                    SchedulerUtils.runTask(plugin, player, () -> loadAdvancements(player, advData));
                                }
                            }
                        }
                        if (plugin.isSyncEconomy()) {
                            double balance = rs.getDouble("economy");
                            plugin.logDebug("Loading economy balance for " + player.getName() + ": " + balance);
                            // Fix for Issue #42: Ensure economy provider is available before restoring balance
                            // Delay balance restoration slightly to ensure Vault is fully loaded
                            SchedulerUtils.runTaskLater(plugin, player, () -> {
                                // Re-check economy provider availability
                                if (plugin.getEconomyProvider() != null) {
                                    setPlayerBalance(player, balance);
                                } else {
                                    plugin.getLogger().warning("Economy provider not available when loading balance for " + 
                                        player.getName() + ". Retrying in 1 second...");
                                    // Retry after 1 second
                                    SchedulerUtils.runTaskLater(plugin, player, () -> {
                                        if (plugin.getEconomyProvider() != null) {
                                            setPlayerBalance(player, balance);
                                        } else {
                                            plugin.getLogger().severe("Failed to restore economy balance for " + 
                                                player.getName() + " - economy provider unavailable");
                                        }
                                    }, 20L);
                                }
                            }, 5L); // 5 ticks delay to ensure Vault is ready
                        } else {
                            plugin.logDebug("Economy sync disabled, skipping balance load for " + player.getName());
                        }
                    }
                }
                
                // Update performance metrics
                long loadTime = System.currentTimeMillis() - startTime;
                totalLoadTime += loadTime;
                loadCount++;
                
                // Log slow loads
                if (loadTime > 2000) { // More than 2 seconds
                    plugin.getLogger().warning("Slow load detected for " + player.getName() + ": " + loadTime + "ms");
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not load data for " + player.getName() + ": " + e.getMessage());
            } finally {
                plugin.returnConnection(connection);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error loading player " + player.getName() + ": " + e.getMessage());
        }
    }

    private void applyExperience(Player player, int total) {
        try {
            // Fix for Issue #43, #45 and XP sync across all versions (1.8-1.21.11)
            // Use giveExp() as primary method - it's more reliable than setTotalExperience()
            // Validate experience value
            if (total < 0) {
                plugin.getLogger().warning("Invalid experience value (" + total + ") for " + player.getName() + ", setting to 0");
                total = 0;
            }
            
            // Store current values for logging
            int oldTotal = player.getTotalExperience();
            int oldLevel = player.getLevel();
            
            // Reset experience completely first to ensure clean state
            // Order matters: setTotalExperience(0) must be called last to reset everything properly
            player.setExp(0.0f);
            player.setLevel(0);
            player.setTotalExperience(0);
            
            // Use giveExp() method which is more reliable across all Minecraft versions (1.8-1.21.11)
            // It automatically calculates level and exp bar correctly without version-specific bugs
            if (total > 0) {
                player.giveExp(total);
                
                // Verify the experience was set correctly
                int actualTotal = player.getTotalExperience();
                if (actualTotal != total) {
                    plugin.getLogger().warning("Experience mismatch for " + player.getName() + 
                        ": expected " + total + ", got " + actualTotal + " (old: " + oldTotal + ", level " + oldLevel + ")");
                    
                    // If we got less than expected, add the difference
                    if (actualTotal < total) {
                        int difference = total - actualTotal;
                        player.giveExp(difference);
                        
                        // Verify again after correction
                        int newTotal = player.getTotalExperience();
                        if (newTotal != total) {
                            plugin.getLogger().warning("Experience correction failed for " + player.getName() + 
                                ": expected " + total + ", got " + newTotal);
                        } else {
                            plugin.getLogger().fine("Experience corrected successfully for " + player.getName() + 
                                ": " + total + " XP (level " + player.getLevel() + ")");
                        }
                    } else if (actualTotal > total) {
                        // If we got more (shouldn't happen with giveExp, but handle it anyway)
                        plugin.getLogger().warning("Experience exceeded expected value for " + player.getName() + 
                            ": expected " + total + ", got " + actualTotal);
                        // Reset and try again
                        player.setExp(0.0f);
                        player.setLevel(0);
                        player.setTotalExperience(0);
                        player.giveExp(total);
                    }
                } else {
                    plugin.getLogger().fine("Experience set successfully for " + player.getName() + 
                        ": " + total + " XP (level " + player.getLevel() + ", was " + oldLevel + ")");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error applying experience to " + player.getName() + ": " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
            
            // Last resort fallback: try to reset and use giveExp directly
            try {
                player.setExp(0.0f);
                player.setLevel(0);
                player.setTotalExperience(0);
                if (total > 0) {
                    player.giveExp(total);
                    plugin.getLogger().info("Fallback experience application succeeded for " + player.getName());
                }
            } catch (Exception e2) {
                plugin.getLogger().severe("Fallback experience application also failed for " + player.getName() + 
                    ": " + e2.getMessage());
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e2);
            }
        }
    }

    private String serializeAdvancements(Player player) {
        AdvancementSyncManager advancementSyncManager = plugin.getAdvancementSyncManager();
        if (advancementSyncManager != null) {
            String serialized = advancementSyncManager.serializeForSave(player);
            if (serialized != null) {
                return serialized;
            }
        }

        return legacySerializeAdvancements(player);
    }

    /**
     * Serialize only newly obtained achievements (not all achievements)
     * This prevents loading all 1000+ achievements on first login
     * CRITICAL FIX: Added timeout protection to prevent server freezing
     */
    private String legacySerializeAdvancements(Player player) {
        // CRITICAL: Add timeout protection to prevent server freezing
        long startTime = System.currentTimeMillis();
        final long TIMEOUT_MS = plugin.getConfig().getLong("performance.achievement_timeout_ms", 5000); // Configurable timeout
        
        // Check if achievement sync is disabled due to performance concerns
        if (plugin.getConfig().getBoolean("performance.disable_achievement_sync_on_large_amounts", true)) {
            int totalAdvancements = 0;
            final int MAX_COUNT_ATTEMPTS = 2000; // Hard limit to prevent infinite loops (increased for Minecraft's 1000+ achievements)
            try {
                // CRITICAL: Add timeout check for counting achievements with hard limit
                Iterator<Advancement> it = Bukkit.getServer().advancementIterator();
                while (it.hasNext() && totalAdvancements < MAX_COUNT_ATTEMPTS) {
                    // Advance the iterator to prevent hasNext() from always returning true
                    it.next();
                    totalAdvancements++;
                    
                    // CRITICAL: Check timeout every 50 achievements to prevent freezing
                    if (totalAdvancements % 50 == 0) {
                        if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                            plugin.getLogger().severe("CRITICAL: Achievement counting timeout for " + player.getName() + 
                                " after " + totalAdvancements + " achievements. Aborting to prevent server freeze.");
                            return null;
                        }
                    }
                }
                
                // If we hit the hard limit, something is wrong
                if (totalAdvancements >= MAX_COUNT_ATTEMPTS) {
                    plugin.getLogger().severe("CRITICAL: Achievement counting hit hard limit (" + MAX_COUNT_ATTEMPTS + 
                        ") for " + player.getName() + ". This indicates an infinite loop. Disabling achievement sync.");
                    return null;
                }
                
                // If there are more than 1500 achievements, disable sync to prevent lag
                if (totalAdvancements > 1500) {
                    plugin.getLogger().warning("Large amount of achievements detected (" + totalAdvancements + 
                        "). Achievement sync disabled for " + player.getName() + " to prevent server lag.");
                    return null;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not count achievements, proceeding with sync: " + e.getMessage());
            }
        }
        
        StringBuilder sb = new StringBuilder();
        int count = 0;
        final int MAX_LENGTH = 16777215; // LONGTEXT max length in MySQL (16MB)
        final int MAX_ACHIEVEMENTS = plugin.getConfig().getInt("performance.max_achievements_per_player", 2000); // Configurable limit (increased for Minecraft's 1000+ achievements)
        
        try {
            // Only serialize achievements that are actually completed
            // This prevents loading all 2000+ achievements on first login
            Iterator<Advancement> it = Bukkit.getServer().advancementIterator();
            int processedCount = 0; // Track total processed (including non-completed)
            final int MAX_PROCESSED = 3000; // Hard limit to prevent infinite loops (increased for Minecraft's 1000+ achievements)
            
            while (it.hasNext() && count < MAX_ACHIEVEMENTS && processedCount < MAX_PROCESSED) {
                processedCount++;
                
                // CRITICAL: Check timeout every 25 achievements
                if (count % 25 == 0 && count > 0) {
                    if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                        plugin.getLogger().severe("CRITICAL: Achievement serialization timeout for " + player.getName() + 
                            " after " + count + " achievements. Aborting to prevent server freeze.");
                        break;
                    }
                }
                
                // CRITICAL: Check if we've processed too many total advancements
                if (processedCount >= MAX_PROCESSED) {
                    plugin.getLogger().severe("CRITICAL: Achievement processing hit hard limit (" + MAX_PROCESSED + 
                        ") for " + player.getName() + ". This indicates an infinite loop. Aborting.");
                    break;
                }
                
                Advancement adv = it.next();
                if (adv == null) {
                    plugin.getLogger().warning("Null advancement encountered, skipping...");
                    continue;
                }
                
                try {
                    AdvancementProgress progress = player.getAdvancementProgress(adv);
                    if (progress != null && progress.isDone()) {
                        String key = adv.getKey().toString();
                        // Add comma separator if not first entry
                        String toAdd = (sb.length() > 0 ? "," : "") + key;
                        
                        // Check if adding this advancement would exceed the limit
                        if (sb.length() + toAdd.length() > MAX_LENGTH) {
                            plugin.getLogger().warning("Advancement data for " + player.getName() + 
                                " is too large, truncating at " + count + " achievements");
                            break;
                        }
                        
                        sb.append(toAdd);
                        count++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error processing advancement for " + player.getName() + ": " + e.getMessage());
                    // Continue with next advancement instead of failing completely
                }
            }
            
            if (count > 0) {
                ConfigManager configManager = plugin.getConfigManager();
                if (configManager != null && configManager.isPerformanceLoggingEnabled()) {
                    plugin.getLogger().info("Serialized " + count + " achievements for " + player.getName() + " in " +
                        (System.currentTimeMillis() - startTime) + "ms");
                }
            }
            
            // CRITICAL: Log if we hit the limit
            if (count >= MAX_ACHIEVEMENTS) {
                plugin.getLogger().warning("CRITICAL: Hit maximum achievement limit (" + MAX_ACHIEVEMENTS + 
                    ") for " + player.getName() + ". This may indicate an infinite loop.");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error serializing achievements for " + player.getName() + ": " + e.getMessage());
            return null;
        }
        
        return sb.toString();
    }

    /**
     * Serialize player potion effects
     */
    private String serializeEffects(Player player) {
        try {
            StringBuilder sb = new StringBuilder();
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                if (sb.length() > 0) sb.append(";");
                // Use getKey().getKey() for better compatibility (getName() is deprecated)
                String effectName = effect.getType().getKey().getKey();
                sb.append(effectName)
                  .append(",").append(effect.getAmplifier())
                  .append(",").append(effect.getDuration())
                  .append(",").append(effect.isAmbient())
                  .append(",").append(effect.hasParticles())
                  .append(",").append(effect.hasIcon());
            }
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Error serializing effects for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Serialize player statistics
     */
    private String serializeStatistics(Player player) {
        try {
            StringBuilder sb = new StringBuilder();
            for (org.bukkit.Statistic stat : org.bukkit.Statistic.values()) {
                try {
                    int value = player.getStatistic(stat);
                    if (value > 0) {
                        if (sb.length() > 0) sb.append(";");
                        sb.append(stat.name()).append(",").append(value);
                    }
                } catch (Exception e) {
                    // Some statistics might require additional parameters, skip them for now
                }
            }
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Error serializing statistics for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Serialize player attributes with version compatibility handling
     */
    private String serializeAttributes(Player player) {
        try {
            StringBuilder sb = new StringBuilder();
            
            // Check if safe attribute sync is enabled in config
            boolean safeAttributeSync = plugin.getConfig().getBoolean("compatibility.safe_attribute_sync", true);
            
            if (safeAttributeSync) {
                // Use reflection to safely get Attribute enum values for better compatibility
                try {
                    Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
                    Object[] attributes = (Object[]) attributeClass.getMethod("values").invoke(null);
                    
                    for (Object attrObj : attributes) {
                        try {
                            String attrName = (String) attrObj.getClass().getMethod("name").invoke(attrObj);
                            org.bukkit.attribute.AttributeInstance instance = player.getAttribute((org.bukkit.attribute.Attribute) attrObj);
                            
                            if (instance != null) {
                                if (sb.length() > 0) sb.append(";");
                                sb.append(attrName).append(",").append(instance.getBaseValue());
                            }
                        } catch (Exception e) {
                            // Some attributes might not be applicable, skip them
                            plugin.getLogger().fine("Skipping attribute due to compatibility issue: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // Fallback to direct method call if reflection fails
                    plugin.getLogger().warning("Reflection failed for attributes, using fallback method: " + e.getMessage());
                    
                    for (org.bukkit.attribute.Attribute attr : org.bukkit.attribute.Attribute.values()) {
                        try {
                            org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attr);
                            if (instance != null) {
                                if (sb.length() > 0) sb.append(";");
                                sb.append(attr.name()).append(",").append(instance.getBaseValue());
                            }
                        } catch (Exception ex) {
                            // Some attributes might not be applicable, skip them
                            plugin.getLogger().fine("Skipping attribute " + attr.name() + ": " + ex.getMessage());
                        }
                    }
                }
            } else {
                // Use direct method call (may cause issues on incompatible versions)
                for (org.bukkit.attribute.Attribute attr : org.bukkit.attribute.Attribute.values()) {
                    try {
                        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attr);
                        if (instance != null) {
                            if (sb.length() > 0) sb.append(";");
                            sb.append(attr.name()).append(",").append(instance.getBaseValue());
                        }
                    } catch (Exception ex) {
                        // Some attributes might not be applicable, skip them
                        plugin.getLogger().fine("Skipping attribute " + attr.name() + ": " + ex.getMessage());
                    }
                }
            }
            
            return sb.toString();
        } catch (Exception e) {
            plugin.getLogger().warning("Error serializing attributes for " + player.getName() + ": " + e.getMessage());
            
            // Check if we should disable attributes on error
            if (plugin.getConfig().getBoolean("compatibility.disable_attributes_on_error", false)) {
                plugin.getLogger().warning("Disabling attribute sync due to error. Set 'compatibility.disable_attributes_on_error: false' to prevent this.");
                plugin.setSyncAttributes(false);
            }
            
            return null;
        }
    }

    /**
     * Load achievements with performance optimization for large amounts
     */
    private void loadAdvancements(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        
        try {
            String[] keys = data.split(",");
            final int totalKeys = keys.length;
            
            // Log the number of achievements to be loaded
            if (totalKeys > 100) {
                plugin.getLogger().info("Loading " + totalKeys + " achievements for " + player.getName() + 
                    " (this may take a moment for large amounts)");
            }
            
            // Process achievements in batches to prevent server lag
            final int BATCH_SIZE = 50;
            for (int i = 0; i < keys.length; i += BATCH_SIZE) {
                final int batchStart = i;
                final int batchEnd = Math.min(i + BATCH_SIZE, keys.length);
                
                // Process batch asynchronously to prevent server lag
                SchedulerUtils.runTaskLater(plugin, player, () -> {
                    int batchLoaded = 0;
                    int batchFailed = 0;
                    
                    for (int j = batchStart; j < batchEnd; j++) {
                        String k = keys[j];
                        if (k.trim().isEmpty()) continue;
                        
                        try {
                            NamespacedKey key = NamespacedKey.fromString(k.trim());
                            if (key == null) {
                                batchFailed++;
                                continue;
                            }
                            
                            Advancement adv = Bukkit.getAdvancement(key);
                            if (adv == null) {
                                batchFailed++;
                                continue;
                            }
                            
                            AdvancementProgress prog = player.getAdvancementProgress(adv);
                            if (!prog.isDone()) {
                                for (String criterion : prog.getRemainingCriteria()) {
                                    prog.awardCriteria(criterion);
                                }
                                batchLoaded++;
                            }
                        } catch (Exception e) {
                            batchFailed++;
                            plugin.getLogger().warning("Failed to load advancement '" + k + "' for " + player.getName() + ": " + e.getMessage());
                        }
                    }
                    
                    // Log progress for large batches
                    if (totalKeys > 100 && batchEnd >= totalKeys) {
                        plugin.getLogger().info("Finished loading achievements for " + player.getName() + 
                            ": " + batchLoaded + " loaded, " + batchFailed + " failed");
                    }
                }, (i / BATCH_SIZE) * 2L); // Spread batches over time to prevent lag
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading achievements for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Load player potion effects
     */
    private void loadEffects(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        
        try {
            // Clear existing effects first
            for (org.bukkit.potion.PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            
            String[] effects = data.split(";");
            for (String effectStr : effects) {
                if (effectStr.trim().isEmpty()) continue;
                
                try {
                    String[] parts = effectStr.split(",");
                    if (parts.length >= 6) {
                        // Both getByName() and getByKey() are deprecated, but getByName() works across all versions
                        // We use it with @SuppressWarnings for compatibility
                        org.bukkit.potion.PotionEffectType type = null;
                        try {
                            @SuppressWarnings("deprecation")
                            org.bukkit.potion.PotionEffectType tempType = org.bukkit.potion.PotionEffectType.getByName(parts[0].toUpperCase());
                            type = tempType;
                        } catch (Exception e) {
                            plugin.getLogger().warning("Could not parse potion effect type: " + parts[0] + ": " + e.getMessage());
                        }
                        if (type != null) {
                            int amplifier = Integer.parseInt(parts[1]);
                            int duration = Integer.parseInt(parts[2]);
                            boolean ambient = Boolean.parseBoolean(parts[3]);
                            boolean particles = Boolean.parseBoolean(parts[4]);
                            boolean icon = Boolean.parseBoolean(parts[5]);
                            
                            org.bukkit.potion.PotionEffect effect = new org.bukkit.potion.PotionEffect(
                                type, duration, amplifier, ambient, particles, icon);
                            player.addPotionEffect(effect);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load effect '" + effectStr + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading effects for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Load player statistics
     */
    private void loadStatistics(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        
        try {
            String[] stats = data.split(";");
            for (String statStr : stats) {
                if (statStr.trim().isEmpty()) continue;
                
                try {
                    String[] parts = statStr.split(",");
                    if (parts.length >= 2) {
                        org.bukkit.Statistic stat = org.bukkit.Statistic.valueOf(parts[0]);
                        int value = Integer.parseInt(parts[1]);
                        player.setStatistic(stat, value);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load statistic '" + statStr + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading statistics for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Load player attributes
     */
    private void loadAttributes(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        
        try {
            String[] attributes = data.split(";");
            for (String attrStr : attributes) {
                if (attrStr.trim().isEmpty()) continue;
                
                try {
                    String[] parts = attrStr.split(",");
                    if (parts.length >= 2) {
                        org.bukkit.attribute.Attribute attr = org.bukkit.attribute.Attribute.valueOf(parts[0]);
                        double value = Double.parseDouble(parts[1]);
                        
                        org.bukkit.attribute.AttributeInstance instance = player.getAttribute(attr);
                        if (instance != null) {
                            instance.setBaseValue(value);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load attribute '" + attrStr + "' for " + player.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading attributes for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Log performance statistics periodically
     */
    private void logPerformanceStats() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPerformanceLog > PERFORMANCE_LOG_INTERVAL) {
            lastPerformanceLog = currentTime;
            
            if (plugin.getConfigManager().isPerformanceLoggingEnabled()) {
                double avgSaveTime = saveCount > 0 ? (double) totalSaveTime / saveCount : 0;
                double avgLoadTime = loadCount > 0 ? (double) totalLoadTime / loadCount : 0;
                
                plugin.getLogger().info(String.format("Performance Stats - Saves: %d (avg: %.1fms), Loads: %d (avg: %.1fms)", 
                    saveCount, avgSaveTime, loadCount, avgLoadTime));
            }
        }
    }
    
    /**
     * Get current performance statistics
     */
    public String getPerformanceStats() {
        double avgSaveTime = saveCount > 0 ? (double) totalSaveTime / saveCount : 0;
        double avgLoadTime = loadCount > 0 ? (double) totalLoadTime / loadCount : 0;
        
        return String.format("Saves: %d (avg: %.1fms), Loads: %d (avg: %.1fms)", 
            saveCount, avgSaveTime, loadCount, avgLoadTime);
    }
    
    /**
     * Reset performance statistics
     */
    public void resetPerformanceStats() {
        totalSaveTime = 0;
        totalLoadTime = 0;
        saveCount = 0;
        loadCount = 0;
        lastPerformanceLog = System.currentTimeMillis();
    }
    
    /**
     * Get player balance using Vault API
     */
    private double getPlayerBalance(Player player) {
        Economy economy = plugin.getEconomyProvider();
        if (economy == null) {
            plugin.getLogger().warning("Economy provider unavailable; skipping balance capture for " + player.getName());
            return 0.0;
        }

        try {
            if (!economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }

            double balance = economy.getBalance(player);
            plugin.logDebug("Retrieved balance for " + player.getName() + ": " + balance);
            return balance;
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting player balance for " + player.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Set player balance using Vault API
     */
    private void setPlayerBalance(Player player, double balance) {
        Economy economy = plugin.getEconomyProvider();
        if (economy == null) {
            plugin.getLogger().warning("Economy provider unavailable; skipping balance restore for " + player.getName());
            return;
        }

        plugin.logDebug("Attempting to set balance for " + player.getName() + " to " + balance);

        try {
            if (!economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }

            plugin.logDebug("Economy provider found: " + economy.getName());

            try {
                java.lang.reflect.Method setBalanceMethod =
                    economy.getClass().getMethod("setBalance", org.bukkit.OfflinePlayer.class, double.class);
                setBalanceMethod.invoke(economy, player, balance);
                plugin.logDebug("Set balance for " + player.getName() + " to " + balance + " using setBalance method");
                return;
            } catch (NoSuchMethodException e) {
                plugin.logDebug("setBalance method not available, using deposit/withdraw approach");
            } catch (ReflectiveOperationException reflectiveError) {
                plugin.getLogger().warning("Failed to invoke setBalance on economy provider " + economy.getName() + ": " + reflectiveError.getMessage());
            }

            double currentBalance = economy.getBalance(player);
            double difference = balance - currentBalance;

            plugin.logDebug("Current balance: " + currentBalance + ", Target balance: " + balance + ", Difference: " + difference);

            if (Math.abs(difference) < 0.01) {
                plugin.logDebug("Balance is already correct (within tolerance)");
                return;
            }

            EconomyResponse response;
            if (difference > 0) {
                response = economy.depositPlayer(player, difference);
                if (!response.transactionSuccess()) {
                    plugin.getLogger().warning("Failed to deposit funds for " + player.getName() + ": " + response.errorMessage);
                    return;
                }
                plugin.logDebug("Added " + difference + " to " + player.getName() + "'s balance (now: " + balance + ")");
            } else {
                response = economy.withdrawPlayer(player, Math.abs(difference));
                if (!response.transactionSuccess()) {
                    plugin.getLogger().warning("Failed to withdraw funds for " + player.getName() + ": " + response.errorMessage);
                    return;
                }
                plugin.logDebug("Removed " + Math.abs(difference) + " from " + player.getName() + "'s balance (now: " + balance + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error setting player balance for " + player.getName() + ": " + e.getMessage());
        }
    }

    public OfflinePlayerData loadOfflinePlayerData(UUID uuid, String fallbackName) {
        String displayName = fallbackName != null ? fallbackName : "unknown";
        if (uuid == null) {
            OfflinePlayerData empty = new OfflinePlayerData(null, displayName);
            empty.setInventoryContents(new ItemStack[36]);
            empty.setArmorContents(new ItemStack[4]);
            empty.setEnderChestContents(new ItemStack[27]);
            return empty;
        }

        String tableName = getTableName();
        String sql = "SELECT inventory, armor, offhand, enderchest FROM " + tableName + " WHERE uuid = ?";

        Connection connection = null;
        try {
            connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                OfflinePlayerData empty = new OfflinePlayerData(uuid, displayName);
                empty.setInventoryContents(new ItemStack[36]);
                empty.setArmorContents(new ItemStack[4]);
                empty.setEnderChestContents(new ItemStack[27]);
                return empty;
            }

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        OfflinePlayerData data = new OfflinePlayerData(uuid, displayName);
                        data.setExistsInDatabase(true);

                        ItemStack[] combinedInventory = InventoryUtils.safeItemStackArrayFromBase64(rs.getString("inventory"));
                        data.setInventoryContents(extractMainInventory(combinedInventory));

                        ItemStack[] armor = InventoryUtils.safeItemStackArrayFromBase64(rs.getString("armor"));
                        if (armor.length == 0 && combinedInventory.length > 36) {
                            armor = new ItemStack[] {
                                combinedInventory.length > 36 ? combinedInventory[36] : null,
                                combinedInventory.length > 37 ? combinedInventory[37] : null,
                                combinedInventory.length > 38 ? combinedInventory[38] : null,
                                combinedInventory.length > 39 ? combinedInventory[39] : null
                            };
                        }
                        data.setArmorContents(normalizeArmorArray(armor));

                        ItemStack offhand = InventoryUtils.safeItemStackFromBase64(rs.getString("offhand"));
                        if (offhand == null && combinedInventory.length > 40) {
                            offhand = combinedInventory[40];
                        }
                        data.setOffhandItem(offhand);

                        ItemStack[] enderChest = InventoryUtils.safeItemStackArrayFromBase64(rs.getString("enderchest"));
                        data.setEnderChestContents(enderChest);

                        return data;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading offline data for " + displayName + ": " + e.getMessage());
        } finally {
            plugin.returnConnection(connection);
        }

        OfflinePlayerData emptyData = new OfflinePlayerData(uuid, displayName);
        emptyData.setInventoryContents(new ItemStack[36]);
        emptyData.setArmorContents(new ItemStack[4]);
        emptyData.setEnderChestContents(new ItemStack[27]);
        return emptyData;
    }

    public boolean saveOfflineInventoryData(OfflinePlayerData data) {
        if (data == null || data.getUuid() == null) {
            return false;
        }

        Connection connection = null;
        try {
            connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                return false;
            }

            ItemStack[] main = data.getInventoryContents();
            ItemStack[] armor = normalizeArmorArray(data.getArmorContents());
            ItemStack offhand = data.getOffhandItem();

            ItemStack[] combined = combineInventoryAndEquipment(main, armor, offhand);
            String inventoryData = InventoryUtils.itemStackArrayToBase64(combined);
            String armorData = InventoryUtils.itemStackArrayToBase64(armor);
            String offhandData = offhand != null ? InventoryUtils.itemStackToBase64(offhand) : "";

            String tableName = getTableName();
            String serverId = plugin.getConfig().getString("server.id", "default");

            if (data.existsInDatabase()) {
                String updateSql = "UPDATE " + tableName
                    + " SET inventory=?, armor=?, offhand=?, last_save=CURRENT_TIMESTAMP, server_id=? WHERE uuid=?";
                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, inventoryData);
                    ps.setString(2, armorData);
                    ps.setString(3, offhandData);
                    ps.setString(4, serverId);
                    ps.setString(5, data.getUuid().toString());
                    if (ps.executeUpdate() > 0) {
                        return true;
                    }
                }
            }

            String insertSql = "INSERT INTO " + tableName
                + " (uuid, inventory, armor, offhand, server_id) VALUES (?,?,?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, inventoryData);
                ps.setString(3, armorData);
                ps.setString(4, offhandData);
                ps.setString(5, serverId);
                if (ps.executeUpdate() > 0) {
                    data.setExistsInDatabase(true);
                    return true;
                }
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("Error saving offline inventory for " + data.getDisplayName() + ": " + e.getMessage());
        } finally {
            plugin.returnConnection(connection);
        }
        return false;
    }

    public boolean saveOfflineEnderChestData(OfflinePlayerData data) {
        if (data == null || data.getUuid() == null) {
            return false;
        }

        Connection connection = null;
        try {
            connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                return false;
            }

            ItemStack[] contents = data.getEnderChestContents();
            String enderData = InventoryUtils.itemStackArrayToBase64(contents != null ? contents : new ItemStack[0]);

            String tableName = getTableName();
            String serverId = plugin.getConfig().getString("server.id", "default");

            if (data.existsInDatabase()) {
                String updateSql = "UPDATE " + tableName
                    + " SET enderchest=?, last_save=CURRENT_TIMESTAMP, server_id=? WHERE uuid=?";
                try (PreparedStatement ps = connection.prepareStatement(updateSql)) {
                    ps.setString(1, enderData);
                    ps.setString(2, serverId);
                    ps.setString(3, data.getUuid().toString());
                    if (ps.executeUpdate() > 0) {
                        return true;
                    }
                }
            }

            String insertSql = "INSERT INTO " + tableName + " (uuid, enderchest, server_id) VALUES (?,?,?)";
            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                ps.setString(1, data.getUuid().toString());
                ps.setString(2, enderData);
                ps.setString(3, serverId);
                if (ps.executeUpdate() > 0) {
                    data.setExistsInDatabase(true);
                    return true;
                }
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("Error saving offline ender chest for " + data.getDisplayName() + ": " + e.getMessage());
        } finally {
            plugin.returnConnection(connection);
        }
        return false;
    }

    private ItemStack[] extractMainInventory(ItemStack[] combined) {
        ItemStack[] main = new ItemStack[36];
        if (combined != null) {
            System.arraycopy(combined, 0, main, 0, Math.min(combined.length, 36));
        }
        return main;
    }

    private ItemStack[] normalizeArmorArray(ItemStack[] armor) {
        ItemStack[] normalized = new ItemStack[4];
        if (armor != null) {
            for (int i = 0; i < Math.min(armor.length, 4); i++) {
                normalized[i] = armor[i];
            }
        }
        return normalized;
    }

    private ItemStack[] combineInventoryAndEquipment(ItemStack[] main, ItemStack[] armor, ItemStack offhand) {
        ItemStack[] combined = new ItemStack[41];
        for (int i = 0; i < 36; i++) {
            combined[i] = (main != null && i < main.length) ? main[i] : null;
        }

        ItemStack[] normalizedArmor = normalizeArmorArray(armor);
        combined[36] = normalizedArmor[0];
        combined[37] = normalizedArmor[1];
        combined[38] = normalizedArmor[2];
        combined[39] = normalizedArmor[3];
        combined[40] = offhand;
        return combined;
    }

    private static class PlayerSnapshot {
        private final UUID uuid;
        private final String playerName;
        private String worldName = null;
        private double x = 0;
        private double y = 0;
        private double z = 0;
        private float yaw = 0;
        private float pitch = 0;
        private int totalExperience = 0;
        private String gamemode = null;
        private String enderChestData = null;
        private String inventoryData = null;
        private String armorData = null;
        private String offhandData = null;
        private String effectsData = null;
        private String statisticsData = null;
        private String attributesData = null;
        private double health = 20.0;
        private int hunger = 20;
        private float saturation = 5f;
        private String advancementsData = null;
        private double economyBalance = 0.0;

        private PlayerSnapshot(UUID uuid, String playerName) {
            this.uuid = uuid;
            this.playerName = playerName;
        }
    }
}
