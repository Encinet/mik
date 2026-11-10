package org.encinet.mik.feature.player.teleport;

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
    void persistedIdsRoundTrip() {
        for (TeleportPolicy policy : TeleportPolicy.values()) {
            assertEquals(policy, TeleportPolicy.fromId(policy.id()));
        }
    }
}
