package at.eepy.tab.config;

import at.eepy.tab.TestConfigs;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigParserTest {

    @Test
    void bundledConfigLoadsWithoutWarnings() {
        ConfigParser parser = new ConfigParser();
        PluginConfig config = parser.parse(TestConfigs.bundledYaml());

        assertEquals(List.of(), parser.warnings());
        assertEquals("mc.eepy.at", config.branding().ip());
        assertEquals("eepy.at", config.branding().website());
        assertEquals("dsc.gg/muezza", config.branding().discord());
        assertTrue(config.layouts().containsKey("default"));
        assertTrue(config.layouts().containsKey("staff"));
        assertEquals("default", config.defaultLayout());
        assertEquals(1, config.layoutRules().size());
        assertEquals(TextColor.color(0xB98CFF), config.palette().get("primary"));
    }

    @Test
    void ranksAreSortedByPriorityAndMappedToGroups() {
        PluginConfig config = TestConfigs.bundled();
        List<String> order = config.ranks().definitions().stream().map(PluginConfig.RankDefinition::id).toList();

        assertEquals(List.of("owner", "admin", "developer", "moderator", "vip", "member"), order);
        assertEquals("member", config.ranks().defaultDefinition().id());
        PluginConfig.RankDefinition developer = config.ranks().definitions().get(2);
        assertEquals(List.of("developer", "dev"), developer.luckPermsGroups());
        assertEquals("eepytab.rank.developer", developer.permission());
        assertTrue(developer.staff());
    }

    @Test
    void invalidValuesFallBackWithWarnings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("palette.primary", "not-a-colour");
        yaml.set("palette.bold", "#FFFFFF");
        yaml.set("layouts.main.header", List.of("<anim:missing>"));
        yaml.set("default-layout", "nope");
        yaml.set("update-intervals.header-footer", 0);
        yaml.set("placeholders.timezone", "Mars/Olympus");
        yaml.set("ranks.prefix-source", "SOMETIMES");

        ConfigParser parser = new ConfigParser();
        PluginConfig config = parser.parse(yaml);
        String warnings = String.join("\n", parser.warnings());

        assertTrue(warnings.contains("palette.primary"), warnings);
        assertTrue(warnings.contains("palette.bold"), warnings);
        assertTrue(warnings.contains("<anim:missing>"), warnings);
        assertTrue(warnings.contains("default-layout"), warnings);
        assertTrue(warnings.contains("header-footer"), warnings);
        assertTrue(warnings.contains("timezone"), warnings);
        assertTrue(warnings.contains("prefix-source"), warnings);
        assertEquals("main", config.defaultLayout());
        assertEquals(5, config.intervals().headerFooter());
        assertEquals(PluginConfig.PrefixSource.AUTO, config.ranks().prefixSource());
        assertEquals(TextColor.color(0xB98CFF), config.palette().get("primary"));
        assertEquals("member", config.ranks().defaultDefinition().id());
    }

    @Test
    void layoutRulesMatchAllGivenConditions() {
        PluginConfig.LayoutRule rule = new PluginConfig.LayoutRule("nether", "eepytab.layout.nether",
                java.util.Set.of("world_nether"), java.util.Set.of(), 10, Integer.MAX_VALUE);

        assertTrue(rule.matches("world_nether", "member", 12, permission -> true));
        assertFalse(rule.matches("world", "member", 12, permission -> true));
        assertFalse(rule.matches("world_nether", "member", 5, permission -> true));
        assertFalse(rule.matches("world_nether", "member", 12, permission -> false));
    }
}
