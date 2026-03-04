package com.example.playerdatasync.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.example.playerdatasync.core.PlayerDataSync;
import com.example.playerdatasync.managers.AdvancementSyncManager;
import com.example.playerdatasync.utils.InventoryUtils;
import com.example.playerdatasync.utils.OfflinePlayerData;
import com.example.playerdatasync.utils.SchedulerUtils;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MongoDatabaseManager implements DatabaseManager {
    private final PlayerDataSync plugin;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    private long totalSaveTime = 0;
    private long totalLoadTime = 0;
    private int saveCount = 0;
    private int loadCount = 0;

    public MongoDatabaseManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        String uri = plugin.getConfig().getString("database.mongodb.uri", "mongodb://localhost:27017");
        String dbName = plugin.getConfig().getString("database.mongodb.database", "minecraft");
        String collectionName = plugin.getConfig().getString("database.mongodb.collection_prefix", "player_data");

        try {
            mongoClient = MongoClients.create(uri);
            database = mongoClient.getDatabase(dbName);
            collection = database.getCollection(collectionName);
            plugin.getLogger().info("Connected to MongoDB at " + uri);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not connect to MongoDB: " + e.getMessage());
        }
    }

    @Override
    public boolean savePlayer(Player player) {
        long startTime = System.currentTimeMillis();

        try {
            Document doc = new Document("uuid", player.getUniqueId().toString())
                    .append("last_save", System.currentTimeMillis())
                    .append("server_id", plugin.getConfig().getString("server.id", "default"));

            if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
                Location loc = player.getLocation();
                World world = loc.getWorld();
                doc.append("world", world != null ? world.getName() : null)
                        .append("x", loc.getX())
                        .append("y", loc.getY())
                        .append("z", loc.getZ())
                        .append("yaw", loc.getYaw())
                        .append("pitch", loc.getPitch());
            }

            if (plugin.isSyncXp()) {
                doc.append("xp", player.getTotalExperience());
            }

            if (plugin.isSyncGamemode()) {
                doc.append("gamemode", player.getGameMode().name());
            }

            if (plugin.isSyncEnderchest()) {
                doc.append("enderchest", InventoryUtils.itemStackArrayToBase64(player.getEnderChest().getContents()));
            }

            if (plugin.isSyncInventory()) {
                doc.append("inventory", InventoryUtils.itemStackArrayToBase64(player.getInventory().getContents()));
            }

            if (plugin.isSyncArmor()) {
                doc.append("armor", InventoryUtils.itemStackArrayToBase64(player.getInventory().getArmorContents()));
            }

            if (plugin.isSyncHealth()) {
                doc.append("health", player.getHealth());
            }

            if (plugin.isSyncHunger()) {
                doc.append("hunger", player.getFoodLevel())
                        .append("saturation", player.getSaturation());
            }

            // Execute replacement
            SchedulerUtils.runTaskAsync(plugin, () -> {
                try {
                    collection.replaceOne(Filters.eq("uuid", player.getUniqueId().toString()), doc,
                            new ReplaceOptions().upsert(true));
                    long saveTime = System.currentTimeMillis() - startTime;
                    totalSaveTime += saveTime;
                    saveCount++;
                } catch (Exception e) {
                    plugin.getLogger()
                            .severe("Could not save data to MongoDB for " + player.getName() + ": " + e.getMessage());
                }
            });

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error saving player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public void loadPlayer(Player player) {
        long startTime = System.currentTimeMillis();

        SchedulerUtils.runTaskAsync(plugin, () -> {
            try {
                Document doc = collection.find(Filters.eq("uuid", player.getUniqueId().toString())).first();
                if (doc != null) {
                    SchedulerUtils.runTask(plugin, player, () -> {
                        if (plugin.isSyncCoordinates() || plugin.isSyncPosition()) {
                            String worldName = doc.getString("world");
                            if (worldName != null && !worldName.isEmpty()) {
                                World world = Bukkit.getWorld(worldName);
                                if (world != null) {
                                    Location loc = new Location(world,
                                            doc.getDouble("x"),
                                            doc.getDouble("y"),
                                            doc.getDouble("z"),
                                            doc.getDouble("yaw").floatValue(),
                                            doc.getDouble("pitch").floatValue());
                                    player.teleport(loc);
                                }
                            }
                        }

                        // Remaining logic simplified for demonstration...
                        // Load inventory, enderchest, etc using the
                        // InventoryUtils.safeItemStackArrayFromBase64
                        if (plugin.isSyncEnderchest()) {
                            String data = doc.getString("enderchest");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    player.getEnderChest().setContents(items);
                                } catch (Exception e) {
                                }
                            }
                        }

                        if (plugin.isSyncInventory()) {
                            String data = doc.getString("inventory");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    player.getInventory().setContents(items);
                                } catch (Exception e) {
                                }
                            }
                        }

                        if (plugin.isSyncArmor()) {
                            String data = doc.getString("armor");
                            if (data != null) {
                                try {
                                    ItemStack[] items = InventoryUtils.safeItemStackArrayFromBase64(data);
                                    player.getInventory().setArmorContents(items);
                                } catch (Exception e) {
                                }
                            }
                        }
                    });
                }

                long loadTime = System.currentTimeMillis() - startTime;
                totalLoadTime += loadTime;
                loadCount++;

            } catch (Exception e) {
                plugin.getLogger()
                        .severe("Could not load data from MongoDB for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @Override
    public String getPerformanceStats() {
        double avgSave = saveCount > 0 ? (double) totalSaveTime / saveCount : 0;
        double avgLoad = loadCount > 0 ? (double) totalLoadTime / loadCount : 0;
        return String.format("MongoDB Save: %.2fms, Load: %.2fms", avgSave, avgLoad);
    }

    @Override
    public void resetPerformanceStats() {
        totalSaveTime = 0;
        totalLoadTime = 0;
        saveCount = 0;
        loadCount = 0;
    }

    @Override
    public double getPlayerBalance(Player player) {
        Document doc = collection.find(Filters.eq("uuid", player.getUniqueId().toString())).first();
        if (doc != null && doc.containsKey("economy")) {
            return doc.getDouble("economy");
        }
        return 0.0;
    }

    @Override
    public void setPlayerBalance(Player player, double balance) {
        collection.updateOne(Filters.eq("uuid", player.getUniqueId().toString()),
                new Document("$set", new Document("economy", balance)));
    }

    @Override
    public OfflinePlayerData loadOfflinePlayerData(UUID uuid, String fallbackName) {
        Document doc = collection.find(Filters.eq("uuid", uuid.toString())).first();
        if (doc == null)
            return null;

        OfflinePlayerData data = new OfflinePlayerData(uuid, fallbackName);
        try {
            if (doc.getString("inventory") != null)
                data.setInventoryContents(InventoryUtils.safeItemStackArrayFromBase64(doc.getString("inventory")));
            if (doc.getString("armor") != null)
                data.setArmorContents(InventoryUtils.safeItemStackArrayFromBase64(doc.getString("armor")));
            if (doc.getString("enderchest") != null)
                data.setEnderChestContents(InventoryUtils.safeItemStackArrayFromBase64(doc.getString("enderchest")));
        } catch (Exception e) {
        }
        return data;
    }

    @Override
    public boolean saveOfflineInventoryData(OfflinePlayerData data) {
        try {
            collection.updateOne(Filters.eq("uuid", data.getUuid().toString()),
                    new Document("$set",
                            new Document("inventory",
                                    InventoryUtils.itemStackArrayToBase64(data.getInventoryContents()))
                                    .append("armor", InventoryUtils.itemStackArrayToBase64(data.getArmorContents()))));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean saveOfflineEnderChestData(OfflinePlayerData data) {
        try {
            collection.updateOne(Filters.eq("uuid", data.getUuid().toString()),
                    new Document("$set", new Document("enderchest",
                            InventoryUtils.itemStackArrayToBase64(data.getEnderChestContents()))));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
