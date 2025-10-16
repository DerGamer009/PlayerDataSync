package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;

import java.sql.*;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

public class DatabaseManager {
    private final PlayerDataSync plugin;
    private final PlayerDataCache cache;
    
    // Performance monitoring
    private long totalSaveTime = 0;
    private long totalLoadTime = 0;
    private int saveCount = 0;
    private int loadCount = 0;
    private long lastPerformanceLog = 0;
    private final long PERFORMANCE_LOG_INTERVAL = 300000; // 5 minutes

    public DatabaseManager(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.cache = new PlayerDataCache(plugin);
    }

    public void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "world VARCHAR(255)," +
                "x DOUBLE,y DOUBLE,z DOUBLE," +
                "yaw FLOAT,pitch FLOAT," +
                "xp INT," +
                "gamemode VARCHAR(20)," +
                "enderchest TEXT," +
                "inventory TEXT," +
                "armor TEXT," +
                "offhand TEXT," +
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
                try (ResultSet rs = meta.getColumns(null, null, "player_data", "hunger")) {
                    if (!rs.next()) {
                        st.executeUpdate("ALTER TABLE player_data ADD COLUMN hunger INT");
                    }
                }
                try (ResultSet rs = meta.getColumns(null, null, "player_data", "saturation")) {
                    if (!rs.next()) {
                        st.executeUpdate("ALTER TABLE player_data ADD COLUMN saturation FLOAT");
                    }
                }
                try (ResultSet rs = meta.getColumns(null, null, "player_data", "advancements")) {
                    if (!rs.next()) {
                        st.executeUpdate("ALTER TABLE player_data ADD COLUMN advancements LONGTEXT");
                    } else {
                        // Check if it's TEXT and upgrade to LONGTEXT
                        String dataType = rs.getString("TYPE_NAME");
                        if ("TEXT".equalsIgnoreCase(dataType)) {
                            try {
                                st.executeUpdate("ALTER TABLE player_data MODIFY COLUMN advancements LONGTEXT");
                                plugin.getLogger().info("Upgraded advancements column from TEXT to LONGTEXT");
                            } catch (SQLException e) {
                                plugin.getLogger().warning("Could not upgrade advancements column to LONGTEXT: " + e.getMessage());
                            }
                        }
                    }
                }
                
