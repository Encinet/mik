package org.encinet.mik.module.social.safety;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialContentSafetyFilterTest {
    @Test
    void normalizationAndCompiledMatchingIgnorePunctuationAndCase() {
        SocialContentSafetyFilter filter = SocialContentSafetyFilter.compile(
                List.of("# comment", "Dangerous phrase", "DANGEROUS-PHRASE", "phrase"),
                "test.txt");

        assertTrue(filter.match("a PHRA-SE appeared").isPresent());
        assertFalse(filter.match("ordinary reply").isPresent());
        assertEquals("制造炸弹", SocialContentSafetyFilter.normalize("制 造-炸\u200B弹"));
        assertTrue(filter.removedRuleCount() >= 1);
    }

    @Test
    void enabledEmptyOrTooShortRulesFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> SocialContentSafetyFilter.compile(List.of("# only"), "empty.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> SocialContentSafetyFilter.compile(List.of("a"), "short.txt"));
        assertFalse(SocialContentSafetyFilter.disabled().enabled());
    }
}
