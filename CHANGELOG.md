# Changelog

All notable changes to PlayerDataSync will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [PlayerDataSync-26.3.4-SNAPSHOT] - 2026-03-11

### 🔧 Fixed & Improved
- **Legacy Minecraft Support (1.8-1.16)**: Resolved multiple critical issues for older server versions.
- **Java 8 Compatibility**: Fixed `NoSuchMethodError: String.isBlank()` by replacing it with Java 8 compatible alternatives in `MessageManager`.
- **FastStats Isolation**: Isolated Java 17-dependent metrics library to prevent `UnsupportedClassVersionError` on Java 8 environments.
- **JDBC Driver Fixes**:
    - Implemented explicit driver registration to solve "No suitable driver found" errors in shaded environments (PaperSpigot 1.8.8).
    - Fixed `AbstractMethodError: Connection.isValid()` by adding safety wrappers for legacy JDBC drivers (SQLite).
- **Configuration Robustness**:
    - Fixed an issue where `config.yml` was being stripped of content and comments on older Bukkit versions by reducing unnecessary saves.
    - Implemented explicit UTF-8 loading for configuration files to prevent character encoding issues on Windows servers.
    - Enhanced configuration migration and initialization to correctly merge defaults without overwriting existing user settings.
- **Stability and Performance**:
    - Fixed a race condition where default configurations were not being saved to disk before being reloaded during the first startup.
    - Consolidated maintenance mode and version checking logs for better readability.

## [PlayerDataSync-26.3.3-SNAPSHOT] - 2026-03-10

### 🚀 New Features
- **Maintenance Mode**: Pause all data syncing with `/sync maintenance <on|off>`. Useful for database migrations or emergency fixes.
- **Management GUI**: Use `/sync menu` for a visual way to toggle sync options and manage the plugin.
- **Performance Profiler**: Use `/sync profile` to track average and maximum save/load times for better performance monitoring.

### 🔧 Fixed
- **Java Compatibility**: Downgraded default Java target to Java 8 to ensure full compatibility with Minecraft 1.8.8 through 1.16.5 (Purpur) and resolved "Unsupported class file major version 65" errors.
- **Build Configuration**: Removed conflicting `activeByDefault` profile to prevent accidental Java 21 builds.

### 🔄 Update Checker
- **Hardcoded API Key**: Added a hardcoded API key (`CSP-PDS-...`) for streamlined server-to-API authentication.
- **Improved Caching**: Implemented a 1-hour cache for update metadata to reduce unnecessary API requests.
- **Manual Check Command**: Added `/sync checkupdate` command to manually trigger an update check (bypasses cache).
- **Silent Reloading**: Added simplified version check handling that can be called asynchronously without blocking the main thread.

## [PlayerDataSync-26.3.2-SNAPSHOT] - 2026-03-07

### 🔧 Fixed
- **MongoDB**: Fixed MongoDB errors / MongoDB-Fehler behoben.

## [PlayerDataSync-26.3-RELEASE] - 2026-03-04

### 🔧 Fixed
- **MongoDB**: Fixed MongoDB errors / MongoDB-Fehler behoben.

## [PlayerDataSync-26.2-RELEASE] - 2026-02-18

### 🛠 XP Sync Fixes
- Fixed an edge-case where spent XP (for example after enchanting) could be restored on relog due to stale total XP capture.
- Added immediate autosave hooks for XP/level changes and enchanting to persist XP updates right away.
- Added new config toggle `autosave.on_xp_change` (default: `true`) for instant XP persistence.

### 🗃 Database & Compatibility
- Kept automatic schema update behavior on startup and runtime upgrade attempts for safe database migrations.
- Prepared compatibility messaging/docs for upcoming 1.26.1/1.26.2 cycle.

## [1.3.0-RELEASE] - 2026-02-13

### 🔄 Reworked Update Checker
- Complete rewrite of version check flow with robust semantic comparison for tags like `-RELEASE`/`-BETA`
- Uses configurable request timeout (`update_checker.timeout`) for reliable network behavior
- Smarter latest-version detection avoids false positive update notifications

### ⚡ XP Synchronization Improvements
- Optimized XP apply flow with bounded correction attempts for more stable cross-version results
- Improved mismatch handling to prevent repeated over/under-correction on join/load
- More precise debug and warning logs for diagnosing XP sync edge-cases

### 💰 Economy Sync Optimization
- Optimized balance transfer with normalized precision for smooth and reliable Vault synchronization
- Added post-transfer verification and auto re-adjustment attempts to reduce desyncs
- Improved fallback logic when provider-specific `setBalance` implementations are inconsistent

## [1.2.9-RELEASE] - 2026-01-25

### 🎯 Custom-Enchantment-Support & Database Upgrade / Custom-Enchantment-Support & Datenbank-Upgrade

### 🔧 Fixed

- **Database Truncation Error (Critical Fix) / Datenbank-Truncation-Fehler (Critical Fix)**: 
  - **EN:** Fixes "Data too long for column" errors with large inventories
  - **DE:** Behebt "Data too long for column" Fehler bei großen Inventaren
  - ✅ **EN:** Automatic upgrade from `TEXT` to `LONGTEXT` for `inventory`, `enderchest`, `armor`, and `offhand` columns
  - ✅ **DE:** Automatisches Upgrade von `TEXT` zu `LONGTEXT` für `inventory`, `enderchest`, `armor` und `offhand` Spalten
  - ✅ **EN:** Now supports inventories with many custom enchantments (e.g., ExcellentEnchants)
  - ✅ **DE:** Unterstützt jetzt Inventare mit vielen Custom-Enchantments (z.B. ExcellentEnchants)
  - ✅ **EN:** Upgrade is performed automatically on server start
  - ✅ **DE:** Upgrade wird automatisch beim Server-Start durchgeführt
  - ✅ **EN:** Runtime upgrade attempt on truncation errors
  - ✅ **DE:** Runtime-Upgrade-Versuch bei Truncation-Fehlern
  - ✅ **EN:** Improved error messages with solution suggestions
  - ✅ **DE:** Verbesserte Fehlermeldungen mit Lösungsvorschlägen
  - 🔧 **EN:** Fixes issues with large inventories and custom enchantments
  - 🔧 **DE:** Behebt Probleme mit großen Inventaren und Custom-Enchantments

