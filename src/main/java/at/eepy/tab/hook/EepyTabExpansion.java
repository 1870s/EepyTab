package at.eepy.tab.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Offers EepyTab data to other plugins, e.g. {@code %eepytab_rank%} in a chat
 * or scoreboard plugin. Values come from caches that EepyTab keeps up to date
 * on the main thread, so lookups are cheap and safe from any thread.
 */
final class EepyTabExpansion extends PlaceholderExpansion {

    private final PlayerDataSource source;
    private final String version;

    EepyTabExpansion(PlayerDataSource source, String version) {
        this.source = source;
        this.version = version;
    }

    @Override
    public String getIdentifier() {
        return "eepytab";
    }

    @Override
    public String getAuthor() {
        return "eepy";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        return source.value(player == null ? null : player.getUniqueId(), params.toLowerCase(Locale.ROOT));
    }
}
