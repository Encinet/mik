package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.safety.SocialContentSafetyFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialContentGuardTest {

    private static final SocialOutputPolicy ENABLED = new SocialOutputPolicy(true, 1_000);

    @Test
    void trustedDocumentTextIsNotScanned() {
        SocialDocument blocked = SocialDocument.of("Hidden", SocialDocument.Tone.WARNING);
        SocialContentGuard guard = guard(blocked);
        SocialDocument trusted = SocialDocument.of("blocked phrase",
                SocialDocument.Tone.INFO,
                SocialDocument.paragraph("blocked phrase in localized system prose"));

        assertSame(trusted, guard.apply(trusted, ENABLED));
    }

    @Test
    void anExplicitUserControlledFragmentCanBlockTheReply() {
        SocialDocument blocked = SocialDocument.of("Hidden", SocialDocument.Tone.WARNING);
        SocialContentGuard guard = guard(blocked);
        SocialDocument document = SocialDocument.of("Players", SocialDocument.Tone.INFO,
                        SocialDocument.paragraph("1 online"))
                .withUntrustedText("name-blocked-phrase");

        assertEquals(blocked, guard.apply(document, ENABLED));
    }

    @Test
    void blockedReplyUsesTheLanguageCarriedByTheOriginalDocument() {
        SocialContentGuard guard = SocialContentGuard.available(
                SocialContentSafetyFilter.compile(List.of("blocked phrase"), "test.txt"),
                language -> SocialDocument.of(language.name(), SocialDocument.Tone.WARNING)
                        .localized(language));
        SocialDocument japanese = SocialDocument.of("Players", SocialDocument.Tone.INFO)
                .localized(Language.JA_JP)
                .withUntrustedText("blocked phrase");

        SocialDocument result = guard.apply(japanese, ENABLED);

        assertEquals(Language.JA_JP, result.language());
        assertEquals("JA_JP", result.title());
    }

    @Test
    void outboundChatBodiesUseTheSameSharedSafetyAutomaton() {
        SocialContentGuard guard = guard(
                SocialDocument.of("Hidden", SocialDocument.Tone.WARNING));

        assertTrue(guard.allowsOutboundText("ordinary chat", ENABLED));
        assertFalse(guard.allowsOutboundText("a BLOCKED---phrase", ENABLED));
        assertTrue(guard.allowsOutboundText("blocked phrase",
                new SocialOutputPolicy(false, 1_000)));
    }

    private static SocialContentGuard guard(SocialDocument blocked) {
        return SocialContentGuard.available(SocialContentSafetyFilter.compile(
                List.of("blocked phrase"), "test.txt"), blocked);
    }
}
