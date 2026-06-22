package org.encinet.mik.module.communication;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

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
    private static final String ACTION_CLOSE = "close";
    private static final int MENU_SIZE = 54;
    private static final int ANNOUNCEMENTS_PER_PAGE = 28;
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    /**
     * lore 每行最大字符数（半角计；中文字符建议按 2 算，此处简单按字符数截断）
     */
    private static final int LORE_LINE_WIDTH = 36;

    private final JavaPlugin plugin;
    private final MenuNavigation menuNavigation;
    private final NamespacedKey menuActionKey;
    private final Map<UUID, Long> previousSeenAt = new ConcurrentHashMap<>();
    private final Map<UUID, MenuState> menuStates = new ConcurrentHashMap<>();
    private List<Announcement> announcements = List.of();
    private String announcementsJson = "[]";

    public AnnouncementModule(JavaPlugin plugin, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.menuNavigation = menuNavigation;
        this.menuActionKey = new NamespacedKey(plugin, "announcement_action");
    }

    public void enable() {
        reload(false);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    //  Inventory 事件

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 必须用 PlainTextComponentSerializer，直接 toString() 会得到 Component JSON
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.equals(MENU_TITLE)) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) handleMenuClick(player, item);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!MENU_TITLE.equals(title)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            String currentTitle = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
            if (!MENU_TITLE.equals(currentTitle)) {
                menuNavigation.clearMainMenuReturn(player, MenuNavigation.ChildMenu.ANNOUNCEMENTS);
            }
        });
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
            player.sendMessage(header);
            for (Announcement a : added) {
                player.sendMessage(chatAnnouncementLine(a));
            }
            player.sendMessage(chatFooterClickable());
        }
    }

    @EventHandler
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(event.getUniqueId());
        previousSeenAt.put(event.getUniqueId(), player.getLastSeen() / 1000);
    }

    /**
     * 玩家上线后推送最近没有看过的公告
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long seenUntilCandidate = previousSeenAt.remove(player.getUniqueId());
        if (seenUntilCandidate <= 0) {
            seenUntilCandidate = player.getFirstPlayed() / 1000;
        }
        long seenUntil = seenUntilCandidate;

        List<Announcement> unseenAnnouncements = announcements.stream()
                .filter(a -> a.timestamp() > seenUntil)
                .sorted(Comparator.comparingLong(Announcement::timestamp).reversed())
                .limit(JOIN_PUSH_LIMIT)
                .toList();

        if (unseenAnnouncements.isEmpty()) return;

        int total = (int) announcements.stream().filter(a -> a.timestamp() > seenUntil).count();

        player.sendMessage(chatHeader("服务器公告",
                Component.text(total + " 条未读公告", NamedTextColor.YELLOW)));

        for (Announcement a : unseenAnnouncements) {
            player.sendMessage(chatAnnouncementLine(a));
        }

        if (total > JOIN_PUSH_LIMIT) {
            player.sendMessage(
                    Component.text("  ", NamedTextColor.GRAY)
                            .append(Component.text("还有 " + (total - JOIN_PUSH_LIMIT) + " 条未显示  ", NamedTextColor.GRAY))
                            .append(chatFooterClickable())
            );
        } else {
            player.sendMessage(chatFooterClickable());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        previousSeenAt.remove(event.getPlayer().getUniqueId());
        menuStates.remove(event.getPlayer().getUniqueId());
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
        MenuState state = menuStates.computeIfAbsent(player.getUniqueId(), _ -> new MenuState(null, 0));
        openAnnouncementsMenu(player, state);
    }

    private void openAnnouncementsMenu(Player player, MenuState requestedState) {
        MenuState state = normalizeState(requestedState);
        menuStates.put(player.getUniqueId(), state);

        List<Announcement> visible = filterAnnouncements(state.monthFilter());
        int totalPages = Math.max(1, (visible.size() + ANNOUNCEMENTS_PER_PAGE - 1) / ANNOUNCEMENTS_PER_PAGE);
        int page = Math.clamp(state.page(), 0, totalPages - 1);
        if (page != state.page()) {
            state = new MenuState(state.monthFilter(), page);
            menuStates.put(player.getUniqueId(), state);
        }

        Inventory inv = Bukkit.createInventory(null, MENU_SIZE, Component.text(MENU_TITLE, MenuItems.TITLE_COLOR));
        paintFrame(inv);
        paintToolbar(inv, state, visible.size(), totalPages);
        paintAnnouncements(inv, visible, page);
        paintFooter(inv, page, totalPages, player.hasPermission("mik.command.reloadannouncements"), player);

        player.openInventory(inv);
    }

    public void handleMenuClick(Player player, ItemStack item) {
        String action = readMenuAction(item);
        if (action == null) return;

        MenuState state = menuStates.getOrDefault(player.getUniqueId(), new MenuState(null, 0));
        switch (action) {
            case ACTION_CLOSE -> {
                if (!menuNavigation.returnToMainMenuIfNeeded(player, MenuNavigation.ChildMenu.ANNOUNCEMENTS)) {
                    player.closeInventory();
                }
            }
            case "all" -> openAnnouncementsMenu(player, new MenuState(null, 0));
            case "latest_month" -> openAnnouncementsMenu(player, new MenuState(latestMonth(), 0));
            case "month_prev" -> openAnnouncementsMenu(player, new MenuState(shiftMonth(state.monthFilter(), -1), 0));
            case "month_next" -> openAnnouncementsMenu(player, new MenuState(shiftMonth(state.monthFilter(), 1), 0));
            case "page_prev" -> openAnnouncementsMenu(player, new MenuState(state.monthFilter(), state.page() - 1));
            case "page_next" -> openAnnouncementsMenu(player, new MenuState(state.monthFilter(), state.page() + 1));
            case "reload" -> {
                if (!player.hasPermission("mik.command.reloadannouncements")) return;
                reload();
                openAnnouncementsMenu(player, state);
                player.sendMessage(Component.text("公告已重新加载", NamedTextColor.GREEN));
            }
            default -> {
            }
        }
    }

    private void paintFrame(Inventory inv) {
        MenuItems.fillExcept(inv, Material.GRAY_STAINED_GLASS_PANE, CONTENT_SLOTS);
    }

    private void paintToolbar(Inventory inv, MenuState state, int visibleCount, int totalPages) {
        boolean canMoveMonthBack = hasAdjacentMonth(state.monthFilter(), -1);
        boolean canMoveMonthForward = hasAdjacentMonth(state.monthFilter(), 1);

        inv.setItem(0, canMoveMonthBack
                ? actionItem(Material.ARROW,
                Component.text("上个月", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(),
                "month_prev")
                : disabledItem(Component.text("上个月", NamedTextColor.GRAY),
                List.of(Component.text("没有更早的月份", NamedTextColor.GRAY))));
        inv.setItem(2, actionItem(Material.COMPASS,
                Component.text("全部公告", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                List.of(Component.text("当前范围：" + rangeLabel(state.monthFilter()), NamedTextColor.GRAY)),
                "all"));
        inv.setItem(4, buildSummaryItem(state, visibleCount, totalPages));
        inv.setItem(6, actionItem(Material.CLOCK,
                Component.text("最新月份", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD),
                List.of(),
                "latest_month"));
        inv.setItem(8, canMoveMonthForward
                ? actionItem(Material.ARROW,
                Component.text("下个月", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(),
                "month_next")
                : disabledItem(Component.text("下个月", NamedTextColor.GRAY),
                List.of(Component.text("没有更晚的月份", NamedTextColor.GRAY))));
    }

    private void paintAnnouncements(Inventory inv, List<Announcement> visible, int page) {
        if (visible.isEmpty()) {
            inv.setItem(22, simpleItem(Material.BARRIER,
                    Component.text("暂无公告", NamedTextColor.GRAY).decorate(TextDecoration.BOLD),
                    List.of(Component.text("当前范围内没有可显示的公告", NamedTextColor.GRAY))));
            return;
        }

        int start = page * ANNOUNCEMENTS_PER_PAGE;
        int end = Math.min(start + ANNOUNCEMENTS_PER_PAGE, visible.size());
        for (int i = start; i < end; i++) {
            inv.setItem(CONTENT_SLOTS[i - start], buildAnnouncementItem(visible.get(i)));
        }
    }

    private void paintFooter(Inventory inv, int page, int totalPages, boolean canReload, Player player) {
        inv.setItem(45, page > 0 ? actionItem(Material.SPECTRAL_ARROW,
                Component.text("上一页", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(Component.text("第 " + (page + 1) + " / " + totalPages + " 页", NamedTextColor.GRAY)),
                "page_prev") : disabledItem(Component.text("上一页", NamedTextColor.GRAY),
                List.of(Component.text("已经是第一页", NamedTextColor.GRAY))));
        if (canReload) {
            inv.setItem(51, actionItem(Material.LIME_DYE,
                    Component.text("重新加载公告", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    List.of(Component.text("从 announcements.txt 重新读取", NamedTextColor.GRAY)),
                    "reload"));
        }
        inv.setItem(52, closeItem(menuNavigation.shouldReturnToMainMenu(player, MenuNavigation.ChildMenu.ANNOUNCEMENTS)));
        inv.setItem(53, page + 1 < totalPages ? actionItem(Material.SPECTRAL_ARROW,
                Component.text("下一页", NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                List.of(Component.text("第 " + (page + 1) + " / " + totalPages + " 页", NamedTextColor.GRAY)),
                "page_next") : disabledItem(Component.text("下一页", NamedTextColor.GRAY),
                List.of(Component.text("已经是最后一页", NamedTextColor.GRAY))));
    }

    private ItemStack buildSummaryItem(MenuState state, int visibleCount, int totalPages) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("当前范围：" + rangeLabel(state.monthFilter()), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("公告数量：" + visibleCount + " 条", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("页数：" + Math.max(1, state.page() + 1) + " / " + totalPages, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        return simpleItem(Material.WRITABLE_BOOK,
                Component.text("服务器公告", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                lore);
    }

    private ItemStack buildAnnouncementItem(Announcement a) {
        String dateStr = java.time.Instant.ofEpochSecond(a.timestamp())
                .atZone(ZoneId.systemDefault())
                .format(DISPLAY_FMT);
        String monthStr = monthOf(a).format(MONTH_FMT);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(monthStr, NamedTextColor.DARK_AQUA)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.addAll(wrapToLore(a.content()));

        return MenuItems.item(Material.PAPER, Component.text(dateStr, NamedTextColor.GOLD).decorate(TextDecoration.BOLD), lore);
    }

    private ItemStack closeItem(boolean returnToMainMenu) {
        if (returnToMainMenu) {
            return actionItem(Material.ARROW, Component.text("返回主菜单", NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    List.of(Component.text("回到主菜单", NamedTextColor.GRAY)), ACTION_CLOSE);
        }
        return actionItem(Material.BARRIER, Component.text("关闭", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                List.of(Component.text("返回游戏", NamedTextColor.GRAY)), ACTION_CLOSE);
    }

    private ItemStack actionItem(Material material, Component name, List<Component> lore, String action) {
        return MenuItems.action(material, name, lore, menuActionKey, action);
    }

    private ItemStack disabledItem(Component name, List<Component> lore) {
        return simpleItem(Material.GRAY_DYE, name, lore);
    }

    private ItemStack simpleItem(Material material, Component name, List<Component> lore) {
        return MenuItems.item(material, name, lore);
    }

    private String readMenuAction(ItemStack item) {
        return MenuItems.readAction(item, menuActionKey);
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

    // lore 折行辅助

    /**
     * 将任意字符串折行为 lore 用的 Component 列表。
     * 先按 \n 分割，再对每段按 LORE_LINE_WIDTH 折行。
     * 所有行统一关闭斜体（Minecraft lore 默认斜体）。
     */
    private List<Component> wrapToLore(String text) {
        List<Component> lines = new ArrayList<>();
        for (String paragraph : normalizeAnnouncementText(text).split("\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) {
                lines.add(Component.empty());
                continue;
            }
            while (remaining.length() > LORE_LINE_WIDTH) {
                // 尽量在空格处断行
                int cut = LORE_LINE_WIDTH;
                int spacePos = remaining.lastIndexOf(' ', cut);
                if (spacePos > LORE_LINE_WIDTH / 2) {
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
        announcementsJson = sb.toString();
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
