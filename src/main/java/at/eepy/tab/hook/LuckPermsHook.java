package at.eepy.tab.hook;

import at.eepy.tab.config.PluginConfig.PrefixSource;
import at.eepy.tab.config.PluginConfig.RankDefinition;
import at.eepy.tab.rank.LuckPermsRankSource;
import at.eepy.tab.rank.RankService;
import at.eepy.tab.rank.ResolvedRank;
import at.eepy.tab.render.TextFormat;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.group.GroupDataRecalculateEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * LuckPerms integration: resolves ranks from groups and pushes changes to the
 * tab list as soon as LuckPerms recalculates a user or group.
 * <p>
 * Only instantiated when LuckPerms is enabled, so the rest of the plugin never
 * touches LuckPerms classes otherwise.
 */
public final class LuckPermsHook implements LuckPermsRankSource {

    private final LuckPerms api;
    private final List<EventSubscription<?>> subscriptions = new ArrayList<>();

    /**
     * @param onUserChanged called (from a LuckPerms thread) with the UUID of a changed user
     * @param onGroupChanged called (from a LuckPerms thread) when any group changed
     */
    public LuckPermsHook(Plugin plugin, Consumer<UUID> onUserChanged, Runnable onGroupChanged) {
        this.api = LuckPermsProvider.get();
        subscriptions.add(api.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class,
                event -> onUserChanged.accept(event.getUser().getUniqueId())));
        subscriptions.add(api.getEventBus().subscribe(plugin, GroupDataRecalculateEvent.class,
                event -> onGroupChanged.run()));
    }

    @Override
    public @Nullable ResolvedRank resolve(Player player, RankService ranks) {
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return null;
        }
        String primaryGroup = user.getPrimaryGroup();
        PrefixSource prefixSource = ranks.settings().prefixSource();

        // 1) the primary group, 2) the heaviest inherited group that has a rank entry
        RankDefinition definition = ranks.definitionForGroup(primaryGroup);
        Group matchedGroup = api.getGroupManager().getGroup(primaryGroup);
        if (definition == null) {
            List<Group> inherited = new ArrayList<>(user.getInheritedGroups(user.getQueryOptions()));
            inherited.sort(Comparator.comparingInt((Group group) -> group.getWeight().orElse(0)).reversed());
            for (Group group : inherited) {
                definition = ranks.definitionForGroup(group.getName());
                if (definition != null) {
                    matchedGroup = group;
                    break;
                }
            }
        }

        int weight = matchedGroup == null ? 0 : matchedGroup.getWeight().orElse(0);
        CachedMetaData meta = user.getCachedData().getMetaData();

        if (definition == null) {
            if (prefixSource == PrefixSource.CONFIG) {
                return null; // let the permission fallback / default rank decide
            }
            ResolvedRank fallback = ranks.defaultRank();
            String displayName = matchedGroup != null && matchedGroup.getDisplayName() != null
                    ? matchedGroup.getDisplayName()
                    : capitalize(primaryGroup);
            return new ResolvedRank("luckperms:" + primaryGroup, displayName,
                    affix(meta.getPrefix()), affix(meta.getSuffix()), fallback.nameColor(),
                    weight, false, "luckperms group " + primaryGroup + " (no rank entry)");
        }

        ResolvedRank rank = ranks.compiled(definition)
                .withSource("luckperms group " + (matchedGroup != null ? matchedGroup.getName() : primaryGroup));
        if (prefixSource == PrefixSource.LUCKPERMS) {
            rank = rank.withAffixes(affix(meta.getPrefix()), affix(meta.getSuffix()));
        }
        if (ranks.settings().useLuckPermsWeight()) {
            rank = rank.withPriority(weight);
        }
        return rank;
    }

    public String version() {
        return api.getPluginMetadata().getVersion();
    }

    public void close() {
        subscriptions.forEach(EventSubscription::close);
        subscriptions.clear();
    }

    private static Component affix(@Nullable String raw) {
        return raw == null ? Component.empty() : TextFormat.parseAuto(raw);
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
