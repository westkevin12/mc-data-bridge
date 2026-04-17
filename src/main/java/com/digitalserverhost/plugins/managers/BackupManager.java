package com.digitalserverhost.plugins.managers;

import com.digitalserverhost.plugins.MCDataBridge;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BackupManager {

    private final MCDataBridge plugin;
    private final DatabaseManager databaseManager;

    public BackupManager(MCDataBridge plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        if (plugin.getConfig().getBoolean("database.backups.enabled", false)) {
            startBackupTask();
        }
    }

    private void startBackupTask() {
        long intervalTicks = plugin.getConfig().getLong("database.backups.interval-hours", 24) * 60 * 60 * 20L;
        new BukkitRunnable() {
            @Override
            public void run() {
                performBackup();
            }
        }.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    public void performBackup() {
        plugin.getLogger().info("Starting automatic database backup...");
        File backupFolder = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.backups.path", "backups/"));
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File backupFile = new File(backupFolder, "backup_" + timeStamp + ".json");

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + databaseManager.getTableName());
             ResultSet resultSet = statement.executeQuery();
             FileWriter writer = new FileWriter(backupFile)) {

            writer.write("[\n");
            boolean first = true;
            while (resultSet.next()) {
                if (!first) writer.write(",\n");
                
                String uuid = resultSet.getString("uuid");
                byte[] data = resultSet.getBytes("data");
                String dataStr = (data != null) ? new String(data, java.nio.charset.StandardCharsets.UTF_8) : "{}";
                
                writer.write("  {\"uuid\": \"" + uuid + "\", \"data\": " + dataStr + "}");
                first = false;
            }
            writer.write("\n]");
            plugin.getLogger().info("Backup completed successfully: " + backupFile.getName());
            
            cleanOldBackups(backupFolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to perform database backup: " + e.getMessage());
        }
    }

    private void cleanOldBackups(File backupFolder) {
        int maxBackups = plugin.getConfig().getInt("database.backups.max-backups", 7);
        File[] files = backupFolder.listFiles((dir, name) -> name.startsWith("backup_") && name.endsWith(".json"));
        if (files != null && files.length > maxBackups) {
            // Simple cleanup logic: delete the oldest ones based on filename/date
            java.util.Arrays.sort(files, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            for (int i = 0; i < files.length - maxBackups; i++) {
                if (files[i].delete()) {
                    plugin.getLogger().info("Deleted old backup: " + files[i].getName());
                }
            }
        }
    }
}
