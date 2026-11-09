package org.encinet.mik.feature.entertainment.music.application;

import java.util.ArrayList;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiOverflowException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicProblemTest {
    @Test void layoutAndComponentFailuresAreInternalErrorsWithTheirOriginalStack() {
        var records = new ArrayList<LogRecord>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var invalidKey = assertThrows(IllegalArgumentException.class, () -> UiKey.named("track:local:folder/song.mp3"));
        var overflow = new CompletionException(new UiOverflowException("Content exceeds viewport"));
        for (var error : new Throwable[] {invalidKey, overflow, new IllegalStateException("Cannot open workspace")}) {
            assertEquals(MusicProblem.Code.INTERNAL, MusicProblem.report(logger, "Opening /music for player", error));
            assertSame(error, records.getLast().getThrown());
            assertEquals(Level.SEVERE, records.getLast().getLevel());
            assertEquals("Opening /music for player", records.getLast().getMessage());
        }
        int count = records.size();
        assertEquals(MusicProblem.Code.INVALID, MusicProblem.report(logger, "Playlist input",
                new CompletionException(new MusicProblem(MusicProblem.Code.INVALID))));
        assertEquals(count, records.size(), "Expected input errors must not produce server error logs");
    }

    @Test void onlyAsyncWrappersAreRemovedAndDomainCategoriesSurviveTheirCauses() {
        var offline = new MusicProblem(MusicProblem.Code.OFFLINE);
        offline.initCause(new IllegalArgumentException("Device data"));
        assertEquals(MusicProblem.Code.OFFLINE, MusicProblem.code(new CompletionException(new ExecutionException(offline))));
        assertEquals(MusicProblem.Code.INTERNAL, MusicProblem.code(new IllegalStateException("Menu failed", offline)),
                "A menu bug must not be classified using a nested operation's user-facing category");
        assertEquals(MusicProblem.Code.INTERNAL, MusicProblem.code(new CompletionException(new IllegalArgumentException("UI dimensions"))));
    }

    @Test void networkAndSourceAvailabilityFailuresAreNotInternalMenuErrors() {
        for (var error : new Throwable[] {new java.io.IOException("Source is unavailable"), new java.util.concurrent.TimeoutException(),
                new java.util.concurrent.CancellationException()}) {
            assertEquals(MusicProblem.Code.UNAVAILABLE, MusicProblem.code(new CompletionException(error)));
        }
    }

    @Test void internalErrorsHaveAnExplicitTranslationInEveryLanguage() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            String internal = FluentTestSupport.text(bundle, Message.MUSIC_SPACE_ERROR, MusicProblem.Code.INTERNAL.name());
            assertFalse(internal.isBlank(), language.name());
            assertNotEquals(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_ERROR, "INVALID"), internal, language.name());
            assertNotEquals(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_ERROR, "UNKNOWN"), internal, language.name());
        }
    }
}
