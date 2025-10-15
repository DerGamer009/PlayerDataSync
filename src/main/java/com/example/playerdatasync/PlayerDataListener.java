package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

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
        plugin.markPlayerDataLoading(player.getUniqueId());

        if (player.hasPermission("playerdatasync.message.show.loading")) {
            player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("loading"));
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CompletableFuture<Boolean> loadFuture;
            try {
                loadFuture = dbManager.loadPlayer(player);
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading data for " + player.getName() + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.markPlayerDataLoaded(player.getUniqueId());
                    if (player.isOnline()) {
                        player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("load_failed"));
                    }
                });
                return;
            }

            loadFuture.whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.markPlayerDataLoaded(player.getUniqueId());

                if (!player.isOnline()) {
                    return;
                }

                if (throwable != null) {
                    plugin.getLogger().severe("Error loading data for " + player.getName() + ": " + throwable.getMessage());
                    player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("load_failed"));
                    return;
                }

                if (Boolean.TRUE.equals(success) && player.hasPermission("playerdatasync.message.show.loaded")) {
                    player.sendMessage(messageManager.get("prefix") + " " + messageManager.get("loaded"));
                }
            }));
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.isPlayerDataLoading(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerAttemptPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        if (plugin.isPlayerDataLoading(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (plugin.isPlayerDataLoading(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.clearPlayerLoadState(player.getUniqueId());

        // Save data synchronously so the database is updated before the player
        // joins another server. Using an async task here can lead to race
        // conditions when switching servers quickly via BungeeCord or similar
        // proxies, causing recent changes not to be stored in time.
        long startTime = System.currentTimeMillis();
        boolean saved = dbManager.savePlayer(player);
        long endTime = System.currentTimeMillis();

        if (!saved) {
            plugin.getLogger().warning("Skipping inventory clear for " + player.getName() + " because saving data failed.");
            return;
        }

        // Log slow saves for performance monitoring
        if (endTime - startTime > 1000) { // More than 1 second
            plugin.getLogger().warning("Slow save detected for " + player.getName() + ": " + (endTime - startTime) + "ms");
        }

        if (plugin.isBungeecordIntegrationEnabled()) {
            player.getInventory().clear();
            player.updateInventory();
        }
    }
    
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!plugin.getConfig().getBoolean("autosave.on_world_change", true)) return;
        
        Player player = event.getPlayer();

        // Save player data asynchronously when changing worlds
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean saved = dbManager.savePlayer(player);
            if (!saved) {
                plugin.getLogger().warning("Failed to save data for " + player.getName() +
                    " on world change: save returned unsuccessful");
            }
        });
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getConfig().getBoolean("autosave.on_death", true)) return;
        
        Player player = event.getEntity();

        // Save player data asynchronously on death
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean saved = dbManager.savePlayer(player);
            if (!saved) {
                plugin.getLogger().warning("Failed to save data for " + player.getName() +
                    " on death: save returned unsuccessful");
            }
        });
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        // Save data when player is kicked (might be server switch)
        if (!plugin.getConfig().getBoolean("autosave.on_kick", true)) return;
        
        Player player = event.getPlayer();
        
        plugin.getLogger().info("DEBUG: Player " + player.getName() + " was kicked, saving data");
        
        long startTime = System.currentTimeMillis();
        boolean saved = dbManager.savePlayer(player);
        long endTime = System.currentTimeMillis();

        if (!saved) {
            plugin.getLogger().severe("Failed to save data for kicked player " + player.getName());
            return;
        }

        plugin.getLogger().info("DEBUG: Saved data for kicked player " + player.getName() +
            " in " + (endTime - startTime) + "ms");
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Check if this is a server-to-server teleport (BungeeCord)
        if (!plugin.getConfig().getBoolean("autosave.on_server_switch", true)) return;
        
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            Player player = event.getPlayer();
            
            // Check if the teleport is to a different server (BungeeCord behavior)
            if (event.getTo() != null && event.getTo().getWorld() != null) {
                plugin.getLogger().info("DEBUG: Player " + player.getName() + " teleported via plugin, saving data");

                long startTime = System.currentTimeMillis();
                boolean saved = dbManager.savePlayer(player);
                long endTime = System.currentTimeMillis();

                if (!saved) {
                    plugin.getLogger().severe("Failed to save data for teleporting player " + player.getName());
                    return;
                }

                plugin.getLogger().info("DEBUG: Saved data for teleporting player " + player.getName() +
                    " in " + (endTime - startTime) + "ms");
            }
        }
    }
}
