package com.example.playerdatasync.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public class FallbackNMSHandler implements NMSHandler {
    @Override public void setItemInOffHand(Player player, ItemStack item) {}
    @Override public ItemStack getItemInOffHand(Player player) { return null; }
    @Override public double getGenericMaxHealth(Player player) { return player.getMaxHealth(); }
    @Override public void setGenericMaxHealth(Player player, double value) {}
    @Override public String serializeAttributes(Player player) { return null; }
    @Override public void loadAttributes(Player player, String data) {}
    @Override public void setupAdvancements(Plugin plugin) {}
    @Override public void shutdownAdvancements() {}
    @Override public void handlePlayerJoinAdvancements(Player player) {}
    @Override public void handlePlayerQuitAdvancements(Player player) {}
    @Override public void seedAdvancementsFromDatabase(UUID uuid, String csv) {}
    @Override public String serializeAdvancements(Player player) { return null; }
    @Override public void loadAdvancements(Player player, String data) {}
    @Override public void queueAdvancementImport(Player player, boolean force) {}
}
