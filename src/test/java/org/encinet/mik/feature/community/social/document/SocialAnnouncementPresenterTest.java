package org.encinet.mik.feature.community.social.document;

import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialAnnouncementPresenterTest {
    @Test
    void announcementDateFollowsTheDestinationLanguageAndUserContentRetainsItsPolicy() {
        var presenter = new SocialAnnouncementPresenter(FluentTestSupport.languages(), ZoneOffset.UTC);
        long time = Instant.parse("2026-11-11T12:34:00Z").getEpochSecond();
        var cn = presenter.present(Language.ZH_CN, time, "Original [image] content");
        var fr = presenter.present(Language.FR_FR, time, "Original [image] content");
        assertNotEquals(cn.blocks().getFirst(), fr.blocks().getFirst());
        assertTrue(fr.plainText().contains("2026"));
        assertTrue(cn.plainText().contains("12:34"));
        assertEquals(cn.blocks().getLast(), fr.blocks().getLast());
        assertEquals(SocialDocument.Moderation.REQUIRED, fr.moderation());
        assertEquals(Language.FR_FR, fr.language());
    }
}
