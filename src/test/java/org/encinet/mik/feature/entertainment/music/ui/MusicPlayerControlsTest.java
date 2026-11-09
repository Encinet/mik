package org.encinet.mik.feature.entertainment.music.ui;

import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.encinet.mik.feature.entertainment.music.playback.*;
import org.encinet.mik.shared.menu.ui.UiIcons;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerControlsTest {
    private final UUID target=UUID.randomUUID(), run=UUID.randomUUID(), entry=UUID.randomUUID();
    private MusicPlaybackSnapshot sample(PlaybackStatus status, UUID preparation, boolean physical) {
        return new MusicPlaybackSnapshot(target,physical ? null : entry,run,MusicWorkspaceStateTest.track("song"),status,0,1,false,preparation);
    }

    @Test void statusDescribesTheBackendWhileTheCentralIconDescribesTheNextAction() {
        var playing=MusicPlayerControls.sample(sample(PlaybackStatus.PLAYING,null,false),PlaybackProgram.empty(),false);
        assertEquals(MusicPlayerControls.Primary.PAUSE,playing.primary());
        assertEquals(UiIcons.ACTIVITY_BARS,playing.status());
        assertEquals("playing",playing.statusLabel());
        var paused=MusicPlayerControls.sample(sample(PlaybackStatus.PAUSED,null,false),PlaybackProgram.empty(),false);
        assertEquals(MusicPlayerControls.Primary.RESUME,paused.primary());
        assertEquals(UiIcons.PAUSE,paused.status());
        assertEquals("paused",paused.statusLabel());
        assertTrue(paused.enabled());
        var stopped=MusicPlayerControls.sample(sample(PlaybackStatus.STOPPED,null,false),PlaybackProgram.empty(),false);
        assertEquals(MusicPlayerControls.Primary.PLAY,stopped.primary());
        assertEquals(UiIcons.STOP,stopped.status());
        assertTrue(stopped.enabled());
    }

    @Test void onlyAnIdentifiedPreparationCanBeCancelledAndPersistenceRemainsBusy() {
        var snapshot=sample(PlaybackStatus.LOADING,UUID.randomUUID(),false);
        var preparing=MusicPlayerControls.sample(snapshot,PlaybackProgram.empty(),false);
        assertEquals(MusicPlayerControls.Primary.CANCEL,preparing.primary());
        assertEquals(UiIcons.CROSS,preparing.primary().icon);
        assertEquals(UiIcons.LOADING,preparing.status());
        assertTrue(preparing.enabled());
        assertFalse(MusicPlayerControls.sample(snapshot,PlaybackProgram.empty(),true).enabled());
        assertFalse(MusicPlayerControls.sample(sample(PlaybackStatus.LOADING,null,false),PlaybackProgram.empty(),false).enabled());
    }

    @Test void physicalDiscsKeepTheirActualPauseCapabilityWithoutPretendingToHaveQueueHistory() {
        var physical=MusicPlayerControls.sample(sample(PlaybackStatus.PLAYING,null,true),PlaybackProgram.empty(),false);
        assertTrue(physical.enabled());
        assertFalse(physical.previous());
        assertFalse(physical.next());
        var noContent=new MusicPlaybackSnapshot(target,null,null,null,PlaybackStatus.STOPPED,0,1,false,null);
        assertFalse(MusicPlayerControls.sample(noContent,PlaybackProgram.empty(),false).enabled());
        var queued=PlaybackProgram.empty().submit(List.of(QueueEntry.create("song",UUID.randomUUID())),PlaybackProgram.Submission.APPEND);
        var ready=MusicPlayerControls.sample(noContent,queued,false);
        assertTrue(ready.enabled());
        assertTrue(ready.next());
        for(var status:PlaybackStatus.values()) {
            var busy=MusicPlayerControls.sample(sample(status,null,false),queued,true);
            assertFalse(busy.enabled()); assertFalse(busy.previous()); assertFalse(busy.next());
        }
    }
}
