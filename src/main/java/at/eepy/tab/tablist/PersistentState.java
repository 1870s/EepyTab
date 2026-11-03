package at.eepy.tab.tablist;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Runtime state that survives restarts (data.yml), kept apart from config.yml
 * so toggling it never rewrites the user's configuration.
 */
public final class PersistentState {

    private final Plugin plugin;
    private final File file;
    private final AtomicReference<String> pendingWrite = new AtomicReference<>();
    private volatile boolean maintenance;

    public PersistentState(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            maintenance = yaml.getBoolean("maintenance", false);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read data.yml, using defaults", e);
        }
    }

    public boolean maintenance() {
        return maintenance;
    }

    public void setMaintenance(boolean enabled) {
        this.maintenance = enabled;
        save();
    }

    /** Serialises on the calling thread, writes the file asynchronously. */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("EepyTab runtime data. Managed by the plugin."));
        yaml.set("maintenance", maintenance);
        pendingWrite.set(yaml.saveToString());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::writePending);
    }

    /** Always writes the newest content, so out-of-order async tasks can not restore an old value. */
    private synchronized void writePending() {
        String content = pendingWrite.getAndSet(null);
        if (content == null) {
            return;
        }
        try {
            Files.createDirectories(file.toPath().getParent());
            File temp = new File(file.getParentFile(), "data.yml.tmp");
            Files.writeString(temp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
        }
    }
}
