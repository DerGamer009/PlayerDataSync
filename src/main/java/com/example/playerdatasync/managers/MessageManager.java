package com.example.playerdatasync.managers;

import com.example.playerdatasync.core.PlayerDataSync;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

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

    /**
     * Loads a language file, falling back to English defaults for missing keys.
     *
     * @param language Language code (e.g., "en", "de")
     */
    public void load(String language) {
        String lang = normalizeLanguage(language);

        FileConfiguration baseEn = loadLanguageFile("en"); // always load English defaults
        FileConfiguration selected = loadLanguageFile(lang);

        if (selected == null) {
            // If requested language is unavailable, use English directly
            this.messages = baseEn;
            return;
        }

        // Overlay English defaults so missing keys fall back
        selected.setDefaults(baseEn);
        selected.options().copyDefaults(true);
        this.messages = selected;
    }

    /**
     * Loads the language specified in config.
     */
    public void loadFromConfig() {
        String lang = plugin.getConfig().getString("messages.language", "en");
        load(lang);
    }

    /**
     * Returns a normalized language code (e.g., "de", "en").
     */
    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank())
            return "en";
        String lang = language.trim().toLowerCase().replace('-', '_');
        if (lang.startsWith("de"))
            return "de";
        if (lang.startsWith("en"))
            return "en";
        return lang;
    }

    private FileConfiguration loadLanguageFile(String lang) {
        String fileName = "messages_" + lang + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);

        // If it doesn't exist in the plugin folder, try saving it from the JAR
        if (!file.exists()) {
            if (plugin.getResource(fileName) != null) {
                plugin.saveResource(fileName, false);
            }
        }

        // Load the file from the plugin folder if it exists
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }

        // Fallback to loading directly from JAR if it couldn't be saved for some reason
        InputStream jarStream = plugin.getResource(fileName);
        if (jarStream != null) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));
        }

        return null;
    }

    /**
     * Gets a message by key, applying color codes.
     *
     * @param key Message key
     * @return Formatted message or key if missing
     */
    public String get(String key) {
        if (messages == null)
            return key;
        String raw = messages.getString(key, key);
        return formatColors(raw);
    }

    /**
     * Gets a message by key and replaces placeholders.
     * Positional placeholders: {0}, {1}, ...
     * Named placeholders: {version}, {url}, {error} (first param used)
     *
     * @param key    Message key
     * @param params Placeholder values
     * @return Formatted message
     */
    public String get(String key, String... params) {
        if (messages == null)
            return key;
        String raw = messages.getString(key, key);

        // Replace positional placeholders {0}, {1}, ...
        for (int i = 0; i < params.length; i++) {
            raw = raw.replace("{" + i + "}", params[i] != null ? params[i] : "");
        }

        // Replace common named placeholders with first parameter if available
        if (params.length > 0 && params[0] != null) {
            raw = raw.replace("{version}", params[0])
                    .replace("{url}", params[0])
                    .replace("{error}", params[0]);
        }

        return formatColors(raw);
    }

    /**
     * Translates standard color codes and hex color codes (&#RRGGBB).
     */
    private String formatColors(String message) {
        if (message == null)
            return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                replacement.append('&').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
