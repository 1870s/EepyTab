package at.eepy.tab.tablist;

import at.eepy.tab.rank.ResolvedRank;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Per-player bookkeeping. Written on the main thread only; fields read by the
 * PlaceholderAPI expansion (possibly from other threads) are volatile.
 */
final class PlayerTabState {

    final UUID uuid;
    volatile ResolvedRank rank;
    volatile String layoutId = "";
    volatile String worldAlias = "";
    volatile String mode = "";

    @Nullable Component lastHeader;
    @Nullable Component lastFooter;
    @Nullable Component lastListName;
    int lastOrder = Integer.MIN_VALUE;
    /** Whether EepyTab currently owns this player's header and footer. */
    boolean headerManaged;
    /** Whether EepyTab changed this player's list name or order. */
    boolean listManaged;
    /** Render on the next tick regardless of the interval (join, world change, rank change). */
    boolean forceNext = true;

    @Nullable String previewLayout;
    long previewUntil;

    PlayerTabState(UUID uuid, ResolvedRank rank) {
        this.uuid = uuid;
        this.rank = rank;
    }

    void clearSentContent() {
        lastHeader = null;
        lastFooter = null;
        lastListName = null;
        lastOrder = Integer.MIN_VALUE;
    }
}
