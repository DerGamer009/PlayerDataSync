package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final int resourceId;

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String latestVersion = reader.readLine();
                    if (latestVersion == null || latestVersion.isEmpty()) {
                        plugin.getLogger().warning("Could not check for updates: empty response");
                        return;
                    }
                    String currentVersion = plugin.getDescription().getVersion();
                    if (currentVersion.equalsIgnoreCase(latestVersion)) {
                        plugin.getLogger().info("You are running the latest version (" + currentVersion + ")");
                    } else {
                        plugin.getLogger().info("A new version is available: " + latestVersion + " (current: " + currentVersion + ")");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }
}
