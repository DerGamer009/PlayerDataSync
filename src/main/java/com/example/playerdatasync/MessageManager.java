package com.example.playerdatasync;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageManager {
    private final PlayerDataSync plugin;
    private FileConfiguration messages;

    public MessageManager(PlayerDataSync plugin) {
        this.plugin = plugin;
    }

	public void load(String language) {
		String normalized = normalizeLanguage(language);

		// Always load English as base defaults (from JAR first, else data folder)
		YamlConfiguration baseEn = new YamlConfiguration();
		InputStream enStream = plugin.getResource("messages_en.yml");
		if (enStream != null) {
			baseEn = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));
		} else {
			File enFile = new File(plugin.getDataFolder(), "messages_en.yml");
			if (enFile.exists()) {
				baseEn = YamlConfiguration.loadConfiguration(enFile);
			}
		}

		// Now try to load the requested language, overlaying on top of English defaults
		YamlConfiguration selected = null;
		File file = new File(plugin.getDataFolder(), "messages_" + normalized + ".yml");
		try {
			if (!file.exists()) {
				plugin.saveResource("messages_" + normalized + ".yml", false);
			}
		} catch (IllegalArgumentException ignored) {
			// Resource not embedded for this language
		}

		if (file.exists()) {
			selected = YamlConfiguration.loadConfiguration(file);
		} else {
			InputStream jarStream = plugin.getResource("messages_" + normalized + ".yml");
			if (jarStream != null) {
				selected = YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
			}
		}

		if (selected == null) {
			// If requested language isn't available, use English directly
			this.messages = baseEn;
			return;
		}

		// Apply English as defaults so missing keys fall back
		selected.setDefaults(baseEn);
		selected.options().copyDefaults(true);
		this.messages = selected;
	}

	public void loadFromConfig() {
		String lang = plugin.getConfig().getString("messages.language", "en");
		load(lang);
	}

	private String normalizeLanguage(String language) {
		if (language == null || language.trim().isEmpty()) return "en";
		String lang = language.trim().toLowerCase().replace('-', '_');
		// Map common locale variants to base language files
		if (lang.startsWith("de")) return "de";
		if (lang.startsWith("en")) return "en";
		return lang;
	}

    public String get(String key) {
        if (messages == null) return key;
        String raw = messages.getString(key, key);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
    
    public String get(String key, String... params) {
        if (messages == null) return key;
        String raw = messages.getString(key, key);
        
        // Replace placeholders with parameters
        for (int i = 0; i < params.length; i++) {
            String placeholder = "{" + i + "}";
            if (raw.contains(placeholder)) {
                raw = raw.replace(placeholder, params[i] != null ? params[i] : "");
            }
        }
        
        // Also support named placeholders for common cases
        if (params.length > 0) {
            raw = raw.replace("{version}", params[0] != null ? params[0] : "");
            raw = raw.replace("{error}", params[0] != null ? params[0] : "");
            raw = raw.replace("{url}", params[0] != null ? params[0] : "");
        }
        
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
