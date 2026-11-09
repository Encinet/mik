package org.encinet.mik.feature.community.communication;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.encinet.mik.feature.community.chat.bridge.ServerChatPublisher;
import org.encinet.mik.feature.community.chat.model.ChatServerMessage;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.community.communication.AnnouncementCatalog.Announcement;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Level;

public final class AnnouncementModule implements Listener, AnnouncementHighlights {

    private static final int JOIN_PUSH_LIMIT = 5;

    private final JavaPlugin plugin;
    private final ServerChatPublisher serverChat;
    private final LanguageService languageService;
    private final AnnouncementSeenStore seenStore;
    private final AnnouncementCatalog catalog;
    private final FloatingMenuScreen<AnnouncementBrowserState> menuScreen;
    private AnnouncementCatalog.Snapshot boardSnapshot;
    private AnnouncementBoard board = new AnnouncementBoard(List.of());

    public AnnouncementModule(JavaPlugin plugin, LanguageService languageService, ServerChatPublisher serverChat) {
        this.serverChat = java.util.Objects.requireNonNull(serverChat, "serverChat");
        this.plugin = plugin;
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.seenStore = new AnnouncementSeenStore(
                plugin.getDataFolder().toPath().resolve("announcements-state.yml"),
                plugin.getLogger());
        this.catalog = new AnnouncementCatalog(
                plugin.getDataFolder().toPath().resolve("announcements.txt"),
                plugin.getLogger());
        this.menuScreen = new FloatingMenuScreen<>("announcements",
                _ -> AnnouncementBrowserState.INITIAL, this::buildAnnouncementsMenu);
    }

    public void enable() {
        reload(false);
        seenStore.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("announcement listeners", () -> HandlerList.unregisterAll(this));
        shutdown.attempt("announcement menus", menuScreen::forgetAll);
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
            menuScreen.updateWhere(_ -> true, state -> state.remap(old, board()));
        }
        if (broadcastAdded && !added.isEmpty()) {
            broadcastNewAnnouncements(added);
        }
        return true;
    }

    //  聊天消息

    /**
     * /reloadannouncements 触发后广播给在线玩家
     */
    private void broadcastNewAnnouncements(List<Announcement> added) {
        for (Announcement announcement : added) {
            ChatContent preview = ChatContent.plainPreview(announcement.content());
            if (preview.plainText().isBlank()) continue;
            serverChat.publish(ChatServerMessage.publicMessage(new ChatServerMessage.RichText(preview), null, true));
        }
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
        String dateStr = AnnouncementReading.date(a.timestamp());

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
        openAnnouncementsMenu(player, menuScreen.state(player).orElse(AnnouncementBrowserState.INITIAL).normalize(board()));
    }

    private void openAnnouncementsMenu(Player player, AnnouncementBrowserState state) {
        markSeenThroughLatest(player.getUniqueId());
        seenStore.save();
        menuScreen.open(player, state);
    }

    @Override public MenuPanel latestPanel(Player player) {
        return () -> MenuScene.screen("latest-announcement").content(AnnouncementPreview.content(
                catalog.current().announcements().stream().findFirst().orElse(null),
                languageService.text(player, Message.ANNOUNCEMENT_LATEST, NamedTextColor.GOLD),
                languageService.text(player, Message.ANNOUNCEMENT_VIEW_ALL, NamedTextColor.AQUA),
                (viewer, _) -> openAnnouncementsMenu(viewer,
                        menuScreen.state(viewer).orElse(AnnouncementBrowserState.INITIAL).latest(board())))).build();
    }

    @Override public Object revision() {
        return catalog.current();
    }

    private AnnouncementBoard board() {
        AnnouncementCatalog.Snapshot snapshot = catalog.current();
        if (boardSnapshot != snapshot) {
            board = new AnnouncementBoard(snapshot.announcements());
            boardSnapshot = snapshot;
        }
        return board;
    }

    private MenuScene buildAnnouncementsMenu(FloatingMenuContext<AnnouncementBrowserState> context) {
        Player player = context.player();
        return AnnouncementMenu.build(board(), context.state(), FloatingMenus.preferences(player).typographyFactor(),
                player.hasPermission("mik.command.reloadannouncements"), context.canGoBack(),
                (message, args) -> languageService.t(player, message, args), context::update, () -> {
                    if (player.hasPermission("mik.command.reloadannouncements")) sendReloadResult(player, reload());
                });
    }

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
