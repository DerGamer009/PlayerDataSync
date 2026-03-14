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
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Update checker for PlayerDataSync using CraftingStudio Pro API
 * API Documentation: https://www.craftingstudiopro.de/docs/api
 */
public class UpdateChecker {

    private static final String API_BASE_URL = "https://api.craftingstudiopro.de/v1"; 
    private static final String PLUGIN_SLUG = "playerdatasync";
    private static final String API_KEY = "csp_264cc3dad0ece1292cff8429e3ba55b725b60bdd5209b5b4d599153610336b12"; // Hardcoded for streamlined authentication
    private static final Pattern VERSION_TOKEN_PATTERN = Pattern.compile("(\\d+)");
    
    private static long lastCheckTimestamp = 0;
    private static String cachedLatestVersion = null;
    private static final long CACHE_DURATION = 3600000; // 1 hour in milliseconds

    private final JavaPlugin plugin;
    private final MessageManager messageManager;

    public UpdateChecker(JavaPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    public void check() {
        check(null, false);
    }

    public void check(org.bukkit.command.CommandSender sender, boolean force) {
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true) && !force) {
            if (sender != null) sender.sendMessage(messageManager.get("update_check_disabled"));
            else plugin.getLogger().info(messageManager.get("update_check_disabled"));
            return;
        }

        // Check cache unless forced
        long now = System.currentTimeMillis();
        if (!force && cachedLatestVersion != null && (now - lastCheckTimestamp) < CACHE_DURATION) {
            if (sender == null) plugin.getLogger().fine("Using cached update information.");
            handleResponse(null, cachedLatestVersion, sender); // Use cached version
            return;
        }

        if (sender != null) sender.sendMessage(messageManager.get("prefix") + " §7Checking for updates...");

        SchedulerUtils.runTaskAsync(plugin, () -> {
            HttpURLConnection connection = null;
            try {
                String apiUrl = API_BASE_URL + "/plugins/" + PLUGIN_SLUG + "/latest";
                connection = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();

                int timeout = Math.max(1000, plugin.getConfig().getInt("update_checker.timeout", 10000));
                connection.setConnectTimeout(timeout);
                connection.setReadTimeout(timeout);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "PlayerDataSync/" + plugin.getDescription().getVersion());
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("X-API-Key", API_KEY); // Simplified authentication

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

                    handleResponse(response.toString(), null, sender);
                    lastCheckTimestamp = System.currentTimeMillis();
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

    private void handleResponse(String jsonResponse, String manualVersion, org.bukkit.command.CommandSender sender) {
        String latestVersion;
        String downloadUrl = API_BASE_URL + "/plugins/" + PLUGIN_SLUG;

        if (manualVersion != null) {
            latestVersion = manualVersion;
        } else {
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                String error = "Empty response";
                if (sender != null) sender.sendMessage(messageManager.get("prefix") + " §cUpdate check failed: " + error);
                else plugin.getLogger().warning(messageManager.get("update_check_failed", error));
                return;
            }

            JsonObject jsonObject;
            try {
                jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            } catch (Exception e) {
                String error = "Invalid JSON response: " + e.getMessage();
                if (sender != null) sender.sendMessage(messageManager.get("prefix") + " §cUpdate check failed: " + error);
                else plugin.getLogger().warning(messageManager.get("update_check_failed", error));
                return;
            }

            if (!jsonObject.has("version")) {
                String error = "Invalid response format: missing version field";
                if (sender != null) sender.sendMessage(messageManager.get("prefix") + " §cUpdate check failed: " + error);
                else plugin.getLogger().warning(messageManager.get("update_check_failed", error));
                return;
            }

            latestVersion = jsonObject.get("version").getAsString();
            cachedLatestVersion = latestVersion; // Update cache
            
            if (jsonObject.has("downloadUrl") && !jsonObject.get("downloadUrl").isJsonNull()) {
                downloadUrl = jsonObject.get("downloadUrl").getAsString();
            }
        }

        String currentVersion = plugin.getDescription().getVersion();
        int comparison = compareVersions(currentVersion, latestVersion);

        if (comparison >= 0) {
            if (sender != null) {
                sender.sendMessage(messageManager.get("prefix") + " §a" + messageManager.get("update_current"));
            } else if (plugin.getConfig().getBoolean("update_checker.notify_ops", true)) {
                plugin.getLogger().info(messageManager.get("update_current"));
            }
        } else {
            if (sender != null) {
                sender.sendMessage(messageManager.get("prefix") + " §e" + messageManager.get("update_available", latestVersion));
                sender.sendMessage(messageManager.get("prefix") + " §e" + messageManager.get("update_download_url", downloadUrl));
            } else {
                plugin.getLogger().info(messageManager.get("update_available", latestVersion));
                plugin.getLogger().info(messageManager.get("update_download_url", downloadUrl));
            }
        }
    }

    /**
     * Compare two versions while gracefully handling suffixes like -RELEASE, -BETA, etc.
     * @return < 0 if current < latest, 0 if equal, > 0 if current > latest
     */
    private int compareVersions(String currentVersion, String latestVersion) {
        int[] currentParts = toVersionParts(currentVersion);
        int[] latestParts = toVersionParts(latestVersion);

        int max = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < max; i++) {
            int currentPart = i < currentParts.length ? currentParts[i] : 0;
            int latestPart = i < latestParts.length ? latestParts[i] : 0;

            if (currentPart != latestPart) {
                return currentPart - latestPart;
            }
        }

        return 0;
    }

    private int[] toVersionParts(String version) {
        if (version == null || version.trim().isEmpty()) {
            return new int[] {0};
        }

        Matcher matcher = VERSION_TOKEN_PATTERN.matcher(version);
        java.util.List<Integer> parts = new java.util.ArrayList<Integer>();
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed numeric chunks and continue
            }
        }

        if (parts.isEmpty()) {
            return new int[] {0};
        }

        int[] result = new int[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            result[i] = parts.get(i);
        }
        return result;
    }
}
