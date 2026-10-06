package org.encinet.mik.module.governance.membership.activity;

/** Exact active-member checks used when a nomination or voter roll is frozen. */
public record ActiveMemberEligibility(
        boolean currentMember,
        boolean memberAgeMet,
        boolean playtimeMet,
        boolean participationDaysMet,
        boolean recentDaysMet,
        boolean noActiveBan
) {
    public boolean eligible() {
        return currentMember && memberAgeMet && playtimeMet && participationDaysMet
                && recentDaysMet && noActiveBan;
    }
}
