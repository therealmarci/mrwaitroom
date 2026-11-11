package at.marcelr.mrwaitroom;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Brings an existing config.yml up to the layout of the bundled one: the bundled file (with its comments and order)
 * is taken as template and every value the server owner had set is carried over. The old file is kept as backup.
 * <p>
 * Files are always written as UTF-8 <b>with BOM</b>, so editors like Notepad++, WinSCP or FileZilla
 * recognise the encoding and show umlauts correctly.
 */
public final class ConfigUpdater {

    public static final int CURRENT_VERSION = 5;
    public static final String BOM = "﻿";
    /** Values whose default changed; the new default replaces the old value (none yet). */
    private static final Set<String> REPLACED_KEYS = Set.of();
    /** Keys that were renamed: old path -> new path (the value is carried over; none yet). */
    private static final Map<String, String> RENAMED_KEYS = Map.of();

    private ConfigUpdater() {
    }

    public static void update(MRwaitroom plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }
        try {
            String current = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration existing = new YamlConfiguration();
            existing.loadFromString(stripBom(current));
            int version = existing.getInt("config-version", 1);
            if (version >= CURRENT_VERSION) {
                if (!current.startsWith(BOM)) {
                    // Same content, only add the BOM so editors detect UTF-8.
                    Files.writeString(file.toPath(), BOM + current, StandardCharsets.UTF_8);
                }
                return;
            }

            YamlConfiguration template = new YamlConfiguration();
            try (InputStream in = plugin.getResource("config.yml")) {
                if (in == null) {
                    return;
                }
                template.loadFromString(stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }

            int carried = 0;
            for (String oldKey : existing.getKeys(true)) {
                String key = RENAMED_KEYS.getOrDefault(oldKey, oldKey);
                if (existing.isConfigurationSection(oldKey) || key.equals("config-version")
                        || REPLACED_KEYS.contains(key) || !template.contains(key)) {
                    continue;
                }
                template.set(key, existing.get(oldKey));
                carried++;
            }
            // v4: the wait before going back became a visible countdown of 10 seconds (old default 5).
            if (version < 4 && existing.getInt("return.delay-seconds", 5) == 5) {
                template.set("return.delay-seconds", 10);
            }
            // v5: a fixed restart countdown of 2 minutes instead of the learned length (old default 90).
            if (version < 5 && existing.getInt("return.estimate-seconds", 90) == 90) {
                template.set("return.estimate-seconds", 120);
            }
            template.set("config-version", CURRENT_VERSION);
            // Keep long values on one line instead of folding them.
            template.options().width(10_000);

            File backup = new File(plugin.getDataFolder(), "config.v" + version + ".backup.yml");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file.toPath(), BOM + template.saveToString(), StandardCharsets.UTF_8);
            plugin.getLogger().info("Updated config.yml from version " + version + " to " + CURRENT_VERSION + " ("
                    + carried + " settings carried over, backup: " + backup.getName() + ").");
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not update config.yml: " + e.getMessage());
        }
    }

    /** Loads config.yml (tolerating a BOM) with the bundled config as defaults for missing keys. */
    public static YamlConfiguration load(MRwaitroom plugin) {
        YamlConfiguration config = new YamlConfiguration();
        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            if (file.exists()) {
                config.loadFromString(stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "config.yml is invalid, using defaults: " + e.getMessage());
        }
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.loadFromString(stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                config.setDefaults(defaults);
                // Fill in values missing from the file. Bukkit ignores the defaults whenever code passes its own
                // fallback (getString(path, "...")), so missing keys would otherwise get those fallbacks.
                for (String key : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(key) && !config.isSet(key)) {
                        config.set(key, defaults.get(key));
                    }
                }
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled config: " + e.getMessage());
        }
        return config;
    }

    /** Writes one value into config.yml, keeping comments and layout of the rest (used by /mrwaitroom setup). */
    public static void set(MRwaitroom plugin, String key, Object value) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.options().parseComments(true);
            config.loadFromString(stripBom(Files.readString(file.toPath(), StandardCharsets.UTF_8)));
            config.set(key, value);
            config.options().width(10_000);
            Files.writeString(file.toPath(), BOM + config.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + key + " to config.yml: " + e.getMessage());
        }
    }

    public static String stripBom(String text) {
        return text.startsWith(BOM) ? text.substring(1) : text;
    }
}
