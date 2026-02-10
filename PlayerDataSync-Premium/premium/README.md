# PlayerDataSync Premium

## Übersicht / Overview

**EN:** PlayerDataSync Premium is the premium version of PlayerDataSync with license validation, automatic update checking, and enhanced features for custom enchantments.  
**DE:** PlayerDataSync Premium ist die Premium-Version von PlayerDataSync mit Lizenz-Validierung, automatischer Update-Prüfung und erweiterten Features für Custom-Enchantments.

## Features

### ✅ License Validation / Lizenz-Validierung
- **EN:** Validates license keys against CraftingStudio Pro API
- **DE:** Validiert Lizenzschlüssel gegen CraftingStudio Pro API
- **EN:** Automatic license re-validation every 24 hours
- **DE:** Automatische Lizenz-Re-Validierung alle 24 Stunden
- **EN:** Caching to reduce API calls (30 minutes)
- **DE:** Caching zur Reduzierung von API-Aufrufen (30 Minuten)
- **EN:** Automatic plugin disabling on invalid license
- **DE:** Automatische Plugin-Deaktivierung bei ungültiger Lizenz

### ✅ Update Checker / Update-Prüfung
- **EN:** Checks for updates using CraftingStudio Pro API
- **DE:** Prüft auf Updates über CraftingStudio Pro API
- **EN:** Notifies operators about available updates
- **DE:** Benachrichtigt Operatoren über verfügbare Updates
- **EN:** Rate limit handling (100 requests/hour)
- **DE:** Rate-Limit-Behandlung (100 Anfragen/Stunde)

### ✅ Premium Features
- **EN:** All features from PlayerDataSync
- **DE:** Alle Features von PlayerDataSync
- **EN:** Enhanced support for custom enchantments (ExcellentEnchants, etc.)
- **DE:** Erweiterte Unterstützung für Custom-Enchantments (ExcellentEnchants, etc.)
- **EN:** Priority support
- **DE:** Prioritäts-Support

## Installation

1. **EN:** Download PlayerDataSync Premium from CraftingStudio Pro
   **DE:** Lade PlayerDataSync Premium von CraftingStudio Pro herunter

2. **EN:** Place the JAR file in your `plugins` folder
   **DE:** Platziere die JAR-Datei in deinem `plugins` Ordner

3. **EN:** Start your server to generate the config file
   **DE:** Starte deinen Server, um die Config-Datei zu generieren

4. **EN:** Edit `plugins/PlayerDataSync-Premium/config.yml` and enter your license key:
   **DE:** Bearbeite `plugins/PlayerDataSync-Premium/config.yml` und trage deinen Lizenzschlüssel ein:

```yaml
license:
  key: YOUR-LICENSE-KEY-HERE
```

5. **EN:** Restart your server
   **DE:** Starte deinen Server neu

## API Integration

### License Validation

**Endpoint:** `POST https://craftingstudiopro.de/api/license/validate`

**Request:**
```json
{
  "licenseKey": "YOUR-LICENSE-KEY",
  "pluginId": "playerdatasync-premium"
}
```

**Response:**
```json
{
  "valid": true,
  "message": "License is valid",
  "purchase": {
    "id": "purchase-id",
    "userId": "user-id",
    "pluginId": "playerdatasync-premium",
    "createdAt": "2025-01-01T00:00:00Z"
  }
}
```

### Update Check

**Endpoint:** `GET https://craftingstudiopro.de/api/plugins/playerdatasync-premium/latest`

**Response:**
```json
{
  "version": "1.2.9-PREMIUM",
  "downloadUrl": "https://...",
  "pluginTitle": "PlayerDataSync Premium",
  "pluginSlug": "playerdatasync-premium"
}
```

## Commands

- `/sync license validate` - Manually validate license key
- `/sync license info` - Show license information (masked)
- `/sync update check` - Manually check for updates

## Support

- Website: https://craftingstudiopro.de
- API Documentation: https://www.craftingstudiopro.de/docs/api
