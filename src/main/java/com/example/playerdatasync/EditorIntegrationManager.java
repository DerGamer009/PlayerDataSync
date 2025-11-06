package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles communication with the remote web editor API and periodically
 * reports the server heartbeat state.
 */
public class EditorIntegrationManager {
    private static final String DEFAULT_BASE_URL = "https://pds.devvoxel.de/api";
    private static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60;
    private static final String API_KEY_ENV = "PDS_EDITOR_API_KEY";
    private static final String API_KEY_PROPERTY = "pds.editor.apiKey";
    private static final String SERVER_ID_ENV = "PDS_EDITOR_SERVER_ID";
    private static final String SERVER_ID_PROPERTY = "pds.editor.serverId";
    private static final String HEARTBEAT_INTERVAL_ENV = "PDS_EDITOR_HEARTBEAT_INTERVAL";
    private static final String HEARTBEAT_INTERVAL_PROPERTY = "pds.editor.heartbeatInterval";

    private final PlayerDataSync plugin;
    private final String baseUrl;
    private final String apiKey;
    private final String serverId;
    private final int heartbeatIntervalSeconds;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private BukkitTask heartbeatTask;

    public EditorIntegrationManager(PlayerDataSync plugin) {
        this.plugin = plugin;

        this.baseUrl = resolveBaseUrl();
        this.apiKey = resolveApiKey();
        this.serverId = resolveServerId();
        this.heartbeatIntervalSeconds = resolveHeartbeatInterval();
    }

