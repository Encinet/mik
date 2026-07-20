package org.encinet.mik.module.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

final class BanAnnouncementBroadcaster {

    private final LanguageService languageService;
    private final BanMessageRenderer renderer;

    BanAnnouncementBroadcaster(LanguageService languageService, BanMessageRenderer renderer) {
        this.languageService = languageService;
        this.renderer = renderer;
    }

    void broadcast(String operator, BanRecord record) {
        for (Player recipient : Bukkit.getOnlinePlayers()) {
            recipient.sendMessage(message(languageService.language(recipient), operator, record));
        }
        Bukkit.getConsoleSender().sendMessage(message(Language.DEFAULT, operator, record));
    }

    Component message(Language language, String operator, BanRecord record) {
        String expiration = renderer.expirationText(language, record.expiresAt());
        String reason = renderer.reasonText(language, record.reason());
        return bulletin(
                languageService.t(language, Message.BAN_BROADCAST_PREFIX),
                languageService.t(language, Message.BANLIST_PLAYER), record.playerName(),
                languageService.t(language, Message.BANLIST_EXPIRES), expiration,
                languageService.t(language, Message.BANLIST_REASON), reason,
                languageService.t(language, Message.BAN_BROADCAST_OPERATOR), operator);
    }

    static Component bulletin(
            String title,
            String playerLabel,
            String player,
            String expirationLabel,
            String expiration,
            String reasonLabel,
            String reason,
            String operatorLabel,
            String operator
    ) {
        return Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD)
                .appendNewline()
                .append(field(playerLabel, player, NamedTextColor.YELLOW))
                .appendNewline()
                .append(field(expirationLabel, expiration, NamedTextColor.WHITE))
                .appendNewline()
                .append(field(reasonLabel, reason, NamedTextColor.RED))
                .appendNewline()
                .append(field(operatorLabel, operator, NamedTextColor.WHITE));
    }

    private static Component field(String label, String value, NamedTextColor valueColor) {
        return Component.text("  " + label + "  ", NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }
}
