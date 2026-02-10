package com.example.playerdatasync.premium.utils;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Represents offline player inventory data that can be viewed or edited
 * without the player being online.
 */
public class OfflinePlayerData {
    private final UUID uuid;
    private final String lastKnownName;
    private ItemStack[] inventoryContents;
    private ItemStack[] armorContents;
    private ItemStack[] enderChestContents;
    private ItemStack offhandItem;
    private boolean existsInDatabase;

    public OfflinePlayerData(UUID uuid, String lastKnownName) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.inventoryContents = new ItemStack[0];
        this.armorContents = new ItemStack[0];
        this.enderChestContents = new ItemStack[0];
        this.offhandItem = null;
        this.existsInDatabase = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getDisplayName() {
        return lastKnownName != null ? lastKnownName : (uuid != null ? uuid.toString() : "unknown");
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents != null ? inventoryContents : new ItemStack[0];
    }

    public void setInventoryContents(ItemStack[] inventoryContents) {
        this.inventoryContents = inventoryContents != null ? inventoryContents : new ItemStack[0];
    }

    public ItemStack[] getArmorContents() {
        return armorContents != null ? armorContents : new ItemStack[0];
    }

    public void setArmorContents(ItemStack[] armorContents) {
        this.armorContents = armorContents != null ? armorContents : new ItemStack[0];
    }

    public ItemStack[] getEnderChestContents() {
        return enderChestContents != null ? enderChestContents : new ItemStack[0];
    }

    public void setEnderChestContents(ItemStack[] enderChestContents) {
        this.enderChestContents = enderChestContents != null ? enderChestContents : new ItemStack[0];
    }

    public ItemStack getOffhandItem() {
        return offhandItem;
    }

    public void setOffhandItem(ItemStack offhandItem) {
        this.offhandItem = offhandItem;
    }

    public boolean existsInDatabase() {
        return existsInDatabase;
    }

    public void setExistsInDatabase(boolean existsInDatabase) {
        this.existsInDatabase = existsInDatabase;
    }
}
