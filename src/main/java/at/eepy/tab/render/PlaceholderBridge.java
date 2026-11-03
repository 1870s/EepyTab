package at.eepy.tab.render;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Resolves external {@code %placeholders%}. Implemented by the PlaceholderAPI
 * hook; kept as an interface so rendering never references PlaceholderAPI.
 */
public interface PlaceholderBridge {

    /**
     * @param player     the viewer, or {@code null} for console previews
     * @param identifier the placeholder including the percent signs, e.g. {@code %vault_eco_balance%}
     * @return the raw result, possibly with legacy colour codes
     */
    String apply(@Nullable Player player, String identifier);
}
