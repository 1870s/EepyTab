package at.eepy.tab.config;

import at.eepy.tab.config.PluginConfig.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a YAML configuration into a validated {@link PluginConfig}.
 * <p>
 * Problems that have a sensible fallback (an invalid colour, an unknown layout
 * in a rule, ...) are collected as warnings instead of failing the whole load,
 * so a typo never leaves the server without a tab list.
 */
public final class ConfigParser {

    private static final Pattern TAG_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern ANIMATION_REFERENCE = Pattern.compile("<anim:([A-Za-z0-9_-]+)>");

    /** Tags provided by EepyTab itself; palette entries must not shadow them. */
    private static final Set<String> RESERVED_TAGS = Set.of(
            "flow", "pulse", "anim", "separator", "online", "max", "load_percent", "load_bar", "tps", "tps_color",
            "mspt", "staff_online", "time", "date", "greeting", "player", "displayname", "ping", "ping_color",
            "world", "world_alias", "mode", "rank", "rank_prefix", "rank_suffix", "name_color", "vanish_marker",
            "server_name", "ip", "website", "discord");

    private final List<String> warnings = new ArrayList<>();

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public PluginConfig parse(ConfigurationSection root) {
        warnings.clear();

        Branding branding = new Branding(
                string(root, "branding.server-name", "eepy"),
                string(root, "branding.ip", "mc.eepy.at"),
                string(root, "branding.website", "eepy.at"),
                string(root, "branding.discord", "dsc.gg/muezza"),
                string(root, "branding.mode", "Survival"));

        Map<String, TextColor> palette = parsePalette(root.getConfigurationSection("palette"));
        Map<String, Layout> layouts = parseLayouts(root.getConfigurationSection("layouts"));

        String defaultLayout = string(root, "default-layout", "default");
        if (!layouts.containsKey(defaultLayout)) {
            String fallback = layouts.keySet().iterator().next();
            warn("default-layout '" + defaultLayout + "' does not exist, using '" + fallback + "'");
            defaultLayout = fallback;
        }

        Animations animations = parseAnimations(root);
        checkAnimationReferences(layouts, animations, root);

        PluginConfig.Ranks ranks = parseRanks(root);
        Maintenance maintenance = new Maintenance(
                enumValue(root, "maintenance.position", MaintenancePosition.class, MaintenancePosition.HEADER_BOTTOM),
                root.getStringList("maintenance.lines"));

        return new PluginConfig(
                branding,
                palette,
                string(root, "separator", ""),
                layouts,
                defaultLayout,
                parseLayoutRules(root.getMapList("layout-rules"), layouts.keySet()),
                animations,
                new PlayerList(
                        root.getBoolean("player-list.enabled", true),
                        string(root, "player-list.format", "<rank_prefix><name_color><player></name_color><rank_suffix>"),
                        enumValue(root, "player-list.name-source", NameSource.class, NameSource.NAME),
                        root.getBoolean("player-list.sort-by-rank", true)),
                ranks,
                parsePlaceholders(root),
                new Intervals(
                        positive(root, "update-intervals.header-footer", 5),
                        positive(root, "update-intervals.player-list", 40),
                        positive(root, "update-intervals.server-stats", 20)),
                new Performance(
                        root.getBoolean("performance.skip-unchanged", true),
                        root.getBoolean("performance.spread-updates", true)),
                new Vanish(
                        root.getBoolean("vanish.hide-from-counts", true),
                        string(root, "vanish.list-marker", "")),
                parseWorlds(root.getConfigurationSection("worlds")),
                maintenance,
                new Integrations(
                        root.getBoolean("integrations.luckperms.enabled", true),
                        root.getBoolean("integrations.placeholderapi.enabled", true),
                        enumValue(root, "integrations.placeholderapi.output-format", PlaceholderFormat.class,
                                PlaceholderFormat.LEGACY),
                        string(root, "integrations.placeholderapi.missing-value", ""),
                        root.getBoolean("integrations.placeholderapi.register-expansion", true)),
                new Compatibility(
                        root.getBoolean("compatibility.warn-about-other-tab-plugins", true),
                        root.getBoolean("compatibility.restore-on-disable", true)),
                Math.max(1, root.getInt("preview-duration-seconds", 30)),
                parseMessages(root.getConfigurationSection("messages")));
    }

