package org.encinet.mik.feature.world.plot;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlotDirectoryTest {
    private final UUID player = UUID.randomUUID(), other = UUID.randomUUID(), world = UUID.randomUUID();

    @Test void ownedAndParticipatingListsStayDistinctAndNamesSortConsistently() {
        Plot alpha = plot(player,"Alpha",Map.of()), beta = plot(player,"beta",Map.of(player,Plot.Role.ADMIN));
        Plot joined = plot(other,"Shared",Map.of(player,Plot.Role.COLLABORATOR)), unrelated = plot(other,"Other",Map.of());
        List<Plot> all = List.of(joined,beta,unrelated,alpha);
        assertEquals(List.of(alpha,beta),PlotDirectory.initial().entries(all,player));
        assertEquals(List.of(joined),PlotDirectory.initial().filter(PlotDirectory.Filter.PARTICIPATING).entries(all,player));
        assertThrows(UnsupportedOperationException.class,() -> PlotDirectory.initial().entries(all,player).clear());
    }

    @Test void searchSupportsNamesAndIdPrefixesAndChangingFilterOrQueryResetsPaging() {
        Plot alpha = plot(player,"River Garden",Map.of()), shared = plot(other,"River station",Map.of(player,Plot.Role.ADMIN));
        List<Plot> all = List.of(alpha,shared);
        var search = PlotDirectory.initial().page(5).query("  GARDEN ");
        assertEquals(0,search.page()); assertEquals(List.of(alpha),search.entries(all,player));
        assertEquals(List.of(alpha),search.query("#"+alpha.id().toString().substring(0,8)).entries(all,player));
        var participating = search.query("river").page(3).filter(PlotDirectory.Filter.PARTICIPATING);
        assertEquals(0,participating.page()); assertEquals("river",participating.query());
        assertEquals(List.of(shared),participating.entries(all,player));
        assertTrue(participating.query("unmatched").entries(all,player).isEmpty());
        assertEquals(List.of(shared),participating.query("").entries(all,player));
    }
    private Plot plot(UUID owner,String name,Map<UUID,Plot.Role> members) {
        return new Plot(UUID.randomUUID(),world,owner,name,0,Set.of(),members,Map.of(),null);
    }
}
