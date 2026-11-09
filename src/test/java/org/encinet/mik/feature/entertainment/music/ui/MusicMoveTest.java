package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.playlist.EntryEdits;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicMoveTest {
    @Test void movementFreezesTheOwnerVersionOrderAndSelectedOccurrences() {
        UUID owner = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var order = new ArrayList<>(List.of(a, b, c)); var selected = new HashSet<>(Set.of(c));
        var move = new MusicMove(owner, 7, order, selected);
        order.clear(); selected.clear();
        assertEquals(List.of(a, b, c), move.order());
        assertEquals(Set.of(c), move.selected());
        assertTrue(move.current(owner, 7));
        assertFalse(move.current(owner, 8));
        assertFalse(move.current(UUID.randomUUID(), 7));
        assertFalse(move.canPlace(EntryEdits.end()));
        assertFalse(move.canPlace(EntryEdits.before(c)));
        assertFalse(move.canPlace(EntryEdits.before(UUID.randomUUID())));
        assertTrue(move.canPlace(EntryEdits.before(a)));
    }
    @Test void onlyVisibleAndEffectiveDestinationsEnableMoving() {
        UUID owner = UUID.randomUUID(), past = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        var move = new MusicMove(owner, 0, List.of(past, a, b), Set.of(a, b));
        assertFalse(move.hasDestination(Set.of(a, b)), "played and current entries are not placement targets");
        assertTrue(move.hasDestination(Set.of(past, a, b)));
        var all = new MusicMove(owner, 0, List.of(a, b), Set.of(a, b));
        assertFalse(all.hasDestination(Set.of(a, b)));
    }
}
