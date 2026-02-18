# PlayerDataSync

A comprehensive Bukkit/Spigot plugin for Minecraft **1.8 to 1.26.2** that synchronizes player data across multiple servers using MySQL, SQLite, or PostgreSQL databases. Perfect for multi-server networks with BungeeCord or Velocity.

Player inventories, experience, health, achievements, economy balance, and more are stored in a shared database whenever they leave a server and restored when they join again.

## ✨ Features

- **Full Player Data Sync**: Inventory, EnderChest, Armor, Offhand, Experience, Health, Hunger, Potion Effects
- **Multi-Server Support**: Shared database across multiple servers (BungeeCord/Velocity compatible)
- **Economy Integration**: Vault economy balance synchronization
- **Achievements & Statistics**: Sync player advancements and statistics
- **Respawn to Lobby**: Automatically send players to lobby server after death/respawn
- **Version Compatibility**: Supports Minecraft 1.8 - 1.26.2 with automatic feature detection
- **Performance Optimized**: Async operations, connection pooling, batch processing
- **Database Support**: MySQL, SQLite, PostgreSQL

## 📋 Supported Versions

This plugin supports Minecraft versions **1.8 to 1.26.2**. Some features are automatically disabled on older versions:
- **Offhand sync**: Requires 1.9+
- **Attribute sync**: Requires 1.9+
- **Advancement sync**: Requires 1.12+

The plugin automatically detects the server version and enables/disables features accordingly.

## 🚀 Quick Start

