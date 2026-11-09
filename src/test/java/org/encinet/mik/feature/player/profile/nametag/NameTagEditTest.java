package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.feature.player.profile.PlayerNameTag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.encinet.mik.feature.player.profile.nametag.NameTagField.*;
import static org.encinet.mik.feature.player.profile.nametag.NameTagTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class NameTagEditTest {
    @Test void editingLoadsTheEffectiveSuffixEvenWhenItWasNotCreatedByThisMenu() {
        var prefix = decoration(PREFIX, "Custom prefix");
        var external = decoration(SUFFIX, " <green>Existing suffix", 100, false, false, true);
        var owned = NameTagStore.snapshot(Set.of(prefix, external));
        var effective = new PlayerNameTag("Custom prefix", " <green>Existing suffix", NamedTextColor.YELLOW);
        var edit = new NameTagEdit(SUFFIX, owned, effective);
        assertEquals(" <green>Existing suffix", edit.value(SUFFIX));
        assertEquals(effective, edit.preview(effective), "opening the editor must preserve the visible suffix");
        assertTrue(edit.changes().get(SUFFIX).expected().isEmpty(), "the external suffix is readable, but does not become owned by the editor");
        assertFalse(edit.canSave());
        edit.input(" <blue>New suffix");
        assertTrue(edit.canSave());
        assertEquals(new PlayerNameTag("Custom prefix", " <blue>New suffix", NamedTextColor.YELLOW), edit.preview(effective));
        edit.input("");
        assertFalse(edit.canSave(), "clearing cannot claim to remove an inherited or externally managed suffix");
        assertFalse(edit.removesCustomValue(), "an empty inherited field must not display a custom-removal warning");
    }

    @Test void clearingAnOwnedSuffixIsAllowedEvenWhenItsEffectiveValueIsEmpty() {
        var suffix = decoration(SUFFIX, "Hidden custom suffix");
        var edit = new NameTagEdit(SUFFIX, NameTagStore.snapshot(Set.of(suffix)), PlayerNameTag.empty());
        assertTrue(edit.value(SUFFIX).isEmpty());
        assertTrue(edit.canSave());
        assertTrue(edit.removesCustomValue());
        assertEquals(Set.of(suffix), edit.changes().get(SUFFIX).expected());
    }

    @Test void effectiveSuffixIsNotReplacedByALexicallySelectedCustomNodeWhenEditingBegins() {
        var stored = decoration(SUFFIX, "A custom suffix");
        var custom = NameTagStore.snapshot(Set.of(stored));
        var effective = new PlayerNameTag("Role prefix", "[stacked suffix] B");
        var edit = new NameTagEdit(SUFFIX, custom, effective);
        assertEquals("A custom suffix", customValue(custom, SUFFIX));
        assertEquals("[stacked suffix] B", edit.value(SUFFIX));
        assertFalse(edit.canSave());
        assertEquals(Set.of(stored), edit.changes().get(SUFFIX).expected());
        edit.input(" New suffix");
        assertEquals(new PlayerNameTag("Role prefix", " New suffix"), edit.preview(effective));
    }
    @Test void sentTextOnlyChangesTheCandidateAndCannotChangeTheOtherFieldOrBaseline() {
        var prefix = decoration(PREFIX, "Original ");
        var suffix = decoration(SUFFIX, " Suffix");
        var current = new NameTagStore.Snapshot(Set.of(prefix, suffix));
        var edit = new NameTagEdit(PREFIX, current, new PlayerNameTag("Original ", " Suffix"));
        assertFalse(edit.canSave());
        edit.input("<red>First ");
        edit.input("<blue>%player_name% ");
        assertEquals("<blue>%player_name% ", edit.value(PREFIX));
        assertEquals(new PlayerNameTag("<blue>%player_name% ", " Suffix"),
                edit.preview(new PlayerNameTag(customValue(current, PREFIX), customValue(current, SUFFIX))));
        assertEquals("Original ", customValue(current, PREFIX));
        assertEquals(Set.of(prefix), edit.changes().get(PREFIX).expected());
        assertTrue(edit.beginSave());
        assertFalse(edit.beginSave());
        edit.input("Must not replace the submitted value");
        assertEquals("<blue>%player_name% ", edit.value(PREFIX));
        edit.failed();
        assertTrue(edit.canSave());
    }

    @Test void invalidTextKeepsTheLastValidPreviewAndDisablesConfirmationUntilCorrected() {
        var edit = new NameTagEdit(PREFIX, new NameTagStore.Snapshot(Set.of()), PlayerNameTag.empty());
        edit.input("<green>Valid");
        edit.input("%server_online%");
        assertEquals("<green>Valid", edit.value(PREFIX));
        assertEquals(NameTagFormat.Problem.UNSUPPORTED_PLACEHOLDER, edit.failure().problem());
        assertFalse(edit.canSave());
        edit.input("<blue>Corrected ");
        assertNull(edit.failure());
        assertTrue(edit.canSave());
    }

    @Test void conflictRebasesOnlyTheVersionAndKeepsTheCandidateForExplicitReconfirmation() {
        var original = decoration(PREFIX, "Original");
        var current = decoration(PREFIX, "Externally changed");
        var edit = new NameTagEdit(PREFIX, new NameTagStore.Snapshot(Set.of(original)), new PlayerNameTag("Original", ""));
        edit.input("My candidate");
        assertTrue(edit.beginSave());
        edit.rebase(new NameTagStore.Snapshot(Set.of(current)), new PlayerNameTag("Externally changed", ""));
        assertEquals("My candidate", edit.value(PREFIX));
        assertEquals(Set.of(current), edit.changes().get(PREFIX).expected());
        assertFalse(edit.saving());
        assertTrue(edit.canSave());
        edit.input("");
        assertTrue(edit.canSave(), "explicit clearing is a valid candidate");
    }

    @Test void unicodeSourceLengthCountsCodePointsAndKeepsIntentionalSpacing() {
        assertTrue(NameTagFormat.validate("😀".repeat(200)).isEmpty());
        assertEquals(NameTagFormat.Problem.TOO_LONG, NameTagFormat.validate("😀".repeat(201)).orElseThrow().problem());
        assertTrue(NameTagFormat.validate(" <rainbow>Prefix </rainbow> ").isEmpty());
        assertTrue(NameTagFormat.validate("").isEmpty());
        assertEquals(NameTagFormat.Problem.INVALID, NameTagFormat.validate("   ").orElseThrow().problem());
        assertEquals(NameTagFormat.Problem.INVALID, NameTagFormat.validate("a\nb").orElseThrow().problem());
        assertTrue(NameTagFormat.validate("<color:not-a-color>bad").isEmpty(), "unrecognized tags follow the renderer's literal-text behavior");
    }
}
