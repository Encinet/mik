package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.safety.SocialContentModerator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialContentGuardTest {

    private static final SocialOutputPolicy ENABLED = new SocialOutputPolicy(true, 1_000);

    @Test
    void trustedDocumentTextIsNotScanned() {
        SocialDocument blocked = SocialDocument.of(TRUSTED, "Hidden", SocialDocument.Tone.WARNING);
        SocialContentGuard guard = guard(blocked);
        SocialDocument trusted = SocialDocument.of(TRUSTED, "blocked phrase",
                SocialDocument.Tone.INFO,
                SocialDocument.paragraph("blocked phrase in localized system prose"));

        assertSame(trusted, guard.apply(trusted, ENABLED));
    }

    @Test
    void moderationReceivesTheCompleteBoundedReplyWithItsContext() {
        SocialDocument blocked = SocialDocument.of(TRUSTED, "Hidden", SocialDocument.Tone.WARNING);
        AtomicReference<String> submitted = new AtomicReference<>();
        SocialContentGuard guard = SocialContentGuard.available(text -> {
            submitted.set(text);
            return false;
        }, blocked);
        SocialDocument document = SocialDocument.of(REQUIRED, "Players", SocialDocument.Tone.INFO,
                        SocialDocument.paragraph("Player: blocked phrase " + "context ".repeat(30)));

        assertEquals(blocked, guard.apply(document, ENABLED));
        assertEquals(document.plainText(), submitted.get());
        guard.apply(document, new SocialOutputPolicy(true, 100));
        assertEquals(document.truncated(100).plainText(), submitted.get());
    }

    @Test
    void blockedReplyUsesTheLanguageCarriedByTheOriginalDocument() {
        SocialContentGuard guard = SocialContentGuard.available(
                _ -> false,
                language -> SocialDocument.of(TRUSTED, language.name(), SocialDocument.Tone.WARNING)
                        .localized(language));
        SocialDocument japanese = SocialDocument.of(REQUIRED, "Players", SocialDocument.Tone.INFO,
                        SocialDocument.paragraph("blocked phrase"))
                .localized(Language.JA_JP);

        SocialDocument result = guard.apply(japanese, ENABLED);

        assertEquals(Language.JA_JP, result.language());
        assertEquals("JA_JP", result.title());
    }

    @Test
    void outboundChatBodiesUseTheSharedModeratorAndRespectThePlatformSwitch() {
        AtomicInteger calls = new AtomicInteger();
        SocialContentGuard guard = SocialContentGuard.available(text -> {
            calls.incrementAndGet();
            return !text.contains("blocked");
        }, SocialDocument.of(TRUSTED, "Hidden", SocialDocument.Tone.WARNING));

        assertTrue(guard.allowsOutboundText("ordinary chat", REQUIRED, ENABLED));
        assertFalse(guard.allowsOutboundText("blocked phrase", REQUIRED, ENABLED));
        assertTrue(guard.allowsOutboundText("blocked phrase", REQUIRED, new SocialOutputPolicy(false, 1_000)));
        assertTrue(guard.allowsOutboundText(" \n", REQUIRED, ENABLED));
        assertEquals(2, calls.get());
    }

    private static SocialContentGuard guard(SocialDocument blocked) {
        return SocialContentGuard.available(text -> !text.contains("blocked"), blocked);
    }

    @Test
    void reloadClosesOldClientsAndConfigurationFailureDoesNotReuseStaleVerdicts() {
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger loaded = new AtomicInteger();
        AtomicBoolean broken = new AtomicBoolean();
        SocialContentGuard guard = SocialContentGuard.reloadable(() -> {
            if (broken.get()) throw new IllegalArgumentException("Invalid configuration");
            int generation = loaded.incrementAndGet();
            return new SocialContentModerator() {
                @Override public boolean allows(String text) { return generation == 1; }
                @Override public void close() { closed.incrementAndGet(); }
            };
        }, _ -> SocialDocument.of(TRUSTED, "Hidden", SocialDocument.Tone.WARNING));
        try (guard) {
            guard.reload();
            assertTrue(guard.allowsOutboundText("hello", REQUIRED, ENABLED));
            guard.reload();
            assertFalse(guard.allowsOutboundText("hello", REQUIRED, ENABLED));
            assertEquals(1, closed.get());
            broken.set(true);
            assertEquals(java.util.Optional.of("Invalid configuration"), guard.reload());
            assertEquals(2, closed.get());
            SocialDocument trusted = SocialDocument.of(TRUSTED, "Help", SocialDocument.Tone.INFO);
            SocialDocument required = SocialDocument.of(REQUIRED, "User text", SocialDocument.Tone.INFO);
            assertSame(trusted, guard.apply(trusted, ENABLED));
            assertThrows(IllegalStateException.class, () -> guard.apply(required, ENABLED));
            assertThrows(IllegalStateException.class,
                    () -> guard.allowsOutboundText("hello", REQUIRED, ENABLED));
            assertTrue(guard.allowsOutboundText("hello", REQUIRED, new SocialOutputPolicy(false, 100)));
            broken.set(false);
            guard.reload();
        }
        guard.close();
        assertEquals(3, closed.get());
        assertThrows(IllegalStateException.class, guard::reload);
    }
}
