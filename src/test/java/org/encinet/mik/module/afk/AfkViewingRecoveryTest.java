package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AfkViewingRecoveryTest {
    private static final UUID WORLD = new UUID(0, 1);

    @Test
    void removingAutomaticAfkForViewingDoesNotRefreshTheIdleClockOrRewards() {
        AfkPlayerSession session = new AfkPlayerSession(0, WORLD, 0, 0, 0);
        long idleAt = AfkPolicy.DEFAULT.idleTimeoutMillis();
        session.enterAfk(idleAt, false);
        session.exitAfkForViewing(WORLD, 0, 0, 0, false);
        assertFalse(session.activity().isSuspended());
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE, session.activity().check(idleAt + 60_000));
        assertFalse(session.isActivityEligible(idleAt + 60_000));
        assertFalse(session.checkAutomaticAfk(idleAt + 60_000, true, true).shouldEnterAfk());
        assertFalse(session.checkAutomaticAfk(idleAt + 60_000, true, false).shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(idleAt + 61_000, true, false).shouldEnterAfk());
    }

    @Test
    void longProtectedViewingDoesNotShiftIntentionalOutcomeAge() {
        AfkPlayerSession session = new AfkPlayerSession(0, WORLD, 0, 0, 0);
        session.enterAfk(10_000, false);
        long now = AfkPolicy.DEFAULT.movementOnlyRewardTimeoutMillis() + 60_000;
        session.exitAfkForViewing(WORLD, 0, 0, 0, false);
        session.activity().recordLightActivity(now);
        assertFalse(session.isActivityEligible(now));
    }

    @Test
    void viewingRecoveryCannotSynthesizeAHeldMovementGesture() {
        AfkPlayerSession session = new AfkPlayerSession(0, WORLD, 0, 0, 0);
        session.enterAfk(180_000, true);
        session.exitAfkForViewing(WORLD, 0, 0, 0, true);
        assertFalse(session.activity().recordMovementInput(true, WORLD, 0, 0, 0, 181_000));
        assertFalse(session.activity().recordMovementInput(false, WORLD, 0, 0, 0, 182_000));
        assertTrue(session.activity().recordMovementInput(true, WORLD, 0, 0, 0, 183_000));
    }

    @Test
    void viewingRecoveryCannotUnlockAnExistingAutomationRewardRestriction() {
        AfkActivityTracker activity = new AfkActivityTracker(0, WORLD, 0, 0, 0);
        activity.recordMovementInput(true, WORLD, 0, 0, 0, 0);
        double positionX = 0;
        double positionZ = 0;
        int samples = (int) (AfkBehaviorAnalyzer.ANALYSIS_MILLIS / AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS);
        for (int sample = 1; sample <= samples; sample++) {
            switch ((sample - 1) / 5 % 4) {
                case 0 -> positionX += 0.25;
                case 1 -> positionZ += 0.25;
                case 2 -> positionX -= 0.25;
                case 3 -> positionZ -= 0.25;
            }
            long now = sample * AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;
            activity.recordObservation(WORLD, positionX, 0, positionZ, 15, now);
            activity.recordMovement(WORLD, positionX, 0, positionZ, now);
        }
        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED, activity.check(AfkBehaviorAnalyzer.ANALYSIS_MILLIS));
        activity.suspendForAfk(AfkBehaviorAnalyzer.ANALYSIS_MILLIS, false);
        activity.resumeForViewing(WORLD, positionX, 0, positionZ, false);
        assertTrue(activity.isAutomationRewardLocked());
        assertFalse(activity.isActivityEligible(AfkBehaviorAnalyzer.ANALYSIS_MILLIS + 1_000));
    }

    @Test
    void viewingIntegrationPreservesManualAndScriptAfkAndNeverRecordsTrustedInput() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/encinet/mik/module/afk/AfkModule.java"));
        int begin = source.indexOf("private void clearAutomaticAfkForViewing");
        int end = source.indexOf("private void notifyListeners", begin);
        String recovery = source.substring(begin, end);
        assertTrue(recovery.contains("state.source() != AfkSource.AUTOMATIC"));
        assertTrue(recovery.contains("exitAfkForViewing("));
        assertFalse(recovery.contains("recordTrustedActivity("));
        assertFalse(recovery.contains("clearAfk("));
        assertFalse(recovery.contains("syncMovementInput("));
        assertTrue(source.indexOf("viewingController.update(now);") < source.indexOf("viewingController.isConfirmedViewing("));
        String controller = Files.readString(Path.of("src/main/java/org/encinet/mik/module/afk/viewing/AfkViewingController.java"));
        assertFalse(controller.contains("recordTrustedActivity("));
        assertFalse(controller.contains("suppressAutomaticAfk("));
    }
}
