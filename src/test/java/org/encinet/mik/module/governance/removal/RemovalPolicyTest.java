package org.encinet.mik.module.governance.removal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemovalPolicyTest {
    @Test
    void sponsorshipThresholdExcludesTheSubject() {
        assertEquals(Integer.MAX_VALUE, RemovalPolicy.sponsorsRequired(1));
        assertEquals(2, RemovalPolicy.sponsorsRequired(2));
        assertEquals(3, RemovalPolicy.sponsorsRequired(3));
        assertEquals(3, RemovalPolicy.sponsorsRequired(20));
    }
}
