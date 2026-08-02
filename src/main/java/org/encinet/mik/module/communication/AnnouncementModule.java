package org.encinet.mik.module.communication;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuScreen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AnnouncementModule implements Listener {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年MM月");
    private static final int JOIN_PUSH_LIMIT = 5;
    private static final String MENU_TITLE = "服务器公告";
    private static final int ANNOUNCEMENTS_PER_PAGE = 6;
    private static final int TEXT_LINE_WIDTH = 36;

    private final JavaPlugin plugin;
    private final File stateFile;
    private final Map<UUID, Long> playerSeenUntil = new ConcurrentHashMap<>();
    private final FloatingMenuScreen<MenuState> menuScreen;
    private List<Announcement> announcements = List.of();
    private volatile String announcementsJson = "[]";
    private volatile byte[] announcementsJsonBytes = "[]".getBytes(StandardCharsets.UTF_8);

    public AnnouncementModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "announcements-state.yml");
        this.menuScreen = new FloatingMenuScreen<>("announcements",
                ignored -> new MenuState(null, 0), this::buildAnnouncementsMenu);
    }

    public void enable() {
        reload(false);
        loadState();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        saveState();
    }

    //  reload

    public void reload() {
        reload(true);
    }

    private void reload(boolean broadcastAdded) {
        List<Announcement> old = this.announcements;
        List<Announcement> loaded = loadAnnouncementsFromFile();
        long cutoff = java.time.Instant.now().minusSeconds(365L * 24 * 60 * 60).getEpochSecond();
        announcements = loaded.stream()
                .filter(a -> a.timestamp() > cutoff)
                .toList();
        buildJsonCache();

        Set<Long> oldTimestamps = old.stream()
                .map(Announcement::timestamp)
                .collect(Collectors.toSet());

        List<Announcement> added = announcements.stream()
                .filter(a -> !oldTimestamps.contains(a.timestamp()))
                .toList();

        if (broadcastAdded && !added.isEmpty()) {
            broadcastNewAnnouncements(added);
        }
    }

    //  聊天消息

    /**
     * /reloadannouncements 触发后广播给在线玩家
     */
    private void broadcastNewAnnouncements(List<Announcement> added) {
        Component header = chatHeader("服务器公告更新",
                Component.text("新增 " + added.size() + " 条", NamedTextColor.YELLOW));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(chatAnnouncementBlock(header, added, chatFooterClickable()));
            markSeenThroughLatest(player.getUniqueId());
        }
        saveState();
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
        long seenUntil = playerSeenUntil.getOrDefault(playerId, latestTimestamp);
        playerSeenUntil.putIfAbsent(playerId, seenUntil);

        List<Announcement> unseenAnnouncements = announcements.stream()
                .filter(a -> a.timestamp() > seenUntil)
                .sorted(Comparator.comparingLong(Announcement::timestamp).reversed())
                .limit(JOIN_PUSH_LIMIT)
                .toList();

        if (unseenAnnouncements.isEmpty()) {
            saveState();
            return;
        }

        int total = (int) announcements.stream().filter(a -> a.timestamp() > seenUntil).count();

        Component header = chatHeader("服务器公告",
                Component.text(total + " 条未读公告", NamedTextColor.YELLOW));
        Component footer;
        if (total > JOIN_PUSH_LIMIT) {
            footer = Component.text("  ", NamedTextColor.GRAY)
                    .append(Component.text("还有 " + (total - JOIN_PUSH_LIMIT) + " 条未显示  ", NamedTextColor.GRAY))
                    .append(chatFooterClickable());
        } else {
            footer = chatFooterClickable();
        }
        player.sendMessage(chatAnnouncementBlock(header, unseenAnnouncements, footer));
        playerSeenUntil.put(playerId, latestTimestamp);
        saveState();
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
    private Component chatFooterClickable() {
        return Component.text()
                .append(Component.text("  ▶ ", NamedTextColor.AQUA))
                .append(Component.text("查看全部公告", NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/announcements"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("打开公告菜单", NamedTextColor.GRAY))))
                .append(Component.text("  （点击）", NamedTextColor.GRAY))
                .build();
    }

    //  GUI 菜单

    public void openAnnouncementsMenu(Player player) {
        markSeenThroughLatest(player.getUniqueId());
        saveState();
        menuScreen.open(player);
    }

    private FloatingMenuDefinition buildAnnouncementsMenu(FloatingMenuContext<MenuState> context) {
        Player player = context.player();
        MenuState requestedState = context.state();
        MenuState state = normalizeState(requestedState);

        List<Announcement> visible = filterAnnouncements(state.monthFilter());
        FloatingMenuPage pagination = new FloatingMenuPage(
                state.page(), visible.size(), ANNOUNCEMENTS_PER_PAGE);
        int totalPages = pagination.count();
        int page = pagination.index();
        if (page != state.page()) {
            state = new MenuState(state.monthFilter(), page);
        }

        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "announcements",
                        Component.text(MENU_TITLE, NamedTextColor.DARK_PURPLE))
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.actions("toolbar", 4),
                        FloatingMenuLayouts.information("summary"),
                        FloatingMenuLayouts.cards("content", 2, 3),
                        FloatingMenuLayouts.actions("footer", 4)));

        boolean hasEarlierMonth = hasAdjacentMonth(state.monthFilter(), -1);
        var previousMonth = menu.navigation("month:previous",
                        Component.text("‹ 上个月", hasEarlierMonth
                                        ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)
                                .decorate(TextDecoration.BOLD))
                .region("toolbar");
        if (hasEarlierMonth) {
            previousMonth.primary((p, handle) -> context.update(value ->
                    new MenuState(shiftMonth(value.monthFilter(), -1), 0)));
        } else {
            previousMonth.disabled(Component.text("没有更早的月份", NamedTextColor.GRAY));
        }

        menu.item("range:all", Material.COMPASS,
                        Component.text("全部公告", NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .region("toolbar")
                .primary((p, handle) -> context.setState(new MenuState(null, 0)));
        menu.information("summary", buildSummary(state, visible.size(), totalPages))
                .region("summary");
        menu.item("range:latest", Material.CLOCK,
                        Component.text("最新月份", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .region("toolbar")
                .primary((p, handle) -> context.setState(new MenuState(latestMonth(), 0)));

        boolean hasLaterMonth = hasAdjacentMonth(state.monthFilter(), 1);
        var nextMonth = menu.navigation("month:next",
                        Component.text("下个月 ›", hasLaterMonth
                                        ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)
                                .decorate(TextDecoration.BOLD))
                .region("toolbar");
        if (hasLaterMonth) {
            nextMonth.primary((p, handle) -> context.update(value ->
                    new MenuState(shiftMonth(value.monthFilter(), 1), 0)));
        } else {
            nextMonth.disabled(Component.text("没有更晚的月份", NamedTextColor.GRAY));
        }

        int start = page * ANNOUNCEMENTS_PER_PAGE;
        int end = Math.min(start + ANNOUNCEMENTS_PER_PAGE, visible.size());
        if (start == end) {
            menu.information("content:empty",
                            Component.text("暂无公告", NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                                    .append(Component.newline())
                                    .append(Component.text("当前范围内没有可显示的公告",
                                            NamedTextColor.DARK_GRAY)))
                    .region("content");
        } else {
            for (int index = start; index < end; index++) {
                Announcement announcement = visible.get(index);
                String date = java.time.Instant.ofEpochSecond(announcement.timestamp())
                        .atZone(ZoneId.systemDefault()).format(DISPLAY_FMT);
                Component content = Component.text(date, NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                        .append(Component.newline())
                        .append(Component.text(monthOf(announcement).format(MONTH_FMT),
                                NamedTextColor.DARK_AQUA));
                List<Component> previewLines = wrapText(announcement.content());
                for (Component line : previewLines.subList(0, Math.min(2, previewLines.size()))) {
                    content = content.append(Component.newline()).append(line);
                }
                if (previewLines.size() > 2) {
                    content = content.append(Component.text(" …", NamedTextColor.DARK_GRAY));
                }
                menu.information("announcement:" + announcement.timestamp() + ":" + index,
                                content)
                        .region("content");
            }
        }

        if (pagination.hasPrevious()) {
            menu.navigation("page:previous",
                            Component.text("‹ 上一页", NamedTextColor.AQUA)
                                    .decorate(TextDecoration.BOLD))
                    .region("footer")
                    .primary((p, handle) -> context.update(value ->
                            new MenuState(value.monthFilter(), pagination.previous().index())));
            menu.on(FloatingMenuInteraction.SCROLL_UP, (p, handle, input) -> context.update(value ->
                    new MenuState(value.monthFilter(), pagination.previous().index())));
        } else {
            menu.navigation("page:previous", Component.text("‹ 上一页", NamedTextColor.DARK_GRAY))
                    .region("footer")
                    .disabled(Component.text("已经是第一页", NamedTextColor.GRAY));
        }
        if (player.hasPermission("mik.command.reloadannouncements")) {
            menu.item("reload", Material.LIME_DYE,
                            Component.text("重新加载公告", NamedTextColor.GREEN)
                                    .decorate(TextDecoration.BOLD))
                    .region("footer")
                    .primary((p, handle) -> {
                        reload();
                        context.redraw();
                        p.sendMessage(Component.text("公告已重新加载", NamedTextColor.GREEN));
                    });
        }
        menu.dismiss(
                        Component.text(context.canGoBack() ? "返回" : "关闭", NamedTextColor.RED)
                                .decorate(TextDecoration.BOLD))
                .region("footer");
        if (pagination.hasNext()) {
            menu.navigation("page:next",
                            Component.text("下一页 ›", NamedTextColor.AQUA)
                                    .decorate(TextDecoration.BOLD))
                    .region("footer")
                    .primary((p, handle) -> context.update(value ->
                            new MenuState(value.monthFilter(), pagination.next().index())));
            menu.on(FloatingMenuInteraction.SCROLL_DOWN, (p, handle, input) -> context.update(value ->
                    new MenuState(value.monthFilter(), pagination.next().index())));
        } else {
            menu.navigation("page:next", Component.text("下一页 ›", NamedTextColor.DARK_GRAY))
                    .region("footer")
                    .disabled(Component.text("已经是最后一页", NamedTextColor.GRAY));
        }
        return menu.build();
    }

    private Component buildSummary(MenuState state, int visibleCount, int totalPages) {
        Component summary = Component.text("服务器公告", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("当前范围：" + rangeLabel(state.monthFilter()),
                        NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("公告数量：" + visibleCount + " 条", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("页数：" + Math.max(1, state.page() + 1)
                        + " / " + totalPages, NamedTextColor.GRAY));
        return summary;
    }

    private MenuState normalizeState(MenuState state) {
        List<YearMonth> months = availableMonths();
        YearMonth month = state.monthFilter();
        if (month != null && !months.contains(month)) {
            month = months.isEmpty() ? null : months.getLast();
        }
        return new MenuState(month, Math.max(0, state.page()));
    }

    private List<Announcement> filterAnnouncements(YearMonth monthFilter) {
        return announcements.stream()
                .filter(a -> monthFilter == null || monthOf(a).equals(monthFilter))
                .sorted(Comparator.comparingLong(Announcement::timestamp).reversed())
                .toList();
    }

    private List<YearMonth> availableMonths() {
        return announcements.stream()
                .map(this::monthOf)
                .distinct()
                .sorted()
                .toList();
    }

    private YearMonth latestMonth() {
        List<YearMonth> months = availableMonths();
        return months.isEmpty() ? null : months.getLast();
    }

    private YearMonth shiftMonth(YearMonth currentMonth, int delta) {
        List<YearMonth> months = availableMonths();
        if (months.isEmpty()) return null;

        YearMonth current = currentMonth == null ? months.getLast() : currentMonth;
        int index = months.indexOf(current);
        if (index < 0) index = months.size() - 1;
        return months.get(Math.clamp(index + delta, 0, months.size() - 1));
    }

    private boolean hasAdjacentMonth(YearMonth currentMonth, int delta) {
        List<YearMonth> months = availableMonths();
        if (months.size() <= 1) return false;
        YearMonth current = currentMonth == null ? months.getLast() : currentMonth;
        int index = months.indexOf(current);
        if (index < 0) return false;
        int next = index + delta;
        return next >= 0 && next < months.size();
    }

    private YearMonth monthOf(Announcement announcement) {
        return YearMonth.from(java.time.Instant.ofEpochSecond(announcement.timestamp())
                .atZone(ZoneId.systemDefault()));
    }

    private String rangeLabel(YearMonth monthFilter) {
        return monthFilter == null ? "全部公告" : monthFilter.format(MONTH_FMT);
    }

    private record MenuState(YearMonth monthFilter, int page) {
    }

    /** Wraps announcement copy into stable, directly rendered scene text. */
    private List<Component> wrapText(String text) {
        List<Component> lines = new ArrayList<>();
        for (String paragraph : normalizeAnnouncementText(text).split("\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) {
                lines.add(Component.empty());
                continue;
            }
            while (remaining.length() > TEXT_LINE_WIDTH) {
                // 尽量在空格处断行
                int cut = TEXT_LINE_WIDTH;
                int spacePos = remaining.lastIndexOf(' ', cut);
                if (spacePos > TEXT_LINE_WIDTH / 2) {
                    cut = spacePos;
                }
                lines.add(Component.text(remaining.substring(0, cut).stripTrailing(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
                remaining = remaining.substring(cut).stripLeading();
            }
            lines.add(Component.text(remaining, NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }

    private long latestAnnouncementTimestamp() {
        return announcements.stream()
                .mapToLong(Announcement::timestamp)
                .max()
                .orElse(0L);
    }

    private void markSeenThroughLatest(UUID playerId) {
        long latestTimestamp = latestAnnouncementTimestamp();
        if (latestTimestamp <= 0) return;

        playerSeenUntil.merge(playerId, latestTimestamp, Math::max);
    }

    private void loadState() {
        playerSeenUntil.clear();
        if (!stateFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String uuidString : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                long seenUntil = config.getLong("players." + uuidString + ".seen-until", 0L);
                if (seenUntil > 0) {
                    playerSeenUntil.put(uuid, seenUntil);
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in announcements-state.yml: " + uuidString);
            }
        }
    }

    private void saveState() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for announcements-state.yml");
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : playerSeenUntil.entrySet()) {
            config.set("players." + entry.getKey() + ".seen-until", entry.getValue());
        }

        try {
            config.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save announcements-state.yml: " + e.getMessage());
        }
    }

    //  命令注册、JSON 缓存、文件读取（无改动）

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("reloadannouncements")
                    .requires(source -> source.getSender().hasPermission("mik.command.reloadannouncements"))
                    .executes(ctx -> {
                        reload();
                        ctx.getSource().getSender().sendMessage(
                                Component.text("公告已重新加载", NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    }).build(), "重新加载公告文件");

            event.registrar().register(Commands.literal("announcements")
                    .executes(ctx -> {
                        if (ctx.getSource().getSender() instanceof Player player) {
                            openAnnouncementsMenu(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), "查看服务器公告");
        });
    }

    public String getAnnouncementsJson() {
        return announcementsJson;
    }

    public byte[] getAnnouncementsJsonBytes() {
        return announcementsJsonBytes;
    }

    private void buildJsonCache() {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Announcement a : announcements) {
            if (count > 0) sb.append(",");
            String iso8601 = java.time.Instant.ofEpochSecond(a.timestamp())
                    .toString();
            sb.append("{\"timestamp\":\"").append(iso8601)
                    .append("\",\"content\":\"").append(escapeJson(a.content())).append("\"}");
            count++;
        }
        sb.append("]");
        String json = sb.toString();
        announcementsJsonBytes = json.getBytes(StandardCharsets.UTF_8);
        announcementsJson = json;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private List<Announcement> loadAnnouncementsFromFile() {
        File file = new File(plugin.getDataFolder(), "announcements.txt");
        if (!file.exists()) return List.of();

        List<Announcement> list = new ArrayList<>();
        try {
            String raw = normalizeAnnouncementText(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            for (String block : raw.split("---")) {
                String trimmed = block.strip();
                if (trimmed.isEmpty()) continue;
                int newline = trimmed.indexOf('\n');
                if (newline == -1) continue;
                String dateLine = trimmed.substring(0, newline).strip();
                String content = normalizeAnnouncementText(trimmed.substring(newline + 1)).strip();
                try {
                    LocalDateTime ldt = LocalDateTime.parse(dateLine, DATE_FMT);
                    long ts = ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
                    list.add(new Announcement(ts, content));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid date in announcements.txt: " + dateLine);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read announcements.txt: " + e.getMessage());
        }
        return list;
    }

    private String normalizeAnnouncementText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    public record Announcement(long timestamp, String content) {
    }
}
