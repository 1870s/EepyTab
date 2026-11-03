package at.eepy.tab.animation;

import at.eepy.tab.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimationEngineTest {

    private static final Map<String, TextColor> PALETTE = Map.of("primary", TextColor.color(0xB98CFF));

    private static AnimationEngine engine(boolean enabled) {
        PluginConfig.Animations settings = new PluginConfig.Animations(enabled, 8000, 4000, Map.of(
                "welcome", new PluginConfig.TextAnimation("welcome", 2000, List.of("one", "two <separator>", "three"))));
        return new AnimationEngine(settings, PALETTE, "<st>   </st>");
    }

    @Test
    void textAnimationsRotateByWallClock() {
        AnimationEngine engine = engine(true);
        assertEquals("[one]", engine.expand("[<anim:welcome>]", 0));
        assertEquals("[one]", engine.expand("[<anim:welcome>]", 1999));
        assertEquals("[two <st>   </st>]", engine.expand("[<anim:welcome>]", 2000));
        assertEquals("[three]", engine.expand("[<anim:welcome>]", 4500));
        assertEquals("[one]", engine.expand("[<anim:welcome>]", 6000));
        assertEquals("[]", engine.expand("[<anim:unknown>]", 0));
    }

    @Test
    void disabledAnimationsShowFirstFrameAndStaticGradients() {
        AnimationEngine engine = engine(false);
        assertEquals("one", engine.expand("<anim:welcome>", 4500));
        assertEquals("<gradient:#B98CFF:#FFFFFF>x</gradient>", engine.expand("<flow:primary:#ffffff>x</flow>", 4500));
        assertEquals("<transition:#B98CFF:#000000:0.000>x</transition>", engine.expand("<pulse:primary:#000000>x</pulse>", 4500));
    }

    @Test
    void flowStartsAsThePlainGradientAndLoops() {
        AnimationEngine engine = engine(true);
        String atStart = engine.expand("<flow:primary:#FFFFFF:primary>x</flow>", 0);
        assertTrue(atStart.startsWith("<gradient:#B98CFF:"), atStart);
        assertTrue(atStart.endsWith(":#B98CFF>x</gradient>"), atStart);
        assertEquals(atStart, engine.expand("<flow:primary:#FFFFFF:primary>x</flow>", 8000));
        assertNotEquals(atStart, engine.expand("<flow:primary:#FFFFFF:primary>x</flow>", 2000));
    }

    @Test
    void unknownFlowColoursArePassedThroughUnchanged() {
        assertEquals("<gradient:nope:#fff>x</gradient>", engine(true).expand("<flow:nope:#fff>x</flow>", 1234));
    }

    @Test
    void pulseBreathesInAndOut() {
        AnimationEngine engine = engine(true);
        assertEquals(0.0, engine.pulsePhase(0), 1e-9);
        assertEquals(1.0, engine.pulsePhase(2000), 1e-9);
        assertEquals(0.5, engine.pulsePhase(1000), 1e-9);
        assertEquals(0.0, engine.pulsePhase(4000), 1e-9);
    }

    /** The rendered text has the same colours at the end of a cycle as at its start, and moves smoothly. */
    @Test
    void flowLoopsSeamlesslyInRenderedText() {
        AnimationEngine engine = engine(true);
        String template = "<flow:#C9A6FF:#8DB6FF:#F3EEFF:#8DB6FF:#C9A6FF>✦ e e p y ✦</flow>";
        List<String> start = colorsOf(engine.expand(template, 0));
        assertEquals(start, colorsOf(engine.expand(template, 7999)));
        assertNotEquals(start, colorsOf(engine.expand(template, 4000)));
        // one 250ms step only shifts colours slightly
        assertTrue(maxChannelDelta(engine.expand(template, 3000), engine.expand(template, 3250)) < 40);
    }

    private static int maxChannelDelta(String a, String b) {
        List<TextColor> first = rawColors(a);
        List<TextColor> second = rawColors(b);
        int max = 0;
        for (int i = 0; i < first.size(); i++) {
            TextColor x = first.get(i);
            TextColor y = second.get(i);
            max = Math.max(max, Math.max(Math.abs(x.red() - y.red()),
                    Math.max(Math.abs(x.green() - y.green()), Math.abs(x.blue() - y.blue()))));
        }
        return max;
    }

    private static List<TextColor> rawColors(String miniMessage) {
        List<TextColor> colors = new ArrayList<>();
        walk(MiniMessage.miniMessage().deserialize(miniMessage), null, colors);
        return colors;
    }

    private static void walk(Component component, TextColor inherited, List<TextColor> out) {
        TextColor color = component.color() != null ? component.color() : inherited;
        if (component instanceof TextComponent text && color != null) {
            TextColor finalColor = color;
            text.content().codePoints().filter(c -> c != ' ').forEach(c -> out.add(finalColor));
        }
        component.children().forEach(child -> walk(child, color, out));
    }

    /** Colour of every visible character, so merged neighbour components do not matter. */
    private static List<String> colorsOf(String miniMessage) {
        List<String> colors = new ArrayList<>();
        collect(MiniMessage.miniMessage().deserialize(miniMessage), null, colors);
        return colors;
    }

    private static void collect(Component component, TextColor inherited, List<String> out) {
        TextColor color = component.color() != null ? component.color() : inherited;
        if (component instanceof TextComponent text && color != null) {
            // Compare coarsely: the last phase step may differ by one or two RGB units.
            String coarse = (color.red() >> 3) + "/" + (color.green() >> 3) + "/" + (color.blue() >> 3);
            text.content().codePoints().filter(c -> c != ' ').forEach(c -> out.add(coarse));
        }
        component.children().forEach(child -> collect(child, color, out));
    }
}
