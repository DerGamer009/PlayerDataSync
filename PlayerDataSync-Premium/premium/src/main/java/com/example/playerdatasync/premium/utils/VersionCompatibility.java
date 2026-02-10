package com.example.playerdatasync.premium.utils;

import org.bukkit.Bukkit;

/**
 * Utility class to check Minecraft version compatibility and feature availability
 */
public class VersionCompatibility {
    private static String serverVersion;
    private static int majorVersion;
    private static int minorVersion;
    private static int patchVersion;
    
    static {
        try {
            serverVersion = Bukkit.getServer().getBukkitVersion();
            parseVersion(serverVersion);
        } catch (Exception e) {
            serverVersion = "unknown";
            majorVersion = 0;
            minorVersion = 0;
            patchVersion = 0;
        }
    }
    
    private static void parseVersion(String version) {
        try {
            // Format: "1.8.8-R0.1-SNAPSHOT" or "1.21.1-R0.1-SNAPSHOT"
            String[] parts = version.split("-")[0].split("\\.");
            majorVersion = Integer.parseInt(parts[0]);
            minorVersion = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            patchVersion = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        } catch (Exception e) {
            majorVersion = 0;
            minorVersion = 0;
            patchVersion = 0;
        }
    }
    
    /**
     * Check if the server version is at least the specified version
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        if (majorVersion > major) return true;
        if (majorVersion < major) return false;
        if (minorVersion > minor) return true;
        if (minorVersion < minor) return false;
        return patchVersion >= patch;
    }
    
    /**
     * Check if the server version is between two versions (inclusive)
     */
    public static boolean isBetween(int majorMin, int minorMin, int patchMin, 
                                   int majorMax, int minorMax, int patchMax) {
        return isAtLeast(majorMin, minorMin, patchMin) && 
               !isAtLeast(majorMax, minorMax, patchMax + 1);
    }
    
    /**
     * Get the server version string
     */
    public static String getServerVersion() {
        return serverVersion;
    }
    
    /**
     * Check if offhand is supported (1.9+)
     */
    public static boolean isOffhandSupported() {
        return isAtLeast(1, 9, 0);
    }
    
    /**
     * Check if attributes are supported (1.9+)
     */
    public static boolean isAttributesSupported() {
        return isAtLeast(1, 9, 0);
    }
    
    /**
     * Check if advancements are supported (1.12+)
     */
    public static boolean isAdvancementsSupported() {
        return isAtLeast(1, 12, 0);
    }
    
    /**
     * Check if NamespacedKey is supported (1.13+)
     */
    public static boolean isNamespacedKeySupported() {
        return isAtLeast(1, 13, 0);
    }
    
    /**
     * Check if the version is 1.8
     */
    public static boolean isVersion1_8() {
        return majorVersion == 1 && minorVersion == 8;
    }
    
    /**
     * Check if the version is 1.9-1.11
     */
    public static boolean isVersion1_9_to_1_11() {
        return majorVersion == 1 && minorVersion >= 9 && minorVersion <= 11;
    }
    
    /**
     * Check if the version is 1.12
     */
    public static boolean isVersion1_12() {
        return majorVersion == 1 && minorVersion == 12;
    }
    
    /**
     * Check if the version is 1.13-1.16
     */
    public static boolean isVersion1_13_to_1_16() {
        return majorVersion == 1 && minorVersion >= 13 && minorVersion <= 16;
    }
    
    /**
     * Check if the version is 1.17
     */
    public static boolean isVersion1_17() {
        return majorVersion == 1 && minorVersion == 17;
    }
    
    /**
     * Check if the version is 1.18-1.20
     */
    public static boolean isVersion1_18_to_1_20() {
        return majorVersion == 1 && minorVersion >= 18 && minorVersion <= 20;
    }
    
    /**
     * Check if the version is 1.21+
     */
    public static boolean isVersion1_21_Plus() {
        return majorVersion == 1 && minorVersion >= 21;
    }
    
    /**
     * Get a human-readable version string
     */
    public static String getVersionString() {
        return majorVersion + "." + minorVersion + "." + patchVersion;
    }
}

