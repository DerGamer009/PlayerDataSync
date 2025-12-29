package com.example.playerdatasync.utils;

import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.UUID;

import com.example.playerdatasync.core.PlayerDataSync;

/**
 * Advanced caching system for PlayerDataSync
 * Provides in-memory caching with TTL, LRU eviction, and performance metrics
 */
public class PlayerDataCache {
    private final PlayerDataSync plugin;
    private final ConcurrentHashMap<UUID, CachedPlayerData> cache;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    
    private final int maxCacheSize;
    private final long defaultTTL;
    private final boolean enableCompression;
    
    public PlayerDataCache(PlayerDataSync plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
        this.maxCacheSize = plugin.getConfig().getInt("performance.cache_size", 100);
        this.defaultTTL = plugin.getConfig().getLong("performance.cache_ttl", 300000); // 5 minutes
        this.enableCompression = plugin.getConfig().getBoolean("performance.cache_compression", true);
    }
    
    /**
     * Cache player data with TTL
     */
    public void cachePlayerData(Player player, CachedPlayerData data) {
        if (cache.size() >= maxCacheSize) {
            evictLeastRecentlyUsed();
        }
        
        data.setLastAccessed(System.currentTimeMillis());
        data.setTtl(defaultTTL);
        
        if (enableCompression) {
            data.compress();
        }
        
        cache.put(player.getUniqueId(), data);
    }
    
    /**
     * Get cached player data
     */
    public CachedPlayerData getCachedPlayerData(Player player) {
        CachedPlayerData data = cache.get(player.getUniqueId());
        
        if (data == null) {
            misses.incrementAndGet();
            return null;
        }
        
        // Check if data has expired
        if (data.isExpired()) {
            cache.remove(player.getUniqueId());
            misses.incrementAndGet();
            return null;
        }
        
        data.setLastAccessed(System.currentTimeMillis());
        hits.incrementAndGet();
        
        if (enableCompression && data.isCompressed()) {
            data.decompress();
        }
        
        return data;
    }
    
    /**
     * Remove player data from cache
     */
    public void removePlayerData(Player player) {
        cache.remove(player.getUniqueId());
    }
    
    /**
     * Clear all cached data
     */
    public void clearCache() {
        cache.clear();
        hits.set(0);
        misses.set(0);
        evictions.set(0);
    }
    
    /**
     * Evict least recently used entries
     */
    private void evictLeastRecentlyUsed() {
        if (cache.isEmpty()) return;
        
        UUID oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<UUID, CachedPlayerData> entry : cache.entrySet()) {
            if (entry.getValue().getLastAccessed() < oldestTime) {
                oldestTime = entry.getValue().getLastAccessed();
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
            evictions.incrementAndGet();
        }
    }
    
    /**
     * Clean expired entries
     */
    public void cleanExpiredEntries() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        long totalRequests = hits.get() + misses.get();
        double hitRate = totalRequests > 0 ? (double) hits.get() / totalRequests * 100 : 0;
        
        return new CacheStats(
            cache.size(),
            maxCacheSize,
            hits.get(),
            misses.get(),
            evictions.get(),
            hitRate
        );
    }
    
    /**
     * Cached player data container
     */
    public static class CachedPlayerData {
        private String inventoryData;
        private String enderChestData;
        private String armorData;
        private String offhandData;
        private String effectsData;
        private String statisticsData;
        private String attributesData;
        private String advancementsData;
        private long lastAccessed;
        private long ttl;
        private boolean compressed = false;
        
        public CachedPlayerData() {
            this.lastAccessed = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getInventoryData() { return inventoryData; }
        public void setInventoryData(String inventoryData) { this.inventoryData = inventoryData; }
        
        public String getEnderChestData() { return enderChestData; }
        public void setEnderChestData(String enderChestData) { this.enderChestData = enderChestData; }
        
        public String getArmorData() { return armorData; }
        public void setArmorData(String armorData) { this.armorData = armorData; }
        
        public String getOffhandData() { return offhandData; }
        public void setOffhandData(String offhandData) { this.offhandData = offhandData; }
        
        public String getEffectsData() { return effectsData; }
        public void setEffectsData(String effectsData) { this.effectsData = effectsData; }
        
        public String getStatisticsData() { return statisticsData; }
        public void setStatisticsData(String statisticsData) { this.statisticsData = statisticsData; }
        
        public String getAttributesData() { return attributesData; }
        public void setAttributesData(String attributesData) { this.attributesData = attributesData; }
        
        public String getAdvancementsData() { return advancementsData; }
        public void setAdvancementsData(String advancementsData) { this.advancementsData = advancementsData; }
        
        public long getLastAccessed() { return lastAccessed; }
        public void setLastAccessed(long lastAccessed) { this.lastAccessed = lastAccessed; }
        
        public long getTtl() { return ttl; }
        public void setTtl(long ttl) { this.ttl = ttl; }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - lastAccessed > ttl;
        }
        
        public boolean isCompressed() { return compressed; }
        public void setCompressed(boolean compressed) { this.compressed = compressed; }
        
        /**
         * Compress data (placeholder for future compression implementation)
         */
        public void compress() {
            // TODO: Implement actual compression
            this.compressed = true;
        }
        
        /**
         * Decompress data (placeholder for future compression implementation)
         */
        public void decompress() {
            // TODO: Implement actual decompression
            this.compressed = false;
        }
    }
    
    /**
     * Cache statistics container
     */
    public static class CacheStats {
        private final int currentSize;
        private final int maxSize;
        private final long hits;
        private final long misses;
        private final long evictions;
        private final double hitRate;
        
        public CacheStats(int currentSize, int maxSize, long hits, long misses, long evictions, double hitRate) {
            this.currentSize = currentSize;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.evictions = evictions;
            this.hitRate = hitRate;
        }
        
        @Override
        public String toString() {
            return String.format("Cache: %d/%d entries, Hit Rate: %.1f%%, Hits: %d, Misses: %d, Evictions: %d",
                currentSize, maxSize, hitRate, hits, misses, evictions);
        }
        
        // Getters
        public int getCurrentSize() { return currentSize; }
        public int getMaxSize() { return maxSize; }
        public long getHits() { return hits; }
        public long getMisses() { return misses; }
        public long getEvictions() { return evictions; }
        public double getHitRate() { return hitRate; }
    }
}
