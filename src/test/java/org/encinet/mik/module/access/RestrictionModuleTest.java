package org.encinet.mik.module.access;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

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
    void playerNameChecksAllowCommandsWithExplicitPlayerTargets() {
        assertFalse(RestrictionModule.checksPlayerNames("/public Steve hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/global Steve hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/msg Steve hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/reply hello"));
        assertFalse(RestrictionModule.checksPlayerNames("/teleport Steve Alex"));
        assertFalse(RestrictionModule.checksPlayerNames("/minecraft:tp Steve Alex"));
        assertFalse(RestrictionModule.checksPlayerNames("/give Steve minecraft:stone"));
        assertFalse(RestrictionModule.checksPlayerNames("/minecraft:give Alex minecraft:stone 64"));
        assertFalse(RestrictionModule.checksPlayerNames(
                "/summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"Steve\"}'}"));
        assertFalse(RestrictionModule.checksPlayerNames(
                "/minecraft:summon armor_stand ~ ~ ~ {CustomName:'{\"text\":\"Steve\"}'}"));
        assertTrue(RestrictionModule.checksPlayerNames("/data get entity Steve"));
    }

    @Test
    void giveStillRejectsTargetSelectors() {
        assertTrue(RestrictionModule.containsRestrictedSelector("/give @a minecraft:stone"));
        assertTrue(RestrictionModule.containsRestrictedSelector(
                "/minecraft:give @e[type=minecraft:zombie] minecraft:stone"));
    }

    @Test
    void summonStillRejectsTargetSelectors() {
        assertTrue(RestrictionModule.containsRestrictedSelector(
                "/summon armor_stand @a"));
    }

    @Test
    void summonCannotUseAnotherOnlinePlayersUuidAsOwner() {
        UUID sender = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID victim = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");
        String ownerArray = uuidArray(victim);
        Set<UUID> online = Set.of(sender, victim);

        assertTrue(RestrictionModule.containsForeignPlayerUuid(
                "minecraft:ender_pearl ~ ~ ~ {Owner:" + ownerArray + "}",
                sender, online::contains));
        assertTrue(RestrictionModule.containsForeignPlayerUuid(
                "wolf ~ ~ ~ {Owner:\"" + victim + "\"}", sender, online::contains));
        assertFalse(RestrictionModule.containsForeignPlayerUuid(
                "minecraft:ender_pearl ~ ~ ~ {Owner:" + uuidArray(sender) + "}",
                sender, online::contains));
        assertFalse(RestrictionModule.containsForeignPlayerUuid(
                "armor_stand ~ ~ ~ {CustomName:'{\"text\":\"Steve\"}'}",
                sender, online::contains));
        assertFalse(RestrictionModule.containsForeignPlayerUuid(
                "wolf ~ ~ ~ {Owner:" + ownerArray + "}", sender,
                Set.of(sender)::contains));
    }

    private static String uuidArray(UUID uuid) {
        return "[I;%d,%d,%d,%d]".formatted(
                (int) (uuid.getMostSignificantBits() >>> 32),
                (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >>> 32),
                (int) uuid.getLeastSignificantBits());
    }

    @Test
    void oversizedSummonsAndSpawnEggGivesAreBlockedBeforeExecution() {
        assertTrue(RestrictionModule.containsOversizedEntityData(
                "/summon minecraft:slime ~ ~ ~ {Size:120000}"));
        assertTrue(RestrictionModule.containsOversizedEntityData(
                "/execute as @s run minecraft:summon slime ~ ~ ~ "
                        + "{attributes:[{id:\"minecraft:scale\",base:120000}]}"));
        assertTrue(RestrictionModule.containsOversizedEntityData(
                "/give @s slime_spawn_egg[entity_data={id:\"minecraft:slime\","
                        + "Size:120000,attributes:[{id:\"minecraft:scale\",base:120000}]}]"));
        assertTrue(RestrictionModule.containsOversizedEntityData(
                "/execute as @s run minecraft:give @s slime_spawn_egg[entity_data="
                        + "{id:\"minecraft:slime\",Size:120000}]"));
        assertFalse(RestrictionModule.containsOversizedEntityData(
                "/give @s slime_spawn_egg[entity_data={id:\"minecraft:slime\",Size:4}]"));
    }
}
