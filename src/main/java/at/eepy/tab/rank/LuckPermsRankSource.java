package at.eepy.tab.rank;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Rank lookup backed by LuckPerms. Kept as an interface so that this package
 * never references LuckPerms classes and loads fine when LuckPerms is absent.
 */
public interface LuckPermsRankSource {

    /**
     * @return the player's rank, or {@code null} if LuckPerms has no data for the
     * player yet and the permission based fallback should be used
     */
    @Nullable ResolvedRank resolve(Player player, RankService ranks);
}
