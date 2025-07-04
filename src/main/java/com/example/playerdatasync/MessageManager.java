package com.example.playerdatasync;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {
    private final PlayerDataSync plugin;
    private FileConfiguration messages;

    public MessageManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        plugin.saveResource("messages_" + language + ".yml", false);
        File file = new File(plugin.getDataFolder(), "messages_" + language + ".yml");
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String key) {
        if (messages == null) return key;
        return messages.getString(key, key);
    }
}
