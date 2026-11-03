package at.eepy.tab.render;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Server values sampled once per {@code update-intervals.server-stats}, so
 * rendering many players never queries the server repeatedly.
 *
 * @param online   players online (including vanished)
 * @param max      configured slot count
 * @param tps      1 minute TPS average, or {@code NaN} if unavailable
 * @param mspt     average tick duration in milliseconds, or {@code NaN} if unavailable
 * @param vanished players that carry vanish metadata
 * @param staff    players whose rank is marked as staff
 */
public record ServerStats(int online, int max, double tps, double mspt, List<Player> vanished, List<Player> staff) {

    public static final ServerStats EMPTY = new ServerStats(0, 0, Double.NaN, Double.NaN, List.of(), List.of());

    /** Online count as the viewer sees it; vanished players hidden from them are not counted. */
    public int visibleOnline(@Nullable Player viewer, boolean hideVanished) {
        return hideVanished ? online - hiddenFrom(viewer, vanished) : online;
    }

    /** Staff online as the viewer sees it. */
    public int visibleStaff(@Nullable Player viewer, boolean hideVanished) {
        if (!hideVanished) {
            return staff.size();
        }
        int count = 0;
        for (Player member : staff) {
            if (!isVanished(member) || (viewer != null && viewer.canSee(member))) {
                count++;
            }
        }
        return count;
    }

    private static int hiddenFrom(@Nullable Player viewer, List<Player> vanished) {
        if (viewer == null) {
            return vanished.size();
        }
        int hidden = 0;
        for (Player player : vanished) {
            if (!player.equals(viewer) && !viewer.canSee(player)) {
                hidden++;
            }
        }
        return hidden;
    }

    /**
     * The {@code vanished} metadata is the de facto standard set by vanish plugins
     * (SuperVanish, PremiumVanish, EssentialsX, CMI). The metadata API is deprecated
     * in Paper but has no replacement those plugins use, so it is read here on purpose.
     */
    @SuppressWarnings("deprecation")
    public static boolean isVanished(Player player) {
        for (MetadataValue value : player.getMetadata("vanished")) {
            if (value.asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
