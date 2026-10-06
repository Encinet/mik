package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotHotPathCorrectnessTest {
    private static PlotRegistry registry() {
        return new PlotRegistry(new PlotRepository(Path.of("unused-hot-path.db")).plan());
    }

    @Test
    void commonPermissionKeysAreReusedWithoutChangingTheirPersistedNames() {
        for (PlotAccessPolicy.Group group : PlotAccessPolicy.Group.values()) {
            String prefix = group.key("");
            for (PlotPermission action : PlotPermission.values()) {
                assertEquals(prefix + action.key(), group.key(action.key()));
                assertSame(group.key(action.key()), group.key(new String(action.key())));
            }
            for (String legacy : List.of("door", "entity_interact")) {
                assertEquals(prefix + legacy, group.key(legacy));
                assertSame(group.key(legacy), group.key(new String(legacy)));
            }
            assertSame(prefix, group.key(""));
            assertEquals(prefix + "custom.extension", group.key("custom.extension"));
            assertEquals(prefix + "null", group.key(null));
        }
    }

    @Test
    void catalogReusesImmutableViewsAndOnlyInvalidatesForPlotChanges() throws Exception {
        var registry = registry();
        var edits = new PlotEdits(registry);
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Plot parent = edits.create(owner, "Parent", world, shape(world, -16, -1));
        Plot child = edits.createSubPlot(parent, owner, false, owner, "Zulu", shape(world, -16, -13));
        Plot other = edits.createSubPlot(parent, owner, false, owner, "Alpha", shape(world, -4, -1));
        List<Plot> all = registry.all();
        List<Plot> children = registry.childrenOf(parent.id());
        assertSame(all, registry.all());
        assertSame(children, registry.childrenOf(parent.id()));
        assertEquals(List.of(other, child), children);
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
        assertThrows(UnsupportedOperationException.class, () -> children.clear());
        registry.setNoticeBoard(parent.id(), "Notice");
        registry.setArrival(parent.id(), new PlotArrival(-8, 65, -8, 0, 0, "", ""));
        registry.setAtmosphere(parent.id(), new PlotAtmosphere(6000, null));
        assertSame(all, registry.all());
        assertSame(children, registry.childrenOf(parent.id()));
        edits.flag(child, child.owner(), false, "newcomer.place", true);
        List<Plot> changed = registry.childrenOf(parent.id());
        assertNotSame(children, changed);
        assertSame(registry.byId(child.id()), changed.get(1));
        assertFalse(children.get(1).flags().containsKey("newcomer.place"));
        assertTrue(changed.get(1).flags().get("newcomer.place"));
        edits.rename(child, child.owner(), false, "Aardvark");
        assertEquals(child.id(), registry.childrenOf(parent.id()).getFirst().id());
        assertEquals("Zulu", children.get(1).name());
        edits.delete(registry.byId(child.id()), registry.byId(child.id()).owner(), false);
        assertEquals(List.of(other), registry.childrenOf(parent.id()));
        assertEquals(2, registry.all().size());
    }

    @Test
    void atmosphereVersionIgnoresUnrelatedSettingsButTracksCoverageAndInheritance() throws Exception {
        var registry = registry();
        var edits = new PlotEdits(registry);
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Plot parent = edits.create(owner, "Parent", world, shape(world, 0, 15));
        Plot child = edits.createSubPlot(parent, owner, false, owner, "Child", shape(world, 0, 3));
        long version = registry.atmosphereRevision();
        edits.flag(parent, parent.owner(), false, "newcomer.place", true);
        edits.rename(parent, parent.owner(), false, "Renamed");
        edits.invite(parent, owner, false, UUID.randomUUID(), Plot.Role.COLLABORATOR);
        registry.setNoticeBoard(parent.id(), "Notice");
        registry.setArrival(parent.id(), new PlotArrival(8, 65, 8, 0, 0, "", ""));
        assertEquals(version, registry.atmosphereRevision());
        PlotAtmosphere inherited = new PlotAtmosphere(6000, PlotAtmosphere.Weather.RAIN);
        registry.setAtmosphere(parent.id(), inherited);
        assertEquals(version + 1, registry.atmosphereRevision());
        assertSame(inherited, registry.effectiveAtmosphere(child.id()));
        registry.setAtmosphere(parent.id(), new PlotAtmosphere(6000, PlotAtmosphere.Weather.RAIN));
        assertEquals(version + 1, registry.atmosphereRevision());
        PlotAtmosphere local = new PlotAtmosphere(12_000, PlotAtmosphere.Weather.CLEAR);
        registry.setAtmosphere(child.id(), local);
        assertSame(local, registry.effectiveAtmosphere(child.id()));
        long beforeResize = registry.atmosphereRevision();
        edits.resize(registry.byId(parent.id()), owner, false, shape(world, 0, 31));
        assertTrue(registry.atmosphereRevision() > beforeResize);
        long beforeDelete = registry.atmosphereRevision();
        edits.delete(child, child.owner(), false);
        assertTrue(registry.atmosphereRevision() > beforeDelete);
    }

    @Test
    void indexedPermissionChecksMatchTheOriginalAlgorithmAcrossRolesHeightsAndBoundaries() throws Exception {
        var registry = registry();
        UUID world = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID visitor = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Plot parent = new Plot(UUID.randomUUID(), world, owner, "Parent", false, 0,
                Set.of(new Cell(-1, 16, -1), new Cell(0, 16, 0), new Cell(0, 17, 0), new Cell(1, 16, 0)),
                Map.of(visitor, Plot.Role.COLLABORATOR), Map.of("member.place", true, "collaborator.container", true), null);
        registry.put(parent);
        Plot child = new Plot(UUID.randomUUID(), world, outsider, "Child", false, 0,
                Set.of(new Cell(0, 16, 0)), Map.of(visitor, Plot.Role.COLLABORATOR),
                Map.of("collaborator.place", false, "member.container", true), parent.id());
        registry.put(child);
        Plot overlap = new Plot(UUID.randomUUID(), world, UUID.randomUUID(), "Other protection", false, 0,
                Set.of(new Cell(0, 16, 0), new Cell(1, 16, 0)), Map.of(), Map.of("member.place", true), null);
        registry.put(overlap);
        for (UUID dimension : List.of(world, elsewhere)) {
            for (UUID actor : List.of(owner, visitor, outsider, overlap.owner())) {
                for (boolean staff : List.of(false, true)) {
                    for (boolean member : List.of(false, true)) {
                        for (String action : List.of("place", "container", "door", "entity_interact", "build", "unknown")) {
                            for (int horizontal : new int[]{-5, -4, -1, 0, 1, 3, 4, 7, 8, 16}) {
                                for (int vertical : new int[]{63, 64, 67, 68, 71, 72}) {
                                    int forward = horizontal < 0 ? -1 : 1;
                                    assertEquals(originalAllowed(registry, dimension, horizontal, vertical, forward,
                                                    actor, member, staff, action),
                                            registry.allowed(dimension, horizontal, vertical, forward,
                                                    actor, member, staff, action),
                                            dimension + ":" + horizontal + ":" + vertical + ":" + action);
                                }
                            }
                        }
                    }
                }
            }
        }
        var edits = new PlotEdits(registry);
        edits.flag(child, child.owner(), false, "member.place", false);
        assertFalse(registry.allowed(world, 1, 65, 1, UUID.randomUUID(), true, false, "place"));
        edits.delete(child, child.owner(), false);
        assertTrue(registry.allowed(world, 1, 65, 1, UUID.randomUUID(), true, false, "place"));
    }

    private static boolean originalAllowed(PlotRegistry registry, UUID world, int x, int y, int z,
                                           UUID actor, boolean member, boolean staff, String action) {
        List<Plot> here = registry.atAll(world, x, y, z);
        Set<UUID> shadowed = new HashSet<>();
        for (Plot plot : here) if (plot.parentId() != null) shadowed.add(plot.parentId());
        for (Plot plot : here) {
            if (shadowed.contains(plot.id())) continue;
            if (!plot.allows(registry.parentOf(plot), action, actor, member, staff)) return false;
        }
        return true;
    }

    private static PlotSelectionShape shape(UUID world, int minimum, int maximum) {
        return PlotSelectionShape.of(new PlotPosition(world, minimum, 64, minimum),
                new PlotPosition(world, maximum, 67, maximum));
    }
}
