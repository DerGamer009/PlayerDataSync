package com.example.playerdatasync.utils;

import org.bukkit.Bukkit;
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
 * Includes robust handling for custom enchantments from plugins like ExcellentEnchants and EcoEnchants
 */
public class InventoryUtils {

    private static final String DOWNGRADE_ERROR_FRAGMENT = "Server downgrades are not supported";
    private static final String NEWER_VERSION_FRAGMENT = "Newer version";
    
    // Statistics for deserialization issues
    private static int customEnchantmentFailures = 0;
    private static int versionCompatibilityFailures = 0;
    private static int otherDeserializationFailures = 0;
    
    /**
     * Convert ItemStack array to Base64 string with validation
     * Preserves custom enchantments and NBT data from plugins like ExcellentEnchants and EcoEnchants
     */
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        if (items == null) return "";
        
        // Validate and sanitize items before serialization
        // Note: sanitizeItemStackArray uses clone() which should preserve all NBT data including custom enchantments
        ItemStack[] sanitizedItems = sanitizeItemStackArray(items);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(sanitizedItems.length);
            for (ItemStack item : sanitizedItems) {
                // BukkitObjectOutputStream serializes the entire ItemStack including all NBT data,
                // which should preserve custom enchantments from plugins like ExcellentEnchants and EcoEnchants
                dataOutput.writeObject(item);
            }
        }
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Convert Base64 string to ItemStack array with validation and version compatibility
     * Preserves all NBT data including custom enchantments from plugins like ExcellentEnchants and EcoEnchants
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
                    // BukkitObjectInputStream deserializes the complete ItemStack including all NBT data
                    // This preserves custom enchantments stored in PersistentDataContainer
                    Object obj = dataInput.readObject();
                    if (obj == null) {
                        items[i] = null;
                        continue;
                    }
                    items[i] = (ItemStack) obj;
                } catch (Exception e) {
                    if (isVersionDowngradeIssue(e)) {
                        versionCompatibilityFailures++;
                        String enchantmentName = extractEnchantmentName(e);
                        Bukkit.getLogger().warning("[PlayerDataSync] Version compatibility issue detected for item " + i
                            + (enchantmentName != null ? " (enchantment: " + enchantmentName + ")" : "")
                            + ": " + collectCompatibilityMessage(e) + ". Skipping unsupported item.");
                        items[i] = null;
                    } else if (isCustomEnchantmentIssue(e)) {
                        customEnchantmentFailures++;
                        String enchantmentName = extractEnchantmentName(e);
                        
                        // Log detailed information about the custom enchantment issue
                        Bukkit.getLogger().warning("[PlayerDataSync] Custom enchantment deserialization failed for item " + i
                            + (enchantmentName != null ? " (enchantment: " + enchantmentName + ")" : "")
                            + ". The enchantment plugin may not be loaded or the enchantment is not registered.");
                        
                        // Extract more details from the error
                        String errorDetails = extractErrorDetails(e);
                        if (errorDetails != null && !errorDetails.isEmpty()) {
                            Bukkit.getLogger().fine("[PlayerDataSync] Error details: " + errorDetails);
                        }
                        
                        // The item cannot be deserialized due to the custom enchantment issue
                        // The NBT data is preserved in the database and will be available once
                        // the enchantment plugin is properly loaded and recognizes the enchantment
                        items[i] = null;
                        
                        Bukkit.getLogger().info("[PlayerDataSync] Item " + i + " skipped. Data preserved in database. " +
                            "Ensure the enchantment plugin (e.g., ExcellentEnchants/EcoEnchants) is loaded and the enchantment is registered.");
                    } else {
                        otherDeserializationFailures++;
                        String errorType = e.getClass().getSimpleName();
                        Bukkit.getLogger().warning("[PlayerDataSync] Failed to deserialize item " + i 
                            + " (error type: " + errorType + "): " + collectCompatibilityMessage(e) + ". Skipping item.");
                        items[i] = null;
                    }
                }
            }
        }
        
        // Validate deserialized items
        // Note: We don't sanitize here to preserve all NBT data including custom enchantments
        // Only validate that items are not corrupted
        if (!validateItemStackArray(items)) {
            // If validation fails, sanitize the items (but preserve NBT data via clone())
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
     * Preserves all NBT data including custom enchantments from plugins like ExcellentEnchants and EcoEnchants
     */
    public static ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) return null;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            try {
                // BukkitObjectInputStream deserializes the complete ItemStack including all NBT data
                // This preserves custom enchantments stored in PersistentDataContainer
                Object obj = dataInput.readObject();
                if (obj == null) {
                    return null;
                }
                return (ItemStack) obj;
            } catch (Exception e) {
                if (isVersionDowngradeIssue(e)) {
                    versionCompatibilityFailures++;
                    String enchantmentName = extractEnchantmentName(e);
                    Bukkit.getLogger().warning("[PlayerDataSync] Version compatibility issue detected for single item"
                        + (enchantmentName != null ? " (enchantment: " + enchantmentName + ")" : "")
                        + ": " + collectCompatibilityMessage(e) + ". Returning null.");
                    return null;
                } else if (isCustomEnchantmentIssue(e)) {
                    customEnchantmentFailures++;
                    String enchantmentName = extractEnchantmentName(e);
                    Bukkit.getLogger().warning("[PlayerDataSync] Custom enchantment deserialization failed for single item"
                        + (enchantmentName != null ? " (enchantment: " + enchantmentName + ")" : "")
                        + ". The enchantment plugin may not be loaded or the enchantment is not registered.");
                    String errorDetails = extractErrorDetails(e);
                    if (errorDetails != null && !errorDetails.isEmpty()) {
                        Bukkit.getLogger().fine("[PlayerDataSync] Error details: " + errorDetails);
                    }
                    Bukkit.getLogger().info("[PlayerDataSync] Item skipped. Data preserved in database. " +
                        "Ensure the enchantment plugin (e.g., ExcellentEnchants/EcoEnchants) is loaded and the enchantment is registered.");
                    return null;
                } else {
                    otherDeserializationFailures++;
                    String errorType = e.getClass().getSimpleName();
                    Bukkit.getLogger().warning("[PlayerDataSync] Failed to deserialize single item (error type: " 
                        + errorType + "): " + collectCompatibilityMessage(e) + ". Returning null.");
                    return null;
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
     * Sanitize ItemStack array (remove invalid items and validate)
     * IMPORTANT: Uses clone() which preserves all NBT data including custom enchantments
     * from plugins like ExcellentEnchants and EcoEnchants. The clone operation maintains the complete
     * ItemStack state including PersistentDataContainer entries.
     */
    public static ItemStack[] sanitizeItemStackArray(ItemStack[] items) {
        if (items == null) return null;
        
        ItemStack[] sanitized = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            try {
                ItemStack item = items[i];
                if (item != null) {
                    // Validate item type and amount
                    if (item.getType() != null && item.getType() != org.bukkit.Material.AIR) {
                        int amount = item.getAmount();
                        // Ensure amount is within valid range (1-64 for most items, up to 127 for stackable items)
                        if (amount > 0 && amount <= item.getMaxStackSize()) {
                            // clone() preserves all NBT data including custom enchantments
                            sanitized[i] = item.clone();
                        } else {
                            // Fix invalid stack sizes
                            if (amount <= 0) {
                                sanitized[i] = null; // Remove invalid items with 0 or negative amount
                            } else {
                                // Clamp to max stack size
                                // clone() preserves all NBT data including custom enchantments
                                ItemStack fixed = item.clone();
                                fixed.setAmount(Math.min(amount, item.getMaxStackSize()));
                                sanitized[i] = fixed;
                            }
                        }
                    } else {
                        sanitized[i] = null; // Remove AIR items
                    }
                } else {
                    sanitized[i] = null;
                }
            } catch (Exception e) {
                // Log exception but don't expose sensitive data
                Bukkit.getLogger().fine("[PlayerDataSync] Error sanitizing item at index " + i + ": " + e.getClass().getSimpleName());
                // Skip invalid items that cause exceptions
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
     * Tracks statistics for debugging and monitoring
     */
    public static ItemStack[] safeItemStackArrayFromBase64(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[0];
        
        int failuresBefore = customEnchantmentFailures + versionCompatibilityFailures + otherDeserializationFailures;
        
        try {
            ItemStack[] result = itemStackArrayFromBase64(data);
            
            // Log statistics if there were failures during this deserialization
            int failuresAfter = customEnchantmentFailures + versionCompatibilityFailures + otherDeserializationFailures;
            if (failuresAfter > failuresBefore) {
                int newFailures = failuresAfter - failuresBefore;
                Bukkit.getLogger().fine("[PlayerDataSync] Deserialization completed with " + newFailures + 
                    " item(s) skipped due to errors. " + getDeserializationStats());
            }
            
            return result;
        } catch (Exception e) {
            otherDeserializationFailures++;
            String errorType = e.getClass().getSimpleName();
            Bukkit.getLogger().severe("[PlayerDataSync] Critical failure deserializing ItemStack array (error type: " 
                + errorType + "): " + collectCompatibilityMessage(e));
            Bukkit.getLogger().severe("[PlayerDataSync] This may indicate corrupted data or a serious compatibility issue.");
            return new ItemStack[0];
        }
    }
    
    /**
     * Safely deserialize single ItemStack with comprehensive error handling
     * Returns null if deserialization fails
     * Tracks statistics for debugging and monitoring
     */
    public static ItemStack safeItemStackFromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        
        try {
            return itemStackFromBase64(data);
        } catch (Exception e) {
            otherDeserializationFailures++;
            String errorType = e.getClass().getSimpleName();
            Bukkit.getLogger().warning("[PlayerDataSync] Failed to deserialize single ItemStack (error type: " 
                + errorType + "): " + collectCompatibilityMessage(e));
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
    
    /**
     * Check if the error is related to custom enchantments not being recognized
     * This happens when plugins like ExcellentEnchants and EcoEnchants store enchantments that aren't
     * recognized during deserialization
     */
    private static boolean isCustomEnchantmentIssue(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                // Check for common custom enchantment error patterns
                String lowerMessage = message.toLowerCase();
                if (message.contains("Failed to get element") ||
                    message.contains("missed input") ||
                    (message.contains("minecraft:") && (message.contains("venom") || 
                     message.contains("enchantments") || message.contains("enchant"))) ||
                    (lowerMessage.contains("excellentenchants") || lowerMessage.contains("ecoenchants") ||
                     lowerMessage.contains("ecoenchant")) ||
                    (message.contains("Cannot invoke") && message.contains("getClass()")) ||
                    (current instanceof IllegalStateException && message.contains("Failed to get element")) ||
                    lowerMessage.contains("enchantment") && (lowerMessage.contains("not found") || 
                     lowerMessage.contains("unknown") || lowerMessage.contains("invalid"))) {
                    return true;
                }
            }
            // Check exception type
            if (current instanceof IllegalStateException) {
                String className = current.getClass().getName();
                if (className.contains("DataResult") || className.contains("serialization") ||
                    className.contains("Codec") || className.contains("Decoder")) {
                    return true;
                }
            }
            // Check for NullPointerException related to enchantment deserialization
            if (current instanceof NullPointerException) {
                StackTraceElement[] stack = current.getStackTrace();
                for (StackTraceElement element : stack) {
                    String className = element.getClassName();
                    if (className.contains("enchant") || className.contains("ItemStack") ||
                        className.contains("serialization") || className.contains("ConfigurationSerialization")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }
    
    /**
     * Extract enchantment name from error message if available
     * Helps identify which specific enchantment caused the issue
     */
    private static String extractEnchantmentName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                // Look for patterns like "minecraft:venom" or "venom" in error messages
                if (message.contains("minecraft:")) {
                    int start = message.indexOf("minecraft:");
                    if (start >= 0) {
                        int end = message.indexOf(" ", start);
                        if (end < 0) end = message.indexOf("}", start);
                        if (end < 0) end = message.indexOf("\"", start);
                        if (end < 0) end = message.indexOf(",", start);
                        if (end < 0) end = Math.min(start + 50, message.length());
                        if (end > start) {
                            String enchantment = message.substring(start, end).trim();
                            // Clean up common suffixes
                            enchantment = enchantment.replaceAll("[}\",\\]]", "");
                            if (enchantment.length() > 10 && enchantment.length() < 100) {
                                return enchantment;
                            }
                        }
                    }
                }
                // Look for enchantment names in quotes or braces
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[\"']([a-z0-9_:-]+enchant[a-z0-9_:-]*|excellentenchants:[a-z0-9_:-]+|ecoenchants:[a-z0-9_:-]+|ecoenchant:[a-z0-9_:-]+|venom|curse|soul|telepathy)[\"']", 
                    java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            current = current.getCause();
        }
        return null;
    }
    
    /**
     * Extract detailed error information for debugging
     */
    private static String extractErrorDetails(Throwable throwable) {
        StringBuilder details = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 3) {
            if (details.length() > 0) {
                details.append(" -> ");
            }
            details.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && message.length() < 200) {
                details.append(": ").append(message.substring(0, Math.min(message.length(), 100)));
                if (message.length() > 100) {
                    details.append("...");
                }
            }
            current = current.getCause();
            depth++;
        }
        return details.toString();
    }
    
    /**
     * Get statistics about deserialization failures
     * Useful for debugging and monitoring
     */
    public static String getDeserializationStats() {
        int total = customEnchantmentFailures + versionCompatibilityFailures + otherDeserializationFailures;
        if (total == 0) {
            return "No deserialization failures recorded.";
        }
        return String.format("Deserialization failures: %d total (Custom Enchantments: %d, Version Issues: %d, Other: %d)",
            total, customEnchantmentFailures, versionCompatibilityFailures, otherDeserializationFailures);
    }
    
    /**
     * Reset deserialization statistics
     */
    public static void resetDeserializationStats() {
        customEnchantmentFailures = 0;
        versionCompatibilityFailures = 0;
        otherDeserializationFailures = 0;
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
