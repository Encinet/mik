package org.encinet.mik.module.access;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictionModuleTest {

    @Test
    void selectorsInDirectMessageBodyArePlainText() {
        assertFalse(RestrictionModule.containsRestrictedSelector("/msg Steve hello @a @e[type=zombie]"));
        assertFalse(RestrictionModule.containsRestrictedSelector("/tell Steve @p and @r"));
        assertFalse(RestrictionModule.containsRestrictedSelector("/whisper Steve look at @n"));
    }

    @Test
    void selectorCannotBeTheDirectMessageTarget() {
        assertTrue(RestrictionModule.containsRestrictedSelector("/msg @a hello"));
        assertTrue(RestrictionModule.containsRestrictedSelector("/tell @e[type=zombie] hello"));
    }

    @Test
    void selectorsInReplyBodyArePlainText() {
        assertFalse(RestrictionModule.containsRestrictedSelector("/r hello @a and @e"));
        assertFalse(RestrictionModule.containsRestrictedSelector("/reply @p"));
    }

    @Test
    void teleportAndOtherCommandsStillRejectSelectors() {
        assertTrue(RestrictionModule.containsRestrictedSelector("/tp @a 0 64 0"));
        assertTrue(RestrictionModule.containsRestrictedSelector("/teleport Steve @e"));
        assertTrue(RestrictionModule.containsRestrictedSelector("/kill @e"));
    }

    @Test
    void namespacedCommandsUseTheSamePolicy() {
        assertFalse(RestrictionModule.containsRestrictedSelector("/mik:msg Steve hello @e"));
        assertTrue(RestrictionModule.containsRestrictedSelector("/minecraft:teleport @a Steve"));
    }

    @Test
    void commandArgumentsCanBeSeparatedByOtherWhitespace() {
        assertFalse(RestrictionModule.containsRestrictedSelector("/msg\tSteve\thello @e"));
        assertTrue(RestrictionModule.containsRestrictedSelector("/tp\t@a\tSteve"));
    }

    @Test
    void identityChecksFollowCommandPolicy() {
        assertFalse(RestrictionModule.checksIdentities("/msg Steve hello"));
        assertFalse(RestrictionModule.checksIdentities("/reply hello"));
        assertFalse(RestrictionModule.checksIdentities("/teleport Steve Alex"));
        assertTrue(RestrictionModule.checksIdentities("/data get entity Steve"));
    }
}
