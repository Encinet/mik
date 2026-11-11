package org.encinet.mik.feature.community.social.document;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** Common date, duration precision and time-zone rules for social documents. */
public final class SocialTimeText {
    private SocialTimeText() { }

    public static String dateTime(Language language, Instant instant, ZoneId zone) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(language.locale()).withZone(zone).format(instant);
    }

    public static String duration(LanguageService languages, Language language, Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        if (days > 0) return languages.t(language, Message.SOCIAL_DURATION_DAYS, days, hours, minutes);
        if (hours > 0) return languages.t(language, Message.SOCIAL_DURATION_HOURS, hours, minutes);
        return languages.t(language, Message.SOCIAL_DURATION_MINUTES, minutes);
    }
}