- **Custom-Enchantment Deserialization / Custom-Enchantment-Deserialisierung**: 
  - **EN:** Robust error handling for custom enchantments
  - **DE:** Robuste Fehlerbehandlung für Custom-Enchantments
  - ✅ **EN:** Improved detection of custom enchantment errors (e.g., `minecraft:venom`)
  - ✅ **DE:** Verbesserte Erkennung von Custom-Enchantment-Fehlern (z.B. `minecraft:venom`)
  - ✅ **EN:** Items are skipped instead of causing plugin crashes
  - ✅ **DE:** Items werden übersprungen statt Plugin-Absturz zu verursachen
  - ✅ **EN:** Data remains preserved in the database
  - ✅ **DE:** Daten bleiben in der Datenbank erhalten
  - ✅ **EN:** Detailed logging with enchantment names
  - ✅ **DE:** Detailliertes Logging mit Enchantment-Namen
  - ✅ **EN:** Support for ExcellentEnchants and similar plugins
  - ✅ **DE:** Unterstützung für ExcellentEnchants und ähnliche Plugins
  - 🔧 **EN:** Prevents crashes with unrecognized custom enchantments
  - 🔧 **DE:** Verhindert Abstürze bei nicht erkannten Custom-Enchantments

- **Stale Player Data / Veraltete Spielerdaten**: 
  - **EN:** Fixes issue with outdated player data
  - **DE:** Behebt Problem mit nicht aktualisierten Spielerdaten
  - ✅ **EN:** Database upgrade enables successful saves
  - ✅ **DE:** Datenbank-Upgrade ermöglicht erfolgreiche Speicherungen
  - ✅ **EN:** Improved error handling prevents data loss
  - ✅ **DE:** Verbesserte Fehlerbehandlung verhindert Datenverlust
  - ✅ **EN:** Automatic recovery after database upgrade
  - ✅ **DE:** Automatische Wiederherstellung nach Datenbank-Upgrade

### ✨ Added

- **Custom-Enchantment Synchronization / Custom-Enchantment-Synchronisation**: 
  - **EN:** Full support for custom enchantments
  - **DE:** Vollständige Unterstützung für Custom-Enchantments
  - ✅ **EN:** Preservation of all NBT data including custom enchantments during serialization
  - ✅ **DE:** Erhaltung aller NBT-Daten inklusive Custom-Enchantments beim Serialisieren
  - ✅ **EN:** Refresh mechanism after loading inventories (2-tick delay)
  - ✅ **DE:** Refresh-Mechanismus nach dem Laden von Inventaren (2-Tick-Delay)
  - ✅ **EN:** Explicit re-setting of items so plugins can process enchantments
  - ✅ **DE:** Explizites Neusetzen von Items, damit Plugins Enchantments verarbeiten können
  - ✅ **EN:** Works for main inventory, armor, offhand, and enderchest
  - ✅ **DE:** Funktioniert für Hauptinventar, Rüstung, Offhand und Enderchest
  - 📝 **EN:** Supports plugins like ExcellentEnchants that use custom enchantments
  - 📝 **DE:** Unterstützt Plugins wie ExcellentEnchants, die Custom-Enchantments verwenden

- **Deserialization Statistics & Monitoring / Deserialisierungs-Statistiken & Monitoring**: 
  - **EN:** Comprehensive monitoring system
  - **DE:** Umfassendes Monitoring-System
  - ✅ **EN:** Counters for custom enchantment errors, version compatibility errors, and other errors
  - ✅ **DE:** Zähler für Custom-Enchantment-Fehler, Versionskompatibilitäts-Fehler und andere Fehler
  - ✅ **EN:** `getDeserializationStats()` method for statistics
  - ✅ **DE:** `getDeserializationStats()` Methode für Statistiken
  - ✅ **EN:** `resetDeserializationStats()` method to reset statistics
  - ✅ **DE:** `resetDeserializationStats()` Methode zum Zurücksetzen
  - ✅ **EN:** Integration into `/sync cache` command
  - ✅ **DE:** Integration in `/sync cache` Befehl
  - ✅ **EN:** Detailed error logging with enchantment names
  - ✅ **DE:** Detaillierte Fehlerprotokollierung mit Enchantment-Namen
  - 📝 **EN:** Admins can now easily monitor custom enchantment issues
  - 📝 **DE:** Admins können jetzt Probleme mit Custom-Enchantments einfach überwachen

- **Improved Error Handling / Verbesserte Fehlerbehandlung**: 
  - **EN:** Extended error detection and handling
  - **DE:** Erweiterte Fehlererkennung und -behandlung
  - ✅ **EN:** Automatic extraction of enchantment names from error messages
  - ✅ **DE:** Automatische Extraktion von Enchantment-Namen aus Fehlermeldungen
  - ✅ **EN:** Detailed error chain analysis (up to 3 levels)
  - ✅ **DE:** Detaillierte Fehlerketten-Analyse (bis zu 3 Ebenen)
  - ✅ **EN:** Contextual error messages with solution suggestions
  - ✅ **DE:** Kontextuelle Fehlermeldungen mit Lösungsvorschlägen
  - ✅ **EN:** Better detection of various error types (IllegalStateException, NullPointerException, etc.)
  - ✅ **DE:** Bessere Erkennung verschiedener Fehlertypen (IllegalStateException, NullPointerException, etc.)
  - ✅ **EN:** Pattern-based detection of custom enchantment errors
  - ✅ **DE:** Pattern-basierte Erkennung von Custom-Enchantment-Fehlern

### 🔄 Changed

- **Database Schema / Datenbank-Schema**: 
  - **EN:** Automatic upgrade for existing installations
  - **DE:** Automatisches Upgrade für bestehende Installationen
  - ✅ **EN:** `inventory`: TEXT → LONGTEXT (max. ~4GB instead of ~65KB)
  - ✅ **DE:** `inventory`: TEXT → LONGTEXT (max. ~4GB statt ~65KB)
  - ✅ **EN:** `enderchest`: TEXT → LONGTEXT
  - ✅ **DE:** `enderchest`: TEXT → LONGTEXT
  - ✅ **EN:** `armor`: TEXT → LONGTEXT
  - ✅ **DE:** `armor`: TEXT → LONGTEXT
  - ✅ **EN:** `offhand`: TEXT → LONGTEXT
  - ✅ **DE:** `offhand`: TEXT → LONGTEXT
  - ✅ **EN:** Upgrade is performed automatically on server start
  - ✅ **DE:** Upgrade wird beim Server-Start automatisch durchgeführt
  - 📝 **EN:** Existing data is preserved, no data migration needed
  - 📝 **DE:** Bestehende Daten bleiben erhalten, keine Datenmigration nötig

