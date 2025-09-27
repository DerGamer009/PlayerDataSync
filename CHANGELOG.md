# Changelog

All notable changes to PlayerDataSync will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
