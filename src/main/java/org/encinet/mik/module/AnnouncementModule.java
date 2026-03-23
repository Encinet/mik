package org.encinet.mik.module;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

public class AnnouncementModule implements Listener {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String title = event.getView().title().toString();

        if (title.equals("服务器公告")) {
            event.setCancelled(true);
            handleMenuClick(player, item);
        } else if (title.contains("公告")) {
            event.setCancelled(true);
        }
    }

    public void reload() {
        List<Announcement> old = this.announcements;
        List<Announcement> loaded = loadAnnouncementsFromFile();
        long cutoff = java.time.Instant.now().minusSeconds(365L * 24 * 60 * 60).getEpochSecond();
        announcements = loaded.stream()
                .filter(a -> a.timestamp() > cutoff)
                .toList();
        buildJsonCache();

        // 找出本次 reload 新增的公告
        Set<Long> oldTimestamps = old.stream()
                .map(Announcement::timestamp)
                .collect(java.util.stream.Collectors.toSet());

        List<Announcement> added = announcements.stream()
                .filter(a -> !oldTimestamps.contains(a.timestamp()))
                .toList();

        if (!added.isEmpty()) {
            broadcastNewAnnouncements(added);
        }
    }
    
    private void broadcastNewAnnouncements(List<Announcement> added) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text("=== 服务器公告更新 ===", NamedTextColor.GOLD));
            for (Announcement a : added) {
                String dateStr = java.time.Instant.ofEpochSecond(a.timestamp())
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                player.sendMessage(Component.text(dateStr + " | " + a.content(), NamedTextColor.WHITE));
            }
            player.sendMessage(Component.text("[查看全部公告]", NamedTextColor.AQUA)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/announcements")));
        }
    }

    public String getAnnouncementsJson() {
        return announcementsJson;
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("reloadannouncements")
                    .requires(source -> source.getSender().hasPermission("mik.command.reloadannouncements"))
                    .executes(ctx -> {
                        reload();
                        ctx.getSource().getSender().sendMessage(
                                Component.text("公告已重新加载。", NamedTextColor.GREEN)
                        );
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

    private void buildJsonCache() {
        StringBuilder sb = new StringBuilder("[");
        int count = 0;
        for (Announcement a : announcements) {
            if (count > 0) sb.append(",");
            sb.append("{\"timestamp\":").append(a.timestamp())
                    .append(",\"content\":\"").append(escapeJson(a.content())).append("\"}");
            count++;
        }
        sb.append("]");
        announcementsJson = sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        long lastPlayed = event.getPlayer().getLastSeen();
        if (lastPlayed == 0) {
            return;
        }

        long lastPlayedSeconds = lastPlayed / 1000;

        List<Announcement> newAnnouncements = announcements.stream()
                .filter(a -> a.timestamp() > lastPlayedSeconds)
                .limit(5)
                .toList();

        if (newAnnouncements.isEmpty()) {
            return;
        }

        event.getPlayer().sendMessage(Component.text("=== 服务器公告 ===", NamedTextColor.GOLD));
        for (Announcement announcement : newAnnouncements) {
            String dateStr = java.time.Instant.ofEpochSecond(announcement.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            event.getPlayer().sendMessage(Component.text(dateStr + " | " + announcement.content(), NamedTextColor.WHITE));
        }

        event.getPlayer().sendMessage(Component.text("[查看更多公告] (点击)", NamedTextColor.AQUA)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/announcements")));
    }

    public void openAnnouncementsMenu(Player player) {
        Map<String, List<Announcement>> byMonth = new TreeMap<>();
        for (Announcement a : announcements) {
            String month = java.time.Instant.ofEpochSecond(a.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy年MM月"));
            byMonth.computeIfAbsent(month, k -> new ArrayList<>()).add(a);
        }

        if (byMonth.isEmpty()) {
            player.sendMessage(Component.text("暂无公告", NamedTextColor.GRAY));
            return;
        }

        int size = Math.min(byMonth.size(), 54);
        Inventory inv = Bukkit.createInventory(null, (size + 9) / 9 * 9, Component.text("服务器公告"));

        int slot = 0;
        for (var entry : byMonth.entrySet()) {
            List<Announcement> list = entry.getValue();
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(entry.getKey(), NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("共 " + list.size() + " 条公告", NamedTextColor.GRAY),
                    Component.text("点击查看", NamedTextColor.YELLOW)
            ));
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }

        player.openInventory(inv);
    }

    public void handleMenuClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return;
        }

        String month = PlainTextComponentSerializer.plainText()
                .serialize(Objects.requireNonNull(item.getItemMeta().displayName()));
        List<Announcement> list = announcements.stream()
                .filter(a -> {
                    String m = java.time.Instant.ofEpochSecond(a.timestamp())
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy年MM月"));
                    return m.equals(month);
                })
                .toList();

        if (list.isEmpty()) {
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(month + " 公告"));

        int slot = 0;
        for (Announcement a : list) {
            String dateStr = java.time.Instant.ofEpochSecond(a.timestamp())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

            ItemStack item2 = new ItemStack(Material.PAPER);
            ItemMeta meta = item2.getItemMeta();
            meta.displayName(Component.text(dateStr, NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text(a.content(), NamedTextColor.WHITE)
            ));
            item2.setItemMeta(meta);

            inv.setItem(slot, item2);
            slot++;
        }

        player.openInventory(inv);
    }

    private List<Announcement> loadAnnouncementsFromFile() {
        File file = new File(plugin.getDataFolder(), "announcements.txt");
        if (!file.exists()) {
            return List.of();
        }

        List<Announcement> list = new ArrayList<>();
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String[] blocks = raw.split("---");
            for (String block : blocks) {
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