- **EditorIntegration Removed / EditorIntegration entfernt**: 
  - **EN:** Preparation for website update
  - **DE:** Vorbereitung für Website-Update
  - ✅ **EN:** EditorIntegrationManager completely removed
  - ✅ **DE:** EditorIntegrationManager komplett entfernt
  - ✅ **EN:** All editor-related commands removed
  - ✅ **DE:** Alle Editor-bezogenen Befehle entfernt
  - ✅ **EN:** Code cleanup for future editor integration
  - ✅ **DE:** Code-Bereinigung für zukünftige Editor-Integration
  - 📝 **EN:** New editor integration will be added in a future version
  - 📝 **DE:** Neue Editor-Integration wird in zukünftiger Version hinzugefügt

### 📊 Technical Details

#### Database Upgrade Process / Datenbank-Upgrade-Prozess

**EN:** The plugin automatically performs an upgrade of database columns on startup:

**DE:** Das Plugin führt beim Start automatisch ein Upgrade der Datenbank-Spalten durch:

1. **EN:** **Check**: Verifies the current data type of each column
   **DE:** **Prüfung**: Überprüft den aktuellen Datentyp jeder Spalte
2. **EN:** **Upgrade**: Converts `TEXT` to `LONGTEXT` if necessary
   **DE:** **Upgrade**: Konvertiert `TEXT` zu `LONGTEXT` wenn nötig
3. **EN:** **Logging**: Logs all upgrades for transparency
   **DE:** **Logging**: Protokolliert alle Upgrades für Transparenz
4. **EN:** **Runtime Upgrade**: Also attempts to upgrade during runtime if an error occurs
   **DE:** **Runtime-Upgrade**: Versucht auch während des Betriebs zu upgraden, wenn ein Fehler auftritt

**EN:** **Why LONGTEXT?**  
**DE:** **Warum LONGTEXT?**
- `TEXT`: Max. ~65KB (65,535 bytes)
- `LONGTEXT`: Max. ~4GB (4,294,967,295 bytes)
- **EN:** Custom enchantments with extensive NBT data can become very large
- **DE:** Custom-Enchantments mit vielen NBT-Daten können sehr groß werden
- **EN:** Large inventories with many items and enchantments require more space
- **DE:** Große Inventare mit vielen Items und Enchantments benötigen mehr Platz

#### Custom-Enchantment Error Handling / Custom-Enchantment-Fehlerbehandlung

**EN:** The improved error handling recognizes various error types:

**DE:** Die verbesserte Fehlerbehandlung erkennt verschiedene Fehlertypen:

- **IllegalStateException** with DataResult/Codec/Decoder
- **NullPointerException** in enchantment-related classes
- **EN:** Error messages with "enchantment not found/unknown/invalid"
- **DE:** Fehlermeldungen mit "enchantment not found/unknown/invalid"
- **EN:** Pattern-based detection of custom enchantment names
- **DE:** Pattern-basierte Erkennung von Custom-Enchantment-Namen

**EN:** **Error Handling Flow:**  
**DE:** **Fehlerbehandlung-Flow:**
1. **EN:** Attempt normal deserialization
   **DE:** Versuch der normalen Deserialisierung
2. **EN:** On error: Check if it's a custom enchantment problem
   **DE:** Bei Fehler: Prüfung ob es ein Custom-Enchantment-Problem ist
3. **EN:** Extract enchantment name from error message
   **DE:** Extraktion des Enchantment-Namens aus der Fehlermeldung
4. **EN:** Detailed logging with context
   **DE:** Detailliertes Logging mit Kontext
5. **EN:** Item is skipped (null), but data remains in database
   **DE:** Item wird übersprungen (null), aber Daten bleiben in DB
6. **EN:** Statistics are updated
   **DE:** Statistiken werden aktualisiert

#### Refresh Mechanism / Refresh-Mechanismus

**EN:** After loading inventories, a refresh mechanism is executed:

**DE:** Nach dem Laden von Inventaren wird ein Refresh-Mechanismus ausgeführt:

1. **EN:** **Initial Load**: ItemStacks are loaded from database
   **DE:** **Initiales Laden**: ItemStacks werden aus der Datenbank geladen
2. **EN:** **2-Tick Delay**: Waits 2 ticks to give plugins time to initialize
   **DE:** **2-Tick-Delay**: Wartet 2 Ticks, damit Plugins Zeit haben zu initialisieren
3. **EN:** **Refresh**: Explicitly re-sets items to trigger plugin processing
   **DE:** **Refresh**: Setzt Items explizit neu, um Plugin-Verarbeitung zu triggern
4. **EN:** **Update**: Calls `updateInventory()` for client synchronization
   **DE:** **Update**: Ruft `updateInventory()` auf für Client-Synchronisation

**EN:** **Why 2 Ticks?**  
**DE:** **Warum 2 Ticks?**
- **EN:** Gives custom enchantment plugins time to register their enchantments
- **DE:** Gibt Custom-Enchantment-Plugins Zeit, ihre Enchantments zu registrieren
- **EN:** Enables plugin event handlers to react to item changes
- **DE:** Ermöglicht Plugin-Event-Handler, auf Item-Änderungen zu reagieren
- **EN:** Prevents race conditions between plugin loading and item loading
- **DE:** Verhindert Race-Conditions zwischen Plugin-Loading und Item-Loading

#### Statistics System / Statistiken-System

**EN:** The new statistics system collects information about deserialization errors:

**DE:** Das neue Statistiken-System sammelt Informationen über Deserialisierungs-Fehler:

- **EN:** **Custom Enchantment Errors**: Counts items skipped due to unrecognized custom enchantments
- **DE:** **Custom-Enchantment-Fehler**: Zählt Items, die wegen nicht erkannter Custom-Enchantments übersprungen wurden
- **EN:** **Version Compatibility Errors**: Counts items with version compatibility issues
- **DE:** **Versionskompatibilitäts-Fehler**: Zählt Items mit Versionskompatibilitätsproblemen
- **EN:** **Other Errors**: Counts all other deserialization errors
- **DE:** **Andere Fehler**: Zählt alle anderen Deserialisierungs-Fehler

**EN:** **Usage:**  
**DE:** **Verwendung:**
```bash
/sync cache          # EN: Shows all statistics / DE: Zeigt alle Statistiken
/sync cache clear    # EN: Resets statistics / DE: Setzt Statistiken zurück
```

### 🔍 Monitoring & Debugging

**EN:** Admins can now easily monitor custom enchantment issues:

**DE:** Admins können jetzt einfach Probleme mit Custom-Enchantments überwachen:

