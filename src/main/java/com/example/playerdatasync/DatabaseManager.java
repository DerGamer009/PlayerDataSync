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

public class DatabaseManager {
    private final PlayerDataSync plugin;

    public DatabaseManager(PlayerDataSync plugin) {
        this.plugin = plugin;
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

    public void savePlayer(Player player) {
        String sql = "REPLACE INTO player_data (uuid, world, x, y, z, yaw, pitch, xp, gamemode, enderchest, inventory, armor, offhand, effects, statistics, attributes, health, hunger, saturation, advancements, last_save, server_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),?)";
        
        Connection connection = null;
        try {
            connection = plugin.getConnection();
            if (connection == null) {
                plugin.getLogger().severe("Database connection unavailable");
                return;
            }
            
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.getUniqueId().toString());
            if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
                Location loc = player.getLocation();
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX());
                ps.setDouble(4, loc.getY());
                ps.setDouble(5, loc.getZ());
                ps.setFloat(6, loc.getYaw());
                ps.setFloat(7, loc.getPitch());
            } else {
                ps.setString(2, null);
                ps.setDouble(3, 0);
                ps.setDouble(4, 0);
                ps.setDouble(5, 0);
                ps.setFloat(6, 0);
                ps.setFloat(7, 0);
            }
            ps.setInt(8, plugin.isSyncXp() ? player.getTotalExperience() : 0);
            ps.setString(9, plugin.isSyncGamemode() ? player.getGameMode().name() : null);
            try {
                ps.setString(10, plugin.isSyncEnderchest() ? InventoryUtils.itemStackArrayToBase64(player.getEnderChest().getContents()) : null);
                ps.setString(11, plugin.isSyncInventory() ? InventoryUtils.itemStackArrayToBase64(player.getInventory().getContents()) : null);
                ps.setString(12, plugin.isSyncArmor() ? InventoryUtils.itemStackArrayToBase64(player.getInventory().getArmorContents()) : null);
                ps.setString(13, plugin.isSyncOffhand() ? InventoryUtils.itemStackToBase64(player.getInventory().getItemInOffHand()) : null);
                ps.setString(14, plugin.isSyncEffects() ? serializeEffects(player) : null);
                ps.setString(15, plugin.isSyncStatistics() ? serializeStatistics(player) : null);
                ps.setString(16, plugin.isSyncAttributes() ? serializeAttributes(player) : null);
            } catch (Exception e) {
                plugin.getLogger().severe("Error serializing data for " + player.getName() + ": " + e.getMessage());
                ps.setString(10, null);
                ps.setString(11, null);
                ps.setString(12, null);
                ps.setString(13, null);
                ps.setString(14, null);
                ps.setString(15, null);
                ps.setString(16, null);
            }
            ps.setDouble(17, plugin.isSyncHealth() ? player.getHealth() : player.getMaxHealth());
            ps.setInt(18, plugin.isSyncHunger() ? player.getFoodLevel() : 20);
            ps.setFloat(19, plugin.isSyncHunger() ? player.getSaturation() : 5);
            String advancementData = null;
            if (plugin.isSyncAchievements()) {
                // CRITICAL: Use async achievement serialization to prevent server freezing
                try {
                    advancementData = serializeAdvancements(player);
                    if (advancementData != null && advancementData.length() > 16777215) {
                        plugin.getLogger().warning("Advancement data for " + player.getName() + " is too large (" + 
                            advancementData.length() + " characters), skipping advancement sync to prevent database errors");
                        advancementData = null;
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("CRITICAL: Achievement serialization failed for " + player.getName() + 
                        ". Disabling achievement sync to prevent server freeze: " + e.getMessage());
                    advancementData = null;
                    
                    // CRITICAL: Disable achievement sync if it causes critical errors
                    if (plugin.getConfig().getBoolean("compatibility.disable_achievements_on_critical_error", true)) {
                        plugin.getLogger().severe("CRITICAL: Automatically disabling achievement sync for " + player.getName() + 
                            " due to critical error. Set 'compatibility.disable_achievements_on_critical_error: false' to prevent this.");
                        // Note: We can't call setSyncAchievements(false) here as it's not accessible
                    }
                }
            }
                
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            // Handle specific data truncation errors
            if (e.getMessage().contains("Data too long for column")) {
                plugin.getLogger().severe("Data truncation error for " + player.getName() + 
                    ": " + e.getMessage() + ". Consider disabling achievement sync or check your database column sizes.");
            } else {
                plugin.getLogger().severe("Could not save data for " + player.getName() + ": " + e.getMessage());
            }
        } finally {
            plugin.returnConnection(connection);
        }
    }

    public void loadPlayer(Player player) {
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
                                ItemStack[] items = InventoryUtils.itemStackArrayFromBase64(data);
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
                                ItemStack[] items = InventoryUtils.itemStackArrayFromBase64(data);
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
                                ItemStack[] armor = InventoryUtils.itemStackArrayFromBase64(armorData);
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
                                ItemStack offhand = InventoryUtils.itemStackFromBase64(offhandData);
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
                }
            }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load data for " + player.getName() + ": " + e.getMessage());
        } finally {
            plugin.returnConnection(connection);
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
            try {
                // CRITICAL: Add timeout check for counting achievements
                for (Iterator<Advancement> it = Bukkit.getServer().advancementIterator(); it.hasNext(); ) {
                    totalAdvancements++;
                    
                    // CRITICAL: Check timeout every 100 achievements to prevent freezing
                    if (totalAdvancements % 100 == 0) {
                        if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                            plugin.getLogger().severe("CRITICAL: Achievement counting timeout for " + player.getName() + 
                                " after " + totalAdvancements + " achievements. Aborting to prevent server freeze.");
                            return null;
                        }
                    }
                }
                
                // If there are more than 500 achievements, disable sync to prevent lag
                if (totalAdvancements > 500) {
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
        final int MAX_ACHIEVEMENTS = plugin.getConfig().getInt("performance.max_achievements_per_player", 1000); // Configurable limit
        
        try {
            // Only serialize achievements that are actually completed
            // This prevents loading all 1000+ achievements on first login
            Iterator<Advancement> it = Bukkit.getServer().advancementIterator();
            
            while (it.hasNext() && count < MAX_ACHIEVEMENTS) {
                // CRITICAL: Check timeout every 50 achievements
                if (count % 50 == 0 && count > 0) {
                    if (System.currentTimeMillis() - startTime > TIMEOUT_MS) {
                        plugin.getLogger().severe("CRITICAL: Achievement serialization timeout for " + player.getName() + 
                            " after " + count + " achievements. Aborting to prevent server freeze.");
                        break;
                    }
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
                                sb.append(attrName).append(",").append(instance.getValue());
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
                                sb.append(attr.name()).append(",").append(instance.getValue());
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
                            sb.append(attr.name()).append(",").append(instance.getValue());
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
}
