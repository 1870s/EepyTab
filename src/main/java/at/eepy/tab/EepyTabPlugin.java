package at.eepy.tab;

import at.eepy.tab.animation.AnimationEngine;
import at.eepy.tab.command.EepyTabCommand;
import at.eepy.tab.config.ConfigParser;
import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.hook.LuckPermsHook;
import at.eepy.tab.hook.PlaceholderApiHook;
import at.eepy.tab.listener.PlayerListener;
import at.eepy.tab.rank.RankService;
import at.eepy.tab.render.PlaceholderService;
import at.eepy.tab.render.TabRenderer;
import at.eepy.tab.tablist.PersistentState;
import at.eepy.tab.tablist.TabService;
import at.eepy.tab.tablist.TabSetup;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * EepyTab entry point. Wires configuration, integrations, the tab service and
 * the command together and owns their lifecycle.
 */
public final class EepyTabPlugin extends JavaPlugin {

    /** Plugins known to manage header/footer or player names themselves. */
    private static final List<String> OTHER_TAB_PLUGINS = List.of(
            "TAB", "TabList", "AnimatedTab", "BetterTab", "SimpleTabList", "AdvancedTabList", "TabListPro", "CMI");

    private PersistentState persistentState;
    private @Nullable LuckPermsHook luckPerms;
    private @Nullable PlaceholderApiHook placeholderApi;
    private volatile @Nullable TabService tabService;
    private List<String> configWarnings = List.of();

    /** Result of a configuration reload, reported back to the command sender. */
    public record ReloadResult(boolean success, List<String> warnings, @Nullable String error, long millis) {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        persistentState = new PersistentState(this);
        persistentState.load();

        PluginConfig config;
        try {
            config = loadConfig();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "config.yml could not be read, using the bundled defaults until it is fixed", e);
            config = parse(bundledDefaults());
        }

        hookIntegrations();
        TabService service = new TabService(this, persistentState, createSetup(config));
        this.tabService = service;
        updateExpansion(config);

