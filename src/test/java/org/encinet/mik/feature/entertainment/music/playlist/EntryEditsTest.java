package org.encinet.mik.feature.entertainment.music.playlist;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class EntryEditsTest {
    @Test
    void noncontiguousSelectionKeepsOrderWhenPlacedAtTheEnd() {
        List<UUID> all = new ArrayList<>();
        for (int i = 0; i < 12; i++) all.add(UUID.randomUUID());
        List<UUID> moved = EntryEdits.move(all, Set.of(all.get(1), all.get(9)), EntryEdits.end(), v -> v);
        assertEquals(List.of(all.get(1), all.get(9)), moved.subList(10, 12));
        assertEquals(12, new HashSet<>(moved).size());
    }
    @Test
    void staleSelectionFailsInsteadOfMovingAnUnrelatedRow() {
        assertThrows(IllegalStateException.class,
                () -> EntryEdits.move(List.of(UUID.randomUUID()), Set.of(UUID.randomUUID()), EntryEdits.end(), v -> v));
    }
    @Test void placementUsesTheAnchorIdentityAndKeepsUnselectedAndFilteredOutEntriesInOrder() {
        var a = UUID.randomUUID(); var hidden = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID();
        var all = List.of(a, hidden, b, c);
        assertEquals(List.of(c, a, hidden, b), EntryEdits.move(all, Set.of(c), EntryEdits.before(a), id -> id));
        assertEquals(List.of(a, hidden, c, b), EntryEdits.move(all, Set.of(c), EntryEdits.before(b), id -> id));
        assertEquals(all, EntryEdits.move(all, Set.of(a), EntryEdits.before(hidden), id -> id));
        assertEquals(all, EntryEdits.move(all, Set.of(c), EntryEdits.end(), id -> id));
    }
    @Test void missingOrSelectedAnchorsCannotSilentlyBecomeAnotherPosition() {
        var a = UUID.randomUUID(); var b = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> EntryEdits.move(List.of(a, b), Set.of(a), EntryEdits.before(UUID.randomUUID()), id -> id));
        assertThrows(IllegalArgumentException.class, () -> EntryEdits.move(List.of(a, b), Set.of(a), EntryEdits.before(a), id -> id));
    }
}
