package com.example.playerdatasync.premium.database;

import org.bukkit.entity.Player;
import java.util.UUID;
import com.example.playerdatasync.premium.utils.OfflinePlayerData;

public interface DatabaseManager {
    void initialize();
    boolean savePlayer(Player player);
    void loadPlayer(Player player);
    
    String getPerformanceStats();
    void resetPerformanceStats();
    
    double getPlayerBalance(Player player);
    void setPlayerBalance(Player player, double balance);
    
    OfflinePlayerData loadOfflinePlayerData(UUID uuid, String fallbackName);
    void saveOfflineInventoryData(OfflinePlayerData data);
    void saveOfflineEnderChestData(OfflinePlayerData data);
}
