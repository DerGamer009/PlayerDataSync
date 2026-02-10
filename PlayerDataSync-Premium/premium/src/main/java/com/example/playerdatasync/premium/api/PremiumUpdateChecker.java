package com.example.playerdatasync.premium.api;

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

/**
 * Update checker for PlayerDataSync Premium using CraftingStudio Pro API
 * API Documentation: https://www.craftingstudiopro.de/docs/api
 * 
 * Checks for updates using the Premium plugin slug
 */
public class PremiumUpdateChecker {
    private static final String API_BASE_URL = "https://craftingstudiopro.de/api";
    private static final String PLUGIN_SLUG = "playerdatasync-premium";
    
    private final JavaPlugin plugin;
    
    public PremiumUpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check for updates asynchronously
     */
    public void check() {
        // Only check for updates if enabled in config
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            plugin.getLogger().info("[PremiumUpdateChecker] Update checking is disabled in config");
            return;
        }
        
        SchedulerUtils.runTaskAsync(plugin, () -> {
            try {
                // Use CraftingStudio Pro API endpoint
                String apiUrl = API_BASE_URL + "/plugins/" + PLUGIN_SLUG + "/latest";
                HttpURLConnection connection;
                try {
                    URI uri = new URI(apiUrl);
                    connection = (HttpURLConnection) uri.toURL().openConnection();
                } catch (URISyntaxException e) {
                    @SuppressWarnings("deprecation")
                    URL fallbackUrl = new URL(apiUrl);
                    connection = (HttpURLConnection) fallbackUrl.openConnection();
                }
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "PlayerDataSync-Premium/" + plugin.getDescription().getVersion());
                connection.setRequestProperty("Accept", "application/json");
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 429) {
                    plugin.getLogger().warning("[PremiumUpdateChecker] Rate limit exceeded. Please try again later.");
                    return;
                }
                
                if (responseCode != 200) {
                    plugin.getLogger().warning("[PremiumUpdateChecker] Update check failed: HTTP " + responseCode);
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
                        plugin.getLogger().warning("[PremiumUpdateChecker] Empty response from API");
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
                        plugin.getLogger().warning("[PremiumUpdateChecker] Invalid JSON response: " + e.getMessage());
                        return;
                    }
                    
                    if (!jsonObject.has("version")) {
                        plugin.getLogger().warning("[PremiumUpdateChecker] Invalid response format: missing version field");
                        return;
                    }
                    
                    final String latestVersion = jsonObject.get("version").getAsString();
                    final String downloadUrl;
                    if (jsonObject.has("downloadUrl") && !jsonObject.get("downloadUrl").isJsonNull()) {
                        downloadUrl = jsonObject.get("downloadUrl").getAsString();
                    } else {
                        downloadUrl = null;
                    }
                    
                    String pluginTitle = "PlayerDataSync Premium";
                    if (jsonObject.has("pluginTitle") && !jsonObject.get("pluginTitle").isJsonNull()) {
                        pluginTitle = jsonObject.get("pluginTitle").getAsString();
                    }
                    
                    String currentVersion = plugin.getDescription().getVersion();
                    if (currentVersion.equalsIgnoreCase(latestVersion)) {
                        if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                            plugin.getLogger().info("[PremiumUpdateChecker] " + pluginTitle + " is up to date (v" + currentVersion + ")");
                        }
                    } else {
                        plugin.getLogger().info("================================================");
                        plugin.getLogger().info("[PremiumUpdateChecker] Update available for " + pluginTitle + "!");
                        plugin.getLogger().info("[PremiumUpdateChecker] Current version: " + currentVersion);
                        plugin.getLogger().info("[PremiumUpdateChecker] Latest version: " + latestVersion);
                        if (downloadUrl != null && !downloadUrl.isEmpty()) {
                            plugin.getLogger().info("[PremiumUpdateChecker] Download: " + downloadUrl);
                        } else {
                            plugin.getLogger().info("[PremiumUpdateChecker] Download: https://craftingstudiopro.de/plugins/" + PLUGIN_SLUG);
                        }
                        plugin.getLogger().info("================================================");
                        
                        // Notify OPs if enabled
                        if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                            SchedulerUtils.runTask(plugin, () -> {
                                Bukkit.getOnlinePlayers().stream()
                                    .filter(p -> p.isOp() || p.hasPermission("playerdatasync.premium.admin"))
                                    .forEach(p -> {
                                        p.sendMessage("§8[§6PlayerDataSync Premium§8] §eUpdate available: v" + latestVersion);
                                        if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                            p.sendMessage("§8[§6PlayerDataSync Premium§8] §7Download: §f" + downloadUrl);
                                        }
                                    });
                            });
                        }
                    }
                }
            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().fine("[PremiumUpdateChecker] No internet connection available for update check.");
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning("[PremiumUpdateChecker] Update check timeout.");
            } catch (Exception e) {
                plugin.getLogger().warning("[PremiumUpdateChecker] Update check failed: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.FINE, "Update check error", e);
            }
        });
    }
}
