package org.encinet.mik.module.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

final class BanMessageRenderer {

    private final LanguageService languageService;
    private final ZoneId zoneId;

    BanMessageRenderer(LanguageService languageService, ZoneId zoneId) {
        this.languageService = languageService;
        this.zoneId = zoneId;
    }

    Component banMessage(Language language, BanRecord record) {
        return Component.text()
                .append(Component.text(languageService.t(language, Message.BAN_KICK_SERVER), NamedTextColor.GOLD,
                        TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text("-------------------", NamedTextColor.DARK_GRAY))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.BAN_KICK_TITLE), NamedTextColor.RED))
                .appendNewline()
                .append(labelLine(language, Message.BANLIST_REASON, reasonText(language, record.reason())))
                .appendNewline()
                .append(labelLine(language, Message.BANLIST_EXPIRES, expirationText(language, record.expiresAt())))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.BAN_KICK_APPEAL), NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text("-------------------", NamedTextColor.DARK_GRAY))
                .build();
    }

    Component labelLine(Language language, Message label, String value) {
        return Component.text(languageService.t(language, label) + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    String statusText(Language language, BanRecord record, Instant now) {
        return switch (record.statusAt(now)) {
            case ACTIVE -> languageService.t(language, Message.BAN_STATUS_ACTIVE);
            case EXPIRED -> languageService.t(language, Message.BAN_STATUS_EXPIRED);
            case REVOKED -> languageService.t(language, Message.BAN_STATUS_REVOKED);
        };
    }

    String expirationText(Language language, Instant expiration) {
        if (expiration == null) {
            return languageService.t(language, Message.BAN_PERMANENT);
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(language.locale()).withZone(zoneId).format(expiration);
    }

    String reasonText(Language language, String reason) {
        String userReason = BanSeverity.userReason(reason);
        return userReason == null || userReason.isBlank()
                ? languageService.t(language, Message.BAN_UNKNOWN_REASON)
                : userReason;
    }

    String sourceText(Language language, String source) {
        return source == null || source.isBlank()
                ? languageService.t(language, Message.BAN_SYSTEM_SOURCE)
                : source;
    }
}
