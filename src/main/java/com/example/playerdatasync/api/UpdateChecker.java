package com.example.playerdatasync.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import com.example.playerdatasync.managers.MessageManager;

public class UpdateChecker {
    private final JavaPlugin plugin;
    private final int resourceId;
    private final MessageManager messageManager;

    public UpdateChecker(JavaPlugin plugin, int resourceId, MessageManager messageManager) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.messageManager = messageManager;
    }

    public void check() {
        // Only check for updates if enabled in config
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            plugin.getLogger().info(messageManager.get("update_check_disabled"));
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
                    plugin.getLogger().warning(messageManager.get("update_check_failed", "HTTP " + responseCode));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String latestVersion = reader.readLine();
                    if (latestVersion == null || latestVersion.isEmpty()) {
                        plugin.getLogger().warning(messageManager.get("update_check_failed", "Empty response"));
                        return;
                    }
                    
                    String currentVersion = plugin.getDescription().getVersion();
                    if (currentVersion.equalsIgnoreCase(latestVersion)) {
                        if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                            plugin.getLogger().info(messageManager.get("update_current"));
                        }
                    } else {
                        plugin.getLogger().info(messageManager.get("update_available", latestVersion));
                        plugin.getLogger().info(messageManager.get("update_download_url", "https://www.spigotmc.org/resources/" + resourceId));
                    }
                }
            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().fine(messageManager.get("update_check_no_internet"));
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning(messageManager.get("update_check_timeout"));
            } catch (Exception e) {
                plugin.getLogger().warning(messageManager.get("update_check_failed", e.getMessage()));
            }
        });
    }
}
