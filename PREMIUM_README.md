# PlayerDataSync Premium

## Übersicht / Overview

**EN:** PlayerDataSync Premium is the premium version of PlayerDataSync with license validation and additional features.  
**DE:** PlayerDataSync Premium ist die Premium-Version von PlayerDataSync mit Lizenz-Validierung und zusätzlichen Features.

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

## Installation / Installation

### Requirements / Anforderungen

- **EN:** Minecraft Server 1.8 - 1.26.2
- **DE:** Minecraft Server 1.8 - 1.26.2
- **EN:** Valid license key from CraftingStudio Pro
- **DE:** Gültiger Lizenzschlüssel von CraftingStudio Pro
- **EN:** Internet connection for license validation
- **DE:** Internetverbindung für Lizenz-Validierung

### Setup / Einrichtung

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

## Configuration / Konfiguration

### License Configuration / Lizenz-Konfiguration

```yaml
license:
  key: YOUR-LICENSE-KEY-HERE  # Your license key from CraftingStudio Pro
```

### Update Checker Configuration / Update-Checker-Konfiguration

```yaml
update_checker:
  enabled: true              # Enable automatic update checking
  notify_ops: true           # Notify operators when updates are available
  timeout: 10000            # Timeout in milliseconds
```

### Premium Features Configuration / Premium-Features-Konfiguration

```yaml
premium:
  revalidation_interval_hours: 24  # Revalidate license every 24 hours
  cache_validation: true            # Cache validation results
  enable_premium_features: true     # Enable premium-specific features
```

## API Integration / API-Integration

### License Validation / Lizenz-Validierung

**Endpoint:**
```
POST https://craftingstudiopro.de/api/license/validate
```

**Request Body:**
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

### Update Check / Update-Prüfung

**Endpoint:**
```
GET https://craftingstudiopro.de/api/plugins/playerdatasync-premium/latest
```

**Response:**
```json
{
  "version": "PlayerDataSync-26.2-RELEASE",
  "downloadUrl": "https://...",
  "createdAt": "2025-01-01T00:00:00Z",
  "title": "Release 26.2",
  "releaseType": "release",
  "pluginTitle": "PlayerDataSync Premium",
  "pluginSlug": "playerdatasync-premium"
}
```

## Rate Limits / Rate-Limits

**EN:** The API has a rate limit of 100 requests per hour per IP address.  
**DE:** Die API hat ein Rate Limit von 100 Anfragen pro Stunde pro IP-Adresse.

**EN:** If you exceed the limit, you will receive a `429 Too Many Requests` status code.  
**DE:** Bei Überschreitung erhalten Sie einen `429 Too Many Requests` Status Code.

**EN:** The plugin uses caching to minimize API calls:
- License validation: Cached for 30 minutes
- Update checks: Performed on server start and can be triggered manually

**DE:** Das Plugin verwendet Caching zur Minimierung von API-Aufrufen:
- Lizenz-Validierung: 30 Minuten gecacht
- Update-Prüfungen: Beim Server-Start und manuell auslösbar

## Troubleshooting / Fehlerbehebung

### License Validation Failed / Lizenz-Validierung fehlgeschlagen

**EN:** **Problem:** License validation fails on startup  
**DE:** **Problem:** Lizenz-Validierung schlägt beim Start fehl

**EN:** **Solutions:**
1. Check your license key in `config.yml`
2. Ensure the license is valid for "playerdatasync-premium"
3. Check your internet connection
4. Verify the license hasn't expired
5. Check server logs for detailed error messages

**DE:** **Lösungen:**
1. Prüfe deinen Lizenzschlüssel in `config.yml`
2. Stelle sicher, dass die Lizenz für "playerdatasync-premium" gültig ist
3. Prüfe deine Internetverbindung
4. Verifiziere, dass die Lizenz nicht abgelaufen ist
5. Prüfe die Server-Logs für detaillierte Fehlermeldungen

### Update Check Not Working / Update-Prüfung funktioniert nicht

**EN:** **Problem:** Update checker doesn't find updates  
**DE:** **Problem:** Update-Checker findet keine Updates

**EN:** **Solutions:**
1. Check `update_checker.enabled: true` in config
2. Verify internet connection
3. Check logs for rate limit errors
4. Manually trigger update check: `/sync update`

**DE:** **Lösungen:**
1. Prüfe `update_checker.enabled: true` in der Config
2. Verifiziere Internetverbindung
3. Prüfe Logs auf Rate-Limit-Fehler
4. Manuell Update-Prüfung auslösen: `/sync update`

### Plugin Disables Itself / Plugin deaktiviert sich selbst

**EN:** **Problem:** Plugin disables itself after 30 seconds  
**DE:** **Problem:** Plugin deaktiviert sich nach 30 Sekunden

**EN:** **Cause:** License validation failed or license is invalid  
**DE:** **Ursache:** Lizenz-Validierung fehlgeschlagen oder Lizenz ist ungültig

**EN:** **Solutions:**
1. Check license key in config
2. Verify license is valid on CraftingStudio Pro
3. Check server logs for validation errors
4. Contact support if license should be valid

**DE:** **Lösungen:**
1. Prüfe Lizenzschlüssel in der Config
2. Verifiziere, dass die Lizenz auf CraftingStudio Pro gültig ist
3. Prüfe Server-Logs auf Validierungsfehler
4. Kontaktiere Support, wenn die Lizenz gültig sein sollte

## Commands / Befehle

### `/sync license validate`
**EN:** Manually validate license key  
**DE:** Lizenzschlüssel manuell validieren

**Permission:** `playerdatasync.premium.admin`

### `/sync license info`
**EN:** Show license information (masked)  
**DE:** Zeige Lizenzinformationen (maskiert)

**Permission:** `playerdatasync.premium.admin`

### `/sync update check`
**EN:** Manually check for updates  
**DE:** Manuell auf Updates prüfen

**Permission:** `playerdatasync.premium.admin`

## Support / Support

**EN:** For support, please visit:
- Website: https://craftingstudiopro.de
- Discord: [Join our Discord](https://discord.gg/...)
- Documentation: https://www.craftingstudiopro.de/docs/api

**DE:** Für Support besuche bitte:
- Website: https://craftingstudiopro.de
- Discord: [Tritt unserem Discord bei](https://discord.gg/...)
- Dokumentation: https://www.craftingstudiopro.de/docs/api

## License / Lizenz

**EN:** PlayerDataSync Premium requires a valid license key from CraftingStudio Pro.  
**DE:** PlayerDataSync Premium benötigt einen gültigen Lizenzschlüssel von CraftingStudio Pro.

**EN:** Without a valid license, the plugin will disable itself after 30 seconds.  
**DE:** Ohne gültige Lizenz deaktiviert sich das Plugin nach 30 Sekunden.

## Changelog / Änderungsprotokoll

See [CHANGELOG.md](CHANGELOG.md) for version history.

Siehe [CHANGELOG.md](CHANGELOG.md) für Versionshistorie.
