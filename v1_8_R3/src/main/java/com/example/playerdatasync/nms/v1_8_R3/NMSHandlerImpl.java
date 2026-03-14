package com.example.playerdatasync.nms.v1_8_R3;

import com.example.playerdatasync.nms.NMSHandler;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public class NMSHandlerImpl implements NMSHandler {

    @Override
    public void setItemInOffHand(Player player, ItemStack item) {
        // Not supported in 1.8
    }

    @Override
    public ItemStack getItemInOffHand(Player player) {
        return null; // Not supported in 1.8
    }

    @Override
    public double getGenericMaxHealth(Player player) {
        return player.getMaxHealth();
    }

    @Override
    public void setGenericMaxHealth(Player player, double value) {
        player.setMaxHealth(value);
    }

    @Override
    public String serializeAttributes(Player player) {
        return null; // 1.8 doesn't have the same attribute system
    }

    @Override
    public void loadAttributes(Player player, String data) {
        // Not supported or handled differently in 1.8
    }

    @Override
    public void setupAdvancements(Plugin plugin) {
        // Not supported in 1.8
    }

    @Override
    public void shutdownAdvancements() {
        // Not supported in 1.8
    }

    @Override
    public void handlePlayerJoinAdvancements(Player player) {
        // Not supported in 1.8
    }

    @Override
    public void handlePlayerQuitAdvancements(Player player) {
        // Not supported in 1.8
    }

    @Override
    public void seedAdvancementsFromDatabase(UUID uuid, String csv) {
        // Not supported in 1.8
    }

    @Override
    public String serializeAdvancements(Player player) {
        return null;
    }

    @Override
    public void loadAdvancements(Player player, String data) {
        // Not supported in 1.8
    }

    @Override
    public void queueAdvancementImport(Player player, boolean force) {
        // Not supported in 1.8
    }
}