                // Add new columns for extended features
                addColumnIfNotExists(meta, st, "armor", "TEXT");
                addColumnIfNotExists(meta, st, "offhand", "TEXT");
                addColumnIfNotExists(meta, st, "effects", "TEXT");
                addColumnIfNotExists(meta, st, "statistics", "LONGTEXT");
                addColumnIfNotExists(meta, st, "attributes", "TEXT");
                addColumnIfNotExists(meta, st, "economy", "DOUBLE DEFAULT 0.0");
                addColumnIfNotExists(meta, st, "last_save", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                addColumnIfNotExists(meta, st, "server_id", "VARCHAR(50) DEFAULT 'default'");
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
    private void addColumnIfNotExists(DatabaseMetaData meta, Statement st, String columnName, String columnType) throws SQLException {
        try (ResultSet rs = meta.getColumns(null, null, "player_data", columnName)) {
            if (!rs.next()) {
                st.executeUpdate("ALTER TABLE player_data ADD COLUMN " + columnName + " " + columnType);
                plugin.getLogger().info("Added new column: " + columnName);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not add column " + columnName + ": " + e.getMessage());
        }
    }

    public boolean savePlayer(Player player) {
        long startTime = System.currentTimeMillis();
        String sql = "REPLACE INTO player_data (uuid, world, x, y, z, yaw, pitch, xp, gamemode, enderchest, inventory, armor, offhand, effects, statistics, attributes, health, hunger, saturation, advancements, economy, last_save, server_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try {
            PlayerSnapshot snapshot;
            if (Bukkit.isPrimaryThread()) {
                snapshot = capturePlayerSnapshot(player);
            } else {
                Future<PlayerSnapshot> future = Bukkit.getScheduler().callSyncMethod(plugin, () -> capturePlayerSnapshot(player));
                snapshot = future.get();
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
                    plugin.getLogger().severe("Data truncation error for " + snapshot.playerName +
                        ": " + e.getMessage() + ". Consider disabling achievement sync or check your database column sizes.");
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
            snapshot.offhandData = plugin.isSyncOffhand()
                ? InventoryUtils.itemStackToBase64(player.getInventory().getItemInOffHand())
                : null;
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

        snapshot.health = plugin.isSyncHealth() ? player.getHealth() : player.getMaxHealth();
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
            plugin.getLogger().info("DEBUG: Saving economy balance for " + player.getName() + ": " + balance);
        } else {
            snapshot.economyBalance = 0.0;
            plugin.getLogger().info("DEBUG: Economy sync disabled, setting balance to 0.0 for " + player.getName());
        }

        return snapshot;
    }

    public void loadPlayer(Player player) {
        long startTime = System.currentTimeMillis();
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        
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
                                    Bukkit.getScheduler().runTask(plugin, () -> player.teleport(loc));
                                } else {
                                    plugin.getLogger().warning("World " + worldName + " not found when loading data for " + player.getName());
                                }
                            }
                        }
                        if (plugin.isSyncXp()) {
                            int xp = rs.getInt("xp");
                            Bukkit.getScheduler().runTask(plugin, () -> applyExperience(player, xp));
                        }
                        if (plugin.isSyncGamemode()) {
                            String gm = rs.getString("gamemode");
                            if (gm != null) {
                                GameMode mode = GameMode.valueOf(gm);
                                Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(mode));
                            }
                        }
                        if (plugin.isSyncEnderchest()) {
                            String data = rs.getString("enderchest");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    Bukkit.getScheduler().runTask(plugin, () -> player.getEnderChest().setContents(items));
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing enderchest for " + player.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                        if (plugin.isSyncInventory()) {
                            String data = rs.getString("inventory");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().setContents(items));
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing inventory for " + player.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                        if (plugin.isSyncHealth()) {
                            double health = rs.getDouble("health");
                            Bukkit.getScheduler().runTask(plugin, () -> player.setHealth(Math.min(health, player.getMaxHealth())));
                        }
                        if (plugin.isSyncHunger()) {
                            int hunger = rs.getInt("hunger");
                            float saturation = rs.getFloat("saturation");
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.setFoodLevel(hunger);
                                player.setSaturation(saturation);
                            });
                        }
                        if (plugin.isSyncArmor()) {
                            String armorData = rs.getString("armor");
                            if (armorData != null) {
                                try {
                                    ItemStack[] armor = InventoryUtils.safeItemStackArrayFromBase64(armorData);
                                    Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().setArmorContents(armor));
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing armor for " + player.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                        if (plugin.isSyncOffhand()) {
                            String offhandData = rs.getString("offhand");
                            if (offhandData != null) {
                                try {
                                    ItemStack offhand = InventoryUtils.safeItemStackFromBase64(offhandData);
                                    Bukkit.getScheduler().runTask(plugin, () -> player.getInventory().setItemInOffHand(offhand));
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error deserializing offhand for " + player.getName() + ": " + e.getMessage());
                                }
                            }
                        }
                        if (plugin.isSyncEffects()) {
                            String effectsData = rs.getString("effects");
                            if (effectsData != null) {
                                Bukkit.getScheduler().runTask(plugin, () -> loadEffects(player, effectsData));
                            }
                        }
                        if (plugin.isSyncStatistics()) {
                            String statsData = rs.getString("statistics");
                            if (statsData != null) {
                                Bukkit.getScheduler().runTask(plugin, () -> loadStatistics(player, statsData));
                            }
                        }
                        if (plugin.isSyncAttributes()) {
                            String attributesData = rs.getString("attributes");
                            if (attributesData != null) {
                                Bukkit.getScheduler().runTask(plugin, () -> loadAttributes(player, attributesData));
                            }
                        }
                        if (plugin.isSyncAchievements()) {
                            String advData = rs.getString("advancements");
                            if (advData != null && !advData.isEmpty()) {
                                // Check if there are too many achievements to prevent lag
                                String[] achievementKeys = advData.split(",");
                                if (achievementKeys.length > 200) {
                                    plugin.getLogger().warning("Large amount of achievements detected for " + player.getName() + 
                                        " (" + achievementKeys.length + "). Loading in background to prevent server lag.");
                                    // Load achievements asynchronously in background
                                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                        try {
                                            loadAdvancements(player, advData);
                                        } catch (Exception e) {
                                            plugin.getLogger().severe("Error loading achievements for " + player.getName() + ": " + e.getMessage());
                                        }
                                    });
                                } else {
                                    // Load achievements normally for smaller amounts
                                    Bukkit.getScheduler().runTask(plugin, () -> loadAdvancements(player, advData));
                                }
                            }
                        }
                        if (plugin.isSyncEconomy()) {
                            double balance = rs.getDouble("economy");
                            plugin.getLogger().info("DEBUG: Loading economy balance for " + player.getName() + ": " + balance);
                            Bukkit.getScheduler().runTask(plugin, () -> setPlayerBalance(player, balance));
                        } else {
                            plugin.getLogger().info("DEBUG: Economy sync disabled, skipping balance load for " + player.getName());
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
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(total);
    }

    /**
     * Serialize only newly obtained achievements (not all achievements)
     * This prevents loading all 1000+ achievements on first login
     * CRITICAL FIX: Added timeout protection to prevent server freezing
     */
    private String serializeAdvancements(Player player) {
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
                plugin.getLogger().info("Serialized " + count + " achievements for " + player.getName() + " in " + 
                    (System.currentTimeMillis() - startTime) + "ms");
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
                sb.append(effect.getType().getName())
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
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
                        org.bukkit.potion.PotionEffectType type = org.bukkit.potion.PotionEffectType.getByName(parts[0]);
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
            plugin.getLogger().info("DEBUG: Retrieved balance for " + player.getName() + ": " + balance);
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

        plugin.getLogger().info("DEBUG: Attempting to set balance for " + player.getName() + " to " + balance);

        try {
            if (!economy.hasAccount(player)) {
                economy.createPlayerAccount(player);
            }

            plugin.getLogger().info("DEBUG: Economy provider found: " + economy.getName());

            try {
                java.lang.reflect.Method setBalanceMethod =
                    economy.getClass().getMethod("setBalance", org.bukkit.OfflinePlayer.class, double.class);
                setBalanceMethod.invoke(economy, player, balance);
                plugin.getLogger().info("DEBUG: Set balance for " + player.getName() + " to " + balance + " using setBalance method");
                return;
            } catch (NoSuchMethodException e) {
                plugin.getLogger().info("DEBUG: setBalance method not available, using deposit/withdraw approach");
            } catch (ReflectiveOperationException reflectiveError) {
                plugin.getLogger().warning("Failed to invoke setBalance on economy provider " + economy.getName() + ": " + reflectiveError.getMessage());
            }

            double currentBalance = economy.getBalance(player);
            double difference = balance - currentBalance;

            plugin.getLogger().info("DEBUG: Current balance: " + currentBalance + ", Target balance: " + balance + ", Difference: " + difference);

            if (Math.abs(difference) < 0.01) {
                plugin.getLogger().info("DEBUG: Balance is already correct (within tolerance)");
                return;
            }

            EconomyResponse response;
            if (difference > 0) {
                response = economy.depositPlayer(player, difference);
                if (!response.transactionSuccess()) {
                    plugin.getLogger().warning("Failed to deposit funds for " + player.getName() + ": " + response.errorMessage);
                    return;
                }
                plugin.getLogger().info("DEBUG: Added " + difference + " to " + player.getName() + "'s balance (now: " + balance + ")");
            } else {
                response = economy.withdrawPlayer(player, Math.abs(difference));
                if (!response.transactionSuccess()) {
                    plugin.getLogger().warning("Failed to withdraw funds for " + player.getName() + ": " + response.errorMessage);
                    return;
                }
                plugin.getLogger().info("DEBUG: Removed " + Math.abs(difference) + " from " + player.getName() + "'s balance (now: " + balance + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error setting player balance for " + player.getName() + ": " + e.getMessage());
        }
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
