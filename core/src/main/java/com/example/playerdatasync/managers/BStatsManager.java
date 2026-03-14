package com.example.playerdatasync.managers;

import com.example.playerdatasync.core.PlayerDataSync;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;

public class BStatsManager {
    private final PlayerDataSync plugin;
    private Metrics metrics;

    public BStatsManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (metrics != null) return;

        // PlayerDataSync bStats ID: 25037
        metrics = new Metrics(plugin, 25037);

        // Database Type
        metrics.addCustomChart(new SimplePie("database_type", plugin::getDatabaseType));

        // NMS Version
        metrics.addCustomChart(new SimplePie("nms_version", plugin::getNmsVersionString));

        // Average Save Time
        metrics.addCustomChart(new SingleLineChart("avg_save_time", () -> (int) plugin.getLastSaveDurationMs()));

        // Sync Feature Adoption
        metrics.addCustomChart(new SimplePie("sync_coordinates", () -> plugin.isSyncCoordinates() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_inventory", () -> plugin.isSyncInventory() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_xp", () -> plugin.isSyncXp() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_enderchest", () -> plugin.isSyncEnderchest() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_health", () -> plugin.isSyncHealth() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_economy", () -> plugin.isSyncEconomy() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_attributes", () -> plugin.isSyncAttributes() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("sync_advancements", () -> plugin.isSyncAchievements() ? "Enabled" : "Disabled"));
        
        // BungeeCord Integration
        metrics.addCustomChart(new SimplePie("bungeecord_integration", () -> plugin.isBungeecordIntegrationEnabled() ? "Enabled" : "Disabled"));
        
        plugin.getLogger().info("bStats metrics initialized.");
    }
}
