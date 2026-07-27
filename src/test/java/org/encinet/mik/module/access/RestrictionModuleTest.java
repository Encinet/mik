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
    void selectorsInPublicChatBodyArePlainText() {
        assertFalse(RestrictionModule.containsRestrictedSelector("/public hello @a and @e[type=zombie]"));
        assertFalse(RestrictionModule.containsRestrictedSelector("/global hello @p"));
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
    void commandsCheckUuidOwnershipExceptExplicitExemptions() {
        assertTrue(RestrictionModule.checksUuids("/msg Steve hello"));
        assertTrue(RestrictionModule.checksUuids("/reply hello"));
        assertTrue(RestrictionModule.checksUuids(
                "/teleport 00000000-0000-0000-0000-000000000001 Steve"));
        assertTrue(RestrictionModule.checksUuids(
                "/minecraft:tp 00000000-0000-0000-0000-000000000001 0 64 0"));
        assertTrue(RestrictionModule.checksUuids("/data get entity Steve"));
        assertTrue(RestrictionModule.checksUuids("/unknown argument"));
        assertFalse(RestrictionModule.checksUuids(
                "/summon wolf ~ ~ ~ {Owner:\"00000000-0000-0000-0000-000000000001\"}"));
        assertFalse(RestrictionModule.checksUuids(
                "/minecraft:summon wolf ~ ~ ~ {Owner:\"00000000-0000-0000-0000-000000000001\"}"));
        assertFalse(RestrictionModule.checksUuids(
                "/mikrepeat 00000000-0000-0000-0000-000000000001"));
        assertFalse(RestrictionModule.checksUuids(
                "/mik:mikrepeat 00000000-0000-0000-0000-000000000001"));
        assertFalse(RestrictionModule.checksUuids("/"));
    }

    @Test
    void playerNameChecksStillAllowNormalTeleportSyntax() {
        assertFalse(RestrictionModule.checksPlayerNames("/public Steve hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/global Steve hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/msg Steve hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/reply hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/teleport Steve Alex"));
        assertFalse(RestrictionModule.checksPlayerNames("/minecraft:tp Steve Alex"));
        assertFalse(RestrictionModule.checksPlayerNames(
                "/summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"Steve\"}'}"));
        assertFalse(RestrictionModule.checksPlayerNames(
                "/minecraft:summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"Steve\"}'}"));
        assertTrue(RestrictionModule.checksPlayerNames("/data get entity Steve"));
    }

    @Test
    void summonStillRejectsTargetSelectors() {
        assertTrue(RestrictionModule.containsRestrictedSelector(
                "/summon armor_stand @a"));
    }
}
