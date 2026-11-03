package at.eepy.tab.render;

import at.eepy.tab.config.PluginConfig.PlaceholderFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

/**
 * Converts text coming from other plugins (PlaceholderAPI results, LuckPerms
 * prefixes) into components. Such text is never spliced into our MiniMessage
 * templates as a string: it is inserted as a finished component, so it can not
 * break or inject formatting.
 */
public final class TextFormat {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.AMPERSAND_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern AMPERSAND_CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR]|#[0-9a-fA-F]{6})");
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[#!/]?[a-zA-Z0-9_:#-]+>");

    private TextFormat() {
    }

    public static Component parse(String text, PlaceholderFormat format) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        return switch (format) {
            case PLAIN -> Component.text(text);
            case MINIMESSAGE -> MiniMessage.miniMessage().deserialize(text);
            case LEGACY -> parseLegacy(text);
        };
    }

    /** Detects legacy codes or MiniMessage tags; used for LuckPerms prefixes. */
    public static Component parseAuto(String text) {
        if (text.isEmpty()) {
            return Component.empty();
        }
        if (text.indexOf(LegacyComponentSerializer.SECTION_CHAR) < 0 && !AMPERSAND_CODE.matcher(text).find()
                && MINI_MESSAGE_TAG.matcher(text).find()) {
            return MiniMessage.miniMessage().deserialize(text);
        }
        return parseLegacy(text);
    }

    private static Component parseLegacy(String text) {
        if (text.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0) {
            return SECTION.deserialize(text);
        }
        if (AMPERSAND_CODE.matcher(text).find()) {
            return AMPERSAND.deserialize(text);
        }
        return Component.text(text);
    }
}
