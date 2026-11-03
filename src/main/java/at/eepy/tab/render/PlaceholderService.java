package at.eepy.tab.render;

import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.rank.ResolvedRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the MiniMessage tag resolvers for EepyTab's built-in placeholders.
 * <p>
 * Resolvers are split by lifetime: style tags live as long as the config,
 * server tags are rebuilt once per tick, viewer tags once per rendered player.
 * Every value is inserted as text or component, never re-parsed, so player
 * names or plugin output can not inject formatting.
 */
public final class PlaceholderService {

    private final PluginConfig config;
    private final TagResolver styleTags;
    private final TextColor good;
    private final TextColor warn;
    private final TextColor bad;
    private final TextColor loadFilled;
    private final TextColor loadEmpty;

    public PlaceholderService(PluginConfig config) {
        this.config = config;
        Map<String, TextColor> palette = config.palette();
        List<TagResolver> resolvers = new ArrayList<>();
        palette.forEach((name, color) -> resolvers.add(TagResolver.resolver(name, Tag.styling(color))));
        PluginConfig.Branding branding = config.branding();
        resolvers.add(Placeholder.unparsed("server_name", branding.serverName()));
        resolvers.add(Placeholder.unparsed("ip", branding.ip()));
        resolvers.add(Placeholder.unparsed("website", branding.website()));
        resolvers.add(Placeholder.unparsed("discord", branding.discord()));
        this.styleTags = TagResolver.resolver(resolvers);
        this.good = palette.get("good");
        this.warn = palette.get("warn");
        this.bad = palette.get("bad");
        this.loadFilled = palette.get("primary");
        this.loadEmpty = palette.get("line");
    }

    /** Palette colours and branding; valid for the lifetime of the config. */
    public TagResolver styleTags() {
        return styleTags;
    }

    /** Values shared by every viewer; build once per tick. */
    public TagResolver serverTags(ServerStats stats, ZonedDateTime now) {
        PluginConfig.Placeholders settings = config.placeholders();
        String unavailable = settings.unavailable();
        boolean hasTps = !Double.isNaN(stats.tps());
        double tps = hasTps ? Math.min(20.0, stats.tps()) : 0.0;
        TextColor tpsColor = !hasTps ? warn : tps >= settings.tpsGood() ? good : tps >= settings.tpsMedium() ? warn : bad;
        return TagResolver.resolver(
                Placeholder.unparsed("max", Integer.toString(stats.max())),
                Placeholder.unparsed("tps", hasTps ? decimal(tps) : unavailable),
                TagResolver.resolver("tps_color", Tag.styling(tpsColor)),
                Placeholder.unparsed("mspt", Double.isNaN(stats.mspt()) ? unavailable : decimal(stats.mspt())),
                Placeholder.unparsed("time", settings.timeFormat().format(now)),
                Placeholder.unparsed("date", settings.dateFormat().format(now)),
                Placeholder.unparsed("greeting", settings.greetings().forHour(now.getHour())));
    }

    /** Values that depend on the viewer. {@code viewer} is {@code null} for console previews. */
    public TagResolver viewerTags(@Nullable Player viewer, ResolvedRank rank, ServerStats stats) {
        PluginConfig.Placeholders settings = config.placeholders();
        boolean hideVanished = config.vanish().hideFromCounts();
        int online = stats.visibleOnline(viewer, hideVanished);

        String name;
        Component displayName;
        String ping;
        TextColor pingColor;
        String world;
        PluginConfig.WorldSettings worldSettings;
        if (viewer != null) {
            name = viewer.getName();
            displayName = viewer.displayName();
            int latency = viewer.getPing();
            ping = latency >= 0 ? Integer.toString(latency) : settings.unavailable();
            pingColor = latency < 0 ? warn : latency <= settings.pingGood() ? good : latency <= settings.pingMedium() ? warn : bad;
            world = viewer.getWorld().getName();
            worldSettings = config.world(world);
        } else {
            name = "Console";
            displayName = Component.text(name);
            ping = settings.unavailable();
            pingColor = warn;
            world = settings.unavailable();
            worldSettings = new PluginConfig.WorldSettings(world, null, true);
        }
        String mode = worldSettings.mode() != null ? worldSettings.mode() : config.branding().mode();
        double load = stats.max() > 0 ? Math.min(1.0, (double) online / stats.max()) : Double.NaN;

        return TagResolver.resolver(
                Placeholder.unparsed("player", name),
                Placeholder.component("displayname", displayName),
                Placeholder.unparsed("ping", ping),
                TagResolver.resolver("ping_color", Tag.styling(pingColor)),
                Placeholder.unparsed("world", world),
                Placeholder.unparsed("world_alias", worldSettings.alias()),
                Placeholder.unparsed("mode", mode),
                Placeholder.unparsed("rank", rank.displayName()),
                Placeholder.component("rank_prefix", rank.prefix()),
                Placeholder.unparsed("online", Integer.toString(online)),
                Placeholder.unparsed("staff_online", Integer.toString(stats.visibleStaff(viewer, hideVanished))),
                Placeholder.unparsed("load_percent", Double.isNaN(load) ? settings.unavailable()
                        : Integer.toString((int) Math.round(load * 100))),
                Placeholder.component("load_bar", loadBar(Double.isNaN(load) ? 0.0 : load)));
    }

    private Component loadBar(double load) {
        PluginConfig.Placeholders settings = config.placeholders();
        int length = settings.loadBarLength();
        int filled = (int) Math.round(load * length);
        if (load > 0 && filled == 0) {
            filled = 1; // show that someone is online even on huge slot counts
        }
        String symbol = settings.loadBarSymbol();
        TextComponent.Builder bar = Component.text();
        if (filled > 0) {
            bar.append(Component.text(symbol.repeat(filled), loadFilled));
        }
        if (filled < length) {
            bar.append(Component.text(symbol.repeat(length - filled), loadEmpty));
        }
        return bar.build();
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
