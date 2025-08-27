package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerDataListener implements Listener {
    private final PlayerDataSync plugin;
    private final DatabaseManager dbManager;

    public PlayerDataListener(PlayerDataSync plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("playerdatasync.message.show.loading")) {
            player.sendMessage(plugin.getMessageManager().get("prefix") + " " + plugin.getMessageManager().get("loading"));
        }
        
        // Delay loading slightly to ensure player is fully initialized
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                dbManager.loadPlayer(player);
                if (player.isOnline() && player.hasPermission("playerdatasync.message.show.loaded")) {
                    Bukkit.getScheduler().runTask(plugin, () -> 
                        player.sendMessage(plugin.getMessageManager().get("prefix") + " " + plugin.getMessageManager().get("loaded")));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading data for " + player.getName() + ": " + e.getMessage());
                if (player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> 
                        player.sendMessage(plugin.getMessageManager().get("prefix") + " " + plugin.getMessageManager().get("load_failed")));
                }
            }
        }, 20L); // 1 second delay
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Save data synchronously so the database is updated before the player
        // joins another server. Using an async task here can lead to race
        // conditions when switching servers quickly via BungeeCord or similar
        // proxies, causing recent changes not to be stored in time.
        try {
            long startTime = System.currentTimeMillis();
            dbManager.savePlayer(player);
            long endTime = System.currentTimeMillis();
            
            // Log slow saves for performance monitoring
            if (endTime - startTime > 1000) { // More than 1 second
                plugin.getLogger().warning("Slow save detected for " + player.getName() + 
                    ": " + (endTime - startTime) + "ms");
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save data for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!plugin.getConfig().getBoolean("autosave.on_world_change", true)) return;
        
        Player player = event.getPlayer();
        
        // Save player data asynchronously when changing worlds
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dbManager.savePlayer(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save data for " + player.getName() + 
                    " on world change: " + e.getMessage());
            }
        });
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("autosave.on_death", true)) return;
        
        Player player = event.getEntity();
        
        // Save player data asynchronously on death
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                dbManager.savePlayer(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save data for " + player.getName() + 
                    " on death: " + e.getMessage());
            }
        });
    }
}
