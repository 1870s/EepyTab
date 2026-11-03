package at.eepy.tab.tablist;

import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.config.PluginConfig.Layout;
import at.eepy.tab.config.PluginConfig.LayoutRule;
import at.eepy.tab.hook.PlayerDataSource;
import at.eepy.tab.rank.ResolvedRank;
import at.eepy.tab.render.ServerStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Drives the tab list: one main-thread task that runs every tick and decides
 * per player whether header/footer or the list entry are due.
 * <p>
 * Design notes:
 * <ul>
 *   <li>With {@code spread-updates}, players are assigned to tick slots by UUID,
 *       so a 5 tick interval renders a fifth of the players each tick instead of
 *       everyone at once.</li>
 *   <li>Layout templates are expanded once per tick and layout. Lines without
 *       viewer specific tags are parsed once and shared by all viewers; only the
 *       remaining lines are parsed per player.</li>
 *   <li>Packets are only sent when the rendered component actually changed.</li>
 *   <li>Sorting uses the native player list order (1.21.2+), so no scoreboard
 *       teams are created and nametag or team plugins keep working.</li>
 *   <li>LuckPerms events arrive on LuckPerms threads; they only mark players as
 *       dirty and the main-thread task picks them up on the next tick.</li>
 * </ul>
 */
public final class TabService implements PlayerDataSource {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int MAX_SHARED_LINES = 512;

    private final Plugin plugin;
    private final Server server;
    private final PersistentState persistentState;
    private final Map<UUID, PlayerTabState> players = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRankRefresh = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean pendingRefreshAll = new AtomicBoolean();
    private final RenderMetrics metrics = new RenderMetrics();
    private final Map<String, LayoutFrame> frameCache = new HashMap<>();
    /** Lines that render identically for every viewer; valid until the server tags change. */
    private final Map<String, Component> sharedLines = new HashMap<>();

    private TabSetup setup;
    private @Nullable BukkitTask task;
    private long tick;
    private boolean statsDirty = true;
    private ServerStats stats = ServerStats.EMPTY;
    private long frameMillis;
    private @Nullable TagResolver frameServerTags;
    private boolean failureReported;