    private Map<String, TextColor> parsePalette(@Nullable ConfigurationSection section) {
        Map<String, TextColor> palette = new LinkedHashMap<>();
        // Defaults keep built-in tags such as <ping_color> working even with an empty palette.
        palette.put("primary", TextColor.color(0xB98CFF));
        palette.put("secondary", TextColor.color(0x8DB6FF));
        palette.put("highlight", TextColor.color(0xF3EEFF));
        palette.put("text", TextColor.color(0xDCD3F5));
        palette.put("muted", TextColor.color(0x9C93BD));
        palette.put("subtle", TextColor.color(0x6F6890));
        palette.put("line", TextColor.color(0x4B4566));
        palette.put("good", TextColor.color(0x8EE8C0));
        palette.put("warn", TextColor.color(0xFFD48A));
        palette.put("bad", TextColor.color(0xFF8FA3));
        if (section == null) {
            return Map.copyOf(palette);
        }
        for (String key : section.getKeys(false)) {
            String name = key.toLowerCase(Locale.ROOT);
            if (!TAG_NAME.matcher(name).matches()) {
                warn("palette." + key + ": names may only contain a-z, 0-9, _ and -");
                continue;
            }
            if (RESERVED_TAGS.contains(name) || StandardTags.defaults().has(name)) {
                warn("palette." + key + ": '" + name + "' is already a MiniMessage or EepyTab tag");
                continue;
            }
            TextColor color = color(section.getString(key));
            if (color == null) {
                warn("palette." + key + ": '" + section.getString(key) + "' is not a colour (use #RRGGBB)");
                continue;
            }
            palette.put(name, color);
        }
        return Map.copyOf(palette);
    }

