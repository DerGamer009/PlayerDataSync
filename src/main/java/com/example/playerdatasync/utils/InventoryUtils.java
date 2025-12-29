package com.example.playerdatasync.utils;

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

    private static final String DOWNGRADE_ERROR_FRAGMENT = "Server downgrades are not supported";
    private static final String NEWER_VERSION_FRAGMENT = "Newer version";
    
    /**
     * Convert ItemStack array to Base64 string with validation
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        if (items == null) return "";
        
        // Validate and sanitize items before serialization
        ItemStack[] sanitizedItems = sanitizeItemStackArray(items);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(sanitizedItems.length);
            for (ItemStack item : sanitizedItems) {
                dataOutput.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Convert Base64 string to ItemStack array with validation and version compatibility
     */
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        ItemStack[] items;
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            int length = dataInput.readInt();
            items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                try {
                    items[i] = (ItemStack) dataInput.readObject();
                } catch (Exception e) {
                    if (isVersionDowngradeIssue(e)) {
                        System.err.println("[PlayerDataSync] Version compatibility issue detected for item " + i
                            + ": " + collectCompatibilityMessage(e) + ". Skipping unsupported item.");
                        items[i] = null;
                    } else {
                        throw e;
                    }
                }
            }
        }
        
        // Validate deserialized items
        if (!validateItemStackArray(items)) {
            // If validation fails, sanitize the items
            return sanitizeItemStackArray(items);
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
     * Convert Base64 string to single ItemStack with version compatibility
     */
    public static ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return null;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            try {
                return (ItemStack) dataInput.readObject();
            } catch (Exception e) {
                if (isVersionDowngradeIssue(e)) {
                    System.err.println("[PlayerDataSync] Version compatibility issue detected for single item: "
                        + collectCompatibilityMessage(e) + ". Returning null.");
                    return null;
                } else {
                    throw e;
                }
            }
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
     * Decompress ItemStack array data with version compatibility
     */
    public static ItemStack[] decompressItemStackArray(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        // For now, just use the standard deserialization
        // In the future, this could implement actual decompression
        return itemStackArrayFromBase64(data);
    }
    
    /**
     * Safely deserialize ItemStack array with comprehensive error handling
     * Returns empty array if deserialization fails completely
     */
    public static ItemStack[] safeItemStackArrayFromBase64(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        try {
            return itemStackArrayFromBase64(data);
        } catch (Exception e) {
            System.err.println("[PlayerDataSync] Failed to deserialize ItemStack array: "
                + collectCompatibilityMessage(e));
            return new ItemStack[0];
        }
    }
    
    /**
     * Safely deserialize single ItemStack with comprehensive error handling
     * Returns null if deserialization fails
     */
    public static ItemStack safeItemStackFromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        
        try {
            return itemStackFromBase64(data);
        } catch (Exception e) {
            System.err.println("[PlayerDataSync] Failed to deserialize single ItemStack: "
                + collectCompatibilityMessage(e));
            return null;
        }
    }

    private static boolean isVersionDowngradeIssue(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains(NEWER_VERSION_FRAGMENT)
                || message.contains(DOWNGRADE_ERROR_FRAGMENT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String collectCompatibilityMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        boolean first = true;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isEmpty()) {
                if (!first) {
                    builder.append(" | cause: ");
                }
                builder.append(message);
                first = false;
            }
            current = current.getCause();
        }
        if (builder.length() == 0) {
            builder.append(throwable.getClass().getName());
        }
        return builder.toString();
    }
}
