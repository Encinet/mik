package org.encinet.mik.feature.community.chat.mention;

import org.encinet.mik.feature.community.chat.mention.PlayerMentionIndex.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerMentionsTest {
    @ParameterizedTest
    @ValueSource(strings = {"@Alice你好", "你好@Alice", "你好@Alice在吗", "@ aLiCe你好",
            "@Aliceこんにちは", "こんにちは@Alice", "@Alice안녕하세요", "안녕@Alice", "𠀀@Alice𠀀"})
    void adjacentEastAsianTextDoesNotRequireSpaces(String source) {
        Target alice = target("Alice");
        PlayerMentions.Match match = PlayerMentions.of(List.of(alice)).find(source, 0);

        assertNotNull(match);
        assertEquals(alice, match.target());
        assertEquals(source.indexOf('@'), match.start());
        assertEquals(source.toLowerCase(java.util.Locale.ROOT).indexOf("alice") + 5, match.end());
    }

    @Test
    void longestEligibleNameStillWinsWhenNamesAndFollowingTextContainChinese() {
        Target shortName = target("Alex");
        Target longName = target("Alex小明");
        PlayerMentions mentions = new PlayerMentions(PlayerMentionIndex.of(List.of(shortName)),
                PlayerMentionIndex.of(List.of(longName)), Set.of(shortName.id()), null);
        String source = "问问@Alex小明你好";

        PlayerMentions.Match match = mentions.find(source, 0);

        assertNotNull(match);
        assertEquals(longName, match.target());
        assertEquals("@Alex小明", source.substring(match.start(), match.end()));
    }

    @ParameterizedTest
    @CsvSource({"Alice, aLiCe", "I, ı", "I, İ", "Σ, ς", "Σ, σ", "𐐀, 𐐨"})
    void unicodeCaseEquivalenceKeepsOriginalNamesAndSourceOffsets(String name, String input) {
        Target target = target(name);
        PlayerMentions mentions = PlayerMentions.of(List.of(target));
        String source = "İ😀 @ " + input;

        PlayerMentions.Match match = mentions.find(source, 0);

        assertEquals(target, match.target());
        assertEquals(source.indexOf('@'), match.start());
        assertEquals(source.length(), match.end());
        assertNull(mentions.find(source, match.start() + 1));
    }

    @Test
    void supportsTheFullSixtyFourCharacterNameWithoutLengthBitOverflow() {
        Target shorter = target("a".repeat(63));
        Target longest = target("a".repeat(64));
        PlayerMentions mentions = PlayerMentions.of(List.of(shorter, longest));
        String source = "@ " + longest.name();

        assertEquals(longest, mentions.find(source, 0).target());
        assertEquals(source.length(), mentions.find(source, 0).end());
        assertNull(mentions.find(source + 'a', 0));
        assertThrows(IllegalArgumentException.class, () -> target("a".repeat(65)));
    }

    @Test
    void supplementaryCharactersAtTheNameLimitKeepUtf16Boundaries() {
        Target target = target("a".repeat(62) + "𐐀");
        PlayerMentions mentions = PlayerMentions.of(List.of(target));
        String source = "@" + "a".repeat(62) + "𐐨";

        assertEquals(target, mentions.find(source, 0).target());
        assertEquals(source.length(), mentions.find(source, 0).end());
    }

    @Test
    void longestEligibleNameWinsAcrossBothIndexes() {
        Target shortName = target("Alex");
        Target longName = target("Alex One");
        PlayerMentionIndex online = PlayerMentionIndex.of(List.of(shortName));
        PlayerMentionIndex bound = PlayerMentionIndex.of(List.of(longName));
        PlayerMentions publicMentions = new PlayerMentions(online, bound, Set.of(shortName.id()), null);
        PlayerMentions privateMentions = new PlayerMentions(
                online, PlayerMentionIndex.empty(), Set.of(shortName.id()), null);

        assertEquals(longName, publicMentions.find("@ Alex One", 0).target());
        assertEquals(shortName, privateMentions.find("@ Alex One", 0).target());
    }

    @Test
    void onlineTargetsTakePrecedenceAndExcludedTargetsFallBackSafely() {
        Target onlineTarget = target("Alice");
        Target boundTarget = target("ALICE");
        PlayerMentionIndex online = PlayerMentionIndex.of(List.of(onlineTarget));
        PlayerMentionIndex bound = PlayerMentionIndex.of(List.of(boundTarget));
        PlayerMentions visible = new PlayerMentions(online, bound, Set.of(onlineTarget.id()), null);
        PlayerMentions hidden = new PlayerMentions(online, bound, Set.of(), null);
        PlayerMentions sender = new PlayerMentions(online, bound, Set.of(onlineTarget.id()), onlineTarget.id());
        PlayerMentions privateSender = new PlayerMentions(
                online, PlayerMentionIndex.empty(), Set.of(onlineTarget.id()), onlineTarget.id());

        assertEquals(onlineTarget, visible.find("@Alice", 0).target());
        assertEquals(boundTarget, hidden.find("@Alice", 0).target());
        assertEquals(boundTarget, sender.find("@Alice", 0).target());
        assertNull(privateSender.find("@Alice", 0));
    }

    @Test
    void equivalentNamesRespectEligibilityBeforeChoosingAPlayer() {
        Target first = target("I");
        Target second = target("ı");
        PlayerMentionIndex index = PlayerMentionIndex.of(List.of(first, second));
        PlayerMentions mentions = new PlayerMentions(index, PlayerMentionIndex.empty(), Set.of(second.id()), null);

        assertEquals(second, mentions.find("@İ", 0).target());
    }

    @Test
    void unchangedNamesReuseTheIndexWhileRenamesAndRemovalsKeepOldSnapshotsStable() {
        Target alice = target("Alice");
        List<Target> source = new ArrayList<>(List.of(alice));
        PlayerMentionIndex original = PlayerMentionIndex.of(source);
        source.clear();

        assertSame(original, original.withTargets(List.of(new Target(alice.id(), "Alice"))));
        assertSame(original, original.including(List.of(new Target(alice.id(), "Alice"))));
        Target renamed = new Target(alice.id(), "Alex");
        PlayerMentionIndex refreshed = original.including(List.of(renamed));
        PlayerMentions oldView = new PlayerMentions(original, PlayerMentionIndex.empty(), Set.of(alice.id()), null);
        PlayerMentions newView = new PlayerMentions(refreshed, PlayerMentionIndex.empty(), Set.of(alice.id()), null);

        assertNotSame(original, refreshed);
        assertEquals(alice, oldView.find("@Alice", 0).target());
        assertNull(newView.find("@Alice", 0));
        assertEquals(renamed, newView.find("@Alex", 0).target());
        assertSame(PlayerMentionIndex.empty(), refreshed.withTargets(List.of()));
    }

    @Test
    void differentMessageScopesShareIndexesWithoutSharingEligibility() {
        Target alice = target("Alice");
        Target bob = target("Bob");
        PlayerMentionIndex index = PlayerMentionIndex.of(List.of(alice, bob));
        Set<UUID> allowed = new java.util.HashSet<>(Set.of(alice.id()));
        PlayerMentions first = new PlayerMentions(index, PlayerMentionIndex.empty(), allowed, null);
        allowed.clear();
        PlayerMentions second = new PlayerMentions(index, PlayerMentionIndex.empty(), Set.of(bob.id()), null);

        assertSame(first.online(), second.online());
        assertEquals(alice, first.find("@Alice", 0).target());
        assertNull(first.find("@Bob", 0));
        assertEquals(bob, second.find("@Bob", 0).target());
        assertNull(second.find("@Alice", 0));
    }

    private static Target target(String name) {
        return new Target(UUID.randomUUID(), name);
    }
}
