package at.eepy.tab.render;

import at.eepy.tab.TestConfigs;
import at.eepy.tab.animation.AnimationEngine;
import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.rank.RankService;
import at.eepy.tab.rank.ResolvedRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders every line of the bundled design and checks that no tag is left
 * unresolved, i.e. that every placeholder used in config.yml actually exists.
 */
class DefaultDesignRenderTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final PluginConfig config = TestConfigs.bundled();
    private final AnimationEngine animations = new AnimationEngine(config.animations(), config.palette(), config.separator());
    private final PlaceholderService placeholders = new PlaceholderService(config);
    private final TabRenderer renderer = new TabRenderer(config.integrations(), null, Logger.getLogger("test"));
    private final RankService ranks = new RankService(config.ranks(), null, animations::expandStatic, placeholders.styleTags());
    private final ServerStats stats = new ServerStats(12, 100, 19.97, 3.4, List.of(), List.of());

    private TagResolver tags() {
        ZonedDateTime now = ZonedDateTime.of(2026, 11, 3, 21, 30, 0, 0, config.placeholders().zone());
        return TagResolver.resolver(placeholders.styleTags(), placeholders.serverTags(stats, now),
                placeholders.viewerTags(null, ranks.defaultRank(), stats));
    }

    @Test
    void everyTemplateResolves() {
        List<String> templates = new ArrayList<>();
        config.layouts().values().forEach(layout -> {
            templates.addAll(layout.header());
            templates.addAll(layout.footer());
        });
        templates.addAll(config.maintenance().lines());
        config.animations().texts().values().forEach(animation -> templates.addAll(animation.frames()));

        TagResolver tags = tags();
        for (long time = 0; time < 30_000; time += 1_000) {
            for (String template : templates) {
                String text = PLAIN.serialize(renderer.renderLine(animations.expand(template, time), null, tags));
                assertFalse(text.contains("<") || text.contains(">"), "Unresolved tag in: " + template + " -> " + text);
            }
        }
    }

    @Test
    void headerShowsRealValues() {
        PluginConfig.Layout layout = config.layout("default");
        Component header = renderer.renderLines(animations.expandAll(layout.header(), 0), null, tags());
        Component footer = renderer.renderLines(animations.expandAll(layout.footer(), 0), null, tags());
        String headerText = PLAIN.serialize(header);
        String footerText = PLAIN.serialize(footer);
        System.out.println("── header ──\n" + headerText + "\n── footer ──\n" + footerText);

        assertTrue(headerText.contains("✦   e e p y   ✦"));
        assertTrue(headerText.contains("ONLINE 12/100"));
        assertTrue(headerText.contains("TPS 20.0"), headerText); // 19.97 is rounded, never above 20
        assertTrue(headerText.contains("Good evening, Console."), headerText);
        assertTrue(footerText.contains("WEBSITE  ›  eepy.at"));
        assertTrue(footerText.contains("DISCORD  ›  dsc.gg/muezza"));
        assertTrue(footerText.contains("Play at mc.eepy.at"));
        assertTrue(footerText.contains("21:30"));
    }

    @Test
    void rankPrefixesRender() {
        for (PluginConfig.RankDefinition definition : config.ranks().definitions()) {
            ResolvedRank rank = ranks.compiled(definition);
            String prefix = PLAIN.serialize(rank.prefix());
            assertFalse(prefix.contains("<"), prefix);
            if (!definition.prefix().isEmpty()) {
                assertTrue(prefix.endsWith(" │ "), prefix);
            }
        }
        assertEquals("OWNER │ ", PLAIN.serialize(ranks.compiled(config.ranks().definitions().getFirst()).prefix()));
    }

    @Test
    void externalPlaceholdersFallBackWithoutPlaceholderApi() {
        String text = PLAIN.serialize(renderer.renderLine("Coins: %vault_eco_balance% · 50% off", null, tags()));
        assertEquals("Coins:  · 50% off", text);
    }

    @Test
    void onlyViewerIndependentLinesAreShared() {
        assertFalse(TabRenderer.isViewerDependent("<separator> <primary><website></primary> <tps>"));
        assertTrue(TabRenderer.isViewerDependent("<muted>PING</muted> <ping_color><ping>ms</ping_color>"));
        assertTrue(TabRenderer.isViewerDependent("Hi <player>!"));
        assertTrue(TabRenderer.isViewerDependent("Coins %vault_eco_balance%"));
        assertFalse(TabRenderer.isViewerDependent("<players_total>")); // only exact tag names count
    }
}
