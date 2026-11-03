package at.eepy.tab.hook;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Read-only view on EepyTab's per-player state, used by the PlaceholderAPI expansion. */
public interface PlayerDataSource {

    /**
     * @param player the player, or {@code null} for player-independent requests
     * @param key placeholder name without the {@code eepytab_} prefix, lower case
     * @return the value, or {@code null} if the key is unknown
     */
    @Nullable String value(@Nullable UUID player, String key);
}
