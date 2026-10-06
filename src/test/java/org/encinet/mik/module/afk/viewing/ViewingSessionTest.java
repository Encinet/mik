package org.encinet.mik.module.afk.viewing;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewingSessionTest {
    private static final UUID SCREEN = new UUID(0, 1);
    private static final UUID OTHER_SCREEN = new UUID(0, 2);
    private final ViewingSession session = new ViewingSession(ViewingPolicy.DEFAULT);

    @Test
    void needsContinuousConfirmationButNeverNeedsMovement() {
        advance(0, 4_000, observation(true));
        assertFalse(session.suppressesAutomaticAfk(4_000));
        session.update(observation(true), 5_000);
        assertTrue(session.isConfirmed());
        advance(6_000, 120_000, observation(true));
        assertTrue(session.suppressesAutomaticAfk(120_000));
    }

    @Test
    void lookingAwayDuringConfirmationRestartsTheConfirmationPeriod() {
        advance(0, 3_000, observation(true));
        session.update(observation(false), 4_000);
        advance(5_000, 9_000, observation(true));
        assertFalse(session.suppressesAutomaticAfk(9_000));
        session.update(observation(true), 10_000);
        assertTrue(session.suppressesAutomaticAfk(10_000));
    }

    @Test
    void pausedScreenCannotEstablishANewViewingSession() {
        advance(0, 70_000, paused(true));
        assertFalse(session.suppressesAutomaticAfk(70_000));
    }

    @Test
    void lookingAwayHasABoundedGraceAndOneExitGrace() {
        confirm();
        advance(6_000, 25_000, observation(false));
        assertTrue(session.isConfirmed());
        session.update(observation(false), 26_000);
        assertFalse(session.isConfirmed());
        assertTrue(session.suppressesAutomaticAfk(26_000));
        advance(27_000, 56_000, observation(false));
        assertFalse(session.suppressesAutomaticAfk(56_000));
    }

    @Test
    void briefHeadTurnCanRecoverWithoutReconfirming() {
        confirm();
        advance(6_000, 12_000, observation(false));
        session.update(observation(true), 13_000);
        assertTrue(session.isConfirmed());
        advance(14_000, 40_000, observation(true));
        assertTrue(session.suppressesAutomaticAfk(40_000));
    }

    @Test
    void repeatedPauseObservationsCannotRenewThePauseDeadline() {
        confirm();
        advance(6_000, 65_000, paused(true));
        assertTrue(session.isConfirmed());
        session.update(paused(true), 66_000);
        assertFalse(session.isConfirmed());
        advance(67_000, 96_000, paused(true));
        assertFalse(session.suppressesAutomaticAfk(96_000));
    }

    @Test
    void pauseAndLookAwayDeadlinesAreIndependent() {
        confirm();
        advance(6_000, 10_000, paused(true));
        advance(11_000, 31_000, paused(false));
        assertFalse(session.isConfirmed());
    }

    @Test
    void repeatedDeparturesDoNotExtendExitGrace() {
        confirm();
        advance(6_000, 35_000, null);
        assertTrue(session.suppressesAutomaticAfk(35_000));
        session.update(null, 36_000);
        assertFalse(session.suppressesAutomaticAfk(36_000));
        advance(37_000, 80_000, null);
        assertFalse(session.suppressesAutomaticAfk(80_000));
    }

    @Test
    void switchingScreensNeedsFreshConfirmation() {
        confirm();
        ViewingSession.Observation other = new ViewingSession.Observation(OTHER_SCREEN, "video", PhysicalScreen.Playback.PLAYING, true);
        advance(6_000, 10_000, other);
        assertFalse(session.isConfirmed());
        session.update(other, 11_000);
        assertTrue(session.isConfirmed());
    }

    @Test
    void changingVideoNeedsFreshConfirmation() {
        confirm();
        session.update(new ViewingSession.Observation(SCREEN, "another-video", PhysicalScreen.Playback.PLAYING, true), 6_000);
        assertFalse(session.isConfirmed());
    }

    @Test
    void missingUpdatesExpireSuppressionWithoutAReleaseCallback() {
        confirm();
        assertTrue(session.suppressesAutomaticAfk(7_500));
        assertFalse(session.suppressesAutomaticAfk(7_501));
        session.update(observation(true), 20_000);
        assertFalse(session.suppressesAutomaticAfk(20_000));
        assertFalse(session.isConfirmed());
    }

    @Test
    void confirmationCannotBridgeALongSamplingGap() {
        advance(0, 3_000, observation(true));
        session.update(observation(true), 8_000);
        assertFalse(session.isConfirmed());
        advance(9_000, 13_000, observation(true));
        assertTrue(session.isConfirmed());
    }

    @Test
    void hardInvalidationDoesNotGrantExitGrace() {
        confirm();
        session.clear();
        assertFalse(session.suppressesAutomaticAfk(6_000));
        assertNull(session.screenId());
    }

    @Test
    void unknownPlaybackCannotEstablishOrIndefinitelyKeepASession() {
        ViewingSession.Observation unknown = new ViewingSession.Observation(SCREEN, "video", PhysicalScreen.Playback.UNKNOWN, true);
        advance(0, 10_000, unknown);
        assertFalse(session.suppressesAutomaticAfk(10_000));
        session.clear();
        confirm();
        advance(6_000, 36_000, unknown);
        assertFalse(session.suppressesAutomaticAfk(36_000));
    }

    @Test
    void clockRollbackCannotPreserveAConfirmedSession() {
        confirm();
        assertFalse(session.suppressesAutomaticAfk(4_000));
        session.update(observation(true), 4_000);
        assertFalse(session.isConfirmed());
    }

    @Test
    void inconsistentOrUnboundedPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ViewingPolicy(45, 30, 3, 5_000, 20_000, 60_000, 30_000, 2_500));
        assertThrows(IllegalArgumentException.class, () -> new ViewingPolicy(30, 45, 3, 5_000, 20_000, 60_000, 30_000, 60_000));
    }

    private void confirm() { advance(0, 5_000, observation(true)); }

    private void advance(long from, long until, ViewingSession.Observation observation) {
        for (long now = from; now <= until; now += 1_000) session.update(observation, now);
    }

    private ViewingSession.Observation observation(boolean looking) {
        return new ViewingSession.Observation(SCREEN, "video", PhysicalScreen.Playback.PLAYING, looking);
    }

    private ViewingSession.Observation paused(boolean looking) {
        return new ViewingSession.Observation(SCREEN, "video", PhysicalScreen.Playback.PAUSED, looking);
    }
}
