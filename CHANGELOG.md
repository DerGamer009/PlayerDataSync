# Changelog

All notable changes to PlayerDataSync will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.5-SNAPSHOT] - 2025-01-05

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
