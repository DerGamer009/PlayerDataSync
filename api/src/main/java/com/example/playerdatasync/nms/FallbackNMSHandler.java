package com.example.playerdatasync.nms;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.UUID;

public class FallbackNMSHandler implements NMSHandler {
    @Override
    public void setItemInOffHand(Player player, ItemStack item) {
        try {
            player.getInventory().getClass().getMethod("setItemInOffHand", ItemStack.class).invoke(player.getInventory(), item);
        } catch (Exception ignored) {
            // Probably version < 1.9
        }
    }

    @Override
    public ItemStack getItemInOffHand(Player player) {
        try {
            return (ItemStack) player.getInventory().getClass().getMethod("getItemInOffHand").invoke(player.getInventory());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public double getGenericMaxHealth(Player player) {
        try {
            // Try to use Attributes API first (1.9+)
            Object attribute = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, 
                Class.forName("org.bukkit.attribute.Attribute").getField("GENERIC_MAX_HEALTH").get(null));
            if (attribute != null) {
                return (double) attribute.getClass().getMethod("getValue").invoke(attribute);
            }
        } catch (Exception ignored) {}
        
        // Fallback to deprecated method for 1.8
        return player.getMaxHealth();
    }

    @Override
    public void setGenericMaxHealth(Player player, double value) {
        try {
            // Try to use Attributes API first (1.9+)
            Object attribute = player.getClass().getMethod("getAttribute", Class.forName("org.bukkit.attribute.Attribute")).invoke(player, 
                Class.forName("org.bukkit.attribute.Attribute").getField("GENERIC_MAX_HEALTH").get(null));
            if (attribute != null) {
                attribute.getClass().getMethod("setBaseValue", double.class).invoke(attribute, value);
                return;
            }
        } catch (Exception ignored) {}
        
        // Fallback to deprecated method for 1.8
        player.setMaxHealth(value);
    }

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
