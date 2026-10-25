package org.encinet.mik.feature.community.governance.membership.promotion;

/** Exact newcomer promotion checks, suitable for command output and auditing. */
public record PromotionEligibility(
        boolean accountAgeMet,
        boolean playtimeMet,
        boolean playDaysMet,
        boolean noActiveBan,
        boolean noActivePause,
        PromotionPause activePause
) {
    public boolean eligible() {
        return accountAgeMet && playtimeMet && playDaysMet
                && noActiveBan && noActivePause;
    }
}