    public boolean hasConfiguredApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public void start() {
        if (!hasConfiguredApiKey()) {
            plugin.getLogger().warning("Editor integration is enabled but no API key is configured. Set the PDS_EDITOR_API_KEY environment variable or the pds.editor.apiKey system property.");
            return;
        }

        if (!started.compareAndSet(false, true)) {
            return;
        }

        // Send initial heartbeat immediately
        sendHeartbeatAsync(true);

        if (heartbeatIntervalSeconds <= 0) {
            return;
        }

        long intervalTicks = Math.max(20L, heartbeatIntervalSeconds * 20L);
        heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> sendHeartbeatAsync(true),
            intervalTicks, intervalTicks);
    }

    public void shutdown() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }

        if (!hasConfiguredApiKey()) {
            return;
        }

        sendHeartbeatAsync(false);
    }

    public CompletableFuture<EditorTokenResult> requestEditorToken(UUID playerUuid, String playerName) {
        CompletableFuture<EditorTokenResult> future = new CompletableFuture<>();

        if (!hasConfiguredApiKey()) {
            future.completeExceptionally(new IllegalStateException("Missing editor API key"));
            return future;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String payload = buildTokenPayload(playerUuid, playerName);
                String response = postJson("/editor/token", payload);
                future.complete(parseTokenResponse(response));
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    private String resolveBaseUrl() {
        String envOverride = System.getenv("PDS_EDITOR_BASE_URL");
        if (envOverride != null && !envOverride.trim().isEmpty()) {
            return trimTrailingSlash(envOverride);
        }

        String propertyOverride = System.getProperty("pds.editor.baseUrl");
        if (propertyOverride != null && !propertyOverride.trim().isEmpty()) {
            return trimTrailingSlash(propertyOverride);
        }

        return DEFAULT_BASE_URL;
    }

    private String resolveApiKey() {
        String value = System.getenv(API_KEY_ENV);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(API_KEY_PROPERTY);
        }

        if ((value == null || value.trim().isEmpty()) && plugin.getConfig().contains("editor.api_key")) {
            value = plugin.getConfig().getString("editor.api_key", "");
        }

        return value != null ? value.trim() : "";
    }

    private String resolveServerId() {
        String override = System.getenv(SERVER_ID_ENV);
        if (override == null || override.trim().isEmpty()) {
            override = System.getProperty(SERVER_ID_PROPERTY);
        }

        if (override != null && !override.trim().isEmpty()) {
            return override.trim();
        }

        ConfigManager configManager = plugin.getConfigManager();
        if (configManager != null) {
            return configManager.getServerId();
        }

        String fallback = plugin.getConfig().getString("server.id", "default");
        if (fallback == null || fallback.trim().isEmpty()) {
            return "default";
        }
        return fallback.trim();
    }

    private int resolveHeartbeatInterval() {
        String value = System.getenv(HEARTBEAT_INTERVAL_ENV);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(HEARTBEAT_INTERVAL_PROPERTY);
        }

        if ((value == null || value.trim().isEmpty()) && plugin.getConfig().contains("editor.heartbeat_interval")) {
            value = String.valueOf(plugin.getConfig().getInt("editor.heartbeat_interval", DEFAULT_HEARTBEAT_INTERVAL_SECONDS));
        }

        if (value != null) {
            try {
                int parsed = Integer.parseInt(value.trim());
                return Math.max(0, parsed);
            } catch (NumberFormatException ignored) {
            }
        }

        return DEFAULT_HEARTBEAT_INTERVAL_SECONDS;
    }

    public CompletableFuture<Boolean> pushSnapshot() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!hasConfiguredApiKey()) {
            future.completeExceptionally(new IllegalStateException("Missing editor API key"));
            return future;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            String payload = buildSnapshotPayload();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    postJson("/editor/snapshot", payload);
                    future.complete(true);
                } catch (Exception ex) {
                    future.completeExceptionally(ex);
                }
            });
        });

        return future;
    }

    public CompletableFuture<Boolean> sendHeartbeatAsync(boolean online) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (!hasConfiguredApiKey()) {
            future.completeExceptionally(new IllegalStateException("Missing editor API key"));
            return future;
        }

        if (!plugin.isEnabled()) {
            try {
                String payload = buildHeartbeatPayload(online);
                postJson("/servers/heartbeat", payload);
                future.complete(true);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to send heartbeat: " + ex.getMessage());
                future.completeExceptionally(ex);
            }
            return future;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            String payload = buildHeartbeatPayload(online);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    postJson("/servers/heartbeat", payload);
                    future.complete(true);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to send heartbeat: " + ex.getMessage());
                    future.completeExceptionally(ex);
                }
            });
        });

        return future;
    }

    private String buildTokenPayload(UUID playerUuid, String playerName) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendJsonString(builder, "serverId", serverId);
        appendJsonString(builder, "playerUuid", playerUuid != null ? playerUuid.toString() : "");
        appendJsonString(builder, "playerName", playerName != null ? playerName : "");
        appendJsonString(builder, "pluginVersion", plugin.getDescription().getVersion());
        appendJsonNumber(builder, "timestamp", Instant.now().getEpochSecond());
        builder.append('}');
        return builder.toString();
    }

    private String buildSnapshotPayload() {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendJsonString(builder, "serverId", serverId);
        appendJsonNumber(builder, "timestamp", System.currentTimeMillis());
        appendJsonNumber(builder, "online", Bukkit.getOnlinePlayers().size());
        appendJsonNumber(builder, "maxPlayers", Bukkit.getMaxPlayers());
        appendJsonString(builder, "motd", Bukkit.getMotd());
        appendJsonString(builder, "minecraftVersion", Bukkit.getBukkitVersion());
        appendJsonString(builder, "serverVersion", Bukkit.getVersion());
        appendJsonString(builder, "pluginVersion", plugin.getDescription().getVersion());

        StringBuilder players = new StringBuilder();
        players.append('[');
        boolean firstPlayer = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!firstPlayer) {
                players.append(',');
            }
            firstPlayer = false;
            players.append('{');
            appendJsonString(players, "uuid", player.getUniqueId().toString());
            appendJsonString(players, "name", player.getName());
            if (player.getWorld() != null) {
                appendJsonString(players, "world", player.getWorld().getName());
            }
            appendJsonNumber(players, "ping", player.getPing());
            players.append('}');
        }
        players.append(']');
        appendJsonRaw(builder, "onlinePlayers", players.toString());

        StringBuilder worlds = new StringBuilder();
        worlds.append('[');
        boolean firstWorld = true;
        for (World world : Bukkit.getWorlds()) {
            if (!firstWorld) {
                worlds.append(',');
            }
            firstWorld = false;
            worlds.append('{');
            appendJsonString(worlds, "name", world.getName());
            appendJsonString(worlds, "environment", world.getEnvironment().name());
            appendJsonNumber(worlds, "players", world.getPlayers().size());
            worlds.append('}');
        }
        worlds.append(']');
        appendJsonRaw(builder, "worlds", worlds.toString());

        builder.append('}');
        return builder.toString();
    }

    private String buildHeartbeatPayload(boolean online) {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        appendJsonString(builder, "serverId", serverId);
        appendJsonNumber(builder, "timestamp", System.currentTimeMillis());
        appendJsonRaw(builder, "online", online ? "true" : "false");
        appendJsonNumber(builder, "onlinePlayers", Bukkit.getOnlinePlayers().size());
        builder.append('}');
        return builder.toString();
    }

    private String postJson(String endpoint, String payload) throws IOException {
        URL url = new URL(baseUrl + (endpoint.startsWith("/") ? endpoint : "/" + endpoint));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readStream(stream);

        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + (response != null && !response.isEmpty() ? (": " + response) : ""));
        }
        return response != null ? response : "";
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private void appendJsonString(StringBuilder builder, String key, String value) {
        appendJsonSeparator(builder);
        builder.append('"').append(escapeJson(key)).append('"').append(':');
        if (value == null) {
            builder.append("null");
        } else {
            builder.append('"').append(escapeJson(value)).append('"');
        }
    }

    private void appendJsonNumber(StringBuilder builder, String key, Number value) {
        appendJsonSeparator(builder);
        builder.append('"').append(escapeJson(key)).append('"').append(':').append(value);
    }

    private void appendJsonRaw(StringBuilder builder, String key, String rawJson) {
        appendJsonSeparator(builder);
        builder.append('"').append(escapeJson(key)).append('"').append(':').append(rawJson);
    }

    private void appendJsonSeparator(StringBuilder builder) {
        if (builder.length() <= 1) {
            return;
        }

        char last = builder.charAt(builder.length() - 1);
        if (last == '{' || last == '[' || last == ',') {
            return;
        }

        builder.append(',');
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private EditorTokenResult parseTokenResponse(String response) {
        if (response == null || response.isEmpty()) {
            return new EditorTokenResult(null, null, -1, "");
        }

        String token = extractJsonString(response, "token");
        String url = extractJsonString(response, "url");
        long expiresIn = extractJsonNumber(response, "expiresIn");

        return new EditorTokenResult(token, url, expiresIn, response);
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"(.*?)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private long extractJsonNumber(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static class EditorTokenResult {
        private final String token;
        private final String url;
        private final long expiresIn;
        private final String rawResponse;

        public EditorTokenResult(String token, String url, long expiresIn, String rawResponse) {
            this.token = token;
            this.url = url;
            this.expiresIn = expiresIn;
            this.rawResponse = rawResponse;
        }

        public String getToken() {
            return token;
        }

        public String getUrl() {
            return url;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }
}
