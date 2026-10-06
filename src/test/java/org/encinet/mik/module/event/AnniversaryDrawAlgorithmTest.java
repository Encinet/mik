package org.encinet.mik.module.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        double noBacklog = AnniversaryDrawAlgorithm.calculateBaseProbability(
                snapshot(2, 8.0D));
        double backlog = AnniversaryDrawAlgorithm.calculateBaseProbability(
                snapshot(8, 8.0D));

        assertTrue(backlog > noBacklog);
    }

    @Test
    void unavailableReleasedStockDisablesWinning() {
        double probability = AnniversaryDrawAlgorithm.calculateBaseProbability(
                new AnniversaryDrawAlgorithm.ControllerSnapshot(
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
        int slotCount = FifthAnniversaryEventModule.RELEASE_SLOT_COUNT;
        assertEquals(140, slotCount);
        for (int stock : new int[]{20, 10, 20, 5}) {
            for (long seed = 1L; seed <= 250L; seed++) {
                int[] slots = AnniversaryDrawAlgorithm.generateReleaseSlots(stock, seed, slotCount);

                assertEquals(stock, slots.length);
                assertTrue(isSorted(slots));
                assertTrue(slots[0] >= 0);
                assertTrue(slots[slots.length - 1] < slotCount);
                long firstHalf = Arrays.stream(slots).filter(slot -> slot < slotCount / 2).count();
                assertTrue(firstHalf >= (stock + 1L) / 2L,
                        "release plan was not front-loaded for stock " + stock
                                + " and seed " + seed);
            }
        }
    }

    @Test
    void persistedReleaseSeedKeepsItsOriginalSchedule() {
        assertArrayEquals(new int[]{
                        0, 0, 1, 4, 11, 14, 24, 30, 37, 44,
                        48, 56, 65, 72, 83, 97, 106, 115, 122, 134
                }, AnniversaryDrawAlgorithm.generateReleaseSlots(
                        20, 12345L, FifthAnniversaryEventModule.RELEASE_SLOT_COUNT));
    }

    @Test
    void simulatedPrizeSelectionIsReadOnly() {
        int[] available = {2, 1, 0, 2};
        int[] before = available.clone();

        assertEquals(0, AnniversaryDrawAlgorithm.releasedPrizeIndex(available, 0));
        assertEquals(0, AnniversaryDrawAlgorithm.releasedPrizeIndex(available, 1));
        assertEquals(1, AnniversaryDrawAlgorithm.releasedPrizeIndex(available, 2));
        assertEquals(3, AnniversaryDrawAlgorithm.releasedPrizeIndex(available, 3));
        assertEquals(3, AnniversaryDrawAlgorithm.releasedPrizeIndex(available, 4));
        assertTrue(Arrays.equals(before, available));
        assertThrows(IllegalArgumentException.class,
                () -> AnniversaryDrawAlgorithm.releasedPrizeIndex(available, 5));
        assertThrows(IllegalArgumentException.class,
                () -> AnniversaryDrawAlgorithm.releasedPrizeIndex(new int[]{1, -1}, 0));
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
    void giftingMovesOneClaimWithoutChangingEitherPlayersDrawEligibility() {
        List<String> senderBag = new java.util.ArrayList<>(List.of("logo-keychain"));
        List<String> recipientBag = new java.util.ArrayList<>();
        Set<String> senderDrawHistory = Set.of("logo-keychain");
        Set<String> recipientDrawHistory = Set.of();

        assertTrue(FifthAnniversaryEventModule.transferPrizeOwnership(
                senderBag, recipientBag, "logo-keychain"));

        assertEquals(List.of(), senderBag);
        assertEquals(List.of("logo-keychain"), recipientBag);
        assertFalse(FifthAnniversaryEventModule.isPrizeTypeEligible(
                senderDrawHistory, "logo-keychain"));
        assertTrue(FifthAnniversaryEventModule.isPrizeTypeEligible(
                recipientDrawHistory, "logo-keychain"));
    }

    @Test
    void giftingOneOfSeveralReceivedCopiesMovesOnlyOnePrize() {
        List<String> senderBag = new java.util.ArrayList<>(List.of(
                "logo-keychain", "logo-keychain"));
        List<String> recipientBag = new java.util.ArrayList<>(List.of("logo-mug"));

        assertTrue(FifthAnniversaryEventModule.transferPrizeOwnership(
                senderBag, recipientBag, "logo-keychain"));

        assertEquals(List.of("logo-keychain"), senderBag);
        assertEquals(List.of("logo-mug", "logo-keychain"), recipientBag);
        assertFalse(FifthAnniversaryEventModule.transferPrizeOwnership(
                senderBag, recipientBag, "group-photo-badge"));
        assertEquals(List.of("logo-keychain"), senderBag);
        assertEquals(List.of("logo-mug", "logo-keychain"), recipientBag);
    }

    @Test
    void persistedBagPreservesTransferredDuplicatesAndMixedPrizeTypes() {
        assertEquals(List.of(
                        "logo-keychain",
                        "anniversary-gift-pack",
                        "logo-mug",
                        "anniversary-gift-pack"),
                FifthAnniversaryEventModule.normalizeVirtualBag(List.of(
                        "logo-keychain",
                        "anniversary-gift-pack",
                        "logo-mug",
                        "anniversary-gift-pack",
                        "not-won")));
        assertEquals(List.of("logo-keychain", "logo-keychain", "logo-mug"),
                FifthAnniversaryEventModule.normalizeVirtualBag(List.of(
                        "logo-keychain", "logo-keychain", "not-won", "logo-mug")));
    }

    @Test
    void oddsLimiterCapsAbruptControllerChanges() {
        double increased = AnniversaryDrawAlgorithm.limitOddsChange(
                0.20D, 0.58D, 0.80D, 1.20D);
        double decreased = AnniversaryDrawAlgorithm.limitOddsChange(
                0.20D, 0.02D, 0.80D, 1.20D);

        assertEquals(0.2307692308D, increased, 0.0000001D);
        assertEquals(0.1666666667D, decreased, 0.0000001D);
    }

    @Test
    void releaseBorrowingNeverExceedsTheCumulativeLimit() {
        assertEquals(12, AnniversaryDrawAlgorithm.availableReleasedStock(
                55, 55, 10, 2));
        assertEquals(1, AnniversaryDrawAlgorithm.availableReleasedStock(
                55, 44, 10, 2));
        assertEquals(0, AnniversaryDrawAlgorithm.availableReleasedStock(
                55, 43, 10, 2));
        assertEquals(0, AnniversaryDrawAlgorithm.availableReleasedStock(
                55, 35, 10, 2));
    }

    @Test
    void personalCalibrationPreservesTheGlobalProbabilityBudget() {
        double base = 0.20D;
        List<Double> weights = List.of(
                AnniversaryDrawAlgorithm.personalWeight(0, 2),
                AnniversaryDrawAlgorithm.personalWeight(0, 1),
                AnniversaryDrawAlgorithm.personalWeight(0, 0),
                AnniversaryDrawAlgorithm.personalWeight(1, 0));
        double offset = AnniversaryDrawAlgorithm.calibratedPersonalOffset(
                base, weights);

        double average = weights.stream()
                .mapToDouble(weight -> AnniversaryDrawAlgorithm.personalizedProbability(
                        base, weight, offset))
                .average()
                .orElseThrow();

        assertEquals(base, average, 0.000001D);
    }

    @Test
    void calibrationAlsoPreservesTheMinimumGlobalRate() {
        double base = 0.02D;
        List<Double> weights = List.of(
                AnniversaryDrawAlgorithm.personalWeight(0, 2),
                AnniversaryDrawAlgorithm.personalWeight(0, 0),
                AnniversaryDrawAlgorithm.personalWeight(2, 0));
        double offset = AnniversaryDrawAlgorithm.calibratedPersonalOffset(
                base, weights);

        double average = weights.stream()
                .mapToDouble(weight -> AnniversaryDrawAlgorithm.personalizedProbability(
                        base, weight, offset))
                .average()
                .orElseThrow();

        assertEquals(base, average, 0.000001D);
    }

    private double probabilityAtRate(double rate) {
        return AnniversaryDrawAlgorithm.calculateBaseProbability(snapshot(4, rate));
    }

    private AnniversaryDrawAlgorithm.ControllerSnapshot snapshot(int available, double rate) {
        return new AnniversaryDrawAlgorithm.ControllerSnapshot(
                40, available, 2, rate, 120.0D, 30);
    }

    private double personalized(double base, int wins, int losses) {
        return AnniversaryDrawAlgorithm.personalizedProbability(
                base,
                AnniversaryDrawAlgorithm.personalWeight(wins, losses),
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
