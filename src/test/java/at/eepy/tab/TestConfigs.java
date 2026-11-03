package at.eepy.tab;

import at.eepy.tab.config.ConfigParser;
import at.eepy.tab.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Loads the bundled config.yml the same way the plugin does. */
public final class TestConfigs {

    private TestConfigs() {
    }

    public static YamlConfiguration bundledYaml() {
        try (InputStream in = Objects.requireNonNull(TestConfigs.class.getResourceAsStream("/config.yml"))) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static PluginConfig bundled() {
        return new ConfigParser().parse(bundledYaml());
    }
}