1. **EN:** **View Statistics**: `/sync cache` shows deserialization statistics
   **DE:** **Statistiken anzeigen**: `/sync cache` zeigt Deserialisierungs-Statistiken
2. **EN:** **Analyze Errors**: Detailed logs show exactly which enchantments cause problems
   **DE:** **Fehler analysieren**: Detaillierte Logs zeigen genau, welche Enchantments Probleme verursachen
3. **EN:** **Fix Issues**: Clear error messages with solution suggestions
   **DE:** **Probleme beheben**: Klare Fehlermeldungen mit Lösungsvorschlägen

**EN:** **Example Output:**  
**DE:** **Beispiel-Output:**
```
Deserialization Stats: Deserialization failures: 5 total 
(Custom Enchantments: 3, Version Issues: 1, Other: 1)
⚠ If you see custom enchantment failures, ensure enchantment plugins 
(e.g., ExcellentEnchants) are loaded and all enchantments are registered.
```

### ⚠️ Important Notes / Wichtige Hinweise

- **EN:** **Database Upgrade**: On first start after update, columns are automatically upgraded
  **DE:** **Datenbank-Upgrade**: Beim ersten Start nach dem Update werden die Spalten automatisch geupgradet
- **EN:** **Custom Enchantments**: Ensure enchantment plugins (e.g., ExcellentEnchants) are installed and active on both servers
  **DE:** **Custom-Enchantments**: Stellen Sie sicher, dass Enchantment-Plugins (z.B. ExcellentEnchants) auf beiden Servern installiert und aktiv sind
- **EN:** **Plugin Load Order**: Enchantment plugins should load before PlayerDataSync (check `plugin.yml`)
  **DE:** **Plugin-Load-Reihenfolge**: Enchantment-Plugins sollten vor PlayerDataSync geladen werden (in `plugin.yml` prüfen)
- **EN:** **EditorIntegration**: EditorIntegration has been removed and will be re-implemented in a future version
  **DE:** **EditorIntegration**: Die EditorIntegration wurde entfernt und wird in einer zukünftigen Version neu implementiert

### 📝 Migration Guide

**EN:** **For Existing Installations:**

**DE:** **Für bestehende Installationen:**

1. **EN:** **Automatic Upgrade**: No manual action needed - plugin performs upgrade automatically
   **DE:** **Automatisches Upgrade**: Keine manuelle Aktion nötig - das Plugin führt das Upgrade automatisch durch
2. **EN:** **Restart Server**: Restart server after update to perform database upgrade
   **DE:** **Server neu starten**: Nach dem Update den Server neu starten, damit das Datenbank-Upgrade durchgeführt wird
3. **EN:** **Check Logs**: Verify logs for upgrade messages:
   **DE:** **Logs prüfen**: Überprüfen Sie die Logs auf Upgrade-Meldungen:
   ```
   [INFO] Upgraded inventory column from TEXT to LONGTEXT to support large inventories
   [INFO] Upgraded enderchest column from TEXT to LONGTEXT to support large inventories
   [INFO] Upgraded armor column from TEXT to LONGTEXT to support large inventories
   [INFO] Upgraded offhand column from TEXT to LONGTEXT to support large inventories
   ```
4. **EN:** **Check Custom Enchantments**: Ensure all enchantment plugins are loaded correctly
   **DE:** **Custom-Enchantments prüfen**: Stellen Sie sicher, dass alle Enchantment-Plugins korrekt geladen sind

**EN:** **Troubleshooting:**

**DE:** **Bei Problemen:**

- **EN:** Check `/sync cache` for deserialization statistics
  **DE:** Prüfen Sie `/sync cache` für Deserialisierungs-Statistiken
- **EN:** Review logs for custom enchantment errors
  **DE:** Überprüfen Sie die Logs auf Custom-Enchantment-Fehler
- **EN:** Ensure enchantment plugins are installed on both servers
  **DE:** Stellen Sie sicher, dass Enchantment-Plugins auf beiden Servern installiert sind
- **EN:** Check plugin load order in `plugin.yml`
  **DE:** Prüfen Sie die Plugin-Load-Reihenfolge in `plugin.yml`

---

## [1.2.7-RELEASE] - 2025-12-29

### 🔧 Critical Fixes & New Features

This release includes critical bug fixes for XP synchronization and Vault economy, plus a new Respawn to Lobby feature.

### Fixed
- **Issue #45 - XP & Level Synchronization (Critical Fix)**: Complete rewrite of experience synchronization
  - ✅ Replaced unreliable `setTotalExperience()` with `giveExp()` as primary method
  - ✅ `giveExp()` is more reliable across all Minecraft versions (1.8-1.21.11)
  - ✅ Better error handling and verification with detailed logging
  - ✅ Automatic correction if experience doesn't match expected value
  - ✅ Prevents XP sync failures on all supported versions
  - ✅ Improved level calculation and synchronization
  - 🔧 Fixes Issue #43, #45 and XP sync problems across version range
  - 📝 Detailed logging for debugging XP sync issues
- **Issue #46 - Vault Balance de-sync on server shutdown**: Fixed economy balance not being saved during shutdown
  - ✅ Enhanced shutdown save process to ensure Vault economy is available
  - ✅ Reconfigure economy integration before shutdown save
  - ✅ Added delay to ensure Vault is fully initialized before saving
  - ✅ Force balance refresh before save to get latest balance
  - ✅ Better error handling and logging during shutdown
  - ✅ Prevents economy balance loss on server restart
  - 🔧 Fixes Issue #46: Vault Balance de-sync on server shutdown

### Added
- **Respawn to Lobby Feature**: New feature to send players to lobby server after death/respawn
  - ✅ Automatically transfers players to lobby server after respawn
  - ✅ Uses existing BungeeCord integration and shared database
  - ✅ Configurable lobby server name
  - ✅ Saves player data before transfer to ensure data consistency
  - ✅ Smart detection to prevent transfers if already on lobby server
  - ✅ Requires BungeeCord integration to be enabled
  - 📝 Configuration: `respawn_to_lobby.enabled` and `respawn_to_lobby.server` in config.yml

### Technical Details
- **XP Sync Method Change**: Switched from `setTotalExperience()` to `giveExp()` for better compatibility
- **Why**: `setTotalExperience()` has version-specific bugs, `giveExp()` works reliably everywhere
- **Verification**: Added automatic verification and correction mechanism
- **Logging**: Enhanced logging with before/after values for debugging
- **Shutdown Process**: Improved economy save process with Vault reconfiguration and balance refresh
- **Respawn Handler**: New `PlayerRespawnEvent` handler for lobby transfer functionality

