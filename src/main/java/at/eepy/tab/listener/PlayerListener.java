package at.eepy.tab.listener;

import at.eepy.tab.tablist.TabService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Forwards the few player events the tab list reacts to. */
public final class PlayerListener implements Listener {

    private final TabService tabService;

    public PlayerListener(TabService tabService) {
        this.tabService = tabService;
    }

    // MONITOR: other plugins (vanish, permissions) have finished their join logic.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        tabService.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tabService.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        tabService.handleWorldChange(event.getPlayer());
    }
}
