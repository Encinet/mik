package org.encinet.mik.feature.server.ban;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanJailedLoginAdmissionTest {
    private static final UUID PLAYER = UUID.fromString("5e6d3f4c-0000-0000-0000-00000000c0de");
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    @Test
    void directBanIsAdmittedOnlyWhenThePrisonHoldsThePlayerInACell() {
        var match = direct();

        assertTrue(BanLoginListener.shouldAdmitJailedPlayer(match, PLAYER, id -> id.equals(PLAYER)),
                "A jailed player must pass the direct-ban check");
        assertFalse(BanLoginListener.shouldAdmitJailedPlayer(match, PLAYER, _ -> false),
                "Without a resolvable cell the ban still applies");
        assertFalse(BanLoginListener.shouldAdmitJailedPlayer(match, PLAYER, id -> !id.equals(PLAYER)),
                "The exemption is keyed by the banned account, not by connection order");
    }

    @Test
    void addressLinkedBanIsNeverOverriddenByAPrisonCell() {
        var linked = new BanAdmissionChecker.AdmissionBan(record(), BanAdmissionChecker.MatchType.ADDRESS_LINK);

        assertFalse(BanLoginListener.shouldAdmitJailedPlayer(linked, PLAYER, _ -> true),
                "An address link must keep rejecting login regardless of prison state");
    }

    private static BanAdmissionChecker.AdmissionBan direct() {
        return new BanAdmissionChecker.AdmissionBan(record(), BanAdmissionChecker.MatchType.DIRECT);
    }

    private static BanRecord record() {
        return new BanRecord(1, PLAYER, "Jailed", "jailed", "reason", "Admin",
                NOW.minusSeconds(60), NOW.minusSeconds(60), null, null, BanRecord.Origin.MIK);
    }
}
