package org.encinet.mik.module;

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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class AnnouncementModule implements Listener {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年MM月");
    /**
     * lore 每行最大字符数（半角计；中文字符建议按 2 算，此处简单按字符数截断）
     */
    private static final int LORE_LINE_WIDTH = 36;

    private final JavaPlugin plugin;
    private List<Announcement> announcements = List.of();
    private String announcementsJson = "[]";

    public AnnouncementModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    //  Inventory 事件

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // 必须用 PlainTextComponentSerializer，直接 toString() 会得到 Component JSON
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());

        if (title.equals("服务器公告")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) {
                handleMenuClick(player, item);
            }
        } else if (title.endsWith("公告")) {
            // 月份详情页，只拦截点击，不做其他操作
            event.setCancelled(true);
        }
    }

    //  reload

    public void reload() {
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

        if (!added.isEmpty()) {
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

    /**
     * 玩家上线后推送其离线期间的公告
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        long lastPlayed = event.getPlayer().getLastSeen();
        if (lastPlayed == 0) return;

        long lastPlayedSeconds = lastPlayed / 1000;

        List<Announcement> newAnnouncements = announcements.stream()
                .filter(a -> a.timestamp() > lastPlayedSeconds)
                .limit(5)
                .toList();

        if (newAnnouncements.isEmpty()) return;

        Player player = event.getPlayer();
        int total = (int) announcements.stream().filter(a -> a.timestamp() > lastPlayedSeconds).count();

        player.sendMessage(chatHeader("服务器公告",
                Component.text("离线期间 " + total + " 条新公告", NamedTextColor.YELLOW)));

        for (Announcement a : newAnnouncements) {
            player.sendMessage(chatAnnouncementLine(a));
        }

        if (total > 5) {
            player.sendMessage(
                    Component.text("  ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("还有 " + (total - 5) + " 条未显示  ", NamedTextColor.GRAY))
                            .append(chatFooterClickable())
            );
        } else {
            player.sendMessage(chatFooterClickable());
        }
    }

    // 聊天消息构造辅助

    /**
     * 标题行：  ━━  {title}  {badge}  ━━
     */
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
                .append(Component.text("  （点击）", NamedTextColor.DARK_GRAY))
                .build();
    }

    //  GUI 菜单

    public void openAnnouncementsMenu(Player player) {
        // 按月份分组，key 升序（TreeMap），最新在后；反转让最新月份排前
        TreeMap<String, List<Announcement>> byMonth = new TreeMap<>();
        for (Announcement a : announcements) {
            String month = java.time.Instant.ofEpochSecond(a.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .format(MONTH_FMT);
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(a);
        }

        if (byMonth.isEmpty()) {
            player.sendMessage(Component.text("暂无公告", NamedTextColor.GRAY));
            return;
        }

        // 最新月份在最前（降序）
        List<Map.Entry<String, List<Announcement>>> entries = new ArrayList<>(byMonth.descendingMap().entrySet());
        int rows = Math.min((entries.size() + 8) / 9, 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9, Component.text("服务器公告"));

        for (int i = 0; i < Math.min(entries.size(), rows * 9); i++) {
            Map.Entry<String, List<Announcement>> entry = entries.get(i);
            inv.setItem(i, buildMonthItem(entry.getKey(), entry.getValue()));
        }

        player.openInventory(inv);
    }

    /**
     * 月份选择菜单中的 PAPER item
     */
    private ItemStack buildMonthItem(String monthLabel, List<Announcement> list) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(monthLabel, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("共 " + list.size() + " 条公告", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        // 预览最新的 3 条（list 内部按 timestamp 升序，取末尾）
        int previewCount = Math.min(3, list.size());
        List<Announcement> preview = list.subList(list.size() - previewCount, list.size());
        for (int i = preview.size() - 1; i >= 0; i--) {
            Announcement a = preview.get(i);
            String dateStr = java.time.Instant.ofEpochSecond(a.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .format(DISPLAY_FMT);
            lore.add(Component.text("◆ ", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(dateStr, NamedTextColor.DARK_AQUA)));
            // 内容折行
            for (Component line : wrapToLore(a.content(), NamedTextColor.WHITE)) {
                lore.add(Component.text("  ").append(line));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("▶ 点击查看全部", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void handleMenuClick(Player player, ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return;

        String month = PlainTextComponentSerializer.plainText()
                .serialize(Objects.requireNonNull(item.getItemMeta().displayName()));

        List<Announcement> list = announcements.stream()
                .filter(a -> java.time.Instant.ofEpochSecond(a.timestamp())
                        .atZone(ZoneId.systemDefault())
                        .format(MONTH_FMT)
                        .equals(month))
                // 最新在前
                .sorted(Comparator.comparingLong(Announcement::timestamp).reversed())
                .toList();

        if (list.isEmpty()) return;

        int rows = Math.min((list.size() + 8) / 9, 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9,
                Component.text(month + " 公告"));

        for (int i = 0; i < Math.min(list.size(), rows * 9); i++) {
            inv.setItem(i, buildAnnouncementItem(list.get(i)));
        }

        player.openInventory(inv);
    }

    /**
     * 月份详情页中单条公告的 PAPER item
     */
    private ItemStack buildAnnouncementItem(Announcement a) {
        String dateStr = java.time.Instant.ofEpochSecond(a.timestamp())
                .atZone(ZoneId.systemDefault())
                .format(DISPLAY_FMT);

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("◆ " + dateStr, NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        for (Component line : wrapToLore(a.content(), NamedTextColor.WHITE)) {
            lore.add(line);
        }
        lore.add(Component.empty());

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // lore 折行辅助

    /**
     * 将任意字符串折行为 lore 用的 Component 列表。
     * 先按 \n 分割，再对每段按 LORE_LINE_WIDTH 折行。
     * 所有行统一关闭斜体（Minecraft lore 默认斜体）。
     */
    private List<Component> wrapToLore(String text, NamedTextColor color) {
        List<Component> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
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
                lines.add(Component.text(remaining.substring(0, cut).stripTrailing(), color)
                        .decoration(TextDecoration.ITALIC, false));
                remaining = remaining.substring(cut).stripLeading();
            }
            lines.add(Component.text(remaining, color)
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
                                Component.text("公告已重新加载。", NamedTextColor.GREEN));
                        return Command.SINGLE_SUCCESS;
                    }).build(), "重新加载公告文件", List.of("reloadannounce"));

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
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            for (String block : raw.split("---")) {
                String trimmed = block.strip();
                if (trimmed.isEmpty()) continue;
                int newline = trimmed.indexOf('\n');
                if (newline == -1) continue;
                String dateLine = trimmed.substring(0, newline).strip();
                String content = trimmed.substring(newline + 1).strip();
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

    public record Announcement(long timestamp, String content) {
    }
}
