package org.encinet.mik.module.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
        assertEquals(0, FifthAnniversaryEventModule.readyRegularDrawCount(
                FOUR_HOURS_MILLIS * 2L, 0, true));
        assertTrue(FifthAnniversaryEventModule.bonusDrawReady(false, true, false));
        assertFalse(FifthAnniversaryEventModule.bonusDrawReady(true, true, false));
        assertFalse(FifthAnniversaryEventModule.bonusDrawReady(false, false, false));
        assertFalse(FifthAnniversaryEventModule.bonusDrawReady(false, true, true));
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
    void simulatedPrizeSelectionIsReadOnly() {
        int[] available = {2, 1, 0, 2};
        int[] before = available.clone();

        assertEquals(0, FifthAnniversaryEventModule.releasedPrizeIndex(available, 0));
        assertEquals(0, FifthAnniversaryEventModule.releasedPrizeIndex(available, 1));
        assertEquals(1, FifthAnniversaryEventModule.releasedPrizeIndex(available, 2));
        assertEquals(3, FifthAnniversaryEventModule.releasedPrizeIndex(available, 3));
        assertEquals(3, FifthAnniversaryEventModule.releasedPrizeIndex(available, 4));
        assertTrue(Arrays.equals(before, available));
        assertThrows(IllegalArgumentException.class,
                () -> FifthAnniversaryEventModule.releasedPrizeIndex(available, 5));
        assertThrows(IllegalArgumentException.class,
                () -> FifthAnniversaryEventModule.releasedPrizeIndex(new int[]{1, -1}, 0));
    }

    @Test
    void eachPlayerCanWinEachPrizeTypeOnlyOnce() {
        assertFalse(FifthAnniversaryEventModule.isPrizeTypeEligible(
                Set.of("logo-mug"), "logo-mug"));
        assertTrue(FifthAnniversaryEventModule.isPrizeTypeEligible(
                Set.of("logo-mug"), "logo-keychain"));
        assertTrue(FifthAnniversaryEventModule.isPrizeTypeEligible(
                Set.of("logo-mug"), "anniversary-gift-pack"));
        assertFalse(FifthAnniversaryEventModule.isPrizeTypeEligible(
                Set.of("anniversary-gift-pack"), "logo-keychain"));
    }

    @Test
    void giftPackReplacesVirtualBagAndRestoresSupersededStock() {
        List<String> virtualBag = new java.util.ArrayList<>(List.of(
                "logo-keychain", "logo-mug", "group-photo-badge"));
        int[] stocks = {19, 9, 19, 4};

        int restored = FifthAnniversaryEventModule.applyWinningPrizeToVirtualBag(
                virtualBag, "anniversary-gift-pack", stocks);

        assertEquals(3, restored);
        assertEquals(List.of("anniversary-gift-pack"), virtualBag);
        assertTrue(Arrays.equals(new int[]{20, 10, 20, 4}, stocks));
    }

    @Test
    void virtualBagIgnoresDuplicateAndNonWinningResults() {
        List<String> virtualBag = new java.util.ArrayList<>();
        int[] stocks = {19, 10, 20, 5};

        assertEquals(0, FifthAnniversaryEventModule.applyWinningPrizeToVirtualBag(
                virtualBag, "logo-keychain", stocks));
        assertEquals(0, FifthAnniversaryEventModule.applyWinningPrizeToVirtualBag(
                virtualBag, "logo-keychain", stocks));
        assertEquals(0, FifthAnniversaryEventModule.applyWinningPrizeToVirtualBag(
                virtualBag, "not-won", stocks));

        assertEquals(List.of("logo-keychain"), virtualBag);
        assertTrue(Arrays.equals(new int[]{19, 10, 20, 5}, stocks));
    }

    @Test
    void persistedGiftPackAlwaysNormalizesToTheOnlyFinalPrize() {
        assertEquals(List.of("anniversary-gift-pack"),
                FifthAnniversaryEventModule.normalizeVirtualBag(List.of(
                        "logo-keychain",
                        "anniversary-gift-pack",
                        "logo-mug",
                        "anniversary-gift-pack",
                        "not-won")));
        assertEquals(List.of("logo-keychain", "logo-mug"),
                FifthAnniversaryEventModule.normalizeVirtualBag(List.of(
                        "logo-keychain", "logo-keychain", "not-won", "logo-mug")));
    }

    @Test
    void virtualBagItemsUseStableCenteredSlots() {
        assertTrue(Arrays.equals(new int[0],
                FifthAnniversaryEventModule.virtualBagPrizeSlots(0)));
        assertTrue(Arrays.equals(new int[]{13},
                FifthAnniversaryEventModule.virtualBagPrizeSlots(1)));
        assertTrue(Arrays.equals(new int[]{12, 14},
                FifthAnniversaryEventModule.virtualBagPrizeSlots(2)));
        assertTrue(Arrays.equals(new int[]{11, 13, 15},
                FifthAnniversaryEventModule.virtualBagPrizeSlots(3)));
        assertThrows(IllegalArgumentException.class,
                () -> FifthAnniversaryEventModule.virtualBagPrizeSlots(4));
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
