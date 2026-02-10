package com.example.playerdatasync.premium.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * License validator for PlayerDataSync Premium using CraftingStudio Pro API
 * API Documentation: https://www.craftingstudiopro.de/docs/api
 * 
 * Validates license keys against the CraftingStudio Pro API
 */
public class LicenseValidator {
    private static final String API_BASE_URL = "https://craftingstudiopro.de/api";
    private static final String LICENSE_VALIDATE_ENDPOINT = "/license/validate";
    private static final String PLUGIN_ID = "playerdatasync-premium"; // Plugin slug or ID
    
    private final JavaPlugin plugin;
    private String cachedLicenseKey;
    private LicenseValidationResult cachedResult;
    private long lastValidationTime = 0;
    private static final long CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(30); // Cache for 30 minutes
    
    public LicenseValidator(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Validate a license key asynchronously
     * 
     * @param licenseKey The license key to validate
     * @return CompletableFuture with validation result
     */
    public CompletableFuture<LicenseValidationResult> validateLicenseAsync(String licenseKey) {
        // Check cache first
        if (licenseKey != null && licenseKey.equals(cachedLicenseKey) && 
            cachedResult != null && 
            (System.currentTimeMillis() - lastValidationTime) < CACHE_DURATION_MS) {
            return CompletableFuture.completedFuture(cachedResult);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = API_BASE_URL + LICENSE_VALIDATE_ENDPOINT;
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
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "PlayerDataSync-Premium/" + plugin.getDescription().getVersion());
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                
                // Create request body
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("licenseKey", licenseKey);
                requestBody.addProperty("pluginId", PLUGIN_ID);
                
                // Send request
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = connection.getResponseCode();
                
                // Handle rate limiting
                if (responseCode == 429) {
                    plugin.getLogger().warning("[LicenseValidator] Rate limit exceeded. Please try again later.");
                    return new LicenseValidationResult(false, "Rate limit exceeded. Please try again later.", null);
                }
                
                if (responseCode != 200) {
                    String errorMessage = "HTTP " + responseCode;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        if (errorResponse.length() > 0) {
                            errorMessage = errorResponse.toString();
                        }
                    } catch (Exception e) {
                        // Ignore error stream reading errors
                    }
                    plugin.getLogger().warning("[LicenseValidator] License validation failed: " + errorMessage);
                    return new LicenseValidationResult(false, errorMessage, null);
                }
                
                // Read response
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    String jsonResponse = response.toString();
                    if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                        return new LicenseValidationResult(false, "Empty response from API", null);
                    }
                    
                    // Parse JSON response
                    // Response format: { valid: boolean, message?: string, purchase?: { id: string, userId: string, pluginId: string, createdAt: string } }
                    JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
                    
                    boolean valid = jsonObject.has("valid") && jsonObject.get("valid").getAsBoolean();
                    String message = null;
                    if (jsonObject.has("message") && !jsonObject.get("message").isJsonNull()) {
                        message = jsonObject.get("message").getAsString();
                    }
                    
                    PurchaseInfo purchaseInfo = null;
                    if (valid && jsonObject.has("purchase") && !jsonObject.get("purchase").isJsonNull()) {
                        JsonObject purchaseObj = jsonObject.getAsJsonObject("purchase");
                        purchaseInfo = new PurchaseInfo(
                            purchaseObj.has("id") ? purchaseObj.get("id").getAsString() : null,
                            purchaseObj.has("userId") ? purchaseObj.get("userId").getAsString() : null,
                            purchaseObj.has("pluginId") ? purchaseObj.get("pluginId").getAsString() : null,
                            purchaseObj.has("createdAt") ? purchaseObj.get("createdAt").getAsString() : null
                        );
                    }
                    
                    LicenseValidationResult result = new LicenseValidationResult(valid, message, purchaseInfo);
                    
                    // Cache result
                    cachedLicenseKey = licenseKey;
                    cachedResult = result;
                    lastValidationTime = System.currentTimeMillis();
                    
                    return result;
                }
            } catch (java.net.UnknownHostException e) {
                plugin.getLogger().warning("[LicenseValidator] No internet connection available for license validation.");
                return new LicenseValidationResult(false, "No internet connection", null);
            } catch (java.net.SocketTimeoutException e) {
                plugin.getLogger().warning("[LicenseValidator] License validation timeout.");
                return new LicenseValidationResult(false, "Connection timeout", null);
            } catch (Exception e) {
                plugin.getLogger().severe("[LicenseValidator] License validation error: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.FINE, "License validation error", e);
                return new LicenseValidationResult(false, "Validation error: " + e.getMessage(), null);
            }
        });
    }
    
    /**
     * Validate license key synchronously (blocks thread)
     * Use validateLicenseAsync() for non-blocking validation
     */
    public LicenseValidationResult validateLicense(String licenseKey) {
        try {
            return validateLicenseAsync(licenseKey).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().severe("[LicenseValidator] Failed to validate license: " + e.getMessage());
            return new LicenseValidationResult(false, "Validation failed: " + e.getMessage(), null);
        }
    }
    
    /**
     * Clear the validation cache
     */
    public void clearCache() {
        cachedLicenseKey = null;
        cachedResult = null;
        lastValidationTime = 0;
    }
    
    /**
     * Check if cached validation is still valid
     */
    public boolean isCacheValid() {
        return cachedResult != null && 
               (System.currentTimeMillis() - lastValidationTime) < CACHE_DURATION_MS;
    }
    
    /**
     * Get cached validation result
     */
    public LicenseValidationResult getCachedResult() {
        return cachedResult;
    }
    
    /**
     * License validation result
     */
    public static class LicenseValidationResult {
        private final boolean valid;
        private final String message;
        private final PurchaseInfo purchase;
        
        public LicenseValidationResult(boolean valid, String message, PurchaseInfo purchase) {
            this.valid = valid;
            this.message = message;
            this.purchase = purchase;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public PurchaseInfo getPurchase() {
            return purchase;
        }
    }
    
    /**
     * Purchase information from API
     */
    public static class PurchaseInfo {
        private final String id;
        private final String userId;
        private final String pluginId;
        private final String createdAt;
        
        public PurchaseInfo(String id, String userId, String pluginId, String createdAt) {
            this.id = id;
            this.userId = userId;
            this.pluginId = pluginId;
            this.createdAt = createdAt;
        }
        
        public String getId() {
            return id;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public String getPluginId() {
            return pluginId;
        }
        
        public String getCreatedAt() {
            return createdAt;
        }
    }
}
