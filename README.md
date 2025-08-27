# PlayerDataSync

A simple Bukkit/Spigot plugin for Minecraft 1.20.4+ that synchronizes player data using either a MySQL or SQLite database. This project is built with Maven.
Player inventories, experience, health and more are stored in the configured
database whenever they leave the server and restored when they join again.

## Building

Run the following command in the project directory:

```bash
mvn package
```

The resulting jar can be found in `target/`.

The build process uses the Maven Shade plugin to bundle required
dependencies (such as bStats) directly into the final jar, so no
additional libraries need to be installed on the server.

## Configuration

`config.yml` contains the database connection settings and options to control which
player data should be synchronized:

```yaml
database:
  type: mysql # or sqlite
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    user: root
    password: password
  sqlite:
    file: plugins/PlayerDataSync/playerdata.db

sync:
  coordinates: true
  xp: true
  gamemode: true
  enderchest: true
  inventory: true
  health: true
  hunger: true
  position: true
  achievements: true  # ⚠️ WARNING: May cause lag with 500+ achievements
autosave:
  interval: 5
language: en
metrics: true
```

`metrics` controls whether anonymous usage statistics are sent to
[bStats](https://bstats.org/). Set it to `false` if you prefer to
opt out of metrics collection.

`autosave.interval` controls how often (in minutes) the plugin saves all online
players to the database. Set it to `0` to disable automatic saves.

Update the database values to match your environment. Set any of the `sync` options to
`false` if you want to skip syncing that particular data type.

Messages support color codes using the `&` character. For example,
`&e` will display text in yellow.

## Performance Considerations

### ⚠️ CRITICAL: Achievement Synchronization
**IMPORTANT**: If you experience server freezing or watchdog timeouts, this is likely caused by achievement synchronization issues. The plugin now includes automatic protection, but you should:

1. **Set `performance.disable_achievement_sync_on_large_amounts: true`** in config.yml
2. **Consider setting `sync.achievements: false`** if problems persist
3. **Monitor server logs** for timeout warnings

If you have a large number of achievements (500+), the achievement sync feature may cause server lag when players join. The plugin automatically detects large amounts and:

- **Disables sync** if more than 500 achievements exist (configurable)
- **Processes in batches** of 50 achievements to prevent lag
- **Loads asynchronously** for large amounts to avoid blocking the main thread

### Configuration for Large Servers
```yaml
performance:
  disable_achievement_sync_on_large_amounts: true  # Auto-disable for 500+ achievements
  achievement_batch_size: 50                       # Process achievements in batches
  connection_pooling: true                         # Use connection pooling
  async_loading: true                              # Load data asynchronously
```

### Disabling Achievement Sync
If you experience performance issues, you can disable achievement synchronization entirely:
```yaml
sync:
  achievements: false  # Disable achievement sync to prevent lag
```

## Version Compatibility

### Known Issues
- **Paper 1.21.1**: The plugin may experience compatibility issues with this server version due to API changes between 1.21.1 and 1.21.7
- **Attribute Sync Errors**: If you encounter `IncompatibleClassChangeError` related to attributes, enable safe attribute sync in the config
- **Minecraft 1.20.4-1.20.6**: Full compatibility confirmed
- **Minecraft 1.21.7-1.21.8**: Full compatibility confirmed

### Compatibility Settings
```yaml
compatibility:
  safe_attribute_sync: true      # Use reflection-based attribute syncing (recommended)
  disable_attributes_on_error: false # Auto-disable attributes if errors occur
  version_check: true            # Perform version compatibility checks on startup
```

### Recommended Server Versions
- **Paper 1.20.4-1.20.6**: Full compatibility, all features work correctly
- **Paper 1.21.7-1.21.8**: Full compatibility, all features work correctly
- **Paper 1.21.1**: Limited compatibility, some features may not work correctly
- **Spigot 1.20.4-1.21.8**: Full compatibility

### Troubleshooting Compatibility Issues
1. **Enable safe attribute sync**: Set `compatibility.safe_attribute_sync: true` in config.yml
2. **Update your server**: Consider updating to Paper 1.21.7+ for best compatibility
3. **Disable problematic features**: Set `sync.attributes: false` if issues persist
4. **Check server logs**: Look for version compatibility warnings on plugin startup

### 🚨 Troubleshooting Server Freezing
If your server freezes or shows watchdog timeouts:

1. **Immediate fix**: Set `sync.achievements: false` in config.yml
2. **Performance settings**: Ensure these are set:
   ```yaml
   performance:
     disable_achievement_sync_on_large_amounts: true
     achievement_timeout_ms: 3000  # Reduce timeout to 3 seconds
     max_achievements_per_player: 500  # Reduce limit
   ```
3. **Compatibility settings**: Enable these:
   ```yaml
   compatibility:
     disable_achievements_on_critical_error: true
   ```
4. **Check server logs** for "CRITICAL: Achievement serialization timeout" messages
