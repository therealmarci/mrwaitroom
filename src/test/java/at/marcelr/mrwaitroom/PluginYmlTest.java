package at.marcelr.mrwaitroom;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** plugin.yml must load - Paper refuses the whole plugin (also an update in plugins/update/) otherwise. */
class PluginYmlTest {

    @Test
    void permissionsAreWellFormed() throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        // Permission names contain dots - they are keys, not paths.
        yml.options().pathSeparator('/');
        try (InputStream in = PluginYmlTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(in, "plugin.yml");
            yml.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        ConfigurationSection permissions = yml.getConfigurationSection("permissions");
        assertNotNull(permissions, "permissions");
        for (String name : permissions.getKeys(false)) {
            ConfigurationSection p = permissions.getConfigurationSection(name);
            assertNotNull(p, name + " is not a section");
            assertTrue(p.isString("description"), name + ": description");
            ConfigurationSection children = p.getConfigurationSection("children");
            if (children != null) {
                for (String child : children.getKeys(false)) {
                    assertTrue(children.isBoolean(child), name + " -> " + child + " must be true/false");
                    assertTrue(permissions.contains(child) || !child.startsWith(yml.getString("name", "").toLowerCase()),
                            name + " -> " + child + " is not declared");
                }
            }
        }
        assertEquals(yml.getString("name"), yml.getString("name").trim());
    }
}
