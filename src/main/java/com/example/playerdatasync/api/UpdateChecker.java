package com.example.playerdatasync.api;

import com.example.playerdatasync.utils.SchedulerUtils;
import com.example.playerdatasync.managers.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            plugin.getLogger().info(messageManager.get("update_check_disabled"));
            return;
        }

        SchedulerUtils.runTaskAsync(plugin, () -> {
            HttpURLConnection connection = null;
            try {
                String apiUrl = API_BASE_URL + "/plugins/" + PLUGIN_SLUG + "/latest";
                connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();

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

                    handleResponse(response.toString());
                }

            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().fine(messageManager.get("update_check_no_internet"));
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning(messageManager.get("update_check_timeout"));
            } catch (Exception e) {
                plugin.getLogger().warning(messageManager.get("update_check_failed", e.getMessage()));
                plugin.getLogger().log(java.util.logging.Level.FINE, "Update check error", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void handleResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            plugin.getLogger().warning(messageManager.get("update_check_failed", "Empty response"));
            return;
        }

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
        String downloadUrl = jsonObject.has("downloadUrl") && !jsonObject.get("downloadUrl").isJsonNull()
                ? jsonObject.get("downloadUrl").getAsString()
                : API_BASE_URL + "/plugins/" + PLUGIN_SLUG;

        String currentVersion = plugin.getDescription().getVersion();

        if (currentVersion.equalsIgnoreCase(latestVersion)) {
            if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                plugin.getLogger().info(messageManager.get("update_current"));
            }
        } else {
            plugin.getLogger().info(messageManager.get("update_available", latestVersion));
            plugin.getLogger().info(messageManager.get("update_download_url", downloadUrl));
        }
    }
}