    private Map<String, Layout> parseLayouts(@Nullable ConfigurationSection section) {
        Map<String, Layout> layouts = new LinkedHashMap<>();
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection layout = section.getConfigurationSection(id);
                if (layout == null) {
                    warn("layouts." + id + " is not a section");
                    continue;
                }
                layouts.put(id, new Layout(id, layout.getStringList("header"), layout.getStringList("footer")));
            }
        }
        if (layouts.isEmpty()) {
            warn("No layouts configured, using an empty default layout");
            layouts.put("default", new Layout("default", List.of(), List.of()));
        }
        return Collections.unmodifiableMap(layouts);
    }

    private List<LayoutRule> parseLayoutRules(List<Map<?, ?>> raw, Set<String> layoutIds) {
        List<LayoutRule> rules = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<?, ?> entry = raw.get(i);
            String layout = Objects.toString(entry.get("layout"), null);
            if (layout == null || !layoutIds.contains(layout)) {
                warn("layout-rules[" + i + "]: unknown layout '" + layout + "', rule ignored");
                continue;
            }
            Object permission = entry.get("permission");
            rules.add(new LayoutRule(
                    layout,
                    permission == null ? null : permission.toString(),
                    stringSet(entry.get("worlds")),
                    stringSet(entry.get("ranks")),
                    intValue(entry.get("min-online"), 0),
                    intValue(entry.get("max-online"), Integer.MAX_VALUE)));
        }
        return List.copyOf(rules);
    }

    private Animations parseAnimations(ConfigurationSection root) {
        Map<String, TextAnimation> texts = new LinkedHashMap<>();
        ConfigurationSection section = root.getConfigurationSection("animations.texts");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                List<String> frames = section.getStringList(id + ".frames");
                if (frames.isEmpty()) {
                    warn("animations.texts." + id + " has no frames");
                    frames = List.of("");
                }
                double interval = section.getDouble(id + ".interval", 5.0);
                if (interval < 0.5) {
                    warn("animations.texts." + id + ".interval is below 0.5s, using 0.5s");
                    interval = 0.5;
                }
                texts.put(id, new TextAnimation(id, Math.round(interval * 1000), List.copyOf(frames)));
            }
        }
        return new Animations(
                root.getBoolean("animations.enabled", true),
                cycleMillis(root, "animations.gradient-cycle-seconds", 8.0),
                cycleMillis(root, "animations.pulse-cycle-seconds", 4.0),
                Collections.unmodifiableMap(texts));
    }

    private void checkAnimationReferences(Map<String, Layout> layouts, Animations animations, ConfigurationSection root) {
        List<String> templates = new ArrayList<>();
        layouts.values().forEach(layout -> {
            templates.addAll(layout.header());
            templates.addAll(layout.footer());
        });
        templates.addAll(root.getStringList("maintenance.lines"));
        Set<String> missing = new TreeSet<>();
        for (String template : templates) {
            Matcher matcher = ANIMATION_REFERENCE.matcher(template);
            while (matcher.find()) {
                if (!animations.texts().containsKey(matcher.group(1))) {
                    missing.add(matcher.group(1));
                }
            }
        }
        missing.forEach(id -> warn("<anim:" + id + "> is used but animations.texts." + id + " does not exist"));
    }

    private PluginConfig.Ranks parseRanks(ConfigurationSection root) {
        List<RankDefinition> definitions = new ArrayList<>();
        ConfigurationSection list = root.getConfigurationSection("ranks.list");
        if (list != null) {
            for (String id : list.getKeys(false)) {
                ConfigurationSection rank = list.getConfigurationSection(id);
                if (rank == null) {
                    warn("ranks.list." + id + " is not a section");
                    continue;
                }
                String nameColorRaw = rank.getString("name-color");
                TextColor nameColor = color(nameColorRaw);
                if (nameColorRaw != null && !nameColorRaw.isBlank() && nameColor == null) {
                    warn("ranks.list." + id + ".name-color: '" + nameColorRaw + "' is not a colour");
                }
                List<String> groups = rank.getStringList("luckperms-groups");
                definitions.add(new RankDefinition(
                        id,
                        rank.getString("display-name", id),
                        rank.getInt("priority", 0),
                        rank.getString("prefix", ""),
                        rank.getString("suffix", ""),
                        nameColor,
                        rank.getString("permission", "eepytab.rank." + id),
                        (groups.isEmpty() ? List.of(id) : groups).stream().map(g -> g.toLowerCase(Locale.ROOT)).toList(),
                        rank.getBoolean("staff", false)));
            }
        }
        if (definitions.isEmpty()) {
            warn("No ranks configured, using a plain 'member' rank");
            definitions.add(new RankDefinition("member", "Member", 0, "", "", null, "eepytab.rank.member",
                    List.of("default"), false));
        }
        // Highest priority first: the permission fallback relies on this order.
        definitions.sort(Comparator.comparingInt(RankDefinition::priority).reversed());

        String defaultRank = string(root, "ranks.default-rank", "member");
        if (definitions.stream().noneMatch(d -> d.id().equals(defaultRank))) {
            warn("ranks.default-rank '" + defaultRank + "' does not exist, using the lowest rank");
        }
        return new PluginConfig.Ranks(
                defaultRank,
                enumValue(root, "ranks.prefix-source", PrefixSource.class, PrefixSource.AUTO),
                root.getBoolean("ranks.use-luckperms-weight", false),
                List.copyOf(definitions));
    }

    private Placeholders parsePlaceholders(ConfigurationSection root) {
        ZoneId zone;
        String zoneId = string(root, "placeholders.timezone", "Europe/Vienna");
        try {
            zone = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            warn("placeholders.timezone: unknown zone '" + zoneId + "', using the server time zone");
            zone = ZoneId.systemDefault();
        }
        return new Placeholders(
                zone,
                formatter(root, "placeholders.time-format", "HH:mm"),
                formatter(root, "placeholders.date-format", "dd.MM.yyyy"),
                new Greetings(
                        string(root, "placeholders.greetings.morning", "Good morning"),
                        string(root, "placeholders.greetings.afternoon", "Good afternoon"),
                        string(root, "placeholders.greetings.evening", "Good evening"),
                        string(root, "placeholders.greetings.night", "Good night")),
                root.getInt("placeholders.ping.good", 80),
                root.getInt("placeholders.ping.medium", 160),
                root.getDouble("placeholders.tps.good", 19.0),
                root.getDouble("placeholders.tps.medium", 16.0),
                Math.clamp(root.getInt("placeholders.load-bar.length", 12), 1, 60),
                string(root, "placeholders.load-bar.symbol", "|"),
                string(root, "placeholders.unavailable", "–"));
    }

    private Map<String, WorldSettings> parseWorlds(@Nullable ConfigurationSection section) {
        Map<String, WorldSettings> worlds = new HashMap<>();
        if (section == null) {
            return Map.of();
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection world = section.getConfigurationSection(name);
            if (world == null) {
                warn("worlds." + name + " is not a section");
                continue;
            }
            worlds.put(name, new WorldSettings(
                    world.getString("alias", name),
                    world.getString("mode"),
                    world.getBoolean("enabled", true)));
        }
        return Map.copyOf(worlds);
    }

    private Messages parseMessages(@Nullable ConfigurationSection section) {
        Map<String, String> entries = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (!key.equals("prefix") && section.isString(key)) {
                    entries.put(key, section.getString(key));
                }
            }
        }
        return new Messages(section == null ? "" : section.getString("prefix", ""), Map.copyOf(entries));
    }

    // ── helpers ────────────────────────────────────────────────────────────

    static @Nullable TextColor color(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            return TextColor.fromHexString(value);
        }
        return NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
    }

    private String string(ConfigurationSection root, String path, String fallback) {
        String value = root.getString(path);
        return value != null ? value : fallback;
    }

    private int positive(ConfigurationSection root, String path, int fallback) {
        int value = root.getInt(path, fallback);
        if (value < 1) {
            warn(path + " must be at least 1, using " + fallback);
            return fallback;
        }
        return value;
    }

    private long cycleMillis(ConfigurationSection root, String path, double fallbackSeconds) {
        double seconds = root.getDouble(path, fallbackSeconds);
        if (seconds < 1.0) {
            warn(path + " must be at least 1 second, using " + fallbackSeconds);
            seconds = fallbackSeconds;
        }
        return Math.round(seconds * 1000);
    }

    private DateTimeFormatter formatter(ConfigurationSection root, String path, String fallback) {
        String pattern = string(root, path, fallback);
        try {
            return DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
        } catch (IllegalArgumentException e) {
            warn(path + ": invalid pattern '" + pattern + "', using '" + fallback + "'");
            return DateTimeFormatter.ofPattern(fallback, Locale.ROOT);
        }
    }

    private <E extends Enum<E>> E enumValue(ConfigurationSection root, String path, Class<E> type, E fallback) {
        String raw = root.getString(path);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(path + ": '" + raw + "' is not one of " + Arrays.toString(type.getEnumConstants()));
            return fallback;
        }
    }

    private static Set<String> stringSet(@Nullable Object raw) {
        if (raw instanceof Collection<?> collection) {
            Set<String> values = new HashSet<>();
            collection.forEach(value -> values.add(String.valueOf(value)));
            return Set.copyOf(values);
        }
        return raw == null ? Set.of() : Set.of(raw.toString());
    }

    private static int intValue(@Nullable Object raw, int fallback) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return fallback;
    }

    private void warn(String message) {
        warnings.add(message);
    }
}
