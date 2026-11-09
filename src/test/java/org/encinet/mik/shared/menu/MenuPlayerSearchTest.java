package org.encinet.mik.shared.menu;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPlayerSearchTest {
    @Test void anUnfilteredDirectoryUsesCaseInsensitiveNamesAndUuidAsAStableTieBreaker() {
        var firstAlex = new MenuPlayerSearch.Candidate(new UUID(0,1),"alex");
        var secondAlex = new MenuPlayerSearch.Candidate(new UUID(0,2),"Alex");
        var bob = new MenuPlayerSearch.Candidate(new UUID(0,3),"Bob");
        var zoe = new MenuPlayerSearch.Candidate(new UUID(0,4),"zoe");
        assertEquals(List.of(firstAlex,secondAlex,bob,zoe),
                MenuPlayerSearch.matching("",List.of(zoe,bob,secondAlex,firstAlex)));
    }

    @Test void partialNamesAreRankedWithoutPickingAndCurrentNamesWinOverStaleDirectoryNames() {
        var exact = new MenuPlayerSearch.Candidate(new UUID(0,1),"Alex");
        var prefix = new MenuPlayerSearch.Candidate(new UUID(0,2),"Alexandra");
        var contains = new MenuPlayerSearch.Candidate(new UUID(0,3),"OldAlex");
        var entries = List.of(contains,prefix,exact,new MenuPlayerSearch.Candidate(exact.id(),"Old_Name"));
        assertEquals(List.of(exact,prefix,contains),MenuPlayerSearch.matching("  aLeX ",entries));
        assertTrue(MenuPlayerSearch.matching("old_name",entries).isEmpty());
        assertEquals(List.of(exact),MenuPlayerSearch.matching(exact.id().toString(),entries));
        assertEquals(3,MenuPlayerSearch.matching("",entries).size());
    }
}
