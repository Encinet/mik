package org.encinet.mik.module.governance.removal;

/** Rules for converting a removal petition into a vote. */
public final class RemovalPolicy {
    private RemovalPolicy() {
    }

    public static int sponsorsRequired(int activeMembersOtherThanSubject) {
        if (activeMembersOtherThanSubject < 2) {
            return Integer.MAX_VALUE;
        }
        return activeMembersOtherThanSubject == 2 ? 2 : 3;
    }
}
