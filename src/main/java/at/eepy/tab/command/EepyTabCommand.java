package at.eepy.tab.command;

import at.eepy.tab.EepyTabPlugin;
import at.eepy.tab.config.PluginConfig;
import at.eepy.tab.hook.LuckPermsHook;
import at.eepy.tab.hook.PlaceholderApiHook;
import at.eepy.tab.rank.ResolvedRank;
import at.eepy.tab.render.ServerStats;
import at.eepy.tab.tablist.RenderMetrics;
import at.eepy.tab.tablist.TabService;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * {@code /eepytab} (alias {@code /etab}) built with Paper's Brigadier API.
 * Subcommands are only suggested to senders that may use them.
 */
public final class EepyTabCommand {

    private static final String ADMIN = "eepytab.admin";
    private static final String RELOAD = "eepytab.reload";
    private static final String PREVIEW = "eepytab.preview";
    private static final String DEBUG = "eepytab.debug";
    private static final String MAINTENANCE = "eepytab.maintenance";

    private final EepyTabPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public EepyTabCommand(EepyTabPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("eepytab")
                .executes(this::info)
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("reload")
                        .requires(permission(RELOAD))
                        .executes(this::reload))
                .then(Commands.literal("preview")
                        .requires(permission(PREVIEW))
                        .executes(ctx -> preview(ctx, null))
                        .then(Commands.argument("layout", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String typed = builder.getRemainingLowerCase();
                                    plugin.tabService().setup().config().layouts().keySet().stream()
                                            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(typed))
                                            .forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> preview(ctx, StringArgumentType.getString(ctx, "layout")))))
                .then(Commands.literal("debug")
                        .requires(permission(DEBUG))
                        .executes(ctx -> debug(ctx, null))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(ctx -> debug(ctx, ctx.getArgument("player", PlayerSelectorArgumentResolver.class)
                                        .resolve(ctx.getSource()).getFirst()))))
                .then(Commands.literal("maintenance")
                        .requires(permission(MAINTENANCE))
                        .executes(ctx -> maintenance(ctx, null))
                        .then(Commands.literal("on").executes(ctx -> maintenance(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> maintenance(ctx, false))))
                .build();
    }

    // ── subcommands ────────────────────────────────────────────────────────

    private int info(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(prefixed("<text>EepyTab <primary><version></primary> <subtle>· tab list design for <server_name></subtle></text>",
                Placeholder.unparsed("version", plugin.getPluginMeta().getVersion())));
        sender.sendMessage(plain("<muted>Website</muted> <secondary><website></secondary>  <line>·</line>  "
                + "<muted>Discord</muted> <primary><discord></primary>  <line>·</line>  <muted>Play</muted> <highlight><ip></highlight>"));
        sender.sendMessage(plain("<subtle>Type</subtle> <text>/eepytab help</text> <subtle>for all commands.</subtle>"));
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(prefixed("<highlight>Commands</highlight>"));
        helpLine(sender, null, "/eepytab", "Show plugin information");
        helpLine(sender, null, "/eepytab help", "Show this list");
        helpLine(sender, RELOAD, "/eepytab reload", "Reload config.yml");
        helpLine(sender, PREVIEW, "/eepytab preview [layout]", "Refresh your tab list or preview a layout");
        helpLine(sender, DEBUG, "/eepytab debug [player]", "Show technical information");
        helpLine(sender, MAINTENANCE, "/eepytab maintenance [on|off]", "Toggle the maintenance display");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        EepyTabPlugin.ReloadResult result = plugin.reload();
        if (!result.success()) {
            sender.sendMessage(message("reload-failed",
                    Placeholder.unparsed("error", result.error() == null ? "unknown error" : result.error())));
            return 0;
        }
        sender.sendMessage(message("reload-success", Placeholder.unparsed("ms", Long.toString(result.millis()))));
        if (!result.warnings().isEmpty()) {
            sender.sendMessage(message("reload-warnings",
                    Placeholder.unparsed("count", Integer.toString(result.warnings().size()))));
            if (!(sender instanceof Player)) {
                return Command.SINGLE_SUCCESS; // the console already got them from the logger
            }
            result.warnings().forEach(warning -> sender.sendMessage(plain("<subtle>• <warning></subtle>",
                    Placeholder.unparsed("warning", warning))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int preview(CommandContext<CommandSourceStack> ctx, @Nullable String layoutId) {
        CommandSender sender = ctx.getSource().getSender();
        TabService service = plugin.tabService();
        PluginConfig config = service.setup().config();
        if (layoutId != null && !config.layouts().containsKey(layoutId)) {
            sender.sendMessage(message("unknown-layout",
                    Placeholder.unparsed("layout", layoutId),
                    Placeholder.unparsed("layouts", String.join(", ", config.layouts().keySet()))));
            return 0;
        }
        if (sender instanceof Player player) {
            if (layoutId == null) {
                service.refreshNow(player);
                sender.sendMessage(message("preview-refreshed", Placeholder.unparsed("layout", service.currentLayout(player))));
            } else {
                service.preview(player, layoutId);
                sender.sendMessage(message("preview-layout",
                        Placeholder.unparsed("layout", layoutId),
                        Placeholder.unparsed("seconds", Integer.toString(config.previewDurationSeconds()))));
            }
            return Command.SINGLE_SUCCESS;
        }
        // Console: print the rendered design, rendered for a neutral viewer.
        PluginConfig.Layout layout = config.layout(layoutId != null ? layoutId : config.defaultLayout());
        Component[] rendered = service.renderPreview(null, layout);
        sender.sendMessage(prefixed("<text>Preview of layout <primary><layout></primary></text>",
                Placeholder.unparsed("layout", layout.id())));
        sender.sendMessage(plain("<subtle>── header ──</subtle>"));
        sender.sendMessage(rendered[0]);
        sender.sendMessage(plain("<subtle>── footer ──</subtle>"));
        sender.sendMessage(rendered[1]);
        return Command.SINGLE_SUCCESS;
    }

    private int debug(CommandContext<CommandSourceStack> ctx, @Nullable Player target) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        TabService service = plugin.tabService();
        PluginConfig config = service.setup().config();
        ServerStats stats = service.stats();
        RenderMetrics metrics = service.metrics();

        sender.sendMessage(prefixed("<highlight>Debug</highlight>"));
        row(sender, "Plugin", "EepyTab " + plugin.getPluginMeta().getVersion() + " · " + Bukkit.getName() + " "
                + Bukkit.getVersion() + " · Minecraft " + Bukkit.getMinecraftVersion() + " · Java " + Runtime.version());
        row(sender, "Hooks", hookSummary(config));
        List<String> others = plugin.detectOtherTabPlugins();
        row(sender, "Conflicts", others.isEmpty() ? "none detected" : String.join(", ", others));
        row(sender, "Players", "tracked " + service.trackedPlayers() + " · online " + stats.online() + "/" + stats.max()
                + " · vanished " + stats.vanished().size() + " · staff " + stats.staff().size());
        row(sender, "Server", "TPS " + decimal(stats.tps(), 2) + " · MSPT " + decimal(stats.mspt(), 2));
        PluginConfig.Intervals intervals = config.intervals();
        row(sender, "Intervals", "header/footer " + intervals.headerFooter() + "t · player list " + intervals.playerList()
                + "t · stats " + intervals.serverStats() + "t · spread " + onOff(config.performance().spreadUpdates())
                + " · skip unchanged " + onOff(config.performance().skipUnchanged()));
        row(sender, "Render", decimal(metrics.averageRenderMicros(), 1) + "µs per player · task "
                + decimal(metrics.averageTickMillis(), 3) + "ms/tick (max " + decimal(metrics.maxTickMillis(), 2)
                + "ms) · sent " + metrics.packetsSent() + " · skipped " + metrics.packetsSkipped());
        PluginConfig.Animations animations = config.animations();
        row(sender, "Animations", onOff(animations.enabled()) + " · gradient " + animations.gradientCycleMillis() / 1000.0
                + "s · pulse " + animations.pulseCycleMillis() / 1000.0 + "s · " + animations.texts().size() + " texts");
        row(sender, "Layouts", String.join(", ", config.layouts().keySet()) + " (default " + config.defaultLayout()
                + ", " + config.layoutRules().size() + " rules)");
        row(sender, "Maintenance", onOff(service.maintenance()));
        row(sender, "Config", plugin.configWarnings().size() + " warning(s)");

        Player subject = target != null ? target : sender instanceof Player self ? self : null;
        if (subject != null) {
            ResolvedRank rank = service.rankOf(subject);
            row(sender, "Player", subject.getName() + " · world " + subject.getWorld().getName() + " · ping "
                    + subject.getPing() + "ms · layout " + service.currentLayout(subject)
                    + (ServerStats.isVanished(subject) ? " · vanished" : ""));
            if (rank != null) {
                row(sender, "Rank", rank.id() + " (" + rank.displayName() + ", priority " + rank.priority()
                        + (rank.staff() ? ", staff" : "") + ") via " + rank.source());
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int maintenance(CommandContext<CommandSourceStack> ctx, @Nullable Boolean enable) {
        CommandSender sender = ctx.getSource().getSender();
        TabService service = plugin.tabService();
        if (enable == null) {
            sender.sendMessage(message("maintenance-status",
                    Placeholder.unparsed("state", service.maintenance() ? "on" : "off")));
            return Command.SINGLE_SUCCESS;
        }
        service.setMaintenance(enable);
        sender.sendMessage(message(enable ? "maintenance-enabled" : "maintenance-disabled"));
        return Command.SINGLE_SUCCESS;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private String hookSummary(PluginConfig config) {
        List<String> parts = new ArrayList<>();
        LuckPermsHook luckPerms = plugin.luckPerms();
        parts.add(luckPerms == null ? "LuckPerms not installed"
                : "LuckPerms " + luckPerms.version() + (config.integrations().luckPerms() ? " (active)" : " (disabled)"));
        PlaceholderApiHook placeholderApi = plugin.placeholderApi();
        if (placeholderApi == null) {
            parts.add("PlaceholderAPI not installed");
        } else {
            Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            String version = papi == null ? "?" : papi.getPluginMeta().getVersion();
            parts.add("PlaceholderAPI " + version + (config.integrations().placeholderApi() ? " (active" : " (disabled")
                    + (placeholderApi.expansionRegistered() ? ", expansion registered)" : ")"));
        }
        return String.join(" · ", parts);
    }

    private void helpLine(CommandSender sender, @Nullable String permission, String usage, String description) {
        if (permission == null || allowed(sender, permission)) {
            sender.sendMessage(plain("<primary><usage></primary> <line>›</line> <text><description></text>",
                    Placeholder.unparsed("usage", usage), Placeholder.unparsed("description", description)));
        }
    }

    private void row(CommandSender sender, String label, String value) {
        sender.sendMessage(plain("<muted><label></muted> <line>›</line> <text><value></text>",
                Placeholder.unparsed("label", String.format(Locale.ROOT, "%-11s", label)),
                Placeholder.unparsed("value", value)));
    }

    private static Predicate<CommandSourceStack> permission(String node) {
        return source -> allowed(source.getSender(), node);
    }

    private static boolean allowed(CommandSender sender, String node) {
        return sender.hasPermission(node) || sender.hasPermission(ADMIN);
    }

    private Component message(String key, TagResolver... resolvers) {
        return prefixed(plugin.tabService().setup().config().messages().get(key), resolvers);
    }

    private Component prefixed(String text, TagResolver... resolvers) {
        return plain(plugin.tabService().setup().config().messages().prefix() + text, resolvers);
    }

    private Component plain(String text, TagResolver... resolvers) {
        TagResolver styles = plugin.tabService().setup().placeholders().styleTags();
        return miniMessage.deserialize(text, TagResolver.resolver(styles, TagResolver.resolver(resolvers)));
    }

    private static String decimal(double value, int digits) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%." + digits + "f", value);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
