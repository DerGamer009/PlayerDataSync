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
        // Only check for updates if enabled in config
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            return;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openConnection();
                connection.setConnectTimeout(10000); // Increased timeout
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", "PlayerDataSync/" + plugin.getDescription().getVersion());

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("Update check failed with HTTP " + responseCode);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String latestVersion = reader.readLine();
                    if (latestVersion == null || latestVersion.isEmpty()) {
                        plugin.getLogger().warning("Could not check for updates: empty response");
                        return;
                    }
                    
                    String currentVersion = plugin.getDescription().getVersion();
                    if (currentVersion.equalsIgnoreCase(latestVersion)) {
                        if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                            plugin.getLogger().info("You are running the latest version (" + currentVersion + ")");
                        }
                    } else {
                        plugin.getLogger().info("A new version is available: " + latestVersion + " (current: " + currentVersion + ")");
                        plugin.getLogger().info("Download at: https://www.spigotmc.org/resources/" + resourceId);
                    }
                }
            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().fine("Could not check for updates: No internet connection");
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning("Update check timed out");
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
            }
        });
    }
}
