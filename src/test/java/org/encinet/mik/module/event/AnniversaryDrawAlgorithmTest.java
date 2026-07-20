package org.encinet.mik.module.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnniversaryDrawAlgorithmTest {

    private static final double EPSILON = 0.0000001D;
    private static final long FOUR_HOURS_MILLIS = 4L * 60L * 60L * 1000L;

    @Test
    void elapsedTimeOnlyUnlocksDrawsUntilThePlayerUsesThem() {
        assertEquals(0, FifthAnniversaryEventModule.readyRegularDrawCount(
                FOUR_HOURS_MILLIS - 1L, 0));
        assertEquals(1, FifthAnniversaryEventModule.readyRegularDrawCount(
                FOUR_HOURS_MILLIS, 0));
        assertEquals(2, FifthAnniversaryEventModule.readyRegularDrawCount(
                FOUR_HOURS_MILLIS * 2L, 0));
        assertEquals(1, FifthAnniversaryEventModule.readyRegularDrawCount(
                FOUR_HOURS_MILLIS * 2L, 1));
        assertEquals(0, FifthAnniversaryEventModule.readyRegularDrawCount(
                FOUR_HOURS_MILLIS * 2L, 2));
    }

    @Test
    void onlyTheThreeConfiguredPlayersAreEventAdministrators() {
        assertTrue(FifthAnniversaryEventModule.isEventAdministrator(
                UUID.fromString("850ab457-2a91-45a5-916d-3cc24dc601c7")));
        assertTrue(FifthAnniversaryEventModule.isEventAdministrator(
                UUID.fromString("531983d3-f5e4-4f0b-b1d3-3756be96b611")));
        assertTrue(FifthAnniversaryEventModule.isEventAdministrator(
                UUID.fromString("bf02846b-0f29-4c42-891c-a95f2befc5fb")));
        assertFalse(FifthAnniversaryEventModule.isEventAdministrator(
                UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertFalse(FifthAnniversaryEventModule.isEventAdministrator(null));
    }

    @Test
    void administratorRemovalRestoresOnlyKnownWinningPrizesWithinInitialStock() {
        int[] stocks = {19, 8, 20, 4};

        int restored = FifthAnniversaryEventModule.restoreWinningPrizeStocks(stocks, List.of(
                "logo-keychain",
                "logo-keychain",
                "logo-mug",
                "logo-mug",
                "logo-mug",
                "group-photo-badge",
                "anniversary-gift-pack",
                "anniversary-gift-pack",
                "not-won",
                "sold-out",
                "unknown"));

        assertEquals(4, restored);
        assertTrue(Arrays.equals(new int[]{20, 10, 20, 5}, stocks));
    }

    @Test
    void winnerListUsesTenEntriesPerPageAndAlwaysHasAnEmptyPage() {
        assertEquals(1, FifthAnniversaryEventModule.winnerListPageCount(0));
        assertEquals(1, FifthAnniversaryEventModule.winnerListPageCount(10));
        assertEquals(2, FifthAnniversaryEventModule.winnerListPageCount(11));
        assertEquals(5, FifthAnniversaryEventModule.winnerListPageCount(50));
    }

    @Test
    void fasterOpportunityProductionLowersTheBaseProbability() {
        double slow = probabilityAtRate(2.0D);
        double medium = probabilityAtRate(8.0D);
        double fast = probabilityAtRate(20.0D);

        assertTrue(slow > medium);
        assertTrue(medium > fast);
        assertTrue(fast >= 0.02D);
        assertTrue(slow <= 0.58D);
    }

    @Test
    void releasedStockBacklogRaisesTheBaseProbability() {
        double noBacklog = FifthAnniversaryEventModule.calculateBaseProbability(
                snapshot(2, 8.0D));
        double backlog = FifthAnniversaryEventModule.calculateBaseProbability(
                snapshot(8, 8.0D));

        assertTrue(backlog > noBacklog);
    }

    @Test
    void unavailableReleasedStockDisablesWinning() {
        double probability = FifthAnniversaryEventModule.calculateBaseProbability(
                new FifthAnniversaryEventModule.ControllerSnapshot(
                        40, 0, 3, 8.0D, 100.0D, 20));

        assertEquals(0.0D, probability, EPSILON);
    }

    @Test
    void personalHistoryAdjustsProbabilityGradually() {
        double base = 0.20D;
        double neutral = personalized(base, 0, 0);
        double oneLoss = personalized(base, 0, 1);
        double twoLosses = personalized(base, 0, 2);
        double oneWin = personalized(base, 1, 0);
        double winThenLoss = personalized(base, 1, 1);

        assertEquals(base, neutral, EPSILON);
        assertTrue(oneLoss > neutral);
        assertTrue(twoLosses > oneLoss);
        assertTrue(oneWin < neutral);
        assertTrue(winThenLoss > oneWin);
        assertTrue(twoLosses - neutral < 0.06D);
        assertTrue(neutral - oneWin < 0.05D);
    }

    @Test
    void releasePlansConserveStockAndRemainFrontLoaded() {
        for (int stock : new int[]{20, 10, 20, 5}) {
            for (long seed = 1L; seed <= 250L; seed++) {
                int[] slots = FifthAnniversaryEventModule.generateReleaseSlots(stock, seed);

                assertEquals(stock, slots.length);
                assertTrue(isSorted(slots));
                assertTrue(slots[0] >= 0);
                assertTrue(slots[slots.length - 1] < 144);
                long firstHalf = Arrays.stream(slots).filter(slot -> slot < 72).count();
                assertTrue(firstHalf >= (stock + 1L) / 2L,
                        "release plan was not front-loaded for stock " + stock
                                + " and seed " + seed);
            }
        }
    }

    @Test
    void allFiftyFivePrizesCanBeTakenWithoutNegativeStock() {
        int[] remaining = {20, 10, 20, 5};
        int[] awarded = new int[remaining.length];

        for (int draw = 0; draw < 55; draw++) {
            int[] available = remaining.clone();
            int prizeIndex = FifthAnniversaryEventModule.takeAvailablePrize(
                    remaining, available, draw % Arrays.stream(available).sum());
            awarded[prizeIndex]++;
            assertTrue(Arrays.stream(remaining).allMatch(stock -> stock >= 0));
        }

        assertTrue(Arrays.equals(new int[]{20, 10, 20, 5}, awarded));
        assertTrue(Arrays.equals(new int[]{0, 0, 0, 0}, remaining));
        assertThrows(IllegalArgumentException.class,
                () -> FifthAnniversaryEventModule.takeAvailablePrize(
                        remaining, remaining.clone(), 0));
    }

    @Test
    void unavailablePrizeTypesCannotBeSelectedOrOverdrawn() {
        int[] remaining = {1, 1, 0, 1};
        int[] available = {0, 1, 0, 0};

        assertEquals(1, FifthAnniversaryEventModule.takeAvailablePrize(
                remaining, available, 0));
        assertTrue(Arrays.equals(new int[]{1, 0, 0, 1}, remaining));
        assertThrows(IllegalArgumentException.class,
                () -> FifthAnniversaryEventModule.takeAvailablePrize(
                        remaining, new int[]{0, 1, 0, 0}, 0));
    }

    @Test
    void oddsLimiterCapsAbruptControllerChanges() {
        double increased = FifthAnniversaryEventModule.limitOddsChange(
                0.20D, 0.58D, 0.80D, 1.20D);
        double decreased = FifthAnniversaryEventModule.limitOddsChange(
                0.20D, 0.02D, 0.80D, 1.20D);

        assertEquals(0.2307692308D, increased, 0.0000001D);
        assertEquals(0.1666666667D, decreased, 0.0000001D);
    }

    @Test
    void releaseBorrowingNeverExceedsTheCumulativeLimit() {
        assertEquals(12, FifthAnniversaryEventModule.availableReleasedStock(
                55, 55, 10, 2));
        assertEquals(1, FifthAnniversaryEventModule.availableReleasedStock(
                55, 44, 10, 2));
        assertEquals(0, FifthAnniversaryEventModule.availableReleasedStock(
                55, 43, 10, 2));
        assertEquals(0, FifthAnniversaryEventModule.availableReleasedStock(
                55, 35, 10, 2));
    }

    @Test
    void personalCalibrationPreservesTheGlobalProbabilityBudget() {
        double base = 0.20D;
        List<Double> weights = List.of(
                FifthAnniversaryEventModule.personalWeight(0, 2),
                FifthAnniversaryEventModule.personalWeight(0, 1),
                FifthAnniversaryEventModule.personalWeight(0, 0),
                FifthAnniversaryEventModule.personalWeight(1, 0));
        double offset = FifthAnniversaryEventModule.calibratedPersonalOffset(
                base, weights);

        double average = weights.stream()
                .mapToDouble(weight -> FifthAnniversaryEventModule.personalizedProbability(
                        base, weight, offset))
                .average()
                .orElseThrow();

        assertEquals(base, average, 0.000001D);
    }

    @Test
    void calibrationAlsoPreservesTheMinimumGlobalRate() {
        double base = 0.02D;
        List<Double> weights = List.of(
                FifthAnniversaryEventModule.personalWeight(0, 2),
                FifthAnniversaryEventModule.personalWeight(0, 0),
                FifthAnniversaryEventModule.personalWeight(2, 0));
        double offset = FifthAnniversaryEventModule.calibratedPersonalOffset(
                base, weights);

        double average = weights.stream()
                .mapToDouble(weight -> FifthAnniversaryEventModule.personalizedProbability(
                        base, weight, offset))
                .average()
                .orElseThrow();

        assertEquals(base, average, 0.000001D);
    }

    private double probabilityAtRate(double rate) {
        return FifthAnniversaryEventModule.calculateBaseProbability(snapshot(4, rate));
    }

    private FifthAnniversaryEventModule.ControllerSnapshot snapshot(int available, double rate) {
        return new FifthAnniversaryEventModule.ControllerSnapshot(
                40, available, 2, rate, 120.0D, 30);
    }

    private double personalized(double base, int wins, int losses) {
        return FifthAnniversaryEventModule.personalizedProbability(
                base,
                FifthAnniversaryEventModule.personalWeight(wins, losses),
                0.0D);
    }

    private boolean isSorted(int[] values) {
        for (int index = 1; index < values.length; index++) {
            if (values[index - 1] > values[index]) {
                return false;
            }
        }
        return true;
    }
}