1. **Download** the latest release from the [releases page](https://github.com/DerGamer009/PlayerDataSync/releases)
2. **Place** the jar file in your server's `plugins/` directory
3. **Configure** the database connection in `plugins/PlayerDataSync/config.yml`
4. **Restart** your server
5. **Enable** BungeeCord/Velocity integration if using a proxy network

## ⚙️ Configuration

The `config.yml` file contains all configuration options. Here's a basic setup:

```yaml
# Server Configuration
server:
  id: default  # Unique identifier for this server instance

# Database Configuration
database:
  type: mysql # Available options: mysql, sqlite, postgresql
  mysql:
    host: localhost
    port: 3306
    database: minecraft
    user: root
    password: password
    ssl: false
    max_connections: 10

# Player Data Synchronization Settings
sync:
  coordinates: true      # Player's current coordinates
  position: true         # Player's position (world, x, y, z, yaw, pitch)
  xp: true              # Experience points and levels
  gamemode: true        # Current gamemode
  inventory: true       # Main inventory contents
  enderchest: true      # Ender chest contents
  armor: true           # Equipped armor pieces
  offhand: true         # Offhand item
  health: true          # Current health
  hunger: true          # Hunger and saturation
  effects: true         # Active potion effects
  achievements: true    # Player advancements/achievements
  statistics: true      # Player statistics
  attributes: true      # Player attributes (max health, speed, etc.)
  economy: true         # Sync economy balance (requires Vault)

# Automatic Save Configuration
autosave:
  enabled: true
  interval: 1           # seconds between automatic saves, 0 to disable
  on_world_change: true # save when player changes world
  on_death: true        # save when player dies
  on_server_switch: true # save when player switches servers (BungeeCord/Velocity)
  on_kick: true         # save when player is kicked
  async: true           # perform saves asynchronously

# Integration Settings
integrations:
  bungeecord: false     # enable BungeeCord/Velocity support
  vault: true           # enable Vault integration for economy
  placeholderapi: false # enable PlaceholderAPI support
  invsee: true          # enable InvSee++ style inventory viewing integration
  openinv: true         # enable OpenInv style inventory viewing integration

# Respawn to Lobby Feature
# Sends players to a lobby server after death/respawn
# Requires BungeeCord or Velocity integration to be enabled
respawn_to_lobby:
  enabled: false        # enable respawn to lobby feature
  server: lobby         # name of the lobby server (must match BungeeCord/Velocity server name)

# Performance Settings
performance:
  connection_pooling: true # use connection pooling for better performance
  async_loading: true   # load player data asynchronously on join
  disable_achievement_sync_on_large_amounts: true # disable achievement sync if more than 1500 achievements exist
  achievement_batch_size: 50 # number of achievements to process in one batch
  achievement_timeout_ms: 5000 # timeout for achievement serialization (milliseconds)
  max_achievements_per_player: 2000 # hard limit to prevent infinite loops

# Message Configuration
messages:
  enabled: true
  show_sync_messages: true  # show sync messages when loading/saving data
  language: en
  prefix: "&8[&bPDS&8]"
  colors: true
```

### Respawn to Lobby Feature

The Respawn to Lobby feature automatically sends players to a designated lobby server after they die and respawn. This is perfect for game servers where players should return to a hub after death.

**Requirements:**
- BungeeCord or Velocity integration must be enabled
- The lobby server name must match the server name in your proxy configuration

**Configuration:**
```yaml
respawn_to_lobby:
  enabled: true         # Enable the feature
  server: lobby         # Server name from your proxy config
```

## 🔨 Building

This project uses Maven. To build the plugin:

```bash
# Clone the repository
git clone https://github.com/DerGamer009/PlayerDataSync.git
cd PlayerDataSync

# Build for default version (1.21)
mvn clean package

# Build for specific Minecraft version
mvn clean package -Pmc-1.8    # Minecraft 1.8 (Java 8)
mvn clean package -Pmc-1.9    # Minecraft 1.9-1.16 (Java 8)
mvn clean package -Pmc-1.17   # Minecraft 1.17 (Java 16)
mvn clean package -Pmc-1.18   # Minecraft 1.18-1.20 (Java 17)
mvn clean package -Pmc-1.21   # Minecraft 1.26+ (Java 21)
```

The resulting jar file will be in the `target/` directory.

The build process uses Maven Shade plugin to bundle required dependencies directly into the final jar, so no additional libraries need to be installed on the server.

## 🔧 Multi-Server Setup (BungeeCord/Velocity)

To use PlayerDataSync across multiple servers:

1. **Configure the same database** on all servers
2. **Enable BungeeCord/Velocity integration** in `config.yml`:
   ```yaml
   integrations:
     bungeecord: true
   ```
3. **Set unique server IDs** for each server (optional, defaults to "default"):
   ```yaml
   server:
     id: survival    # Different ID for each server
   ```
4. **Enable server switching autosave**:
   ```yaml
   autosave:
     on_server_switch: true
   ```

The plugin will automatically save player data when they switch servers and restore it when they join a new server.

## ⚠️ Performance Considerations

### Achievement Synchronization

If you experience server freezing or lag when players join, it may be caused by achievement synchronization. The plugin includes automatic protection, but you should:

1. **Set `performance.disable_achievement_sync_on_large_amounts: true`** in config.yml
2. **Consider setting `sync.achievements: false`** if problems persist
3. **Monitor server logs** for timeout warnings

For servers with many achievements (1500+), the plugin automatically:
- Disables sync if too many achievements exist
- Processes achievements in batches
- Loads asynchronously to avoid blocking the main thread

### Recommended Performance Settings

```yaml
performance:
  connection_pooling: true
  async_loading: true
  disable_achievement_sync_on_large_amounts: true
  achievement_batch_size: 50
  achievement_timeout_ms: 5000
  max_achievements_per_player: 2000
```

### Disabling Achievement Sync

If you experience performance issues, you can disable achievement synchronization entirely:

```yaml
sync:
  achievements: false  # Disable achievement sync to prevent lag
```

## 🐛 Known Issues & Fixes

### Fixed Issues

- ✅ **Issue #45 - XP Sync**: Fixed experience synchronization not working across versions 1.8-1.26.2
- ✅ **Issue #46 - Vault Balance de-sync**: Fixed economy balance not being saved on server shutdown

### Troubleshooting

**Server Freezing:**
1. Set `sync.achievements: false` in config.yml
2. Enable performance settings (see above)
3. Check server logs for timeout warnings

**Economy Balance Not Syncing:**
1. Ensure Vault and an economy plugin are installed
2. Check that `sync.economy: true` in config.yml
3. Verify Vault integration is enabled: `integrations.vault: true`

**Players Not Switching Servers:**
1. Enable BungeeCord/Velocity integration: `integrations.bungeecord: true`
2. Verify server names match your proxy configuration
3. Check that `autosave.on_server_switch: true` is enabled

**Data Not Syncing:**
1. Verify database connection settings
2. Check server logs for database errors
3. Ensure all servers use the same database
4. Verify server IDs are set correctly (if using multiple servers)

## 📝 Version Compatibility

### Tested Versions
- ✅ **Minecraft 1.8 - 1.26.2**: Full compatibility with automatic feature detection
- ✅ **Paper 1.20.4 - 1.26.2**: Full compatibility
- ✅ **Spigot 1.8 - 1.26.2**: Full compatibility

### Compatibility Settings

```yaml
compatibility:
  safe_attribute_sync: true      # Use reflection-based attribute syncing
  disable_attributes_on_error: false # Auto-disable attributes if errors occur
  version_check: true            # Perform version compatibility checks on startup
```

The plugin automatically detects the server version and enables/disables features accordingly.

## 🔐 Permissions

The plugin uses the following permissions:

- `playerdatasync.*` - All permissions
- `playerdatasync.message.show.loading` - Show loading messages
- `playerdatasync.message.show.loaded` - Show loaded messages
- `playerdatasync.message.show.saving` - Show saving messages
- `playerdatasync.message.show.errors` - Show error messages

## 📊 Metrics

The plugin uses [bStats](https://bstats.org/) to collect anonymous usage statistics. You can disable this in the config:

```yaml
metrics:
  bstats: false  # Disable bStats metrics collection
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🔗 Links

- **GitHub**: https://github.com/DerGamer009/PlayerDataSync
- **Issues**: https://github.com/DerGamer009/PlayerDataSync/issues
- **Releases**: https://github.com/DerGamer009/PlayerDataSync/releases

## 📚 Changelog

See [CHANGELOG.md](CHANGELOG.md) for a detailed list of changes.

## 💡 Features in Detail

### Experience & Level Sync
- Reliable XP synchronization using `giveExp()` method
- Works across all Minecraft versions (1.8-1.26.2)
- Automatic verification and correction if XP doesn't match

### Economy Sync (Vault)
- Synchronizes economy balance across servers
- Requires Vault and an economy plugin (e.g., EssentialsX, CMI)
- Ensures balance is saved on server shutdown

### Inventory Sync
- Full inventory synchronization
- EnderChest support
- Armor and offhand items
- Client synchronization after loading

### Respawn to Lobby
- Automatically sends players to lobby server after death
- Uses BungeeCord/Velocity server switching
- Saves player data before transfer
- Smart detection to prevent unnecessary transfers

### Database Support
- **MySQL**: Full support with connection pooling
- **SQLite**: File-based database for single-server setups
- **PostgreSQL**: Experimental support

## 🆘 Support

If you encounter any issues:
1. Check the [Issues](https://github.com/DerGamer009/PlayerDataSync/issues) page
2. Review server logs for error messages
3. Verify your configuration matches the examples
4. Create a new issue with details about your problem

---

**Made with ❤️ for the Minecraft community**
