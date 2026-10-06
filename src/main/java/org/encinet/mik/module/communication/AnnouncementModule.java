package org.encinet.mik.module.communication;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuAnimation;
import org.encinet.mik.module.menu.FloatingMenuEasing;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuPlacement;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuScreen;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.util.ShutdownSequence;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class AnnouncementModule implements Listener {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int JOIN_PUSH_LIMIT = 5;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final AnnouncementSeenStore seenStore;
    private final AnnouncementCatalog catalog;
    private final FloatingMenuScreen<MenuState> menuScreen;
    private AnnouncementCatalog.Snapshot boardSnapshot;
    private AnnouncementBoard board = new AnnouncementBoard(List.of());

    public AnnouncementModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.seenStore = new AnnouncementSeenStore(
                plugin.getDataFolder().toPath().resolve("announcements-state.yml"),
                plugin.getLogger());
        this.catalog = new AnnouncementCatalog(
                plugin.getDataFolder().toPath().resolve("announcements.txt"),
                plugin.getLogger());
        this.menuScreen = new FloatingMenuScreen<>("announcements",
                ignored -> new MenuState(0, 0, AnnouncementSlide.rest(0)), this::buildAnnouncementsMenu);
    }

    public void enable() {
        reload(false);
        seenStore.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("announcement listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("announcement read positions", seenStore::save);
        shutdown.finish("announcement module");
    }

    //  reload

    public boolean reload() {
        return reload(true);
    }

    private boolean reload(boolean broadcastAdded) {
        List<Announcement> old = catalog.current().announcements();
        List<Announcement> announcements;
        try {
            announcements = catalog.reload().announcements();
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Failed to read announcements.txt", error);
            return false;
        }

        Set<Long> oldTimestamps = old.stream()
                .map(Announcement::timestamp)
                .collect(Collectors.toSet());

        List<Announcement> added = announcements.stream()
                .filter(a -> !oldTimestamps.contains(a.timestamp()))
                .toList();

        if (!old.equals(announcements)) {
            menuScreen.updateWhere(state -> true, state -> settledState(new MenuState(
                    remapSelectedIndex(old, announcements, state.announcementIndex()),
                    state.part(), state.slide())));
        }
        if (broadcastAdded && !added.isEmpty()) {
            broadcastNewAnnouncements(added);
        }
        return true;
    }

    static int remapSelectedIndex(List<Announcement> previous,
                                  List<Announcement> current, int requestedIndex) {
        if (previous.isEmpty() || current.isEmpty()) return 0;
        Announcement selected = previous.get(Math.clamp(requestedIndex, 0,
                previous.size() - 1));
        int exact = current.indexOf(selected);
        if (exact >= 0) return exact;
        for (int index = 0; index < current.size(); index++) {
            if (current.get(index).timestamp() == selected.timestamp()) return index;
        }
        return Math.clamp(requestedIndex, 0, current.size() - 1);
    }

    //  聊天消息

    /**
     * /reloadannouncements 触发后广播给在线玩家
     */
    private void broadcastNewAnnouncements(List<Announcement> added) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Component header = chatHeader(languageService.t(player, Message.ANNOUNCEMENT_UPDATED),
                    Component.text(languageService.t(player,
                            Message.ANNOUNCEMENT_NEW_COUNT, added.size()), NamedTextColor.YELLOW));
            player.sendMessage(chatAnnouncementBlock(header, added, chatFooterClickable(player)));
            markSeenThroughLatest(player.getUniqueId());
        }
        seenStore.save();
    }

    /**
     * 玩家上线后推送最近没有看过的公告
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long latestTimestamp = latestAnnouncementTimestamp();
        if (latestTimestamp <= 0) return;

        UUID playerId = player.getUniqueId();
        long seenUntil = seenStore.positionOrStartAt(playerId, latestTimestamp);
        List<Announcement> announcements = catalog.current().announcements();

        List<Announcement> unseenAnnouncements = announcements.stream()
                .filter(a -> a.timestamp() > seenUntil)
                .sorted(Comparator.comparingLong(Announcement::timestamp).reversed())
                .limit(JOIN_PUSH_LIMIT)
                .toList();

        if (unseenAnnouncements.isEmpty()) {
            seenStore.save();
            return;
        }

        int total = (int) announcements.stream().filter(a -> a.timestamp() > seenUntil).count();

        Component header = chatHeader(languageService.t(player, Message.MAIN_ANNOUNCEMENTS),
                Component.text(languageService.t(player,
                        Message.ANNOUNCEMENT_UNREAD_COUNT, total), NamedTextColor.YELLOW));
        Component footer;
        if (total > JOIN_PUSH_LIMIT) {
            footer = Component.text("  ", NamedTextColor.GRAY)
                    .append(Component.text(languageService.t(player,
                            Message.ANNOUNCEMENT_MORE_COUNT, total - JOIN_PUSH_LIMIT) + "  ",
                            NamedTextColor.GRAY))
                    .append(chatFooterClickable(player));
        } else {
            footer = chatFooterClickable(player);
        }
        player.sendMessage(chatAnnouncementBlock(header, unseenAnnouncements, footer));
        seenStore.markSeenThrough(playerId, latestTimestamp);
        seenStore.save();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        menuScreen.forget(event.getPlayer());
    }

    private Component chatHeader(String title, Component badge) {
        return Component.text()
                .append(Component.text("━━ ", NamedTextColor.GOLD))
                .append(Component.text(title, NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("  "))
                .append(badge)
                .append(Component.text("  ━━", NamedTextColor.GOLD))
                .build();
    }

    private Component chatAnnouncementBlock(Component header, List<Announcement> entries, Component footer) {
        Component block = header;
        for (Announcement announcement : entries) {
            block = block.append(Component.newline())
                    .append(chatAnnouncementLine(announcement));
        }
        return block.append(Component.newline())
                .append(footer);
    }

    /**
     * 单条公告行：  ◆ [日期]  内容
     */
    private Component chatAnnouncementLine(Announcement a) {
        String dateStr = java.time.Instant.ofEpochSecond(a.timestamp())
                .atZone(ZoneId.systemDefault())
                .format(DISPLAY_FMT);

        return Component.text()
                .append(Component.text("  ◆ ", NamedTextColor.GOLD))
                .append(Component.text("[" + dateStr + "] ", NamedTextColor.DARK_AQUA))
                .append(Component.text(a.content(), NamedTextColor.WHITE))
                .build();
    }

    /**
     * 底部可点击行：  ▶ 查看全部公告（点击）
     */
    private Component chatFooterClickable(Player player) {
        return Component.text()
                .append(Component.text("  ▶ ", NamedTextColor.AQUA))
                .append(Component.text(languageService.t(player,
                                Message.ANNOUNCEMENT_VIEW_ALL), NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/announcements")))
                .build();
    }

    //  GUI 菜单

    public void openAnnouncementsMenu(Player player) {
        markSeenThroughLatest(player.getUniqueId());
        seenStore.save();
        menuScreen.open(player);
    }

    private AnnouncementBoard board() {
        AnnouncementCatalog.Snapshot snapshot = catalog.current();
        if (boardSnapshot != snapshot) {
            board = new AnnouncementBoard(snapshot.announcements());
            boardSnapshot = snapshot;
        }
        return board;
    }

    private FloatingMenuDefinition buildAnnouncementsMenu(FloatingMenuContext<MenuState> context) {
        Player player = context.player();
        AnnouncementBoard board = board();
        MenuState state = normalizeState(context.state());
        var position = new AnnouncementBoard.Position(state.announcementIndex(), state.part());
        boolean spatial = FloatingMenus.supportsSpatialScenes(player);
        double typography = FloatingMenus.preferences(player).typographyFactor();
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("announcements")
                .appearance(FloatingMenuAppearance.NO_BACKGROUNDS)
                .framing(FloatingMenuFraming.COMFORTABLE)
                .animation(new FloatingMenuAnimation(7, 5, 4, 0.04, 0.06, 0, 0,
                        FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT))
                .stableAnchor()
                .layout(AnnouncementBoardLayout.create(typography));
        if (spatial) {
            menu.framingDecorations("wall");
            menu.decoration(FloatingMenuDecoration.volume("wall",
                    FloatingMenuPose.at(new FloatingMenuPoint(0, 0, -0.04)),
                    Material.BLACK_CONCRETE.createBlockData(),
                    (float) (AnnouncementBoardLayout.WIDTH * typography),
                    (float) (AnnouncementBoardLayout.HEIGHT * typography), 0.025F));
            menu.refreshEvery(1, (viewer, handle) -> {
                if (state.slide().startedAt() != 0) {
                    if (state.slide().moving(System.nanoTime())) context.redraw();
                    else context.update(this::settledState);
                } else if (FloatingMenus.preferences(viewer).typographyFactor() != typography) {
                    context.redraw();
                }
            });
        }
        Component heading = Component.text(languageService.t(player, Message.MAIN_ANNOUNCEMENTS), NamedTextColor.GOLD);
        if (spatial && board.size() > 1)
            heading = heading.append(Component.newline()).append(Component.text(
                    languageService.t(player, Message.ANNOUNCEMENT_SCROLL_HINT), NamedTextColor.GRAY));
        menu.information("heading", heading).region("heading").textWidth(FloatingMenuTextWidth.WIDE).keepAccessible();
        if (board.size() == 0) {
            menu.information("empty", Component.text(languageService.t(player,
                            Message.ANNOUNCEMENT_EMPTY), NamedTextColor.GRAY))
                    .region("body").textWidth(FloatingMenuTextWidth.WIDE).keepAccessible();
        } else {
            if (spatial) {
                for (var fragment : AnnouncementViewport.fragments(board, state.slide().position(System.nanoTime()),
                        page -> announcementDate(page.announcement()))) {
                    var page = board.page(fragment.page());
                    menu.decoration(new FloatingMenuDecoration(
                            noticeId(page.announcement(), page.announcementIndex()) + ":" + page.part() + ":" + fragment.row(),
                            FloatingMenuPlacement.local(AnnouncementBoardLayout.fragmentPose(fragment, typography)),
                            new FloatingMenuDecoration.Text(Component.text(fragment.text(),
                                    fragment.row() == 0 ? NamedTextColor.AQUA : NamedTextColor.WHITE),
                                    0, Math.max(0.25F, (fragment.width() + 4) * 0.025F), 0.25F,
                                    AnnouncementViewport.FONT_SCALE, FloatingMenuDecoration.Alignment.LEFT, false),
                            FloatingMenuDecoration.Motion.NONE).tracking());
                }
            } else {
                var page = board.page(board.start(position));
                String date = announcementDate(page.announcement());
                if (page.parts() > 1) date += " · " + (page.part() + 1) + "/" + page.parts();
                menu.information("body", Component.text(date, NamedTextColor.AQUA)
                                .append(Component.text("\n\n"))
                                .append(Component.text(page.text(), NamedTextColor.WHITE)))
                        .region("body").alignment(FloatingMenuDecoration.Alignment.LEFT)
                        .textWidth(FloatingMenuTextWidth.WIDE);
            }
            if (board.maximumStart() > 0) {
                var newer = menu.navigation("newer", Component.text("‹ " + languageService.t(player,
                                Message.ANNOUNCEMENT_NEWER), NamedTextColor.AQUA))
                        .region("browse").keepAccessible().primary((viewer, handle) ->
                                context.update(value -> moveBoard(value, -1, spatial)));
                if (board.start(position) == 0) newer.disabled(Component.text(languageService.t(player,
                        Message.ANNOUNCEMENT_FIRST_PAGE), NamedTextColor.GRAY));
                var older = menu.navigation("older", Component.text(languageService.t(player,
                                Message.ANNOUNCEMENT_OLDER) + " ›", NamedTextColor.AQUA))
                        .region("browse").keepAccessible().primary((viewer, handle) ->
                                context.update(value -> moveBoard(value, 1, spatial)));
                if (board.start(position) == board.maximumStart()) older.disabled(Component.text(languageService.t(player,
                        Message.ANNOUNCEMENT_LAST_PAGE), NamedTextColor.GRAY));
                menu.on(FloatingMenuInteraction.SCROLL_UP, (viewer, handle, input) ->
                        context.update(value -> moveBoard(value, -1, spatial)));
                menu.on(FloatingMenuInteraction.SCROLL_DOWN, (viewer, handle, input) ->
                        context.update(value -> moveBoard(value, 1, spatial)));
            }
        }
        if (player.hasPermission("mik.command.reloadannouncements")) {
            menu.control("reload", Component.text(languageService.t(player,
                            Message.ANNOUNCEMENT_RELOAD), NamedTextColor.GREEN))
                    .region("footer").textWidth(FloatingMenuTextWidth.RING)
                    .primary((viewer, handle) -> sendReloadResult(viewer, reload()));
        }
        menu.dismiss(Component.text(languageService.t(player,
                        context.canGoBack() ? Message.BACK_TO_MAIN : Message.CLOSE), NamedTextColor.RED))
                .region("footer").textWidth(FloatingMenuTextWidth.RING).keepAccessible();
        return menu.build();
    }

    private String announcementDate(Announcement announcement) {
        return Instant.ofEpochSecond(announcement.timestamp())
                .atZone(ZoneId.systemDefault()).format(DISPLAY_FMT);
    }

    private static String noticeId(Announcement announcement, int index) {
        return "notice:" + announcement.timestamp() + ":" + index;
    }

    private MenuState normalizeState(MenuState requested) {
        var position = board().normalize(new AnnouncementBoard.Position(requested.announcementIndex(), requested.part()));
        int target = board().start(position);
        AnnouncementSlide slide = requested.slide().target() == target ? requested.slide() : AnnouncementSlide.rest(target);
        return new MenuState(position.announcementIndex(), position.part(), slide);
    }

    private MenuState settledState(MenuState requested) {
        MenuState state = normalizeState(requested);
        return new MenuState(state.announcementIndex(), state.part(), AnnouncementSlide.rest(state.slide().target()));
    }

    private MenuState moveBoard(MenuState requested, int direction, boolean spatial) {
        MenuState state = normalizeState(requested);
        var position = board().move(new AnnouncementBoard.Position(state.announcementIndex(), state.part()), direction);
        int target = board().start(position);
        AnnouncementSlide slide = spatial ? state.slide().move(target, System.nanoTime()) : AnnouncementSlide.rest(target);
        return new MenuState(position.announcementIndex(), position.part(), slide);
    }

    private record MenuState(int announcementIndex, int part, AnnouncementSlide slide) { }

    private long latestAnnouncementTimestamp() {
        return catalog.current().announcements().stream()
                .mapToLong(Announcement::timestamp)
                .max()
                .orElse(0L);
    }

    private void markSeenThroughLatest(UUID playerId) {
        long latestTimestamp = latestAnnouncementTimestamp();
        if (latestTimestamp <= 0) return;

        seenStore.markSeenThrough(playerId, latestTimestamp);
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("reloadannouncements")
                    .requires(source -> source.getSender().hasPermission("mik.command.reloadannouncements"))
                    .executes(ctx -> {
                        boolean success = reload();
                        sendReloadResult(ctx.getSource().getSender(), success);
                        return success ? Command.SINGLE_SUCCESS : 0;
                    }).build(), languageService.t(Language.DEFAULT,
                            Message.ANNOUNCEMENT_RELOAD_COMMAND_DESCRIPTION));

            event.registrar().register(Commands.literal("announcements")
                    .executes(ctx -> {
                        if (ctx.getSource().getSender() instanceof Player player) {
                            openAnnouncementsMenu(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), languageService.t(Language.DEFAULT,
                            Message.ANNOUNCEMENT_VIEW_COMMAND_DESCRIPTION));
        });
    }

    public byte[] getAnnouncementsJsonBytes() {
        return catalog.current().jsonBytes();
    }

    private void sendReloadResult(CommandSender sender, boolean success) {
        Message message = success ? Message.ANNOUNCEMENT_RELOADED
                : Message.ANNOUNCEMENT_RELOAD_FAILED;
        String reply = sender instanceof Player player
                ? languageService.t(player, message)
                : languageService.t(Language.DEFAULT, message);
        sender.sendMessage(Component.text(reply,
                success ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