    public TabService(Plugin plugin, PersistentState persistentState, TabSetup setup) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.persistentState = persistentState;
        this.setup = setup;
    }

    // ── lifecycle ──────────────────────────────────────────────────────────

    public void start() {
        if (task == null) {
            task = server.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
        server.getOnlinePlayers().forEach(this::track);
    }

    /** Applies a new configuration and re-renders everyone on the next tick. */
    public void apply(TabSetup newSetup) {
        boolean listWasEnabled = setup.config().playerList().enabled();
        this.setup = newSetup;
        frameCache.clear();
        sharedLines.clear();
        frameServerTags = null;
        failureReported = false;
        statsDirty = true;
        for (Player player : server.getOnlinePlayers()) {
            PlayerTabState state = players.get(player.getUniqueId());
            if (state == null) {
                continue;
            }
            if (listWasEnabled && !newSetup.config().playerList().enabled()) {
                resetListEntry(player, state);
            }
            state.rank = newSetup.ranks().resolve(player);
            state.previewLayout = null;
            state.clearSentContent();
            state.forceNext = true;
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (setup.config().compatibility().restoreOnDisable()) {
            for (Player player : server.getOnlinePlayers()) {
                PlayerTabState state = players.get(player.getUniqueId());
                if (state == null) {
                    continue;
                }
                if (state.headerManaged) {
                    player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                }
                resetListEntry(player, state);
            }
        }
        players.clear();
        frameCache.clear();
    }

    // ── player events (main thread) ────────────────────────────────────────

    public void handleJoin(Player player) {
        PlayerTabState state = track(player);
        statsDirty = true;
        refreshStats();
        updateListEntry(player, state, true);
        updateHeaderFooter(player, state, true);
        state.forceNext = false;
    }

    public void handleQuit(Player player) {
        players.remove(player.getUniqueId());
        statsDirty = true;
    }

    public void handleWorldChange(Player player) {
        PlayerTabState state = players.get(player.getUniqueId());
        if (state != null) {
            state.forceNext = true;
        }
    }

    // ── LuckPerms callbacks (any thread) ───────────────────────────────────

    public void markRankDirty(UUID uuid) {
        pendingRankRefresh.add(uuid);
    }

    public void markAllRanksDirty() {
        pendingRefreshAll.set(true);
    }

    // ── commands ───────────────────────────────────────────────────────────

    /** Re-renders the player right now and sends everything, even if unchanged. */
    public void refreshNow(Player player) {
        PlayerTabState state = track(player);
        state.rank = setup.ranks().resolve(player);
        beginFrame();
        updateListEntry(player, state, true);
        updateHeaderFooter(player, state, true);
    }

    public void preview(Player player, String layoutId) {
        PlayerTabState state = track(player);
        state.previewLayout = layoutId;
        state.previewUntil = System.currentTimeMillis() + setup.config().previewDurationSeconds() * 1000L;
        refreshNow(player);
    }

    /** Renders a layout for chat/console output, without sending anything. */
    public Component[] renderPreview(@Nullable Player viewer, Layout layout) {
        beginFrame();
        ResolvedRank rank = viewer == null ? setup.ranks().defaultRank() : track(viewer).rank;
        LayoutFrame frame = frame(layout);
        TagResolver tags = viewerResolver(viewer, rank);
        return new Component[]{
                setup.renderer().renderLines(frame.header(), viewer, tags),
                setup.renderer().renderLines(frame.footer(), viewer, tags)};
    }

    public String currentLayout(Player player) {
        return selectLayout(player, track(player)).id();
    }

    public @Nullable ResolvedRank rankOf(Player player) {
        PlayerTabState state = players.get(player.getUniqueId());
        return state == null ? null : state.rank;
    }

    public void setMaintenance(boolean enabled) {
        persistentState.setMaintenance(enabled);
        frameCache.clear();
        players.values().forEach(state -> state.forceNext = true);
    }

    public boolean maintenance() {
        return persistentState.maintenance();
    }

    public TabSetup setup() {
        return setup;
    }

    public RenderMetrics metrics() {
        return metrics;
    }

    public ServerStats stats() {
        return stats;
    }

    public int trackedPlayers() {
        return players.size();
    }

    // ── tick ───────────────────────────────────────────────────────────────

    private void tick() {
        long started = System.nanoTime();
        try {
            tick++;
            PluginConfig.Intervals intervals = setup.config().intervals();
            beginFrame();
            if (statsDirty || tick % intervals.serverStats() == 0) {
                refreshStats();
            }
            boolean refreshAllRanks = pendingRefreshAll.getAndSet(false);
            for (Player player : server.getOnlinePlayers()) {
                PlayerTabState state = track(player);
                boolean rankDirty = refreshAllRanks || pendingRankRefresh.remove(player.getUniqueId());
                boolean force = state.forceNext;
                state.forceNext = false;

                if (force || rankDirty || isDue(player, intervals.playerList())) {
                    state.rank = setup.ranks().resolve(player);
                    updateListEntry(player, state, force);
                }
                if (force || rankDirty || isDue(player, intervals.headerFooter())) {
                    updateHeaderFooter(player, state, force);
                }
            }
            if (!pendingRankRefresh.isEmpty()) {
                pendingRankRefresh.removeIf(uuid -> !players.containsKey(uuid)); // players that went offline
            }
            failureReported = false;
        } catch (RuntimeException e) {
            // Never let one bad render kill the task; report once per failure streak.
            if (!failureReported) {
                failureReported = true;
                plugin.getLogger().log(Level.SEVERE, "Tab list update failed", e);
            }
        } finally {
            metrics.recordTick(System.nanoTime() - started);
        }
    }

    private boolean isDue(Player player, int interval) {
        if (interval <= 1) {
            return true;
        }
        if (!setup.config().performance().spreadUpdates()) {
            return tick % interval == 0;
        }
        return Math.floorMod(tick + player.getUniqueId().hashCode(), interval) == 0;
    }

    private PlayerTabState track(Player player) {
        return players.computeIfAbsent(player.getUniqueId(),
                uuid -> new PlayerTabState(uuid, setup.ranks().resolve(player)));
    }

    private void beginFrame() {
        frameMillis = System.currentTimeMillis();
        frameCache.clear();
        if (sharedLines.size() > MAX_SHARED_LINES) {
            sharedLines.clear(); // animated lines produce new keys every frame
        }
    }

    private void refreshStats() {
        Collection<? extends Player> online = server.getOnlinePlayers();
        List<Player> vanished = new ArrayList<>();
        List<Player> staff = new ArrayList<>();
        for (Player player : online) {
            if (ServerStats.isVanished(player)) {
                vanished.add(player);
            }
            PlayerTabState state = players.get(player.getUniqueId());
            if (state != null && state.rank.staff()) {
                staff.add(player);
            }
        }
        double tps = Double.NaN;
        double mspt = Double.NaN;
        try {
            tps = server.getTPS()[0];
            mspt = server.getAverageTickTime();
        } catch (UnsupportedOperationException | ArrayIndexOutOfBoundsException ignored) {
            // not provided by this server implementation; shown as "unavailable"
        }
        stats = new ServerStats(online.size(), server.getMaxPlayers(), tps, mspt, List.copyOf(vanished), List.copyOf(staff));
        statsDirty = false;
        // Server tags (TPS, time, ...) are rebuilt from the new sample, so shared lines may change.
        frameServerTags = null;
        sharedLines.clear();
    }

    // ── header & footer ────────────────────────────────────────────────────

    private void updateHeaderFooter(Player player, PlayerTabState state, boolean force) {
        PluginConfig config = setup.config();
        PluginConfig.WorldSettings world = config.world(player.getWorld().getName());
        state.worldAlias = world.alias();
        state.mode = world.mode() != null ? world.mode() : config.branding().mode();
        if (!world.enabled()) {
            if (state.headerManaged) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                state.headerManaged = false;
                state.lastHeader = null;
                state.lastFooter = null;
            }
            return;
        }

        long started = System.nanoTime();
        Layout layout = selectLayout(player, state);
        LayoutFrame frame = frame(layout);
        TagResolver tags = viewerResolver(player, state.rank);
        Component header = setup.renderer().renderLines(frame.header(), player, tags, sharedLines);
        Component footer = setup.renderer().renderLines(frame.footer(), player, tags, sharedLines);
        metrics.recordRender(System.nanoTime() - started);

        if (force || !config.performance().skipUnchanged()
                || !header.equals(state.lastHeader) || !footer.equals(state.lastFooter)) {
            player.sendPlayerListHeaderAndFooter(header, footer);
            state.lastHeader = header;
            state.lastFooter = footer;
            state.headerManaged = true;
            metrics.sent();
        } else {
            metrics.skipped();
        }
    }

    private Layout selectLayout(Player player, PlayerTabState state) {
        PluginConfig config = setup.config();
        Layout selected = null;
        if (state.previewLayout != null) {
            if (System.currentTimeMillis() < state.previewUntil && config.layouts().containsKey(state.previewLayout)) {
                selected = config.layouts().get(state.previewLayout);
            } else {
                state.previewLayout = null;
            }
        }
        if (selected == null) {
            int online = stats.visibleOnline(player, config.vanish().hideFromCounts());
            String world = player.getWorld().getName();
            for (LayoutRule rule : config.layoutRules()) {
                if (rule.matches(world, state.rank.id(), online, player::hasPermission)) {
                    selected = config.layout(rule.layout());
                    break;
                }
            }
        }
        if (selected == null) {
            selected = config.layout(config.defaultLayout());
        }
        state.layoutId = selected.id();
        return selected;
    }

    /** Expanded templates of a layout for the current tick, shared by all viewers. */
    private LayoutFrame frame(Layout layout) {
        return frameCache.computeIfAbsent(layout.id(), id -> {
            PluginConfig config = setup.config();
            List<String> header = setup.animations().expandAll(layout.header(), frameMillis);
            List<String> footer = setup.animations().expandAll(layout.footer(), frameMillis);
            if (persistentState.maintenance() && !config.maintenance().lines().isEmpty()) {
                List<String> lines = setup.animations().expandAll(config.maintenance().lines(), frameMillis);
                if (config.maintenance().position() == PluginConfig.MaintenancePosition.HEADER_BOTTOM) {
                    header = concat(header, lines);
                } else {
                    footer = concat(lines, footer);
                }
            }
            return new LayoutFrame(header, footer);
        });
    }

    private TagResolver viewerResolver(@Nullable Player viewer, ResolvedRank rank) {
        if (frameServerTags == null) {
            ZonedDateTime now = ZonedDateTime.now(setup.config().placeholders().zone());
            frameServerTags = setup.placeholders().serverTags(stats, now);
        }
        return TagResolver.resolver(
                setup.placeholders().styleTags(),
                frameServerTags,
                setup.placeholders().viewerTags(viewer, rank, stats));
    }

    // ── player list entry ──────────────────────────────────────────────────

    private void updateListEntry(Player player, PlayerTabState state, boolean force) {
        PluginConfig config = setup.config();
        if (!config.playerList().enabled()) {
            return;
        }
        ResolvedRank rank = state.rank;
        TagResolver entryTags = TagResolver.resolver(
                Placeholder.component("rank_prefix", rank.prefix()),
                Placeholder.component("rank_suffix", rank.suffix()),
                TagResolver.resolver("name_color", rank.nameColor() != null
                        ? Tag.styling(rank.nameColor())
                        : Tag.styling(style -> { })),
                config.playerList().nameSource() == PluginConfig.NameSource.DISPLAY_NAME
                        ? Placeholder.component("player", player.displayName())
                        : Placeholder.unparsed("player", player.getName()),
                Placeholder.component("vanish_marker",
                        ServerStats.isVanished(player) ? setup.vanishMarker() : Component.empty()));
        // Entry tags come first so they win over the generic viewer tags.
        TagResolver tags = TagResolver.resolver(entryTags, viewerResolver(player, rank));
        Component name = setup.renderer().renderLine(setup.listTemplate(), player, tags);

        if (force || !config.performance().skipUnchanged() || !name.equals(state.lastListName)) {
            player.playerListName(name);
            state.lastListName = name;
            state.listManaged = true;
        }
        int order = config.playerList().sortByRank() ? rank.priority() : 0;
        if (force || order != state.lastOrder) {
            player.setPlayerListOrder(order);
            state.lastOrder = order;
            state.listManaged = true;
        }
    }

    private static void resetListEntry(Player player, PlayerTabState state) {
        if (state.listManaged) {
            player.playerListName(null);
            player.setPlayerListOrder(0);
            state.listManaged = false;
        }
    }

    // ── PlaceholderAPI expansion (any thread) ──────────────────────────────

    @Override
    public @Nullable String value(@Nullable UUID player, String key) {
        if (key.equals("maintenance")) {
            return Boolean.toString(persistentState.maintenance());
        }
        PlayerTabState state = player == null ? null : players.get(player);
        if (state == null) {
            return "";
        }
        ResolvedRank rank = state.rank;
        return switch (key) {
            case "rank" -> rank.displayName();
            case "rank_id" -> rank.id();
            case "rank_prefix" -> LEGACY.serialize(rank.prefix());
            case "rank_suffix" -> LEGACY.serialize(rank.suffix());
            case "rank_priority" -> Integer.toString(rank.priority());
            case "layout" -> state.layoutId;
            case "world_alias" -> state.worldAlias;
            case "mode" -> state.mode;
            default -> null;
        };
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> joined = new ArrayList<>(first.size() + second.size());
        joined.addAll(first);
        joined.addAll(second);
        return joined;
    }

    private record LayoutFrame(List<String> header, List<String> footer) {
    }
}
