package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSearchStateTest {
    @Test void onlyTheInitialRequestIsAWholePageLoadingPlaceholder() {
        var state = new MenuSearchState<String>();
        assertTrue(state.initializing());
        assertTrue(state.pending);
        long obsolete = state.begin("Alex"), current = state.begin("");
        assertFalse(state.complete(obsolete, List.of("Alex"), null));
        assertTrue(state.initializing());
        assertTrue(state.complete(current, List.of("Alex", "Bob"), null));
        assertFalse(state.initializing());
        long next = state.begin("Bob");
        assertTrue(state.pending);
        assertFalse(state.initializing(), "New queries must clear stale results instead of preserving a loaded page");
        assertTrue(state.complete(next, null, new IllegalStateException("storage")));
        assertFalse(state.initializing());
    }

    @Test void newerQueriesInvalidateOldResultsAndResetThePageBeforeLoading() {
        var state = new MenuSearchState<String>();
        long first = state.begin("Alex");
        assertTrue(state.complete(first,List.of("Alex"),null));
        state.page = 2;
        long next = state.begin("  Bob  ");
        assertEquals("Bob",state.query);
        assertEquals(0,state.page);
        assertTrue(state.pending);
        assertTrue(state.results.isEmpty());
        assertFalse(state.complete(first,List.of("Alex"),null));
        assertTrue(state.pending);
        assertTrue(state.complete(next,List.of("Bob"),null));
        assertEquals(List.of("Bob"),state.results);
    }

    @Test void failureAndClosingCannotLeaveClickableStaleCandidatesOrResurrectTheSearch() {
        var state = new MenuSearchState<String>();
        long first = state.begin("Alex");
        assertTrue(state.complete(first,null,new IllegalStateException("storage")));
        assertTrue(state.failed);
        assertFalse(state.pending);
        assertTrue(state.results.isEmpty());
        long retry = state.begin(state.query);
        assertFalse(state.failed);
        state.close();
        assertFalse(state.complete(retry,List.of("Alex"),null));
        assertTrue(state.results.isEmpty());
        assertThrows(IllegalStateException.class,() -> state.begin("Bob"));
    }

    @Test void largeDirectoriesHaveAnExplicitTotalAndCannotExhaustTheUiTreeBudget() {
        var state = new MenuSearchState<Integer>();
        long request = state.begin("");
        var directory = java.util.stream.IntStream.range(0,10_000).boxed().toList();
        assertTrue(state.complete(request,directory,null));
        assertEquals(10_000,state.total);
        assertEquals(MenuSearchState.MAX_RESULTS,state.results.size());
        assertEquals(0,state.results.getFirst());
        assertEquals(MenuSearchState.MAX_RESULTS-1,state.results.getLast());
        long narrowed = state.begin("9999");
        assertTrue(state.complete(narrowed,List.of(9999),null));
        assertEquals(1,state.total);
        assertEquals(List.of(9999),state.results);
    }
}
