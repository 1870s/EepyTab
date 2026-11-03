package at.eepy.tab.hook;

import at.eepy.tab.render.PlaceholderBridge;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** PlaceholderAPI integration. Only instantiated when PlaceholderAPI is enabled. */
public final class PlaceholderApiHook implements PlaceholderBridge {

    private @Nullable EepyTabExpansion expansion;

    @Override
    public String apply(@Nullable Player player, String identifier) {
        return PlaceholderAPI.setPlaceholders(player, identifier);
    }

    public void registerExpansion(PlayerDataSource source, String version) {
        if (expansion == null) {
            EepyTabExpansion created = new EepyTabExpansion(source, version);
            if (created.register()) {
                expansion = created;
            }
        }
    }

    public void unregisterExpansion() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    public boolean expansionRegistered() {
        return expansion != null;
    }
}
