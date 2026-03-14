package com.example.playerdatasync.managers;

import com.example.playerdatasync.core.PlayerDataSync;

public class FastStatsManager {
    private final PlayerDataSync plugin;
    private Object faststatsMetrics;
    private static Object ERROR_TRACKER;

    public FastStatsManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (faststatsMetrics != null) {
            return;
        }

        // FastStats requires Java 17+ (Class version 61)
        if (!isJava17OrNewer()) {
            plugin.getLogger().info("FastStats metrics disabled (requires Java 17+)");
            return;
        }

        try {
            // Use a separate helper to avoid loading FastStats classes into this class's
            // constant pool
            FastStatsHelper.initialize(this, plugin);
            plugin.getLogger().info("FastStats metrics initialized.");
        } catch (Throwable t) {
            // Catch Throwable to handle LinkageError/UnsupportedClassVersionError
            plugin.getLogger().warning("Could not initialize FastStats metrics: " + t.getMessage());
        }
    }

    protected void setMetrics(Object metrics) {
        this.faststatsMetrics = metrics;
    }

    protected static void setErrorTracker(Object tracker) {
        ERROR_TRACKER = tracker;
    }

    protected static Object getErrorTracker() {
        return ERROR_TRACKER;
    }

    private boolean isJava17OrNewer() {
        try {
            String version = System.getProperty("java.version");
            if (version.startsWith("1.")) {
                return false; // Java 8 or older
            }
            String majorVersionStr = version.split("\\.")[0].split("-")[0];
            int majorVersion = Integer.parseInt(majorVersionStr);
            return majorVersion >= 17;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        if (faststatsMetrics != null) {
            try {
                if (isJava17OrNewer()) {
                    FastStatsHelper.shutdown(faststatsMetrics);
                }
            } catch (Throwable ignored) {
            }
            faststatsMetrics = null;
        }
    }

    // Inner class or separate class to isolate FastStats dependencies
    private static class FastStatsHelper {
        private static void initialize(FastStatsManager manager, PlayerDataSync plugin) {
            if (manager.getErrorTracker() == null) {
                manager.setErrorTracker(dev.faststats.core.ErrorTracker.contextAware());
            }

            dev.faststats.bukkit.BukkitMetrics metrics = dev.faststats.bukkit.BukkitMetrics.factory()
                    .token("cfc414c1c6ad3d95ed350dae82d82ced")
                    .errorTracker((dev.faststats.core.ErrorTracker) manager.getErrorTracker())
                    .addMetric(dev.faststats.core.data.Metric.string("database_type",
                            plugin::getDatabaseType)) // Database Type
                    .addMetric(dev.faststats.core.data.Metric.string("nms_version",
                            plugin::getNmsVersionString)) // Active NMS Handler
                    .addMetric(dev.faststats.core.data.Metric.number("avg_save_time",
                            plugin::getLastSaveDurationMs)) // Average Save Time
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_coordinates", plugin::isSyncCoordinates))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_inventory", plugin::isSyncInventory))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_xp", plugin::isSyncXp))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_enderchest", plugin::isSyncEnderchest))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_health", plugin::isSyncHealth))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_economy", plugin::isSyncEconomy))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_attributes", plugin::isSyncAttributes))
                    .addMetric(dev.faststats.core.data.Metric.bool("sync_advancements", plugin::isSyncAchievements))
                    .addMetric(dev.faststats.core.data.Metric.bool("bungeecord_integration",
                            plugin::isBungeecordIntegrationEnabled))
                    .create(plugin);

            metrics.ready();
            manager.setMetrics(metrics);
        }

        private static void shutdown(Object metrics) {
            if (metrics instanceof dev.faststats.bukkit.BukkitMetrics) {
                ((dev.faststats.bukkit.BukkitMetrics) metrics).shutdown();
            }
        }
    }
}
