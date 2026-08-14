package org.encinet.mik.module.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

final class BanMessageRenderer {

    private static final String SCREEN_DIVIDER = "━━━━━━━━━━━━━━━━━━━━";
    private static final TextColor SCREEN_BRAND = TextColor.color(0xF6C453);
    private static final TextColor SCREEN_DIVIDER_COLOR = TextColor.color(0x6F4355);
    private static final TextColor SCREEN_TITLE_START = TextColor.color(0xFF5C7A);
    private static final TextColor SCREEN_TITLE_END = TextColor.color(0xFF9A62);
    private static final TextColor SCREEN_LABEL = TextColor.color(0xA7B0C0);
    private static final TextColor SCREEN_VALUE = TextColor.color(0xF4F6FA);
    private static final TextColor SCREEN_EXPIRATION = TextColor.color(0xFFD166);
    private static final TextColor SCREEN_APPEAL = TextColor.color(0x69D2E7);

    private final LanguageService languageService;
    private final ZoneId zoneId;

    BanMessageRenderer(LanguageService languageService, ZoneId zoneId) {
        this.languageService = languageService;
        this.zoneId = zoneId;
    }

    Component banMessage(Language language, BanRecord record) {
        return banScreen(
                languageService.t(language, Message.BAN_KICK_SERVER),
                languageService.t(language, Message.BAN_KICK_TITLE),
                languageService.t(language, Message.BANLIST_REASON),
                reasonText(language, record.reason()),
                languageService.t(language, Message.BANLIST_EXPIRES),
                expirationText(language, record.expiresAt()),
                languageService.t(language, Message.BAN_KICK_APPEAL));
    }

    static Component banScreen(
            String server,
            String title,
            String reasonLabel,
            String reason,
            String expirationLabel,
            String expiration,
            String appeal
    ) {
        return Component.text()
                .append(Component.text(server, SCREEN_BRAND, TextDecoration.BOLD))
                .appendNewline()
                .append(Component.text(SCREEN_DIVIDER, SCREEN_DIVIDER_COLOR))
                .appendNewline()
                .appendNewline()
                .append(gradientTitle(title))
                .appendNewline()
                .appendNewline()
                .append(sectionLabel(reasonLabel))
                .appendNewline()
                .append(Component.text(reason, SCREEN_VALUE))
                .appendNewline()
                .appendNewline()
                .append(sectionLabel(expirationLabel))
                .appendNewline()
                .append(Component.text(expiration, SCREEN_EXPIRATION))
                .appendNewline()
                .appendNewline()
                .append(Component.text(appeal, SCREEN_APPEAL))
                .appendNewline()
                .append(Component.text(SCREEN_DIVIDER, SCREEN_DIVIDER_COLOR))
                .build();
    }

    private static Component sectionLabel(String label) {
        return Component.text(label, SCREEN_LABEL, TextDecoration.BOLD);
    }

    private static Component gradientTitle(String title) {
        int[] codePoints = title.codePoints().toArray();
        Component result = Component.empty();
        for (int index = 0; index < codePoints.length; index++) {
            double ratio = codePoints.length <= 1 ? 0.0D : (double) index / (codePoints.length - 1);
            result = result.append(Component.text(
                    Character.toString(codePoints[index]),
                    interpolate(SCREEN_TITLE_START, SCREEN_TITLE_END, ratio),
                    TextDecoration.BOLD));
        }
        return result;
    }

    private static TextColor interpolate(TextColor start, TextColor end, double ratio) {
        int red = interpolate(start.red(), end.red(), ratio);
        int green = interpolate(start.green(), end.green(), ratio);
        int blue = interpolate(start.blue(), end.blue(), ratio);
        return TextColor.color(red, green, blue);
    }

    private static int interpolate(int start, int end, double ratio) {
        return (int) Math.round(start + (end - start) * ratio);
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