---

## [1.2.8-BETA] - 2025-12-29

### 🎉 Big Update - Major Improvements & API Migration

This release includes significant improvements, API migrations, and enhanced compatibility features.

### Fixed
- **XP Synchronization (Critical Fix)**: Complete rewrite of experience synchronization
  - ✅ Replaced unreliable `setTotalExperience()` with `giveExp()` as primary method
  - ✅ `giveExp()` is more reliable across all Minecraft versions (1.8-1.21.11)
  - ✅ Better error handling and verification with detailed logging
  - ✅ Automatic correction if experience doesn't match expected value
  - ✅ Prevents XP sync failures on all supported versions
  - 🔧 Fixes Issue #43, #45 and XP sync problems across version range

### Changed
- **Update Checker**: Complete migration to CraftingStudio Pro API
  - ✅ Migrated from SpigotMC Legacy API to CraftingStudio Pro API
  - ✅ New API endpoint: `https://craftingstudiopro.de/api/plugins/playerdatasync/latest`
  - ✅ Uses plugin slug (`playerdatasync`) instead of resource ID
  - ✅ Improved JSON response parsing using Gson library
  - ✅ Better error handling for HTTP status codes (429 Rate Limit, etc.)
  - ✅ Enhanced update information with download URLs from API response
  - 📖 API Documentation: https://www.craftingstudiopro.de/docs/api
- **Plugin API Version**: Updated to 1.13 for better modern API support
  - Minimum required Minecraft version: 1.13
  - Still supports versions 1.8-1.21.11 with automatic compatibility handling
  - Improved NamespacedKey support and modern Material API usage

### Fixed
- **Version Compatibility**: Fixed critical GRAY_STAINED_GLASS_PANE compatibility issue
  - ✅ Prevents fatal error on Minecraft 1.8-1.12 servers
  - ✅ Automatic version detection and Material selection
  - ✅ Uses `STAINED_GLASS_PANE` with durability value 7 for older versions (1.8-1.12)
  - ✅ Uses `GRAY_STAINED_GLASS_PANE` for modern versions (1.13+)
  - ✅ Filler item in inventory viewer now works correctly across all supported versions
- **Inventory Synchronization**: Enhanced inventory sync reliability
  - ✅ Added `updateInventory()` calls after loading inventory, armor, and offhand
  - ✅ Improved client synchronization for all inventory types
  - ✅ Better inventory size validation (normalized to 36 slots for main inventory)
  - ✅ Improved enderchest size validation (normalized to 27 slots)
  - ✅ Better armor array normalization (ensures exactly 4 slots)
- **ItemStack Validation**: Enhanced ItemStack sanitization and validation
  - ✅ Improved validation of item amounts (checks against max stack size)
  - ✅ Better handling of invalid stack sizes (clamps to max instead of removing)
  - ✅ Improved AIR item filtering
  - ✅ More robust error handling for corrupted items
- **Logging System**: Complete logging overhaul
  - ✅ Replaced all `System.err.println()` calls with proper Bukkit logger
  - ✅ Replaced all `printStackTrace()` calls with proper `logger.log()` calls
  - ✅ Better log levels (WARNING/SEVERE instead of stderr for compatibility issues)
  - ✅ More consistent error messages across the codebase
  - ✅ Stack traces now properly logged through plugin logger
  - ✅ Improved logging for version compatibility issues

### Improved
- **Code Quality**: Significant improvements to error handling and resource management
  - ✅ Comprehensive exception handling with proper stack trace logging
  - ✅ Better debug logging throughout inventory operations
  - ✅ Improved client synchronization after inventory changes
  - ✅ Better resource management and cleanup
  - ✅ Enhanced error diagnostics throughout the codebase
- **Performance**: Optimizations for inventory operations
  - ✅ Better item validation prevents unnecessary operations
  - ✅ Improved error recovery mechanisms
  - ✅ Enhanced memory management

### Technical Details
- **API Migration**: Complete rewrite of UpdateChecker class
  - Old: SpigotMC Legacy API (plain text response)
  - New: CraftingStudio Pro API (JSON response with Gson parsing)
  - Improved error handling for network issues and rate limits
- **Compatibility**: Maintained support for Minecraft 1.8-1.21.11
  - Version-based feature detection and automatic disabling
  - Graceful degradation for unsupported features on older versions
  - Comprehensive version compatibility checking at startup

### Breaking Changes
⚠️ **Plugin API Version**: Changed from `1.8` to `1.13`
- Plugins compiled with this version require at least Minecraft 1.13
- Server administrators using 1.8-1.12 should continue using previous versions
- Automatic legacy conversion may still work, but not guaranteed

### Migration Guide
If upgrading from 1.2.6-RELEASE or earlier:
1. No configuration changes required
2. Update checker will now use CraftingStudio Pro API
3. All existing data is compatible
4. Recommended to test on a staging server first

---

## [1.2.6-RELEASE] - 2025-12-29

