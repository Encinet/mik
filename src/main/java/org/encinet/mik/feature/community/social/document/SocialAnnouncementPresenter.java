package org.encinet.mik.feature.community.social.document;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** Announcement layout and target-language formatting, independent of routing and transport. */
public final class SocialAnnouncementPresenter {
    private final LanguageService languages;
    private final ZoneId zone;

    public SocialAnnouncementPresenter(LanguageService languages, ZoneId zone) {
        this.languages = Objects.requireNonNull(languages, "languages");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public SocialDocument present(Language language, long timestamp, String content) {
        return SocialDocument.of(SocialDocument.Moderation.REQUIRED,
                languages.t(language, Message.ANNOUNCEMENT_UPDATED), SocialDocument.Tone.INFO,
                SocialDocument.paragraph(SocialTimeText.dateTime(language, Instant.ofEpochSecond(timestamp), zone)),
                SocialDocument.paragraph(content)).localized(language);
    }
}
