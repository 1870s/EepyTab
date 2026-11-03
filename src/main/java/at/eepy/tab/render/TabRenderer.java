package at.eepy.tab.render;

import at.eepy.tab.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses already expanded templates (see {@link at.eepy.tab.animation.AnimationEngine})
 * into components for one viewer.
 * <p>
 * External {@code %placeholders%} are swapped for private tags before parsing
 * and their results are inserted as components. That keeps PlaceholderAPI
 * output (which usually carries legacy colour codes) from breaking MiniMessage.
 */
public final class TabRenderer {

    /** PlaceholderAPI placeholders always look like {@code %identifier_params%}. */
    static final Pattern EXTERNAL_PLACEHOLDER = Pattern.compile("%([A-Za-z0-9]+_[^%\\s<>]+)%");
    private static final String EXTERNAL_TAG = "eepy_ext_";
    /** Tags from {@link PlaceholderService#viewerTags} and the player list entry. */
    private static final Pattern VIEWER_TAG = Pattern.compile(
            "<(player|displayname|ping|ping_color|world|world_alias|mode|rank|rank_prefix|rank_suffix|name_color"
                    + "|vanish_marker|online|staff_online|load_percent|load_bar)[:>]");

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final PluginConfig.Integrations settings;
    private final @Nullable PlaceholderBridge bridge;
    private final Logger logger;
    private final Set<String> reportedErrors = ConcurrentHashMap.newKeySet();

    public TabRenderer(PluginConfig.Integrations settings, @Nullable PlaceholderBridge bridge, Logger logger) {
        this.settings = settings;
        this.bridge = settings.placeholderApi() ? bridge : null;
        this.logger = logger;
    }

    /** Renders several lines and joins them with line breaks. */
    public Component renderLines(List<String> lines, @Nullable Player viewer, TagResolver tags) {
        return renderLines(lines, viewer, tags, null);
    }

    /**
     * Renders several lines and joins them with line breaks.
     *
     * @param shared cache for lines that look the same for every viewer (no viewer
     *               tags, no external placeholders). Such lines are parsed once and
     *               reused, which is where most of the parsing cost of a design sits
     *               (title gradient, separators, links). {@code null} disables sharing.
     */
    public Component renderLines(List<String> lines, @Nullable Player viewer, TagResolver tags,
                                 @Nullable Map<String, Component> shared) {
        if (lines.isEmpty()) {
            return Component.empty();
        }
        List<Component> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (shared != null && !isViewerDependent(line)) {
                Component cached = shared.get(line);
                if (cached == null) {
                    cached = renderLine(line, viewer, tags);
                    shared.put(line, cached);
                }
                rendered.add(cached);
            } else {
                rendered.add(renderLine(line, viewer, tags));
            }
        }
        return Component.join(JoinConfiguration.newlines(), rendered);
    }

    /** Whether the rendered line can differ between viewers. */
    static boolean isViewerDependent(String line) {
        return line.indexOf('%') >= 0 || VIEWER_TAG.matcher(line).find();
    }

    public Component renderLine(String line, @Nullable Player viewer, TagResolver tags) {
        String template = line;
        TagResolver resolver = tags;
        if (line.indexOf('%') >= 0) {
            Matcher matcher = EXTERNAL_PLACEHOLDER.matcher(line);
            if (matcher.find()) {
                List<TagResolver> external = new ArrayList<>();
                StringBuilder out = new StringBuilder(line.length());
                int index = 0;
                do {
                    String tag = EXTERNAL_TAG + index++;
                    external.add(Placeholder.component(tag, resolveExternal(viewer, matcher.group())));
                    matcher.appendReplacement(out, "<" + tag + ">");
                } while (matcher.find());
                matcher.appendTail(out);
                template = out.toString();
                resolver = TagResolver.resolver(tags, TagResolver.resolver(external));
            }
        }
        try {
            return miniMessage.deserialize(template, resolver);
        } catch (RuntimeException e) {
            if (reportedErrors.add(line)) {
                logger.log(Level.WARNING, "Could not render line: " + line, e);
            }
            return Component.text(miniMessage.stripTags(line));
        }
    }

    private Component resolveExternal(@Nullable Player viewer, String placeholder) {
        if (bridge == null) {
            return TextFormat.parse(settings.missingPlaceholderValue(), PluginConfig.PlaceholderFormat.PLAIN);
        }
        String value;
        try {
            value = bridge.apply(viewer, placeholder);
        } catch (RuntimeException e) {
            if (reportedErrors.add(placeholder)) {
                logger.log(Level.WARNING, "PlaceholderAPI failed for " + placeholder, e);
            }
            value = null;
        }
        if (value == null || value.isEmpty() || value.equals(placeholder)) {
            // Unknown placeholders come back unchanged; show the fallback instead of raw %text%.
            value = settings.missingPlaceholderValue();
        }
        return TextFormat.parse(value, settings.placeholderFormat());
    }
}
