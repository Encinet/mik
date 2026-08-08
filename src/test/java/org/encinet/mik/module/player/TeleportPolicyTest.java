package org.encinet.mik.module.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportPolicyTest {

    @Test
    void consentIsTheDefaultForMissingOrInvalidSettings() {
        assertEquals(TeleportPolicy.REQUIRE_CONSENT,
                TeleportPolicy.fromId(null));
        assertEquals(TeleportPolicy.REQUIRE_CONSENT,
                TeleportPolicy.fromId("unknown"));
    }

    @Test
    void cyclesThroughAllThreePolicies() {
        assertEquals(TeleportPolicy.REQUIRE_CONSENT,
                TeleportPolicy.ALWAYS_ALLOW.next());
        assertEquals(TeleportPolicy.ALWAYS_DENY,
                TeleportPolicy.REQUIRE_CONSENT.next());
        assertEquals(TeleportPolicy.ALWAYS_ALLOW,
                TeleportPolicy.ALWAYS_DENY.next());
    }

    @Test
    void persistedIdsRoundTrip() {
        for (TeleportPolicy policy : TeleportPolicy.values()) {
            assertEquals(policy, TeleportPolicy.fromId(policy.id()));
        }
    }
}
