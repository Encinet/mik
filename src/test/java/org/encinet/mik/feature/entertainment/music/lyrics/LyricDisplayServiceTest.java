package org.encinet.mik.feature.entertainment.music.lyrics;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.protocol.music.LyricsCue;
import org.encinet.mik.protocol.music.LyricsDocument;
import org.encinet.mik.protocol.music.LyricsPage;
import org.encinet.mik.protocol.music.LyricsResult;
import org.encinet.mik.protocol.music.LyricsTrack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LyricDisplayServiceTest {
    private static final String VERSION = "1".repeat(32);
    private static final ByteString TICKET = ByteString.copyFrom(new byte[16]);

    @Test
    @Timeout(5)
    void onlyAudibleListenersSeePlaybackCaptionsAndGapsAndRemovalClearThem() throws InterruptedException {
        var listenerId = UUID.randomUUID();
        var outsiderId = UUID.randomUUID();
        var listener = mock(Player.class);
        var outsider = mock(Player.class);
        var world = mock(World.class);
        var location = new Location(world, 0, 64, 0);
        when(listener.getWorld()).thenReturn(world);
        when(listener.getLocation()).thenReturn(location);
        when(listener.locale()).thenReturn(Locale.ENGLISH);
        var players = Map.of(listenerId, listener, outsiderId, outsider);
        var position = new AtomicLong(100);
        var audience = Map.of(location, Set.of(listenerId));

        try (var service = new LyricsService(command -> {
            var reply = LyricsResult.newBuilder().setState(LyricsResult.State.READY).setTicket(TICKET);
            switch (command.getOperationCase()) {
                case LOOKUP -> reply.setDocument(LyricsDocument.newBuilder().setMediaVersion(VERSION)
                        .setRevision("a".repeat(64)).setDefaultOriginalTrack(1)
                        .addTracks(LyricsTrack.newBuilder().setTrackKey(1).setRole(LyricsTrack.Role.ORIGINAL).setTimed(true)));
                case READ -> reply.setPage(LyricsPage.newBuilder().setTrackKey(1)
                        .addCues(LyricsCue.newBuilder().setStartMs(100).setEndMs(200).setText("Verse")));
                case RELEASE -> reply.setState(LyricsResult.State.RELEASED);
                default -> throw new AssertionError("unexpected lyrics request");
            }
            return CompletableFuture.completedFuture(reply.build());
        }); var display = new LyricDisplayService(service, players::get)) {
            var document = display.document(VERSION);
            while (document.poll() == LyricsService.Status.PENDING) Thread.sleep(2);
            assertInstanceOf(LyricsService.Available.class, document.poll());
            var remove = display.start(location, document, position::get);
            display.refresh(audience);
            position.set(200);
            display.refresh(audience);
            position.set(100); // rewind or loop
            display.refresh(audience);
            remove.run();
            display.refresh(audience);

            var order = inOrder(listener);
            order.verify(listener).sendActionBar(Component.text("Verse"));
            order.verify(listener).sendActionBar(Component.empty());
            order.verify(listener).sendActionBar(Component.text("Verse"));
            order.verify(listener).sendActionBar(Component.empty());
            verifyNoInteractions(outsider);
        }
    }

    @Test
    void translationUsesItsOwnIntervalsAndPlainTextDoesNotAppearInPlayback() {
        var lyrics = new Lyrics(VERSION, "revision", 1, List.of(
                new Lyrics.Track(1, Lyrics.Role.ORIGINAL, "ja", List.of(new Lyrics.Cue(100, 400L, "Verse")), List.of()),
                new Lyrics.Track(2, Lyrics.Role.TRANSLATION, "en", List.of(new Lyrics.Cue(150, 200L, "Meaning")), List.of()),
                new Lyrics.Track(3, Lyrics.Role.PRONUNCIATION, "en", List.of(), List.of("Unaligned text"))));
        var preferred = Locale.LanguageRange.parse("en-US");
        assertEquals("Verse", LyricDisplayService.captionAt(lyrics, preferred, Lyrics.Role.TRANSLATION, 149));
        assertEquals("Verse · Meaning", LyricDisplayService.captionAt(lyrics, preferred, Lyrics.Role.TRANSLATION, 150));
        assertEquals("Verse", LyricDisplayService.captionAt(lyrics, preferred, Lyrics.Role.TRANSLATION, 200));
        assertEquals("Verse", LyricDisplayService.captionAt(lyrics, preferred, Lyrics.Role.PRONUNCIATION, 150));
        assertNull(LyricDisplayService.captionAt(lyrics, preferred, Lyrics.Role.TRANSLATION, 400));
    }
}
