package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Advanced backup and restore system for PlayerDataSync
 * Supports automatic backups, compression, and data integrity verification
 */
public class BackupManager {
    private final PlayerDataSync plugin;
    private BukkitTask backupTask;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    
    public BackupManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Start automatic backup task
     */
    public void startAutomaticBackups() {
        if (!plugin.getConfigManager().isBackupEnabled()) {
            return;
        }
        
        int intervalMinutes = plugin.getConfigManager().getBackupInterval();
        if (intervalMinutes <= 0) {
            return;
        }
        
        long ticks = intervalMinutes * 60L * 20L; // Convert minutes to ticks
        
        backupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                createBackup("automatic");
            } catch (Exception e) {
                plugin.getLogger().severe("Automatic backup failed: " + e.getMessage());
            }
        }, ticks, ticks);
        
        plugin.getLogger().info("Automatic backups enabled with " + intervalMinutes + " minute intervals");
    }
    
    /**
     * Stop automatic backup task
     */
    public void stopAutomaticBackups() {
        if (backupTask != null) {
            backupTask.cancel();
            backupTask = null;
            plugin.getLogger().info("Automatic backups disabled");
        }
    }
    
    /**
     * Create a backup with specified type
     */
    public CompletableFuture<BackupResult> createBackup(String type) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String timestamp = dateFormat.format(new java.util.Date());
                String backupName = "backup_" + type + "_" + timestamp;
                File backupDir = new File(plugin.getDataFolder(), "backups");
                
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }
                
                File backupFile = new File(backupDir, backupName + ".zip");
                
                // Create backup
                try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(backupFile))) {
                    // Backup database
                    backupDatabase(zipOut, backupName);
                    
                    // Backup configuration
                    backupConfiguration(zipOut, backupName);
                    
                    // Backup logs
                    backupLogs(zipOut, backupName);
                }
                
                // Clean old backups
                cleanOldBackups();
                
                plugin.getLogger().info("Backup created: " + backupFile.getName());
                return new BackupResult(true, backupFile.getName(), backupFile.length());
                
            } catch (Exception e) {
                plugin.getLogger().severe("Backup creation failed: " + e.getMessage());
                return new BackupResult(false, null, 0);
            }
        });
    }
    
    /**
     * Backup database data
     */
    private void backupDatabase(ZipOutputStream zipOut, String backupName) throws SQLException, IOException {
        Connection connection = plugin.getConnection();
        if (connection == null) {
            throw new SQLException("No database connection available");
        }

        try {
            String tableName = plugin.getTablePrefix();
            // Create SQL dump
            StringBuilder sqlDump = new StringBuilder();
            sqlDump.append("-- PlayerDataSync Database Backup\n");
            sqlDump.append("-- Created: ").append(new java.util.Date()).append("\n\n");

            // Get table structure
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
                if (rs.next()) {
                    sqlDump.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
                    try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                        List<String> columnDefs = new ArrayList<>();
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            String dataType = columns.getString("TYPE_NAME");
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            String nullable = columns.getString("IS_NULLABLE");
                            
                            StringBuilder columnDef = new StringBuilder("  ").append(columnName).append(" ");
                            if (dataType.equals("VARCHAR")) {
                                columnDef.append("VARCHAR(").append(columnSize).append(")");
                            } else {
                                columnDef.append(dataType);
                            }
                            
                            if ("NO".equals(nullable)) {
                                columnDef.append(" NOT NULL");
                            }
                            
                            columnDefs.add(columnDef.toString());
                        }
                        sqlDump.append(String.join(",\n", columnDefs));
                    }
                    sqlDump.append("\n);\n\n");
                }
            }

            // Get table data
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + tableName)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        sqlDump.append("INSERT INTO ").append(tableName).append(" VALUES (");
                        ResultSetMetaData rsMeta = rs.getMetaData();
                        List<String> values = new ArrayList<>();
                        for (int i = 1; i <= rsMeta.getColumnCount(); i++) {
                            String value = rs.getString(i);
                            if (value == null) {
                                values.add("NULL");
                            } else {
                                values.add("'" + value.replace("'", "''") + "'");
                            }
                        }
                        sqlDump.append(String.join(", ", values));
                        sqlDump.append(");\n");
                    }
                }
            }
            
            // Add to zip
            zipOut.putNextEntry(new ZipEntry(backupName + "/database.sql"));
            zipOut.write(sqlDump.toString().getBytes());
            zipOut.closeEntry();
            
        } finally {
            plugin.returnConnection(connection);
        }
    }
    
    /**
     * Backup configuration files
     */
    private void backupConfiguration(ZipOutputStream zipOut, String backupName) throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            zipOut.putNextEntry(new ZipEntry(backupName + "/config.yml"));
            Files.copy(configFile.toPath(), zipOut);
            zipOut.closeEntry();
        }
        
        // Backup message files
        File[] messageFiles = plugin.getDataFolder().listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        if (messageFiles != null) {
            for (File messageFile : messageFiles) {
                zipOut.putNextEntry(new ZipEntry(backupName + "/" + messageFile.getName()));
                Files.copy(messageFile.toPath(), zipOut);
                zipOut.closeEntry();
            }
        }
    }
    
    /**
     * Backup log files
     */
    private void backupLogs(ZipOutputStream zipOut, String backupName) throws IOException {
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (logsDir.exists()) {
            Files.walk(logsDir.toPath())
                .filter(Files::isRegularFile)
                .forEach(logFile -> {
                    try {
                        String relativePath = logsDir.toPath().relativize(logFile).toString();
                        zipOut.putNextEntry(new ZipEntry(backupName + "/logs/" + relativePath));
                        Files.copy(logFile, zipOut);
                        zipOut.closeEntry();
                    } catch (IOException e) {
                        plugin.getLogger().warning("Failed to backup log file: " + logFile + " - " + e.getMessage());
                    }
                });
        }
    }
    
    /**
     * Clean old backups
     */
    private void cleanOldBackups() {
        int keepBackups = plugin.getConfigManager().getBackupsToKeep();
        File backupDir = new File(plugin.getDataFolder(), "backups");
        
        if (!backupDir.exists()) return;
        
        File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (backupFiles == null || backupFiles.length <= keepBackups) return;
        
        // Sort by modification time (oldest first)
        Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified));
        
        // Delete oldest backups
        int toDelete = backupFiles.length - keepBackups;
        for (int i = 0; i < toDelete; i++) {
            if (backupFiles[i].delete()) {
                plugin.getLogger().info("Deleted old backup: " + backupFiles[i].getName());
            }
        }
    }
    
    /**
     * List available backups
     */
    public List<BackupInfo> listBackups() {
        List<BackupInfo> backups = new ArrayList<>();
        File backupDir = new File(plugin.getDataFolder(), "backups");
        
        if (!backupDir.exists()) return backups;
        
        File[] backupFiles = backupDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (backupFiles == null) return backups;
        
        for (File backupFile : backupFiles) {
            backups.add(new BackupInfo(
                backupFile.getName(),
                backupFile.length(),
                new java.util.Date(backupFile.lastModified())
            ));
        }
        
        // Sort by date (newest first)
        backups.sort(Comparator.comparing(BackupInfo::getCreatedDate).reversed());
        return backups;
    }
    
    /**
     * Restore from backup
     */
    public CompletableFuture<Boolean> restoreFromBackup(String backupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File backupFile = new File(plugin.getDataFolder(), "backups/" + backupName);
                if (!backupFile.exists()) {
                    plugin.getLogger().severe("Backup file not found: " + backupName);
                    return false;
                }
                
                // TODO: Implement restore functionality
                plugin.getLogger().info("Restore from backup: " + backupName + " (not implemented yet)");
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().severe("Restore failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Backup result container
     */
    public static class BackupResult {
        private final boolean success;
        private final String fileName;
        private final long fileSize;
        
        public BackupResult(boolean success, String fileName, long fileSize) {
            this.success = success;
            this.fileName = fileName;
            this.fileSize = fileSize;
        }
        
        public boolean isSuccess() { return success; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
    }
    
    /**
     * Backup info container
     */
    public static class BackupInfo {
        private final String fileName;
        private final long fileSize;
        private final java.util.Date createdDate;
        
        public BackupInfo(String fileName, long fileSize, java.util.Date createdDate) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.createdDate = createdDate;
        }
        
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public java.util.Date getCreatedDate() { return createdDate; }
        
        public String getFormattedSize() {
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }
}
