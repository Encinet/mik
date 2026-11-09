package org.encinet.mik.feature.player.teleport;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import org.encinet.mik.shared.menu.ui.UiKey;
import org.encinet.mik.shared.menu.ui.UiReadingPosition;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class HomeMenuStateTest {
    @Test void nameSearchIsPartialCaseInsensitiveAndKeepsTheSavedNamesAndCount() {
        var homes = new ArrayList<>(List.of("South GARDEN", "庭院基地", "base", "Garden North", "庭院工坊"));
        var original = List.copyOf(homes);
        var state = HomeMenuState.initial().search("  garDEN  ");
        var view = state.view(homes);
        assertEquals("garDEN", state.query());
        assertEquals(List.of("Garden North", "South GARDEN"), view.matches());
        assertEquals(2, view.matches().size());
        assertEquals(5, view.homeCount(), "the summary and creation quota use every saved home");
        assertEquals(original, homes, "search must not sort or remove saved entries in place");
        assertEquals(List.of("庭院基地", "庭院工坊"), state.search("庭院").view(homes).matches());
        assertEquals(List.of("κόσμος"), state.search("Σ").view(List.of("κόσμος", "base")).matches(),
                "case-insensitive matching includes a letter whose lowercase form depends on its word position");
        assertEquals(List.of("base", "Base"), HomeMenuState.initial().view(List.of("base", "Base")).matches(),
                "case-only names keep the same stable ordering used when focusing a newly created home");
    }

    @Test void pagingSelectionAndUpdatesKeepTheQueryAndANewQueryStartsAtTheFirstMatch() {
        var homes = IntStream.range(0, 20).mapToObj(index -> "Garden " + String.format("%02d", index)).toList();
        var state = HomeMenuState.initial().search("garden").browse(new UiReadingPosition(UiKey.item("home:Garden 12"), 0, 12)).focus("Garden 13");
        var view = state.view(homes);
        assertEquals(12, state.position().index());
        assertEquals("Garden 13", view.focusedHome());
        assertSame(state, state.search(" garden "), "confirming an unchanged query keeps the user's place");
        state = state.browse(new UiReadingPosition(UiKey.item("home:Garden 16"), 0, 16));
        assertEquals("garden", state.query());
        assertEquals("Garden 13", state.view(homes).focusedHome(), "browsing does not change the action target");
        state = state.search("19");
        assertEquals(UiReadingPosition.START, state.position());
        assertEquals(List.of("Garden 19"), state.view(homes).matches());
        assertEquals("Garden 19", state.view(homes).focusedHome());
        state = state.search("  ");
        assertEquals("", state.query());
        assertEquals(20, state.view(homes).matches().size());
    }

    @Test void noMatchesHideTheDestinationAndDeletingResultsClampsTheFilteredPage() {
        var homes = IntStream.range(0, 20).mapToObj(index -> "Base " + index).toList();
        var state = HomeMenuState.initial().focus("Base 19").search("missing");
        var empty = state.view(homes);
        assertEquals(20, empty.homeCount());
        assertEquals(0, empty.matches().size());
        assertTrue(empty.matches().isEmpty());
        assertNull(empty.focusedHome(), "a filtered-out home cannot remain an action target");
        state = state.search("base").browse(new UiReadingPosition(UiKey.item("home:Base 19"), 0, 19)).focus("Base 19");
        var reduced = state.view(List.of("Base 1", "Base 2", "Other"));
        assertEquals(19, state.position().index(), "measurement resolves a removed bookmark without mutating the owner");
        assertEquals(List.of("Base 1", "Base 2"), reduced.matches());
        assertEquals("Base 1", reduced.focusedHome());
        var noHomes = state.view(List.of());
        assertEquals(0, noHomes.homeCount());
        assertNull(noHomes.focusedHome());
    }
}
