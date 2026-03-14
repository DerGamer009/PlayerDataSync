package com.example.playerdatasync.nms.v1_16_R3;

import com.example.playerdatasync.nms.NMSHandler;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.Iterator;
import java.util.UUID;
import java.util.StringJoiner;

public class NMSHandlerImpl implements NMSHandler {

    @Override
    public void setItemInOffHand(Player player, ItemStack item) {
        player.getInventory().setItemInOffHand(item);
    }

    @Override
    public ItemStack getItemInOffHand(Player player) {
        return player.getInventory().getItemInOffHand();
    }

    @Override
    public double getGenericMaxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attr != null ? attr.getValue() : player.getMaxHealth();
    }

    @Override
    public void setGenericMaxHealth(Player player, double value) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(value);
        } else {
            player.setMaxHealth(value);
        }
    }

    @Override
    public String serializeAttributes(Player player) {
        StringJoiner joiner = new StringJoiner("|");
        for (Attribute attr : Attribute.values()) {
            AttributeInstance instance = player.getAttribute(attr);
            if (instance != null) {
                joiner.add(attr.name() + ":" + instance.getBaseValue());
            }
        }
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
                    Attribute attr = Attribute.valueOf(kv[0]);
                    double value = Double.parseDouble(kv[1]);
                    AttributeInstance instance = player.getAttribute(attr);
                    if (instance != null) {
                        instance.setBaseValue(value);
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void setupAdvancements(Plugin plugin) {
        // Basic implementation, usually handled by a manager
    }

    @Override
    public void shutdownAdvancements() {
    }

    @Override
    public void handlePlayerJoinAdvancements(Player player) {
    }

    @Override
    public void handlePlayerQuitAdvancements(Player player) {
    }

    @Override
    public void seedAdvancementsFromDatabase(UUID uuid, String csv) {
    }

    @Override
    public String serializeAdvancements(Player player) {
        StringJoiner joiner = new StringJoiner(",");
        Iterator<Advancement> it = player.getServer().advancementIterator();
        while (it.hasNext()) {
            Advancement adv = it.next();
            AdvancementProgress prog = player.getAdvancementProgress(adv);
            if (prog.isDone()) {
                joiner.add(adv.getKey().toString());
            }
        }
        return joiner.toString();
    }

    @Override
    public void loadAdvancements(Player player, String data) {
        if (data == null || data.isEmpty()) return;
        String[] keys = data.split(",");
        for (String key : keys) {
            try {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    NamespacedKey nsk = new NamespacedKey(parts[0], parts[1]);
                    Advancement adv = player.getServer().getAdvancement(nsk);
                    if (adv != null) {
                        AdvancementProgress prog = player.getAdvancementProgress(adv);
                        for (String criterion : prog.getRemainingCriteria()) {
                            prog.awardCriteria(criterion);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void queueAdvancementImport(Player player, boolean force) {
        // Could trigger a rescan
    }
}
