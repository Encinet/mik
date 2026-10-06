package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkBehaviorAnalyzerTest {

    private static final UUID WORLD = new UUID(0L, 1L);
    private static final long END = AfkBehaviorAnalyzer.ANALYSIS_MILLIS;
    private static final long INTERVAL = AfkBehaviorAnalyzer.SAMPLE_INTERVAL_MILLIS;

    @Test
    void balancedCycleHasHighMarginalButLowConditionalEntropy() {
        AfkBehaviorAnalyzer analyzer = analyze(square(), END);
        AfkBehaviorAnalyzer.Analysis analysis = analyzer.analyze(END);

        assertEquals(AfkBehaviorAnalyzer.Pattern.LOCAL_REPETITION, analysis.pattern());
        assertTrue(analysis.recent().directionEntropy() > 0.98D);
        assertTrue(analysis.recent().conditionalDirectionEntropy() < 0.01D);
        assertTrue(analysis.recent().periodicity() > 0.95D);
        assertTrue(analyzer.isLikelyAutomated(END, 0L));
    }

    @Test
    void confinedRandomWalkHasUnpredictableDirectionChangesAcrossSeeds() {
        for (int seed = 0; seed < 10; seed++) {
            AfkBehaviorAnalyzer analyzer = analyze(randomWalk(seed), END);
            AfkBehaviorAnalyzer.Analysis analysis = analyzer.analyze(END);

            assertEquals(AfkBehaviorAnalyzer.Pattern.RANDOMIZED_LOCAL_MOVEMENT,
                    analysis.pattern(), "seed=" + seed);
            assertTrue(analysis.recent().directionEntropy() > 0.85D);
            assertTrue(analysis.recent().conditionalDirectionEntropy() > 0.80D);
            assertTrue(analyzer.isLikelyAutomated(END, 0L));
        }
    }

    @Test
    void actualCircularPositionsAreRecognizedWithoutDependingOnViewRotation() {
        AfkBehaviorAnalyzer analyzer = analyze(circle(), END);

        assertEquals(AfkBehaviorAnalyzer.Pattern.LOCAL_REPETITION, analyzer.analyze(END).pattern());
        assertTrue(analyzer.analyze(END).recent().horizontalRange() > 8.0D);
        assertTrue(Double.isNaN(analyzer.analyze(END).recent().conditionalDirectionEntropy()));
        assertTrue(analyzer.analyze(END).recent().periodicity() > 0.95D);
    }

    @Test
    void jumpReversalsAreDifferentFromContinuousVerticalTravel() {
        AfkBehaviorAnalyzer jumping = analyze(jumps(), END);
        AfkBehaviorAnalyzer ascending = analyze(
                index -> new Position(0.0D, index * 0.25D, 0.0D), END);

        assertEquals(AfkBehaviorAnalyzer.Pattern.VERTICAL_REPETITION, jumping.analyze(END).pattern());
        assertTrue(jumping.analyze(END).recent().verticalReversals() > 100);
        assertEquals(AfkBehaviorAnalyzer.Pattern.ORDINARY_MOVEMENT, ascending.analyze(END).pattern());
    }

    @Test
    void directedExplorationIsNotRestrictedEvenWithRandomLooking() {
        AfkBehaviorAnalyzer analyzer = analyze(
                index -> new Position(index * 0.25D, 0.0D, Math.sin(index)), END);

        assertEquals(AfkBehaviorAnalyzer.Pattern.ORDINARY_MOVEMENT, analyzer.analyze(END).pattern());
        assertFalse(analyzer.isLikelyAutomated(END, 0L));
    }

    @Test
    void shortHumanPlayIsInsufficientEvidenceRatherThanAutomation() {
        for (Trajectory trajectory : new Trajectory[]{jumps(), circle(), square(), randomWalk(1)}) {
            AfkBehaviorAnalyzer analyzer = analyze(trajectory, 60_000L);

            assertEquals(AfkBehaviorAnalyzer.Pattern.INSUFFICIENT_DATA,
                    analyzer.analyze(60_000L).pattern());
            assertFalse(analyzer.isLikelyAutomated(60_000L, 0L));
        }
    }

    @Test
    void longAmbiguousMovementRestrictsOnlyRewardsAndNeverCreatesAfk() {
        for (Trajectory trajectory : new Trajectory[]{jumps(), circle(), square(), randomWalk(1)}) {
            AfkPlayerSession session = new AfkPlayerSession(0L, WORLD, 0.0D, 0.0D, 0.0D);
            AfkActivityTracker tracker = session.activity();
            tracker.recordMovementInput(true, WORLD, 0.0D, 0.0D, 0.0D, 0L);
            for (int index = 1; index <= END / INTERVAL; index++) {
                long now = index * INTERVAL;
                Position position = trajectory.at(index);
                tracker.recordObservation(WORLD, position.positionX, position.positionY,
                        position.positionZ, 0.0F, now);
                tracker.recordMovement(WORLD, position.positionX, position.positionY,
                        position.positionZ, now);
            }

            AfkPlayerSession.AutomaticCheck check = session.checkAutomaticAfk(END, true);
            assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED, check.result());
            assertFalse(check.shouldEnterAfk());
            assertFalse(tracker.isAutomaticAfkCandidate(END));
            assertTrue(tracker.canMovementClearAfk());
            assertFalse(session.checkAutomaticAfk(END + 1_000L, true).shouldEnterAfk());
        }
    }

    @Test
    void acceptedGameplayProtectsAPlayerWhoContinuesRunningAndJumping() {
        AfkPlayerSession session = new AfkPlayerSession(0L, WORLD, 0.0D, 0.0D, 0.0D);
        AfkActivityTracker tracker = session.activity();
        tracker.recordMovementInput(true, WORLD, 0.0D, 0.0D, 0.0D, 0L);
        Trajectory trajectory = randomWalk(3);
        for (int index = 1; index <= END / INTERVAL; index++) {
            long now = index * INTERVAL;
            Position position = trajectory.at(index);
            tracker.recordObservation(WORLD, position.positionX, position.positionY,
                    position.positionZ, 0.0F, now);
            tracker.recordMovement(WORLD, position.positionX, position.positionY, position.positionZ, now);
            if (now % 60_000L == 0L) {
                tracker.recordAction(new AfkActionEvidence(AfkActionEvidence.Type.BLOCK_CHANGE,
                        "built:" + now), now);
            }
            assertFalse(session.checkAutomaticAfk(now, true).shouldEnterAfk());
        }

        assertFalse(tracker.isAutomationRewardLocked());
        assertTrue(tracker.isActivityEligible(END));
    }

    @Test
    void randomWeakClicksCannotResetMovementAnalysisOrIntentionalAge() {
        AfkActivityTracker tracker = new AfkActivityTracker(0L, WORLD, 0.0D, 0.0D, 0.0D);
        tracker.recordMovementInput(true, WORLD, 0.0D, 0.0D, 0.0D, 0L);
        Trajectory trajectory = randomWalk(4);
        for (int index = 1; index <= END / INTERVAL; index++) {
            long now = index * INTERVAL;
            Position position = trajectory.at(index);
            tracker.recordObservation(WORLD, position.positionX, 0.0D, position.positionZ, 0.0F, now);
            tracker.recordMovement(WORLD, position.positionX, 0.0D, position.positionZ, now);
            if (now % 31_000L == 0L) {
                tracker.recordAction(new AfkActionEvidence(AfkActionEvidence.Type.BLOCK_INTERACTION,
                        "clicked:" + now), now);
            }
        }

        assertEquals(AfkActivityTracker.CheckResult.ACTIVITY_REWARD_LOCKED, tracker.check(END));
        assertFalse(tracker.isActivityEligible(END));
    }

    @Test
    void sparseAndStaleSamplesAreExplicitlyInsufficient() {
        AfkBehaviorAnalyzer analyzer = new AfkBehaviorAnalyzer();
        for (long now = 0L; now <= END; now += 2_000L) {
            analyzer.record(WORLD, now / 100.0D, 0.0D, 0.0D, 0.0F, true, now);
        }
        assertEquals(AfkBehaviorAnalyzer.Pattern.INSUFFICIENT_DATA, analyzer.analyze(END).pattern());

        AfkBehaviorAnalyzer dense = analyze(square(), END);
        assertEquals(AfkBehaviorAnalyzer.Pattern.INSUFFICIENT_DATA,
                dense.analyze(END * 2L + INTERVAL).pattern());
    }

    @Test
    void missingIntervalDoesNotBecomeARecordedTeleportDistance() {
        AfkBehaviorAnalyzer analyzer = new AfkBehaviorAnalyzer();
        analyzer.record(WORLD, 0.0D, 0.0D, 0.0D, 0.0F, true, 0L);
        analyzer.record(WORLD, 1.0D, 0.0D, 0.0D, 0.0F, true, INTERVAL);
        analyzer.record(WORLD, 10_000.0D, 0.0D, 0.0D, 0.0F, true, 5_000L);

        assertEquals(1.0D, analyzer.analyze(5_000L).recent().totalDistance(), 1.0E-9D);
    }

    @Test
    void worldChangesAndNonFinitePositionsInvalidateOldEvidence() {
        AfkBehaviorAnalyzer analyzer = analyze(square(), END);
        analyzer.record(new UUID(0L, 2L), 100.0D, 0.0D, 0.0D, 0.0F, true, END + INTERVAL);
        assertEquals(AfkBehaviorAnalyzer.Pattern.INSUFFICIENT_DATA,
                analyzer.analyze(END + INTERVAL).pattern());

        AfkBehaviorAnalyzer invalid = analyze(square(), END);
        invalid.record(WORLD, Double.NaN, 0.0D, 0.0D, 0.0F, true, END + INTERVAL);
        assertEquals(AfkBehaviorAnalyzer.Pattern.INSUFFICIENT_DATA,
                invalid.analyze(END + INTERVAL).pattern());
    }

    @Test
    void passiveTransportAndViewOnlyChangesDoNotCountAsControlledMovement() {
        AfkBehaviorAnalyzer analyzer = new AfkBehaviorAnalyzer();
        Trajectory trajectory = square();
        for (int index = 1; index <= END / INTERVAL; index++) {
            Position position = trajectory.at(index);
            analyzer.record(WORLD, position.positionX, 0.0D, position.positionZ,
                    index % 360, false, index * INTERVAL);
        }

        assertEquals(AfkBehaviorAnalyzer.Pattern.ORDINARY_MOVEMENT, analyzer.analyze(END).pattern());
        assertFalse(analyzer.isLikelyAutomated(END, 0L));
    }

    @Test
    void latestOrdinaryTravelCancelsAFormerlyLocalPattern() {
        AfkBehaviorAnalyzer analyzer = analyze(square(), END - 60_000L);
        for (long now = END - 60_000L + INTERVAL; now <= END; now += INTERVAL) {
            analyzer.record(WORLD, (now - END + 60_000L) / 1_000.0D, 0.0D, 0.0D,
                    0.0F, true, now);
        }

        assertFalse(analyzer.isLikelyAutomated(END, 0L));
    }

    @Test
    void stationarySamplingDoesNotRefreshAnyActivityClock() {
        AfkActivityTracker tracker = new AfkActivityTracker(0L, WORLD, 0.0D, 0.0D, 0.0D);
        for (long now = INTERVAL; now <= AfkPolicy.DEFAULT.idleTimeoutMillis(); now += INTERVAL) {
            tracker.recordObservation(WORLD, 0.0D, 0.0D, 0.0D, 0.0F, now);
        }
        assertEquals(AfkActivityTracker.CheckResult.AFK_IDLE,
                tracker.check(AfkPolicy.DEFAULT.idleTimeoutMillis()));
    }

    @Test
    void reconnectShiftsHistoryButDoesNotCombineDifferentWorlds() {
        AfkBehaviorAnalyzer sameWorld = analyze(square(), END);
        sameWorld.resumeAfter(60_000L, WORLD, 0.0D, 0.0D, 0.0D);
        assertTrue(sameWorld.isLikelyAutomated(END + 60_000L, 60_000L));

        AfkBehaviorAnalyzer changedWorld = analyze(square(), END);
        changedWorld.resumeAfter(60_000L, new UUID(0L, 2L), 0.0D, 0.0D, 0.0D);
        assertEquals(AfkBehaviorAnalyzer.Pattern.INSUFFICIENT_DATA,
                changedWorld.analyze(END + 60_000L).pattern());
    }

    @Test
    void longSamplingKeepsOnlyTheBoundedAnalysisHorizon() {
        AfkBehaviorAnalyzer analyzer = analyze(square(), END * 3L);
        AfkBehaviorAnalyzer.Analysis analysis = analyzer.analyze(END * 3L);

        assertTrue(analysis.previous().samples() + analysis.recent().samples() <= END / INTERVAL + 1L);
        assertEquals(AfkBehaviorAnalyzer.Pattern.LOCAL_REPETITION, analysis.pattern());
    }

    private static AfkBehaviorAnalyzer analyze(Trajectory trajectory, long duration) {
        AfkBehaviorAnalyzer analyzer = new AfkBehaviorAnalyzer();
        for (int index = 1; index <= duration / INTERVAL; index++) {
            Position position = trajectory.at(index);
            analyzer.record(WORLD, position.positionX, position.positionY, position.positionZ,
                    index % 360, true, index * INTERVAL);
        }
        return analyzer;
    }

    private static Trajectory square() {
        double[] position = new double[2];
        return index -> {
            step(position, (index - 1) / 5 % 4);
            return new Position(position[0], 0.0D, position[1]);
        };
    }

    private static Trajectory randomWalk(long seed) {
        Random random = new Random(seed);
        double[] position = new double[2];
        return index -> {
            step(position, random.nextInt(4));
            position[0] = Math.max(-4.0D, Math.min(4.0D, position[0]));
            position[1] = Math.max(-4.0D, Math.min(4.0D, position[1]));
            return new Position(position[0], 0.0D, position[1]);
        };
    }

    private static void step(double[] position, int direction) {
        switch (direction) {
            case 0 -> position[0] += 0.25D;
            case 1 -> position[1] += 0.25D;
            case 2 -> position[0] -= 0.25D;
            case 3 -> position[1] -= 0.25D;
            default -> throw new IllegalArgumentException("Unknown direction");
        }
    }

    private static Trajectory circle() {
        return index -> new Position(4.0D * Math.cos(index * Math.PI / 80.0D),
                0.0D, 4.0D * Math.sin(index * Math.PI / 80.0D));
    }

    private static Trajectory jumps() {
        return index -> new Position(0.0D, index % 2 == 0 ? 0.0D : 0.6D, 0.0D);
    }

    private record Position(double positionX, double positionY, double positionZ) {
    }

    private interface Trajectory {
        Position at(int index);
    }
}
