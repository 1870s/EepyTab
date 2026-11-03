package at.eepy.tab.rank;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

/**
 * The rank a player currently has, ready for rendering.
 *
 * @param id          configured rank id, or {@code luckperms:<group>} for groups without a rank entry
 * @param displayName human readable name, shown by {@code <rank>}
 * @param prefix      rendered prefix component
 * @param suffix      rendered suffix component
 * @param nameColor   colour applied to the player name, {@code null} for none
 * @param priority    sort priority, higher is listed first
 * @param staff       counted by {@code <staff_online>}
 * @param source      where the rank came from, for /eepytab debug
 */
public record ResolvedRank(String id, String displayName, Component prefix, Component suffix,
                           @Nullable TextColor nameColor, int priority, boolean staff, String source) {

    public ResolvedRank withSource(String newSource) {
        return new ResolvedRank(id, displayName, prefix, suffix, nameColor, priority, staff, newSource);
    }

    public ResolvedRank withPriority(int newPriority) {
        return new ResolvedRank(id, displayName, prefix, suffix, nameColor, newPriority, staff, source);
    }

    public ResolvedRank withAffixes(Component newPrefix, Component newSuffix) {
        return new ResolvedRank(id, displayName, newPrefix, newSuffix, nameColor, priority, staff, source);
    }
}
