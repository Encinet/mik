package org.encinet.mik.feature.server.ban;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import org.encinet.mik.shared.menu.MenuPlayerSearch;
import static org.junit.jupiter.api.Assertions.*;

class BanMenuTest {
    @Test void namesWithoutPlayHistoryNeedAnExplicitConfirmation() {
        assertTrue(BanMenu.needsNeverJoinedConfirmation(false));
        assertFalse(BanMenu.needsNeverJoinedConfirmation(true));
    }

    @Test void partialMatchesAreListedAndUnknownLiteralNamesRemainExplicitCandidates() {
        var moderator = new MenuPlayerSearch.Candidate(new UUID(0,1),"Moderator");
        var alex = new MenuPlayerSearch.Candidate(new UUID(0,2),"Alex");
        var alexandra = new MenuPlayerSearch.Candidate(new UUID(0,3),"Alexandra");
        var players = List.of(moderator,alex,alexandra);
        var exact = BanMenu.candidates("aLeX",players,moderator.id());
        assertEquals(List.of(new BanMenu.Candidate(alex.id(),alex.name()),new BanMenu.Candidate(alexandra.id(),alexandra.name())),exact);
        var partial = BanMenu.candidates("Ale",players,moderator.id());
        assertEquals(3,partial.size());
        assertEquals(new BanMenu.Candidate(null,"Ale"),partial.getLast());
        assertEquals(List.of(new BanMenu.Candidate(null,"NeverJoined")),BanMenu.candidates("NeverJoined",players,moderator.id()));
        assertTrue(BanMenu.candidates("wrong name!",players,moderator.id()).isEmpty());
        assertTrue(BanMenu.candidates("Moderator",players,moderator.id()).isEmpty());
    }
}