### Changed
- **Update Checker**: Migrated to CraftingStudio Pro API
  - Updated from SpigotMC API to CraftingStudio Pro API (https://craftingstudiopro.de/api)
  - Now uses plugin slug instead of resource ID
  - Improved JSON response parsing for better update information
  - Better error handling for rate limits (429 responses)
  - API endpoint: `/api/plugins/playerdatasync/latest`

### Fixed
- **Version Compatibility**: Fixed GRAY_STAINED_GLASS_PANE compatibility issue for Minecraft 1.8-1.12
  - Added version check to use STAINED_GLASS_PANE with durability value 7 for older versions
  - Prevents fatal error when loading plugin on 1.8-1.12 servers
  - Filler item in inventory viewer now works correctly across all supported versions

---

## [1.2.7-ALPHA] - 2025-12-29

### Changed
- **Update Checker**: Migrated to CraftingStudio Pro API
  - Updated from SpigotMC API to CraftingStudio Pro API (https://craftingstudiopro.de/api)
  - Now uses plugin slug instead of resource ID
  - Improved JSON response parsing for better update information
  - Better error handling for rate limits (429 responses)
  - API endpoint: `/api/plugins/playerdatasync/latest`

### Fixed
- **Version Compatibility**: Fixed GRAY_STAINED_GLASS_PANE compatibility issue for Minecraft 1.8-1.12
  - Added version check to use STAINED_GLASS_PANE with durability value 7 for older versions
  - Prevents fatal error when loading plugin on 1.8-1.12 servers
  - Filler item in inventory viewer now works correctly across all supported versions

---

## [1.2.6-ALPHA] - 2025-12-29

### Improved
- **Inventory Synchronization**: Significantly improved inventory sync reliability
  - Added `updateInventory()` call after loading inventory, armor, and offhand to ensure client synchronization
  - Improved inventory size validation (normalized to 36 slots for main inventory)
  - Improved enderchest size validation (normalized to 27 slots)
  - Better armor array normalization (ensures exactly 4 slots)
  - Enhanced error handling with detailed logging and stack traces
  - Added debug logging for successful inventory loads
- **ItemStack Validation**: Enhanced ItemStack sanitization
  - Improved validation of item amounts (checks against max stack size)
  - Better handling of invalid stack sizes (clamps to max stack size instead of removing)
  - Improved AIR item filtering
  - More robust error handling for corrupted items
- **Logging System**: Improved logging consistency
  - Replaced all `System.err.println()` calls with proper Bukkit logger
  - Replaced all `printStackTrace()` calls with proper logger.log() calls
  - Better log levels (WARNING instead of stderr for compatibility issues)
  - More consistent error messages across the codebase
  - Improved logging for version compatibility issues
  - Stack traces now properly logged through plugin logger instead of printStackTrace()
- **Code Quality**: Further improvements to error handling
  - Added comprehensive exception handling with stack traces
  - Better debug logging throughout inventory operations
  - Improved client synchronization after inventory changes
  - Better resource management and cleanup

### Fixed
- **Inventory Sync Issues**: Fixed cases where inventory changes weren't synchronized with client
- **ItemStack Validation**: Fixed potential issues with invalid item amounts and stack sizes
- **Logging**: Fixed inconsistent logging using System.err instead of proper logger

---

## [1.2.5-SNAPSHOT] - 2025-12-29

### Fixed
- **Issue #45 - XP Sync Not Working**: Fixed experience synchronization not working
  - Improved `applyExperience()` method with proper reset before setting experience
  - Added verification to ensure experience is set correctly
  - Added fallback mechanism using `giveExp()` if `setTotalExperience()` doesn't work
  - Better error handling with detailed logging and stack traces
  - Now works reliably across all Minecraft versions (1.8-1.21.11)

### Improved
- **Code Quality**: Fixed deprecated method usage and improved compatibility
  - Replaced deprecated `URL(String)` constructor with `URI.toURL()` for better Java 20+ compatibility
  - Replaced deprecated `PotionEffectType.getName()` with `getKey().getKey()` for better compatibility
  - Improved `PotionEffectType.getByName()` usage with NamespacedKey fallback
  - Replaced deprecated `getMaxHealth()` with Attribute system where available
  - Improved `getOfflinePlayer(String)` usage with better error handling
  - Added `@SuppressWarnings` annotations for necessary deprecated method usage
  - Cleaned up unused imports and improved code organization

---

## [1.2.4-SNAPSHOT] - 2025-12-29

### Added
- **Extended Version Support**: Full compatibility with Minecraft 1.8 to 1.21.11
  - Comprehensive version detection and compatibility checking
  - Maven build profiles for all major Minecraft versions (1.8, 1.9-1.16, 1.17, 1.18-1.20, 1.21+)
  - Automatic feature detection and disabling based on server version
  - VersionCompatibility utility class for runtime version checks
- **Project Structure Reorganization**: Complete package restructuring
  - Organized code into logical packages: `core`, `database`, `integration`, `listeners`, `managers`, `utils`, `commands`, `api`
  - Improved code maintainability and organization
  - All imports and package declarations updated accordingly
- **Version-Based Feature Management**: Automatic feature disabling
  - Offhand sync automatically disabled on 1.8 (requires 1.9+)
  - Attribute sync automatically disabled on 1.8 (requires 1.9+)
  - Advancement sync automatically disabled on 1.8-1.11 (requires 1.12+)
  - Features are checked and disabled during plugin initialization

### Fixed
- **Issue #43 - Experience Synchronization Error**: Fixed experience synchronization issues
  - Initial fix for experience synchronization problems
  - Added validation for negative experience values
- **Issue #42 - Vault Reset on Server Restart**: Fixed economy balance not being restored on server restart
  - Economy integration is now reconfigured during shutdown to ensure availability
  - Balance restoration with 5-tick delay and retry mechanism
  - Improved Vault provider availability checking
- **Issue #41 - Potion Effect on Death**: Fixed potion effects being restored after death
  - Effects are only restored if player is not dead or respawning
  - Added death/respawn state checking before effect restoration
  - Effects are properly cleared on death as expected
- **Issue #40 - Heartbeat HTTP 500**: Improved error handling for HTTP 500 errors
  - Enhanced error handling with detailed logging
  - Specific error messages for different HTTP status codes (400, 401, 404, 500+)
  - Connection timeout and socket timeout handling
  - Better debugging information for API issues

### Changed
- **Minecraft Version Support**: Extended from 1.20.4-1.21.9 to 1.8-1.21.11
  - Default Java version set to 8 for maximum compatibility
  - Maven profiles for different Java versions (8, 16, 17, 21)
  - Plugin API version set to 1.8 (lowest supported version)
- **Build System**: Enhanced Maven configuration
  - Compiler plugin now uses variables for source/target versions
  - Multiple build profiles for different Minecraft versions
  - Proper Java version handling per Minecraft version
- **Code Organization**: Complete package restructure
  - `core/`: Main plugin class (PlayerDataSync)
  - `database/`: Database management (DatabaseManager, ConnectionPool)
  - `integration/`: Plugin integrations (EditorIntegrationManager, InventoryViewerIntegrationManager)
  - `listeners/`: Event listeners (PlayerDataListener, ServerSwitchListener)
  - `managers/`: Manager classes (AdvancementSyncManager, BackupManager, ConfigManager, MessageManager)
  - `utils/`: Utility classes (InventoryUtils, OfflinePlayerData, PlayerDataCache, VersionCompatibility)
  - `commands/`: Command handlers (SyncCommand)
  - `api/`: API and update checker (UpdateChecker)
- **Version Compatibility Checking**: Enhanced startup version validation
  - Detects Minecraft version range (1.8 to 1.21.11)
  - Logs feature availability based on version
  - Provides warnings for unsupported versions
  - Tests critical API methods with version checks

### Technical Details
- **Build Profiles**: 
  - `mvn package -Pmc-1.8` for Minecraft 1.8 (Java 8)
  - `mvn package -Pmc-1.9` through `-Pmc-1.16` for 1.9-1.16 (Java 8)
  - `mvn package -Pmc-1.17` for Minecraft 1.17 (Java 16)
  - `mvn package -Pmc-1.18` through `-Pmc-1.20` for 1.18-1.20 (Java 17)
  - `mvn package -Pmc-1.21` for Minecraft 1.21+ (Java 21) - Default
- **Code Quality**: Improved error handling and version compatibility
- **Resource Management**: Better cleanup and memory management
- **Exception Handling**: More specific error messages and recovery mechanisms

### Compatibility
- **Minecraft 1.8**: Full support (some features disabled)
- **Minecraft 1.9-1.11**: Full support (advancements disabled)
- **Minecraft 1.12-1.16**: Full support
- **Minecraft 1.17**: Full support
- **Minecraft 1.18-1.20**: Full support
- **Minecraft 1.21-1.21.11**: Full support

---

## [Unreleased] - 2025-01-14

### Fixed
- **API Server ID**: Fixed "Missing server_id" error in heartbeat and API requests
  - Changed JSON field name from `"serverId"` (camelCase) to `"server_id"` (snake_case) in all API payloads
  - Improved server_id resolution from config file with better fallback handling
  - Added detailed logging to track which server_id source is being used
  - Fixed server_id not being read correctly from `server.id` config option
  - All API endpoints (heartbeat, token, snapshot) now correctly send server_id

### Added
- **Message Configuration**: New option to disable sync messages
  - Added `messages.show_sync_messages` config option (default: `true`)
  - When set to `false`, all sync-related messages (loading, saving, server switch) are disabled
  - Prevents empty messages from being sent when message strings are empty
  - Works in conjunction with existing permission system

### Changed
- **API Integration**: All JSON payloads now use snake_case for `server_id` field
  - `buildHeartbeatPayload()`: Uses `"server_id"` instead of `"serverId"`
  - `buildTokenPayload()`: Uses `"server_id"` instead of `"serverId"`
  - `buildSnapshotPayload()`: Uses `"server_id"` instead of `"serverId"`
- **Server ID Resolution**: Enhanced resolution logic with better error handling
  - Checks environment variables first, then system properties, then ConfigManager, then direct config
  - Always ensures a valid server_id is returned (never null or empty)
  - Added comprehensive logging for debugging server_id resolution

### Configuration
- **New Settings**:
  - `messages.show_sync_messages`: Control whether sync messages are shown to players (default: `true`)

---

## [1.1.7-SNAPSHOT] - 2025-01-05

### Added
- **Extended Version Support**: Full compatibility with Minecraft 1.20.4 to 1.21.9
  - Comprehensive version detection and compatibility checking
  - Maven build profiles for different Minecraft versions
  - Enhanced startup version validation
- **Enhanced Message System**: Improved localization support
  - Added parameter support to MessageManager (`get(String key, String... params)`)
  - Support for both indexed (`{0}`, `{1}`) and named (`{version}`, `{error}`, `{url}`) placeholders
  - Dynamic message content with variable substitution
- **Version Compatibility Fixes**: Robust ItemStack deserialization
  - Safe deserialization methods (`safeItemStackArrayFromBase64`, `safeItemStackFromBase64`)
  - Graceful handling of version-incompatible ItemStack data
  - Individual item error handling to prevent complete deserialization failures
  - Fallback mechanisms for corrupted or incompatible data
- **Enhanced Update Checker**: Improved console messaging
  - Localized update checker messages in German and English
  - Better error handling with specific error messages
  - Configurable update checking with proper console feedback
  - Dynamic content in update notifications (version numbers, URLs)

### Changed
- **Java Version**: Upgraded from Java 17 to Java 21 for optimal performance
- **Minecraft Version**: Updated default target to Minecraft 1.21
- **Plugin Metadata**: Enhanced plugin.yml with proper `authors` array and `load: STARTUP`
- **Version Compatibility**: Comprehensive support for 1.20.4 through 1.21.9
- **Message Handling**: All hardcoded messages replaced with localized MessageManager calls
- **Error Recovery**: Better handling of version compatibility issues

### Fixed
- **Critical Version Compatibility**: Fixed "Newer version! Server downgrades are not supported!" errors
  - ItemStack deserialization now handles version mismatches gracefully
  - Individual items that can't be deserialized are skipped instead of crashing
  - Empty arrays returned as fallback for completely failed deserialization
- **MessageManager Compilation**: Fixed "Method get cannot be applied to given types" errors
  - Added overloaded `get` method with parameter support
  - Proper parameter replacement for dynamic content
  - Backward compatibility with existing `get(String key)` method
- **Update Checker Messages**: Fixed missing console messages
  - All update checker events now display proper localized messages
  - Dynamic content (version numbers, URLs) properly integrated
  - Better error reporting for different failure scenarios
- **Database Loading**: Enhanced error handling for corrupted inventory data
  - Safe deserialization prevents server crashes from version issues
  - Partial data recovery when some items are incompatible
  - Better logging for version compatibility issues

### Security
- **Data Validation**: Enhanced ItemStack validation and sanitization
- **Error Handling**: Graceful degradation for corrupted or incompatible data
- **Version Safety**: Protection against version-related crashes

### Performance
- **Memory Efficiency**: Better handling of large ItemStack arrays
- **Error Recovery**: Faster recovery from deserialization failures
- **Resource Management**: Improved cleanup of failed operations

### Compatibility
- **Minecraft 1.20.4**: Full support confirmed
- **Minecraft 1.20.5**: Full support confirmed
- **Minecraft 1.20.6**: Full support confirmed
- **Minecraft 1.21.0**: Full support confirmed
- **Minecraft 1.21.1**: Full support confirmed
- **Minecraft 1.21.2**: Full support confirmed
- **Minecraft 1.21.3**: Full support confirmed
- **Minecraft 1.21.4**: Full support confirmed
- **Minecraft 1.21.5**: Full support confirmed
- **Minecraft 1.21.6**: Full support confirmed
- **Minecraft 1.21.7**: Full support confirmed
- **Minecraft 1.21.8**: Full support confirmed
- **Minecraft 1.21.9**: Full support confirmed

### Configuration
- **New Messages**:
  - `loaded`: "Player data loaded successfully" / "Spielerdaten erfolgreich geladen"
  - `load_failed`: "Failed to load player data" / "Fehler beim Laden der Spielerdaten"
  - `update_check_disabled`: "Update checking is disabled" / "Update-Prüfung ist deaktiviert"
  - `update_check_timeout`: "Update check timed out" / "Update-Prüfung ist abgelaufen"
  - `update_check_no_internet`: "No internet connection for update check" / "Keine Internetverbindung für Update-Prüfung"
  - `update_download_url`: "Download at: {url}" / "Download unter: {url}"

### Commands
- **Enhanced Commands**:
  - All commands now use localized messages with parameter support
  - Better error reporting with dynamic content
  - Improved user feedback for all operations

### Technical Details
- **Build System**: Maven profiles for different Minecraft versions
  - `mvn package -Pmc-1.20.4` for Minecraft 1.20.4 (Java 17)
  - `mvn package -Pmc-1.21` for Minecraft 1.21 (Java 21) - Default
  - `mvn package -Pmc-1.21.1` for Minecraft 1.21.1 (Java 21)
- **Code Quality**: Enhanced error handling and parameter validation
- **Resource Management**: Better cleanup and memory management
- **Exception Handling**: More specific error messages and recovery mechanisms
- **Debugging**: Enhanced diagnostic information and version compatibility logging

---

### Added
- **Backup System**: Complete backup and restore functionality
  - Automatic daily backups with configurable retention
  - Manual backup creation via `/sync backup create`
  - Backup restoration via `/sync restore <player> [backup_id]`
  - SQL dump generation for database backups
  - ZIP compression for file backups
  - Backup listing and management commands
- **Performance Caching**: In-memory player data cache
  - Configurable cache size and TTL (Time-To-Live)
  - LRU (Least Recently Used) eviction policy
  - Optional compression for memory optimization
  - Cache statistics and management via `/sync cache`
- **Enhanced Performance Monitoring**
  - Detailed save/load time tracking
  - Connection pool statistics
  - Performance metrics logging
  - Slow operation detection and warnings
- **Improved Update Checker**
  - Configurable timeout settings
  - Better error handling for network issues
  - Download link in update notifications
  - User-Agent header for API requests
- **Emergency Configuration System**
  - Automatic config file creation if missing
  - Fallback configuration generation
  - Debug information for configuration issues
  - Multi-layer configuration loading approach

### Changed
- **Achievement Sync Limits**: Increased limits for Minecraft's 1000+ achievements
  - `MAX_COUNT_ATTEMPTS`: 1000 → 2000
  - `MAX_ACHIEVEMENTS`: 1000 → 2000
  - `MAX_PROCESSED`: 2000 → 3000
  - Large amount warning threshold: 500 → 1500
- **Database Performance**: Enhanced connection pooling
  - Exponential backoff for connection acquisition
  - Increased total timeout to 10 seconds
  - Better connection pool exhaustion handling
- **Inventory Utilities**: Improved ItemStack handling
  - Integrated sanitization and validation
  - Better corruption data handling
  - Enhanced Base64 serialization/deserialization
- **Configuration Management**: Robust config loading
  - Multiple fallback mechanisms
  - Emergency configuration creation
  - Better error diagnostics

### Fixed
- **Critical Achievement Bug**: Fixed infinite loop in achievement counting
  - Added hard limits to prevent server freezes
  - Implemented timeout-based processing
  - Better error handling and logging
- **Database Parameter Error**: Fixed "No value specified for parameter 20" error
  - Corrected SQL REPLACE INTO statement
  - Added missing `advancements` and `server_id` parameters
  - Fixed PreparedStatement parameter setting
- **Slow Save Detection**: Optimized achievement serialization
  - Reduced processing time for large achievement counts
  - Added performance monitoring
  - Implemented batch processing with timeouts
- **Configuration Loading**: Fixed empty config.yml issue
  - Added multiple fallback mechanisms
  - Emergency configuration generation
  - Better resource loading handling
- **Compilation Errors**: Fixed missing imports
  - Added `java.io.File` and `java.io.FileWriter` imports
  - Resolved Date ambiguity in BackupManager
  - Fixed try-catch block nesting issues

### Security
- **Enhanced Data Validation**: Improved ItemStack sanitization
- **Audit Logging**: Better security event tracking
- **Error Recovery**: Graceful handling of corrupted data

### Performance
- **Memory Optimization**: Cache compression and TTL management
- **Database Efficiency**: Connection pooling and batch processing
- **Async Operations**: Non-blocking backup and save operations
- **Resource Management**: Better memory and connection cleanup

### Compatibility
- **Minecraft 1.21.5**: Full support for Paper 1.21.5
- **Legacy Support**: Maintained compatibility with 1.20.x
- **API Compatibility**: Proper handling of version differences
- **Database Compatibility**: Support for MySQL, SQLite, and PostgreSQL

### Configuration
- **New Settings**:
  - `performance.cache_ttl`: Cache time-to-live in milliseconds
  - `performance.cache_compression`: Enable cache compression
  - `update_checker.timeout`: Connection timeout for update checks
  - `performance.max_achievements_per_player`: Increased to 2000
  - `server.id`: Unique server identifier for multi-server setups

### Commands
- **New Commands**:
  - `/sync backup create`: Create manual backup
  - `/sync restore <player> [backup_id]`: Restore from backup
  - `/sync cache clear`: Clear performance statistics
- **Enhanced Commands**:
  - `/sync cache`: Now shows performance and connection pool stats
  - `/sync status`: Improved status reporting
  - `/sync reload`: Better configuration reload handling

### Technical Details
- **Code Quality**: Improved error handling and logging
- **Resource Management**: Better cleanup and memory management
- **Exception Handling**: More specific error messages and recovery
- **Debugging**: Enhanced diagnostic information and logging

---

## [1.1.4] - 2024-12-XX

### Added
- Initial release with basic player data synchronization
- MySQL and SQLite database support
- Basic configuration system
- Player event handling (join, quit, world change, death)
- Sync command with basic functionality

### Features
- Coordinate synchronization
- Experience (XP) synchronization
- Gamemode synchronization
- Inventory synchronization
- Enderchest synchronization
- Armor synchronization
- Offhand synchronization
- Health and hunger synchronization
- Potion effects synchronization
- Achievements synchronization
- Statistics synchronization
- Attributes synchronization

---

## [1.1.3] - 2024-12-XX

### Fixed
- Various bug fixes and stability improvements
- Database connection handling improvements
- Better error messages and logging

---

## [1.1.2] - 2024-12-XX

### Fixed
- Configuration loading issues
- Database parameter errors
- Achievement sync problems

---

## [1.1.1] - 2024-12-XX

### Fixed
- Initial bug fixes and improvements
- Better error handling

---

## [1.1.0] - 2024-12-XX

### Added
- Initial stable release
- Core synchronization features
- Basic configuration system
- Database support

---

## [1.0.0] - 2024-12-XX

### Added
- Initial development release
- Basic plugin structure
- Core functionality implementation
