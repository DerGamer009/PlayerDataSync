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
        java.util.StringJoiner joiner = new java.util.StringJoiner("|");
        joiner.add("GENERIC_MAX_HEALTH:" + player.getMaxHealth());
        joiner.add("GENERIC_MOVEMENT_SPEED:" + player.getWalkSpeed());
        joiner.add("GENERIC_FLYING_SPEED:" + player.getFlySpeed());
        return joiner.toString();
    }

    @Override
    public void loadAttributes(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        String[] parts = data.split("\\|");
        for (String part : parts) {
            String[] kv = part.split(":");
            if (kv.length == 2) {
                try {
                    String name = kv[0];
                    double value = Double.parseDouble(kv[1]);
                    if (name.equals("GENERIC_MAX_HEALTH")) {
                        player.setMaxHealth(value);
                    } else if (name.equals("GENERIC_MOVEMENT_SPEED")) {
                        player.setWalkSpeed((float) value);
                    } else if (name.equals("GENERIC_FLYING_SPEED")) {
                        player.setFlySpeed((float) value);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void setupAdvancements(Plugin plugin) {}

    @Override
    public void shutdownAdvancements() {}

    @Override
    public void handlePlayerJoinAdvancements(Player player) {}

    @Override
    public void handlePlayerQuitAdvancements(Player player) {}

    @Override
    public void seedAdvancementsFromDatabase(UUID uuid, String csv) {}

    @Override
    public String serializeAdvancements(Player player) {
        java.util.StringJoiner joiner = new java.util.StringJoiner(",");
        for (org.bukkit.Achievement ach : org.bukkit.Achievement.values()) {
            if (player.hasAchievement(ach)) {
                joiner.add(ach.toString());
            }
        }
        return joiner.toString();
    }

    @Override
    public void loadAdvancements(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        String[] parts = data.split(",");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                org.bukkit.Achievement ach = org.bukkit.Achievement.valueOf(part.trim().toUpperCase());
                player.awardAchievement(ach);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void queueAdvancementImport(Player player, boolean force) {
        // Not supported in 1.8
    }
}
