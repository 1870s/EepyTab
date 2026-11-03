package at.eepy.tab.config;

import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable snapshot of config.yml. A new instance is created on every reload,
 * so consumers can hold a reference without worrying about concurrent changes.
 */
public record PluginConfig(
        Branding branding,
        Map<String, TextColor> palette,
        String separator,
        Map<String, Layout> layouts,
        String defaultLayout,
        List<LayoutRule> layoutRules,
        Animations animations,
        PlayerList playerList,
        Ranks ranks,
        Placeholders placeholders,
        Intervals intervals,
        Performance performance,
        Vanish vanish,
        Map<String, WorldSettings> worlds,
        Maintenance maintenance,
        Integrations integrations,
        Compatibility compatibility,
        int previewDurationSeconds,
        Messages messages) {

    public Layout layout(String id) {
        Layout layout = layouts.get(id);
        return layout != null ? layout : layouts.get(defaultLayout);
    }

    public WorldSettings world(String worldName) {
        WorldSettings settings = worlds.get(worldName);
        return settings != null ? settings : new WorldSettings(worldName, null, true);
    }

    public record Branding(String serverName, String ip, String website, String discord, String mode) {
    }

    public record Layout(String id, List<String> header, List<String> footer) {
    }

    public record LayoutRule(String layout, @Nullable String permission, Set<String> worlds, Set<String> ranks,
                             int minOnline, int maxOnline) {

        public boolean matches(String world, String rankId, int online, Predicate<String> hasPermission) {
            return (worlds.isEmpty() || worlds.contains(world))
                    && (ranks.isEmpty() || ranks.contains(rankId))
                    && online >= minOnline
                    && online <= maxOnline
                    && (permission == null || hasPermission.test(permission));
        }
    }

    public record Animations(boolean enabled, long gradientCycleMillis, long pulseCycleMillis,
                             Map<String, TextAnimation> texts) {
    }

    public record TextAnimation(String id, long intervalMillis, List<String> frames) {
    }

    public enum NameSource { NAME, DISPLAY_NAME }

    public record PlayerList(boolean enabled, String format, NameSource nameSource, boolean sortByRank) {
    }

    public enum PrefixSource { CONFIG, LUCKPERMS, AUTO }

    public record Ranks(String defaultRank, PrefixSource prefixSource, boolean useLuckPermsWeight,
                        List<RankDefinition> definitions) {

        public RankDefinition defaultDefinition() {
            for (RankDefinition definition : definitions) {
                if (definition.id().equals(defaultRank)) {
                    return definition;
                }
            }
            return definitions.getLast();
        }
    }

    /**
     * One configured rank. Prefix and suffix are MiniMessage strings that are
     * parsed by the renderer together with the palette.
     */
    public record RankDefinition(String id, String displayName, int priority, String prefix, String suffix,
                                 @Nullable TextColor nameColor, String permission, List<String> luckPermsGroups,
                                 boolean staff) {
    }

    public record Placeholders(ZoneId zone, DateTimeFormatter timeFormat, DateTimeFormatter dateFormat,
                               Greetings greetings, int pingGood, int pingMedium, double tpsGood, double tpsMedium,
                               int loadBarLength, String loadBarSymbol, String unavailable) {
    }

    public record Greetings(String morning, String afternoon, String evening, String night) {

        public String forHour(int hour) {
            if (hour >= 5 && hour < 12) return morning;
            if (hour >= 12 && hour < 18) return afternoon;
            if (hour >= 18 && hour < 22) return evening;
            return night;
        }
    }

    public record Intervals(int headerFooter, int playerList, int serverStats) {
    }

    public record Performance(boolean skipUnchanged, boolean spreadUpdates) {
    }

    public record Vanish(boolean hideFromCounts, String listMarker) {
    }

    public record WorldSettings(String alias, @Nullable String mode, boolean enabled) {
    }

    public enum MaintenancePosition { HEADER_BOTTOM, FOOTER_TOP }

    public record Maintenance(MaintenancePosition position, List<String> lines) {
    }

    public enum PlaceholderFormat { LEGACY, MINIMESSAGE, PLAIN }

    public record Integrations(boolean luckPerms, boolean placeholderApi, PlaceholderFormat placeholderFormat,
                               String missingPlaceholderValue, boolean registerExpansion) {
    }

    public record Compatibility(boolean warnAboutOtherTabPlugins, boolean restoreOnDisable) {
    }

    public record Messages(String prefix, Map<String, String> entries) {

        public String get(String key) {
            String value = entries.get(key);
            return value != null ? value : "<red>Missing message: " + key + "</red>";
        }
    }
}
