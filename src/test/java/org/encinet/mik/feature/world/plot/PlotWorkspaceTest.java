package org.encinet.mik.feature.world.plot;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlotWorkspaceTest {
    private final UUID owner = UUID.randomUUID(), collaborator = UUID.randomUUID();

    @Test void entriesReflectActualCapabilitiesInsteadOfAnyManagementRole() {
        Plot plot = plot(null, Map.of());
        assertEquals(List.of(PlotWorkspace.Task.DESTINATIONS), tasks(plot, collaborator, true, false));
        assertEquals(List.of(PlotWorkspace.Task.values()), tasks(plot, owner, true, false));
        Plot delegated = plot(null, Map.of("collaborator.manage_area",true));
        assertEquals(List.of(PlotWorkspace.Task.AREA), tasks(delegated, collaborator, false, false));
        Plot settings = plot(null, Map.of("collaborator.manage_settings",true));
        assertEquals(List.of(PlotWorkspace.Task.DESTINATIONS,PlotWorkspace.Task.SETTINGS), tasks(settings, collaborator, false, false));
        Plot membership = plot(null, Map.of("collaborator.manage_members",true));
        assertEquals(List.of(PlotWorkspace.Task.ACCESS), tasks(membership,collaborator,false,false));
        Plot ownership = plot(null, Map.of("collaborator.transfer",true));
        assertEquals(List.of(PlotWorkspace.Task.SETTINGS),tasks(ownership,collaborator,false,false));
    }

    @Test void visitorsCanBrowseExistingSubplotsButCannotCreateAnotherLevel() {
        UUID visitor = UUID.randomUUID();
        Plot root = plot(null,Map.of());
        assertEquals(List.of(PlotWorkspace.Task.SUBPLOTS),tasks(root,visitor,false,true));
        assertTrue(tasks(root,visitor,false,false).isEmpty());
        Plot child = plot(UUID.randomUUID(),Map.of());
        assertFalse(tasks(child,owner,true,true).contains(PlotWorkspace.Task.SUBPLOTS));
    }

    private List<PlotWorkspace.Task> tasks(Plot plot, UUID player, boolean destinations, boolean children) {
        return PlotWorkspace.tasks(plot,permission -> plot.allows(permission.key(),player,false,false),destinations,children);
    }
    private Plot plot(UUID parent,Map<String,Boolean> flags) {
        return new Plot(UUID.randomUUID(),UUID.randomUUID(),owner,"Park",0,Set.of(),Map.of(collaborator,Plot.Role.COLLABORATOR),flags,parent);
    }
}
