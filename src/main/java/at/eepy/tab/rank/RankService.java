package at.eepy.tab.rank;

import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.config.PluginConfig.RankDefinition;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

/**
 * Resolves the rank of a player.
 * <p>
 * With LuckPerms the player's groups decide (see {@link LuckPermsRankSource}).
 * Without it, the highest-priority rank whose permission node the player has
 * wins. Those permission nodes are registered with default {@code false}, so
 * operators do not silently receive the owner rank.
 */
public final class RankService {

    private final PluginConfig.Ranks settings;
    private final Map<String, RankDefinition> byGroup = new HashMap<>();
    private final Map<String, ResolvedRank> compiled = new HashMap<>();
    private final ResolvedRank defaultRank;
    private final @Nullable LuckPermsRankSource luckPerms;
    private final List<Permission> registeredPermissions = new ArrayList<>();

    /**
     * @param expandStatic expands EepyTab tags (flow, pulse, separator) without motion
     * @param styleTags    palette and branding tags available inside prefixes
     */
    public RankService(PluginConfig.Ranks settings, @Nullable LuckPermsRankSource luckPerms,
                       UnaryOperator<String> expandStatic, TagResolver styleTags) {
        this.settings = settings;
        this.luckPerms = luckPerms;
        MiniMessage miniMessage = MiniMessage.miniMessage();
        for (RankDefinition definition : settings.definitions()) {
            compiled.put(definition.id(), new ResolvedRank(
                    definition.id(),
                    definition.displayName(),
                    miniMessage.deserialize(expandStatic.apply(definition.prefix()), styleTags),
                    miniMessage.deserialize(expandStatic.apply(definition.suffix()), styleTags),
                    definition.nameColor(),
                    definition.priority(),
                    definition.staff(),
                    "config"));
            for (String group : definition.luckPermsGroups()) {
                byGroup.putIfAbsent(group, definition);
            }
        }
        this.defaultRank = compiled.get(settings.defaultDefinition().id());
    }

    public ResolvedRank resolve(Player player) {
        if (luckPerms != null) {
            ResolvedRank rank = luckPerms.resolve(player, this);
            if (rank != null) {
                return rank;
            }
        }
        for (RankDefinition definition : settings.definitions()) {
            if (player.hasPermission(definition.permission())) {
                return compiled.get(definition.id()).withSource("permission " + definition.permission());
            }
        }
        return defaultRank.withSource("default");
    }

    public ResolvedRank defaultRank() {
        return defaultRank;
    }

    public PluginConfig.Ranks settings() {
        return settings;
    }

    public boolean usesLuckPerms() {
        return luckPerms != null;
    }

    public @Nullable RankDefinition definitionForGroup(String group) {
        return byGroup.get(group.toLowerCase(Locale.ROOT));
    }

    public ResolvedRank compiled(RankDefinition definition) {
        return compiled.get(definition.id());
    }

    /** Registers the rank permission nodes so they default to false, even for operators. */
    public void registerPermissions(PluginManager pluginManager) {
        for (RankDefinition definition : settings.definitions()) {
            if (pluginManager.getPermission(definition.permission()) == null) {
                Permission permission = new Permission(definition.permission(),
                        "Gives the EepyTab rank '" + definition.id() + "'", PermissionDefault.FALSE);
                pluginManager.addPermission(permission);
                registeredPermissions.add(permission);
            }
        }
    }

    public void unregisterPermissions(PluginManager pluginManager) {
        registeredPermissions.forEach(pluginManager::removePermission);
        registeredPermissions.clear();
    }
}