        getServer().getPluginManager().registerEvents(new PlayerListener(service), this);
        EepyTabCommand command = new EepyTabCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command.build(), "EepyTab tab list commands", List.of("etab")));

        service.start();
        logWarnings();
        // Run once the server is fully started so plugins loading after us are seen too.
        getServer().getScheduler().runTask(this, this::warnAboutOtherTabPlugins);
        getLogger().info("Enabled. LuckPerms: " + (luckPerms != null ? "hooked" : "not found")
                + ", PlaceholderAPI: " + (placeholderApi != null ? "hooked" : "not found"));
    }

    @Override
    public void onDisable() {
        TabService service = tabService;
        if (service != null) {
            service.shutdown();
            service.setup().ranks().unregisterPermissions(getServer().getPluginManager());
            tabService = null;
        }
        if (placeholderApi != null) {
            placeholderApi.unregisterExpansion();
            placeholderApi = null;
        }
        if (luckPerms != null) {
            luckPerms.close();
            luckPerms = null;
        }
    }

    /** Reloads config.yml; on failure the active configuration is kept. */
    public ReloadResult reload() {
        long started = System.nanoTime();
        TabService service = Objects.requireNonNull(tabService, "plugin is not enabled");
        PluginConfig config;
        try {
            config = loadConfig();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.WARNING, "Reload failed, keeping the previous configuration", e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : firstLine(e.getMessage());
            return new ReloadResult(false, List.of(), reason, elapsedMillis(started));
        }
        service.setup().ranks().unregisterPermissions(getServer().getPluginManager());
        service.apply(createSetup(config));
        updateExpansion(config);
        logWarnings();
        return new ReloadResult(true, configWarnings, null, elapsedMillis(started));
    }

    public TabService tabService() {
        return Objects.requireNonNull(tabService, "plugin is not enabled");
    }

    public @Nullable LuckPermsHook luckPerms() {
        return luckPerms;
    }

    public @Nullable PlaceholderApiHook placeholderApi() {
        return placeholderApi;
    }

    public List<String> configWarnings() {
        return configWarnings;
    }

    public List<String> detectOtherTabPlugins() {
        PluginManager pluginManager = getServer().getPluginManager();
        return OTHER_TAB_PLUGINS.stream()
                .map(pluginManager::getPlugin)
                .filter(Objects::nonNull)
                .filter(Plugin::isEnabled)
                .map(Plugin::getName)
                .toList();
    }

    // ── internals ──────────────────────────────────────────────────────────

    private PluginConfig loadConfig() throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), "config.yml"));
        // Missing keys fall back to the bundled defaults, so older configs keep working after updates.
        yaml.setDefaults(bundledDefaults());
        return parse(yaml);
    }

    private PluginConfig parse(YamlConfiguration yaml) {
        ConfigParser parser = new ConfigParser();
        PluginConfig config = parser.parse(yaml);
        configWarnings = parser.warnings();
        return config;
    }

    private YamlConfiguration bundledDefaults() {
        try (InputStream in = Objects.requireNonNull(getResource("config.yml"), "config.yml missing in jar")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the bundled config.yml", e);
        }
    }

    private TabSetup createSetup(PluginConfig config) {
        AnimationEngine animations = new AnimationEngine(config.animations(), config.palette(), config.separator());
        PlaceholderService placeholders = new PlaceholderService(config);
        TabRenderer renderer = new TabRenderer(config.integrations(), placeholderApi, getLogger());
        RankService ranks = new RankService(config.ranks(),
                config.integrations().luckPerms() ? luckPerms : null,
                animations::expandStatic, placeholders.styleTags());
        ranks.registerPermissions(getServer().getPluginManager());
        String listTemplate = animations.expandStatic(config.playerList().format());
        Component vanishMarker = MiniMessage.miniMessage()
                .deserialize(animations.expandStatic(config.vanish().listMarker()), placeholders.styleTags());
        return new TabSetup(config, animations, placeholders, renderer, ranks, listTemplate, vanishMarker);
    }

    private void hookIntegrations() {
        PluginManager pluginManager = getServer().getPluginManager();
        if (pluginManager.isPluginEnabled("LuckPerms")) {
            try {
                luckPerms = new LuckPermsHook(this,
                        uuid -> {
                            TabService service = tabService;
                            if (service != null) service.markRankDirty(uuid);
                        },
                        () -> {
                            TabService service = tabService;
                            if (service != null) service.markAllRanksDirty();
                        });
            } catch (LinkageError | IllegalStateException e) {
                getLogger().log(Level.WARNING, "LuckPerms is installed but its API could not be loaded", e);
            }
        }
        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            try {
                placeholderApi = new PlaceholderApiHook();
            } catch (LinkageError e) {
                getLogger().log(Level.WARNING, "PlaceholderAPI is installed but its API could not be loaded", e);
            }
        }
    }

    private void updateExpansion(PluginConfig config) {
        if (placeholderApi == null) {
            return;
        }
        PluginConfig.Integrations integrations = config.integrations();
        if (integrations.placeholderApi() && integrations.registerExpansion()) {
            placeholderApi.registerExpansion(tabService(), getPluginMeta().getVersion());
        } else {
            placeholderApi.unregisterExpansion();
        }
    }

    private void warnAboutOtherTabPlugins() {
        TabService service = tabService;
        if (service == null || !service.setup().config().compatibility().warnAboutOtherTabPlugins()) {
            return;
        }
        for (String name : detectOtherTabPlugins()) {
            getLogger().warning(name + " is installed and may also manage the tab list. Disable its tab list "
                    + "features (or set player-list.enabled: false here) to avoid flickering.");
        }
    }

    private void logWarnings() {
        configWarnings.forEach(warning -> getLogger().warning("config.yml: " + warning));
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private static String firstLine(String message) {
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
