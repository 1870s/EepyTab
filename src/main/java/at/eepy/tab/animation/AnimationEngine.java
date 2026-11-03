package at.eepy.tab.animation;

import at.eepy.tab.config.PluginConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands EepyTab's time based tags into plain MiniMessage before parsing.
 * <ul>
 *   <li>{@code <separator>} becomes the configured divider,</li>
 *   <li>{@code <anim:id>} becomes the current frame of a rotating text,</li>
 *   <li>{@code <flow:a:b:...>} becomes a {@code <gradient>} whose colours loop with time,</li>
 *   <li>{@code <pulse:a:b>} becomes a {@code <transition>} that softly breathes between two colours.</li>
 * </ul>
 * All effects are derived from wall clock time, so their speed does not depend
 * on how often the tab list is refreshed. Expansion is pure string work and is
 * done once per layout and tick, not once per player.
 */
public final class AnimationEngine {

    private static final Pattern ANIMATION_TAG = Pattern.compile("<anim:([A-Za-z0-9_-]+)>");
    private static final Pattern EFFECT_TAG = Pattern.compile("<(flow|pulse):([^<>]+)>");
    /** Resolution of the sampled {@code <flow>} loop; 4 stops per colour step are visually exact. */
    private static final int SAMPLES_PER_SEGMENT = 4;

    private final PluginConfig.Animations settings;
    private final Map<String, TextColor> palette;
    private final String separator;

    public AnimationEngine(PluginConfig.Animations settings, Map<String, TextColor> palette, String separator) {
        this.settings = settings;
        this.palette = palette;
        this.separator = separator;
    }

    /** Expands a template for the given moment, honouring {@code animations.enabled}. */
    public String expand(String template, long nowMillis) {
        return expand(template, nowMillis, settings.enabled());
    }

    /** Expands a template without motion: first frames, gradients at rest. */
    public String expandStatic(String template) {
        return expand(template, 0L, false);
    }

    public List<String> expandAll(List<String> templates, long nowMillis) {
        return templates.stream().map(template -> expand(template, nowMillis)).toList();
    }

    private String expand(String template, long nowMillis, boolean animate) {
        String result = template;
        if (result.contains("<separator>")) {
            result = result.replace("<separator>", separator);
        }
        if (result.contains("<anim:")) {
            result = expandTextAnimations(result, nowMillis, animate);
        }
        if (result.contains("<flow") || result.contains("<pulse")) {
            result = expandEffects(result, nowMillis, animate);
        }
        return result;
    }

    private String expandTextAnimations(String template, long nowMillis, boolean animate) {
        Matcher matcher = ANIMATION_TAG.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 64);
        while (matcher.find()) {
            PluginConfig.TextAnimation animation = settings.texts().get(matcher.group(1));
            String frame = animation == null ? "" : frame(animation, nowMillis, animate);
            // Frames may use <separator>, <flow> and <pulse>; those are expanded afterwards.
            matcher.appendReplacement(out, Matcher.quoteReplacement(frame.replace("<separator>", separator)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String frame(PluginConfig.TextAnimation animation, long nowMillis, boolean animate) {
        List<String> frames = animation.frames();
        if (!animate || frames.size() == 1) {
            return frames.getFirst();
        }
        long index = Math.floorDiv(nowMillis, animation.intervalMillis());
        return frames.get(Math.floorMod(index, frames.size()));
    }

    private String expandEffects(String template, long nowMillis, boolean animate) {
        Matcher matcher = EFFECT_TAG.matcher(template);
        StringBuilder out = new StringBuilder(template.length() + 32);
        while (matcher.find()) {
            String arguments = matcher.group(2);
            List<TextColor> colors = resolveColors(arguments);
            String joined = colors == null ? arguments : join(colors);
            String replacement;
            if (matcher.group(1).equals("flow")) {
                replacement = animate && colors != null && colors.size() > 1
                        ? "<gradient:" + join(cyclicStops(colors, gradientPhase(nowMillis))) + ">"
                        : "<gradient:" + joined + ">";
            } else {
                replacement = "<transition:" + joined + ":" + format(animate ? pulsePhase(nowMillis) : 0.0) + ">";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString().replace("</flow>", "</gradient>").replace("</pulse>", "</transition>");
    }

    /**
     * Colour stops of a looping gradient shifted by {@code phase}.
     * <p>
     * MiniMessage's own gradient phase clamps at the last colour instead of
     * wrapping around, which would make the animation jump once per cycle. So the
     * loop is computed here: the colours are treated as a ring (the first colour
     * is appended if the list does not already end with it) and sampled at evenly
     * spaced points starting at the phase offset. The result is a plain gradient
     * that MiniMessage spreads across the text.
     */
    static List<TextColor> cyclicStops(List<TextColor> colors, double phase) {
        List<TextColor> ring = new ArrayList<>(colors);
        if (!ring.getFirst().equals(ring.getLast())) {
            ring.add(ring.getFirst());
        }
        int segments = ring.size() - 1;
        double start = phase * segments;
        int samples = segments * SAMPLES_PER_SEGMENT;
        List<TextColor> stops = new ArrayList<>(samples + 1);
        for (int k = 0; k <= samples; k++) {
            double position = (start + (double) k / SAMPLES_PER_SEGMENT) % segments;
            int index = (int) Math.floor(position);
            stops.add(TextColor.lerp((float) (position - index), ring.get(index), ring.get(index + 1)));
        }
        return stops;
    }

    /** Linear 0 to 1 ramp over one gradient cycle. */
    double gradientPhase(long nowMillis) {
        long cycle = settings.gradientCycleMillis();
        return (double) Math.floorMod(nowMillis, cycle) / cycle;
    }

    /** Eased 0 to 1 to 0 so the colour change has no visible edge. */
    double pulsePhase(long nowMillis) {
        long cycle = settings.pulseCycleMillis();
        double progress = (double) Math.floorMod(nowMillis, cycle) / cycle;
        return (1.0 - Math.cos(progress * Math.PI * 2.0)) / 2.0;
    }

    /** Palette names, hex colours and named colours; {@code null} if any argument is not a colour. */
    private @Nullable List<TextColor> resolveColors(String arguments) {
        List<TextColor> colors = new ArrayList<>();
        for (String part : arguments.split(":")) {
            String name = part.trim().toLowerCase(Locale.ROOT);
            TextColor color = palette.get(name);
            if (color == null) {
                color = name.startsWith("#") ? TextColor.fromHexString(name) : NamedTextColor.NAMES.value(name);
            }
            if (color == null) {
                return null;
            }
            colors.add(color);
        }
        return colors;
    }

    private static String join(List<TextColor> colors) {
        StringBuilder out = new StringBuilder(colors.size() * 8);
        for (TextColor color : colors) {
            if (!out.isEmpty()) {
                out.append(':');
            }
            out.append(color.asHexString());
        }
        return out.toString();
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
