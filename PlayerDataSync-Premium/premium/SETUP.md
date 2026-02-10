# PlayerDataSync Premium - Setup Anleitung

## Projektstruktur

Die Premium-Version benötigt alle Klassen aus PlayerDataSync, aber mit angepassten Package-Namen:

### Package-Mapping

**Von:** `com.example.playerdatasync.*`  
**Zu:** `com.example.playerdatasync.premium.*`

### Zu kopierende Klassen

Alle folgenden Klassen müssen aus `PlayerDataSync/src/main/java/com/example/playerdatasync/` nach `PlayerDataSync-Premium/premium/src/main/java/com/example/playerdatasync/premium/` kopiert und Package-Namen angepasst werden:

#### Core
- `core/PlayerDataSync.java` → `premium/core/PlayerDataSyncPremium.java` ✅ (bereits erstellt)

#### Database
- `database/ConnectionPool.java` → `premium/database/ConnectionPool.java`
- `database/DatabaseManager.java` → `premium/database/DatabaseManager.java`

#### Commands
- `commands/SyncCommand.java` → `premium/commands/SyncCommand.java`
  - Premium-Befehle hinzufügen: `/sync license validate`, `/sync license info`, `/sync update check`

#### Listeners
- `listeners/PlayerDataListener.java` → `premium/listeners/PlayerDataListener.java`
- `listeners/ServerSwitchListener.java` → `premium/listeners/ServerSwitchListener.java`

#### Managers
- `managers/AdvancementSyncManager.java` → `premium/managers/AdvancementSyncManager.java`
- `managers/BackupManager.java` → `premium/managers/BackupManager.java`
- `managers/ConfigManager.java` → `premium/managers/ConfigManager.java`
- `managers/MessageManager.java` → `premium/managers/MessageManager.java`
- `managers/LicenseManager.java` → `premium/managers/LicenseManager.java` ✅ (bereits erstellt)

#### Integration
- `integration/InventoryViewerIntegrationManager.java` → `premium/integration/InventoryViewerIntegrationManager.java`

#### Utils
- `utils/InventoryUtils.java` → `premium/utils/InventoryUtils.java`
- `utils/OfflinePlayerData.java` → `premium/utils/OfflinePlayerData.java`
- `utils/PlayerDataCache.java` → `premium/utils/PlayerDataCache.java`
- `utils/VersionCompatibility.java` → `premium/utils/VersionCompatibility.java`

#### API
- `api/PremiumUpdateChecker.java` → `premium/api/PremiumUpdateChecker.java` ✅ (bereits erstellt)
- `api/LicenseValidator.java` → `premium/api/LicenseValidator.java` ✅ (bereits erstellt)

### Resources

- `resources/config.yml` → `premium/src/main/resources/config.yml` ✅ (bereits erstellt)
- `resources/plugin.yml` → `premium/src/main/resources/plugin.yml` ✅ (bereits erstellt)
- `resources/messages_en.yml` → `premium/src/main/resources/messages_en.yml`
- `resources/messages_de.yml` → `premium/src/main/resources/messages_de.yml`

## Anpassungen

### 1. Package-Namen ändern

Alle Klassen müssen von:
```java
package com.example.playerdatasync.xxx;
```

zu:
```java
package com.example.playerdatasync.premium.xxx;
```

### 2. Imports anpassen

Alle Imports müssen angepasst werden:
```java
// Alt
import com.example.playerdatasync.database.DatabaseManager;

// Neu
import com.example.playerdatasync.premium.database.DatabaseManager;
```

### 3. SyncCommand erweitern

In `SyncCommand.java` müssen Premium-Befehle hinzugefügt werden:

```java
case "license":
    return handleLicense(sender, args);

case "update":
    return handleUpdate(sender, args);
```

### 4. PlayerDataSyncPremium.java vervollständigen

Die Hauptklasse `PlayerDataSyncPremium.java` ist bereits erstellt, aber alle Methoden aus der originalen `PlayerDataSync.java` müssen kopiert werden.

## Build

```bash
cd PlayerDataSync-Premium/premium
mvn clean package
```

Die JAR-Datei wird in `target/PlayerDataSync-Premium-1.2.9-PREMIUM.jar` erstellt.

## Wichtige Hinweise

1. **License Key erforderlich**: Die Premium-Version funktioniert nur mit einem gültigen Lizenzschlüssel
2. **API-Zugriff**: Benötigt Internetverbindung für Lizenz-Validierung und Update-Checks
3. **Rate Limits**: API hat ein Limit von 100 Requests/Stunde pro IP
4. **Caching**: Lizenz-Validierung wird 30 Minuten gecacht

## Nächste Schritte

1. Alle Klassen aus PlayerDataSync kopieren
2. Package-Namen anpassen
3. Imports anpassen
4. Premium-Befehle in SyncCommand hinzufügen
5. Build und Test
