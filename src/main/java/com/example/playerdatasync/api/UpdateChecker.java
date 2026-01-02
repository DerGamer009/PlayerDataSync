package com.example.playerdatasync.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.example.playerdatasync.managers.MessageManager;

/**
 * Update checker for PlayerDataSync using CraftingStudio Pro API
 * API Documentation: https://www.craftingstudiopro.de/docs/api
 */
public class UpdateChecker {
    private static final String API_BASE_URL = "https://craftingstudiopro.de/api";
    private static final String PLUGIN_SLUG = "playerdatasync";
    
    private final JavaPlugin plugin;
    private final MessageManager messageManager;

    public UpdateChecker(JavaPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
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
                // Use CraftingStudio Pro API endpoint
                String apiUrl = API_BASE_URL + "/plugins/" + PLUGIN_SLUG + "/latest";
                HttpURLConnection connection;
                try {
                    URI uri = new URI(apiUrl);
                    connection = (HttpURLConnection) uri.toURL().openConnection();
                } catch (URISyntaxException e) {
                    // Fallback to deprecated constructor if URI parsing fails
                    @SuppressWarnings("deprecation")
                    URL fallbackUrl = new URL(apiUrl);
                    connection = (HttpURLConnection) fallbackUrl.openConnection();
                }
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "PlayerDataSync/" + plugin.getDescription().getVersion());
                connection.setRequestProperty("Accept", "application/json");

                int responseCode = connection.getResponseCode();
                if (responseCode == 429) {
                    plugin.getLogger().warning(messageManager.get("update_check_failed", "Rate limit exceeded. Please try again later."));
                    return;
                }
                
                if (responseCode != 200) {
                    plugin.getLogger().warning(messageManager.get("update_check_failed", "HTTP " + responseCode));
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String jsonResponse = response.toString();
                    if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                        plugin.getLogger().warning(messageManager.get("update_check_failed", "Empty response"));
                        return;
                    }
                    
                    // Parse JSON response using Gson
                    // Response format: { version: string, downloadUrl: string, createdAt: string | null, 
                    //                   title: string, releaseType: "release", slug: string | null, 
                    //                   pluginTitle: string, pluginSlug: string }
                    JsonObject jsonObject;
                    try {
                        jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                    } catch (Exception e) {
                        plugin.getLogger().warning(messageManager.get("update_check_failed", "Invalid JSON response: " + e.getMessage()));
                        return;
                    }
                    
                    if (!jsonObject.has("version")) {
                        plugin.getLogger().warning(messageManager.get("update_check_failed", "Invalid response format: missing version field"));
                        return;
                    }
                    
                    String latestVersion = jsonObject.get("version").getAsString();
                    String downloadUrl = null;
                    if (jsonObject.has("downloadUrl") && !jsonObject.get("downloadUrl").isJsonNull()) {
                        downloadUrl = jsonObject.get("downloadUrl").getAsString();
                    }
                    
                    String currentVersion = plugin.getDescription().getVersion();
                    if (currentVersion.equalsIgnoreCase(latestVersion)) {
                        if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                            plugin.getLogger().info(messageManager.get("update_current"));
                        }
                    } else {
                        plugin.getLogger().info(messageManager.get("update_available", latestVersion));
                        if (downloadUrl != null && !downloadUrl.isEmpty()) {
                            plugin.getLogger().info(messageManager.get("update_download_url", downloadUrl));
                        } else {
                            plugin.getLogger().info(messageManager.get("update_download_url", 
                                "https://craftingstudiopro.de/plugins/" + PLUGIN_SLUG));
                        }
                    }
                }
            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().fine(messageManager.get("update_check_no_internet"));
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning(messageManager.get("update_check_timeout"));
            } catch (Exception e) {
                plugin.getLogger().warning(messageManager.get("update_check_failed", e.getMessage()));
                plugin.getLogger().log(java.util.logging.Level.FINE, "Update check error", e);
            }
        });
    }
}
