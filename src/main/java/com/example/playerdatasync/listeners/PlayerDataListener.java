package com.example.playerdatasync.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.example.playerdatasync.core.PlayerDataSync;
import com.example.playerdatasync.database.DatabaseManager;
import com.example.playerdatasync.managers.AdvancementSyncManager;
import com.example.playerdatasync.managers.MessageManager;

public class PlayerDataListener implements Listener {
    private final PlayerDataSync plugin;
    private final DatabaseManager dbManager;
    private final MessageManager messageManager;

    public PlayerDataListener(PlayerDataSync plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.messageManager = plugin.getMessageManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.getConfigManager() != null && plugin.getConfigManager().shouldShowSyncMessages() 
            && player.hasPermission("playerdatasync.message.show.loading")) {
            player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("loading"));
        }

        // Load data almost immediately after join to minimize empty inventories during server switches
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                dbManager.loadPlayer(player);
                if (player.isOnline() && plugin.getConfigManager() != null 
                    && plugin.getConfigManager().shouldShowSyncMessages() 
                    && player.hasPermission("playerdatasync.message.show.loaded")) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("loaded")));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading data for " + player.getName() + ": " + e.getMessage());
                if (player.isOnline() && plugin.getConfigManager() != null 
                    && plugin.getConfigManager().shouldShowSyncMessages()) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("load_failed")));
                }
            }
        }, 1L);

        AdvancementSyncManager advancementSyncManager = plugin.getAdvancementSyncManager();
        if (advancementSyncManager != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> advancementSyncManager.handlePlayerJoin(player), 2L);
        }
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
            boolean saved = dbManager.savePlayer(player);
            long endTime = System.currentTimeMillis();

            // Log slow saves for performance monitoring
            if (saved && endTime - startTime > 1000) { // More than 1 second
                plugin.getLogger().warning("Slow save detected for " + player.getName() +
                    ": " + (endTime - startTime) + "ms");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save data for " + player.getName() + ": " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
        }

        AdvancementSyncManager advancementSyncManager = plugin.getAdvancementSyncManager();
        if (advancementSyncManager != null) {
            advancementSyncManager.handlePlayerQuit(player);
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
        
        // Fix for Issue #41: Potion Effect on Death
        // Save player data BEFORE death effects are cleared
        // This ensures potion effects are saved, but they won't be restored on respawn
        // because Minecraft clears them on death
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Capture data before death clears effects
                dbManager.savePlayer(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save data for " + player.getName() + 
                    " on death: " + e.getMessage());
            }
        });
        
        // Schedule a delayed save after respawn to ensure death state is saved
        // This prevents potion effects from being restored after death
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                // Clear any potion effects that might have been restored
                // This ensures death clears effects as expected
                player.getActivePotionEffects().clear();
            }
        }, 1L);
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        // Save data when player is kicked (might be server switch)
        if (!plugin.getConfig().getBoolean("autosave.on_kick", true)) return;
        
        Player player = event.getPlayer();
        
        plugin.logDebug("Player " + player.getName() + " was kicked, saving data");
        
        try {
            long startTime = System.currentTimeMillis();
            dbManager.savePlayer(player);
            long endTime = System.currentTimeMillis();
            
            plugin.logDebug("Saved data for kicked player " + player.getName() + 
                " in " + (endTime - startTime) + "ms");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save data for kicked player " + player.getName() + ": " + e.getMessage());
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Check if this is a server-to-server teleport (BungeeCord)
        if (!plugin.getConfig().getBoolean("autosave.on_server_switch", true)) return;
        
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Player player = event.getPlayer();
            
                // Check if the teleport is to a different server (BungeeCord behavior)
                if (event.getTo() != null && event.getTo().getWorld() != null) {
                    plugin.logDebug("Player " + player.getName() + " teleported via plugin, saving data");
                
                // Save data before teleport
                try {
                    long startTime = System.currentTimeMillis();
                    dbManager.savePlayer(player);
                    long endTime = System.currentTimeMillis();
                    
                    plugin.logDebug("Saved data for teleporting player " + player.getName() + 
                        " in " + (endTime - startTime) + "ms");
                    
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to save data for teleporting player " + player.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handle player respawn - Respawn to Lobby feature
     * Sends player to lobby server after death if enabled
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Check if respawn to lobby is enabled
        if (!plugin.getConfig().getBoolean("respawn_to_lobby.enabled", false)) {
            return;
        }
        
        // Check if BungeeCord integration is enabled (required for server switching)
        if (!plugin.isBungeecordIntegrationEnabled()) {
            plugin.getLogger().warning("Respawn to lobby is enabled but BungeeCord integration is disabled. " +
                "Please enable BungeeCord integration in config.yml");
            return;
        }
        
        Player player = event.getPlayer();
        String lobbyServer = plugin.getConfig().getString("respawn_to_lobby.server", "lobby");
        
        // Check if current server is already the lobby server
        String currentServerId = plugin.getConfig().getString("server.id", "default");
        if (currentServerId.equalsIgnoreCase(lobbyServer)) {
            plugin.logDebug("Player " + player.getName() + " is already on lobby server, skipping respawn transfer");
            return;
        }
        
        // Save player data before transferring
        plugin.logDebug("Saving data for " + player.getName() + " before respawn to lobby");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean saved = dbManager.savePlayer(player);
                if (saved) {
                    plugin.logDebug("Data saved for " + player.getName() + " before respawn to lobby");
                } else {
                    plugin.getLogger().warning("Failed to save data for " + player.getName() + " before respawn to lobby");
                }
                
                // Transfer player to lobby server after save completes
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getLogger().info("Transferring " + player.getName() + " to lobby server '" + lobbyServer + "' after respawn");
                        plugin.connectPlayerToServer(player, lobbyServer);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving data for " + player.getName() + " before respawn to lobby: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stack trace:", e);
            }
        });
    }
}
