package org.encinet.mik.module.plot.board;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotNoticePublisher;
import org.encinet.mik.module.plot.PlotDataPaths;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Delivers community board changes to affected players in their own language. */
public final class PlotBoardNotifications implements Listener {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotNoticePublisher notices;

    private JsonArray alerts = new JsonArray();
    private PlotBoardAlertLedger ledger;

    PlotBoardNotifications(JavaPlugin plugin, LanguageService language, PlotNoticePublisher notices) {
        this.plugin = plugin;
        this.language = language;
        this.notices = notices;
    }

    void enable() {
        try {
            ledger = PlotBoardAlertLedger.open(
                    PlotDataPaths.in(plugin.getDataFolder().toPath()).boardAlerts(),
                    System.currentTimeMillis());
        } catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not open plot board alert ledger", error);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void disable() {
        HandlerList.unregisterAll(this);
    }

    void publish(JsonArray entries) {
        alerts = entries;
        notifyNewEvents(entries);
    }

    private void notifyNewEvents(JsonArray entries) {
        if (ledger == null) return;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (JsonElement element : entries) {
            try {
                JsonObject item = element.getAsJsonObject();
                String id = item.get("id").getAsString();
                String kind = item.get("kind").getAsString();
                String status = item.get("status").getAsString();
                if (!kind.equals("notice") && !kind.equals("objection")) continue;
                boolean ruled = kind.equals("objection") && status.equals("ruled");
                boolean withdrawn = kind.equals("objection") && status.equals("withdrawn");
                long eventAt = item.get(ruled || withdrawn ? "updatedAt" : "createdAt").getAsLong();
                String key = (ruled ? "ruled:" : withdrawn ? "withdrawn:" : "posted:") + id;
                if (!ledger.shouldAlert(key, eventAt, now)) continue;
                Map<UUID, String> recipients = recipients(item);
                if (recipients.isEmpty()) continue;
                Message alert = ruled ? Message.PLOT_BOARD_RULING_ALERT
                        : withdrawn ? Message.PLOT_BOARD_WITHDRAWAL_ALERT
                        : kind.equals("objection") ? Message.PLOT_BOARD_OBJECTION_ALERT
                        : noticeAlert(item);
                String author = safeAlertText(item.get("authorName").getAsString());
                String subject = safeAlertText(item.get("subject").getAsString());
                if (author.length() > 64 || subject.length() > 80) continue;
                for (Map.Entry<UUID, String> recipient : recipients.entrySet()) {
                    try {
                        UUID uuid = recipient.getKey();
                        Player online = Bukkit.getPlayer(uuid);
                        String name = online == null ? recipient.getValue() : online.getName();
                        Language recipientLanguage = online == null
                                ? language.preferredLanguage(uuid).orElse(Language.DEFAULT)
                                : language.language(online);
                        String body = language.t(recipientLanguage, alert, author, subject,
                                reference(recipientLanguage, id));
                        notices.publish(uuid, name, body);
                    } catch (RuntimeException invalidTarget) {
                        plugin.getLogger().warning("Skipped a community alert recipient: "
                                + invalidTarget.getMessage());
                    }
                }
                ledger.mark(key);
                changed = true;
            } catch (RuntimeException invalid) {
                plugin.getLogger().warning("Skipped an invalid community board alert: "
                        + invalid.getMessage());
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (deliverPending(online)) changed = true;
        }
        if (changed) saveLedger();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (deliverPending(event.getPlayer())) saveLedger();
    }

    private boolean deliverPending(Player player) {
        if (ledger == null) return false;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (JsonElement element : alerts) {
            try {
                JsonObject item = element.getAsJsonObject();
                if (!recipients(item).containsKey(player.getUniqueId())) continue;
                String kind = item.get("kind").getAsString();
                String status = item.get("status").getAsString();
                boolean ruled = kind.equals("objection") && status.equals("ruled");
                boolean withdrawn = kind.equals("objection") && status.equals("withdrawn");
                long eventAt = item.get(ruled || withdrawn ? "updatedAt" : "createdAt").getAsLong();
                if (player.getFirstPlayed() > eventAt) continue;
                String id = item.get("id").getAsString();
                String key = (ruled ? "ruled:" : withdrawn ? "withdrawn:" : "posted:") + id;
                String playerId = player.getUniqueId().toString();
                if (!ledger.shouldDeliverToPlayer(key, playerId, eventAt, now)) continue;
                Message alert = ruled ? Message.PLOT_BOARD_RULING_ALERT
                        : withdrawn ? Message.PLOT_BOARD_WITHDRAWAL_ALERT
                        : kind.equals("objection") ? Message.PLOT_BOARD_OBJECTION_ALERT
                        : noticeAlert(item);
                player.sendMessage(language.text(player, alert, NamedTextColor.GOLD,
                        safeAlertText(item.get("authorName").getAsString()),
                        safeAlertText(item.get("subject").getAsString()),
                        reference(language.language(player), id)));
                ledger.markDelivered(key, playerId);
                changed = true;
            } catch (RuntimeException invalid) {
                plugin.getLogger().warning("Skipped an invalid community board inbox alert: "
                        + invalid.getMessage());
            }
        }
        return changed;
    }

    private void saveLedger() {
        try { ledger.save(); }
        catch (IOException error) {
            plugin.getLogger().log(Level.WARNING, "Could not save plot board alert ledger", error);
        }
    }

    private static Message noticeAlert(JsonObject item) {
        if (!item.has("source") || !item.get("source").getAsString().equals("plot")
                || !item.has("details") || !item.get("details").isJsonObject())
            return Message.PLOT_BOARD_NOTICE_ALERT;
        JsonObject details = item.getAsJsonObject("details");
        if (!details.has("autoEvent")) return Message.PLOT_BOARD_NOTICE_ALERT;
        return switch (details.get("autoEvent").getAsString()) {
            case "registered" -> Message.PLOT_BOARD_REGISTERED_ALERT;
            default -> Message.PLOT_BOARD_NOTICE_ALERT;
        };
    }

    static Map<UUID, String> recipients(JsonObject item) {
        Map<UUID, String> recipients = new LinkedHashMap<>();
        if (!item.has("notifiedRecipients") || !item.get("notifiedRecipients").isJsonArray())
            return recipients;
        for (JsonElement target : item.getAsJsonArray("notifiedRecipients")) {
            if (!target.isJsonObject()) continue;
            try {
                JsonObject person = target.getAsJsonObject();
                UUID uuid = UUID.fromString(person.get("uuid").getAsString());
                String name = person.get("name").getAsString();
                if (!name.isBlank() && name.length() <= 64) recipients.putIfAbsent(uuid, name);
            } catch (RuntimeException ignored) { }
        }
        return recipients;
    }

    public static String safeAlertText(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        value.codePoints().forEach(character -> {
            if (character == '@') safe.append('＠');
            else if (Character.isISOControl(character)) safe.append(' ');
            else safe.appendCodePoint(character);
        });
        return safe.toString().strip();
    }

    static String boardUrl(Language recipientLanguage, String id) {
        String locale = switch (recipientLanguage) {
            case ZH_CN, ZH_HK, ZH_TW, LZH -> "zh-CN";
            default -> "en";
        };
        return "https://mcmik.top/" + locale + "/community/notices#notice-" + id;
    }

    private static String reference(Language recipientLanguage, String id) {
        return "/plot board " + id.substring(0, 8) + " · " + boardUrl(recipientLanguage, id);
    }
}
