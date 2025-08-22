package com.example.playerdatasync;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Enhanced inventory utilities for PlayerDataSync
 * Supports serialization of various inventory types and single items
 */
public class InventoryUtils {
    
    /**
     * Convert ItemStack array to Base64 string
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        if (items == null) return "";
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Convert Base64 string to ItemStack array
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        ItemStack[] items;
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
        }
        return items;
    }
    
    /**
     * Convert single ItemStack to Base64 string
     */
    public static String itemStackToBase64(ItemStack item) throws IOException {
        if (item == null) return "";
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Convert Base64 string to single ItemStack
     */
    public static ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return null;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        }
    }
    
    /**
     * Validate ItemStack array for corruption
     */
    public static boolean validateItemStackArray(ItemStack[] items) {
        if (items == null) return true;
        
        try {
            for (ItemStack item : items) {
                if (item != null) {
                    // Basic validation - check if the item is valid
                    item.getType();
                    item.getAmount();
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Sanitize ItemStack array (remove invalid items)
     */
    public static ItemStack[] sanitizeItemStackArray(ItemStack[] items) {
        if (items == null) return null;
        
        ItemStack[] sanitized = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            try {
                ItemStack item = items[i];
                if (item != null && item.getType() != null && item.getAmount() > 0) {
                    sanitized[i] = item.clone();
                }
            } catch (Exception e) {
                // Skip invalid items
                sanitized[i] = null;
            }
        }
        return sanitized;
    }
    
    /**
     * Count non-null items in array
     */
    public static int countItems(ItemStack[] items) {
        if (items == null) return 0;
        
        int count = 0;
        for (ItemStack item : items) {
            if (item != null) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Calculate total storage size of items
     */
    public static long calculateStorageSize(ItemStack[] items) {
        if (items == null) return 0;
        
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeInt(items.length);
                for (ItemStack item : items) {
                    dataOutput.writeObject(item);
                }
            }
            return outputStream.size();
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Compress ItemStack array data
     */
    public static String compressItemStackArray(ItemStack[] items) throws IOException {
        if (items == null) return "";
        
        // For now, just use the standard serialization
        // In the future, this could implement actual compression
        return itemStackArrayToBase64(items);
    }
    
    /**
     * Decompress ItemStack array data
     */
    public static ItemStack[] decompressItemStackArray(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        // For now, just use the standard deserialization
        // In the future, this could implement actual decompression
        return itemStackArrayFromBase64(data);
    }
}
