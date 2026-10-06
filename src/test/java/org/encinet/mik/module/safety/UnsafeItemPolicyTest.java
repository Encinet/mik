package org.encinet.mik.module.safety;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsafeItemPolicyTest {

    @Test
    void bookLimitAppliesEvenWhenPaperPageLimitIsDisabled() {
        assertFalse(UnsafeItemPolicy.checkBookPages(
                List.of("A short page"), false, 0, 1.0D).blocked());

        var oversized = UnsafeItemPolicy.checkBookPages(
                List.of("x".repeat(256 * 1024 + 1)), false, 0, 1.0D);
        assertTrue(oversized.blocked());
        assertEquals(256 * 1024, oversized.allowed());
    }

    @Test
    void malformedPagesAreRejectedBeforeBookEdit() {
        assertEquals("missing-pages", UnsafeItemPolicy.checkBookPages(
                null, true, 200, 1.0D).error());
        assertEquals("null-page", UnsafeItemPolicy.checkBookPages(
                Arrays.asList("valid", null), true, 200, 1.0D).error());
    }

    @Test
    void paperPageLimitCountsMultibyteText() {
        var ordinary = UnsafeItemPolicy.checkBookPages(
                List.of("Short text"), true, 200, 1.0D);
        var multibyte = UnsafeItemPolicy.checkBookPages(
                List.of("界".repeat(100)), true, 200, 1.0D);

        assertFalse(ordinary.blocked());
        assertTrue(multibyte.blocked());
        assertEquals(300, multibyte.bytes());
        assertTrue(multibyte.allowed() < multibyte.bytes());
    }
}
