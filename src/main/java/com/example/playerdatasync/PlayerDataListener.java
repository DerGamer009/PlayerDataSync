package com.example.playerdatasync;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> dbManager.loadPlayer(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("playerdatasync.message.show.saving")) {
            player.sendMessage(plugin.getMessageManager().get("prefix") + " " + plugin.getMessageManager().get("saving"));
        }
        // Save data synchronously so the database is updated before the player
        // joins another server. Using an async task here can lead to race
        // conditions when switching servers quickly via BungeeCord or similar
        // proxies, causing recent changes not to be stored in time.
        dbManager.savePlayer(player);
    }
}
