package org.encinet.mik.module.governance.membership.promotion;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromotionPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-22T12:00:00Z");

    @Test
    void promotionRequiresEveryConditionAtTheBoundary() {
        PromotionEligibility eligible = PromotionPolicy.evaluate(
                NOW.minus(PromotionPolicy.MINIMUM_ACCOUNT_AGE),
                PromotionPolicy.MINIMUM_PLAYTIME.toSeconds(),
                PromotionPolicy.MINIMUM_PLAY_DAYS, false, null, NOW);

        assertTrue(eligible.eligible());
        assertFalse(PromotionPolicy.evaluate(
                NOW.minus(PromotionPolicy.MINIMUM_ACCOUNT_AGE),
                PromotionPolicy.MINIMUM_PLAYTIME.toSeconds(),
                PromotionPolicy.MINIMUM_PLAY_DAYS - 1, false, null, NOW).eligible());
        assertFalse(PromotionPolicy.evaluate(
                NOW.minus(PromotionPolicy.MINIMUM_ACCOUNT_AGE),
                PromotionPolicy.MINIMUM_PLAYTIME.toSeconds(),
                PromotionPolicy.MINIMUM_PLAY_DAYS, true, null, NOW).eligible());
    }

    @Test
    void activePauseBlocksPromotion() {
        PromotionPause pause = new PromotionPause(1, "pending grief review", NOW,
                NOW.plusSeconds(3_600), "appeal in #moderation", "custodian");

        assertTrue(pause.isActive(NOW));
        assertFalse(PromotionPolicy.evaluate(
                NOW.minusSeconds(300_000), 30_000, 3,
                false, pause, NOW).eligible());
    }
}
