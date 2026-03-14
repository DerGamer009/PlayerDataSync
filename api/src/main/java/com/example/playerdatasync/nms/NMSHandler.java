package com.example.playerdatasync.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public interface NMSHandler {
    
    // Inventory
    void setItemInOffHand(Player player, ItemStack item);
    ItemStack getItemInOffHand(Player player);
    
    // Attributes
    double getGenericMaxHealth(Player player);
    void setGenericMaxHealth(Player player, double value);
    String serializeAttributes(Player player);
    void loadAttributes(Player player, String data);
    
    // Advancements
    void setupAdvancements(Plugin plugin);
    void shutdownAdvancements();
    void handlePlayerJoinAdvancements(Player player);
    void handlePlayerQuitAdvancements(Player player);
    void seedAdvancementsFromDatabase(UUID uuid, String csv);
    String serializeAdvancements(Player player);
    void loadAdvancements(Player player, String data);
    void queueAdvancementImport(Player player, boolean force);
}
