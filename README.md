# PlayerDataSync

A comprehensive Bukkit/Spigot plugin for Minecraft 1.20.4 to 1.21.9 that synchronizes player data using MySQL, SQLite, or PostgreSQL databases. This project is built with Maven.
Player inventories, experience, health, achievements, and more are stored in the configured
database whenever they leave the server and restored when they join again.

## Supported Versions

This plugin supports Minecraft versions **1.20.4 to 1.21.9** as confirmed by [PaperMC](https://papermc.io/downloads/all?project=paper).

### Building for Different Versions

The project includes Maven profiles for building against different Minecraft versions:

```bash
# Build for Minecraft 1.20.4 (Java 17)
mvn package -Pmc-1.20.4

# Build for Minecraft 1.21 (Java 21) - Default
mvn package -Pmc-1.21

# Build for Minecraft 1.21.1 (Java 21)
mvn package -Pmc-1.21.1

# Build for default version (1.21)
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
# =====================================
# PlayerDataSync Configuration
# Compatible with Minecraft 1.20.4 - 1.21.8
# =====================================

# Server Configuration
server:
  id: default  # Unique identifier for this server instance

database:
  type: mysql # Available options: mysql, sqlite, postgresql
  
  # MySQL Database Configuration
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    user: root
    password: password
    ssl: false
    connection_timeout: 5000 # milliseconds
    max_connections: 10
    
  # SQLite Database Configuration  
  sqlite:
    file: plugins/PlayerDataSync/playerdata.db
    
  # PostgreSQL Database Configuration (experimental)
  postgresql:
    host: localhost
    port: 5432
    database: minecraft
    user: postgres
    password: password
    ssl: false

# Player Data Synchronization Settings
sync:
  # Basic Player Data
  coordinates: true      # Player's current coordinates
  position: true         # Player's position (world, x, y, z, yaw, pitch)
  xp: true              # Experience points and levels
  gamemode: true        # Current gamemode
  
  # Inventory and Storage
  inventory: true       # Main inventory contents
  enderchest: true      # Ender chest contents
  armor: true           # Equipped armor pieces
  offhand: true         # Offhand item
  
  # Player Status
  health: true          # Current health
  hunger: true          # Hunger and saturation
  effects: true         # Active potion effects
  
  # Progress and Achievements
  achievements: true    # Player advancements/achievements (WARNING: May cause lag with 1000+ achievements)
  statistics: true      # Player statistics (blocks broken, distance traveled, etc.)
  
  # Advanced Features
  attributes: true      # Player attributes (max health, speed, etc.)
  permissions: false    # Sync permissions (requires LuckPerms integration)
  economy: false        # Sync economy balance (requires Vault)

# Automatic Save Configuration
autosave:
  enabled: true
  interval: 1           # seconds between automatic saves, 0 to disable
  on_world_change: true # save when player changes world
  on_death: true        # save when player dies
  async: true           # perform saves asynchronously

# Data Management
data_management:
  cleanup:
    enabled: false      # automatically clean old player data
    days_inactive: 90   # remove data for players inactive for X days
  backup:
    enabled: true       # create backups of player data
    interval: 1440      # backup interval in minutes (1440 = daily)
    keep_backups: 7     # number of backups to keep
  validation:
    enabled: true       # validate data before saving/loading
    strict_mode: false  # strict validation (may cause issues with custom items)

# Performance Settings
performance:
  batch_size: 50        # number of players to process in one batch
  cache_size: 100       # number of player data entries to cache
  cache_ttl: 300000     # cache time-to-live in milliseconds (5 minutes)
  cache_compression: true # enable cache compression for memory optimization
  connection_pooling: true # use connection pooling for better performance
  async_loading: true   # load player data asynchronously on join
  disable_achievement_sync_on_large_amounts: true # disable achievement sync if more than 500 achievements exist
  achievement_batch_size: 50 # number of achievements to process in one batch to prevent lag
  achievement_timeout_ms: 5000 # timeout for achievement serialization to prevent server freeze (milliseconds)
  max_achievements_per_player: 1000 # hard limit to prevent infinite loops

# Compatibility Settings
compatibility:
  safe_attribute_sync: true  # use reflection-based attribute syncing for better version compatibility
  disable_attributes_on_error: false # automatically disable attribute sync if errors occur
  version_check: true        # perform version compatibility checks on startup
  legacy_1_20_support: true # enable additional compatibility features for Minecraft 1.20.x
  modern_1_21_support: true # enable additional compatibility features for Minecraft 1.21.x
  disable_achievements_on_critical_error: true # automatically disable achievement sync on critical errors to prevent server freeze

# Security Settings
security:
  encrypt_data: false   # encrypt sensitive data in database
  hash_uuids: false     # hash player UUIDs for privacy
  audit_log: true       # log all data operations

# Integration Settings
integrations:
  bungeecord: false     # enable BungeeCord support
  luckperms: false      # enable LuckPerms integration
  vault: false          # enable Vault integration for economy
  placeholderapi: false # enable PlaceholderAPI support

# Message Configuration
messages:
  enabled: true         # enable player messages
  language: en          # default language (en, de, fr, es, etc.)
  prefix: "&8[&bPDS&8]" # message prefix
  colors: true          # enable color codes in messages
  
# Logging and Debugging
logging:
  level: INFO           # Log level: DEBUG, INFO, WARN, ERROR
  log_database: false   # log database operations
  log_performance: false # log performance metrics
  debug_mode: false     # enable debug mode for troubleshooting

# Update Checker
update_checker:
  enabled: true         # check for plugin updates
  notify_ops: true      # notify operators about updates
  auto_download: false  # automatically download updates (not recommended)
  timeout: 10000        # connection timeout in milliseconds

# Metrics and Analytics
metrics:
  bstats: true          # Enable bStats metrics collection
  custom_metrics: true  # Enable custom plugin metrics
```

`metrics` controls whether anonymous usage statistics are sent to
[bStats](https://bstats.org/). Set it to `false` if you prefer to
opt out of metrics collection.

`autosave.interval` controls how often (in seconds) the plugin saves all online
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
