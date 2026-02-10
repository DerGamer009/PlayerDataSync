# PlayerDataSync Premium - Integration Guide

## Übersicht / Overview

Dieses Dokument beschreibt, wie die Premium-Komponenten (Lizenz-Validierung und Update-Check) in PlayerDataSync Premium integriert werden.

This document describes how to integrate the premium components (license validation and update check) into PlayerDataSync Premium.

## Komponenten / Components

### 1. LicenseValidator
**Pfad / Path:** `com.example.playerdatasync.premium.api.LicenseValidator`

**Funktion / Function:**
- Validiert Lizenzschlüssel gegen die CraftingStudio Pro API
- Validates license keys against the CraftingStudio Pro API
- Caching von Validierungsergebnissen (30 Minuten)
- Caches validation results (30 minutes)

**API Endpoint:**
```
POST https://craftingstudiopro.de/api/license/validate
Body: { "licenseKey": "YOUR-KEY", "pluginId": "playerdatasync-premium" }
Response: { "valid": boolean, "message": string, "purchase": {...} }
```

### 2. PremiumUpdateChecker
**Pfad / Path:** `com.example.playerdatasync.premium.api.PremiumUpdateChecker`

**Funktion / Function:**
- Prüft auf Updates über die CraftingStudio Pro API
- Checks for updates via CraftingStudio Pro API
- Benachrichtigt OPs über verfügbare Updates
- Notifies OPs about available updates

**API Endpoint:**
```
GET https://craftingstudiopro.de/api/plugins/playerdatasync-premium/latest
Response: { "version": string, "downloadUrl": string, ... }
```

### 3. LicenseManager
**Pfad / Path:** `com.example.playerdatasync.premium.managers.LicenseManager`

**Funktion / Function:**
- Verwaltet Lizenz-Validierung und Caching
- Manages license validation and caching
- Periodische Re-Validierung (alle 24 Stunden)
- Periodic re-validation (every 24 hours)
- Automatische Plugin-Deaktivierung bei ungültiger Lizenz
- Automatic plugin disabling on invalid license

### 4. PremiumIntegration
**Pfad / Path:** `com.example.playerdatasync.premium.PremiumIntegration`

**Funktion / Function:**
- Wrapper-Klasse für einfache Integration
- Wrapper class for easy integration
- Kombiniert LicenseManager und PremiumUpdateChecker
- Combines LicenseManager and PremiumUpdateChecker

## Integration in Hauptklasse / Integration in Main Class

### Beispiel / Example:

```java
package com.example.playerdatasync.premium.core;

import org.bukkit.plugin.java.JavaPlugin;
import com.example.playerdatasync.premium.PremiumIntegration;

public class PlayerDataSyncPremium extends JavaPlugin {
    private PremiumIntegration premiumIntegration;
    
    @Override
    public void onEnable() {
        getLogger().info("Enabling PlayerDataSync Premium...");
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize premium features (license validation)
        premiumIntegration = new PremiumIntegration(this);
        if (!premiumIntegration.initialize()) {
            // License validation failed, plugin will be disabled
            getLogger().severe("License validation failed. Plugin disabled.");
            return;
        }
        
        // Only continue if license is valid
        if (!premiumIntegration.isLicenseValid()) {
            getLogger().severe("Invalid license. Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // ... rest of your plugin initialization ...
        
        getLogger().info("PlayerDataSync Premium enabled successfully!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Disabling PlayerDataSync Premium...");
        
        if (premiumIntegration != null) {
            premiumIntegration.shutdown();
        }
        
        // ... rest of shutdown code ...
        
        getLogger().info("PlayerDataSync Premium disabled successfully");
    }
    
    public PremiumIntegration getPremiumIntegration() {
        return premiumIntegration;
    }
}
```

## Konfiguration / Configuration

### config.yml Beispiel / Example:

```yaml
# License Configuration
license:
  key: YOUR-LICENSE-KEY-HERE  # Your license key from CraftingStudio Pro

# Update Checker
update_checker:
  enabled: true
  notify_ops: true
  timeout: 10000

# Premium Features
premium:
  revalidation_interval_hours: 24
  cache_validation: true
  enable_premium_features: true
```

## API Verwendung / API Usage

### Lizenz validieren / Validate License:

```java
LicenseValidator validator = new LicenseValidator(plugin);

// Asynchron
CompletableFuture<LicenseValidationResult> future = 
    validator.validateLicenseAsync("YOUR-LICENSE-KEY");
    
future.thenAccept(result -> {
    if (result.isValid()) {
        // License is valid
        PurchaseInfo purchase = result.getPurchase();
        // Use purchase information
    } else {
        // License is invalid
        String message = result.getMessage();
    }
});

// Synchron (blockiert Thread)
LicenseValidationResult result = validator.validateLicense("YOUR-LICENSE-KEY");
```

### Update prüfen / Check for Updates:

```java
PremiumUpdateChecker updateChecker = new PremiumUpdateChecker(plugin);
updateChecker.check(); // Asynchron
```

### LicenseManager verwenden / Use LicenseManager:

```java
LicenseManager licenseManager = new LicenseManager(plugin);
licenseManager.initialize(); // Validiert Lizenz beim Start

// Prüfen ob Lizenz gültig ist
if (licenseManager.isLicenseValid()) {
    // Plugin kann verwendet werden
}

// Lizenz neu validieren
licenseManager.revalidateLicense();

// Neue Lizenz setzen
licenseManager.setLicenseKey("NEW-LICENSE-KEY");
```

## Fehlerbehandlung / Error Handling

### Rate Limiting:
Die API hat ein Rate Limit von 100 Requests pro Stunde pro IP.
The API has a rate limit of 100 requests per hour per IP.

Bei Überschreitung wird ein `429 Too Many Requests` Status Code zurückgegeben.
On exceeding the limit, a `429 Too Many Requests` status code is returned.

### Netzwerk-Fehler / Network Errors:
- `UnknownHostException`: Keine Internetverbindung / No internet connection
- `SocketTimeoutException`: Timeout beim Verbindungsaufbau / Connection timeout
- Alle Fehler werden geloggt / All errors are logged

## Sicherheit / Security

- **Lizenzschlüssel maskieren**: In Logs werden nur die ersten und letzten 4 Zeichen angezeigt
- **License key masking**: Only first and last 4 characters shown in logs
- **Caching**: Validierungsergebnisse werden 30 Minuten gecacht
- **Caching**: Validation results are cached for 30 minutes
- **Re-Validierung**: Automatische Re-Validierung alle 24 Stunden
- **Re-validation**: Automatic re-validation every 24 hours

## Troubleshooting

### Lizenz wird nicht akzeptiert / License not accepted:
1. Prüfen Sie den Lizenzschlüssel in `config.yml`
2. Check license key in `config.yml`
3. Stellen Sie sicher, dass die Lizenz für "playerdatasync-premium" gültig ist
4. Ensure license is valid for "playerdatasync-premium"
5. Prüfen Sie die Logs auf Fehlermeldungen
6. Check logs for error messages

### Update-Check funktioniert nicht / Update check not working:
1. Prüfen Sie die Internetverbindung
2. Check internet connection
3. Prüfen Sie, ob `update_checker.enabled: true` in der Config ist
4. Check if `update_checker.enabled: true` in config
5. Prüfen Sie die Logs auf Rate-Limit-Fehler
6. Check logs for rate limit errors

## API Dokumentation / API Documentation

Vollständige API-Dokumentation: https://www.craftingstudiopro.de/docs/api

Complete API documentation: https://www.craftingstudiopro.de/docs/api
