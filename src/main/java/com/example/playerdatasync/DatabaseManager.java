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
                "health DOUBLE," +
                "hunger INT," +
                "saturation FLOAT," +
                "advancements TEXT" +
                ")";
        Connection connection = plugin.getConnection();
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
                    st.executeUpdate("ALTER TABLE player_data ADD COLUMN advancements TEXT");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create table: " + e.getMessage());
        }
    }

    public void savePlayer(Player player) {
        String sql = "REPLACE INTO player_data (uuid, world, x, y, z, yaw, pitch, xp, gamemode, enderchest, inventory, health, hunger, saturation, advancements) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Connection connection = plugin.getConnection();
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
            } catch (Exception e) {
                plugin.getLogger().severe("Error serializing inventory for " + player.getName() + ": " + e.getMessage());
                ps.setString(10, null);
                ps.setString(11, null);
            }
            ps.setDouble(12, plugin.isSyncHealth() ? player.getHealth() : player.getMaxHealth());
            ps.setInt(13, plugin.isSyncHunger() ? player.getFoodLevel() : 20);
            ps.setFloat(14, plugin.isSyncHunger() ? player.getSaturation() : 5);
            ps.setString(15, plugin.isSyncAchievements() ? serializeAdvancements(player) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save data for " + player.getName() + ": " + e.getMessage());
        }
    }

    public void loadPlayer(Player player) {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        Connection connection = plugin.getConnection();
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
                    if (plugin.isSyncAchievements()) {
                        String advData = rs.getString("advancements");
                        if (advData != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> loadAdvancements(player, advData));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load data for " + player.getName() + ": " + e.getMessage());
        }
    }

    private void applyExperience(Player player, int total) {
        player.setExp(0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(total);
    }

    private String serializeAdvancements(Player player) {
        StringBuilder sb = new StringBuilder();
        for (Advancement adv : Bukkit.getServer().advancementIterator()) {
            AdvancementProgress progress = player.getAdvancementProgress(adv);
            if (progress.isDone()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(adv.getKey());
            }
        }
        return sb.toString();
    }

    private void loadAdvancements(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        String[] keys = data.split(",");
        for (String k : keys) {
            NamespacedKey key = NamespacedKey.fromString(k);
            if (key == null) continue;
            Advancement adv = Bukkit.getAdvancement(key);
            if (adv == null) continue;
            AdvancementProgress prog = player.getAdvancementProgress(adv);
            for (String criterion : prog.getRemainingCriteria()) {
                prog.awardCriteria(criterion);
            }
        }
    }
}
