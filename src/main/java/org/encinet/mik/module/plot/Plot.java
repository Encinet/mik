package org.encinet.mik.module.plot;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable logical state shared with protection and edit threads. */
public record Plot(UUID id, UUID world, UUID owner, String name, boolean publicProject,
                   long createdAt, Set<PlotGeometry.Cell> cells, Map<UUID, Role> members,
                   Map<String, Boolean> flags, UUID parentId) {
    public enum Role { ADMIN, COLLABORATOR }

    public Plot {
        cells = Set.copyOf(cells);
        members = Map.copyOf(members);
        flags = Map.copyOf(flags);
        if (id.equals(parentId))
            throw new IllegalArgumentException("A sub-plot cannot be its own parent");
    }

    public boolean subPlot() { return parentId != null; }

    public boolean canBuild(UUID actor, boolean staff) {
        return allows("build", actor, false, staff);
    }

    public boolean canManage(UUID actor, boolean staff) {
        return canManage(null, actor, staff);
    }

    boolean canManage(Plot parent, UUID actor, boolean staff) {
        return PlotPermission.Category.MANAGEMENT.permissions().stream()
                .anyMatch(action -> allows(parent, action.key(), actor, false, staff));
    }

    public boolean allows(String flag, UUID actor, boolean member, boolean staff) {
        return allows(null, flag, actor, member, staff);
    }

    public boolean allows(Plot parent, String flag, UUID actor, boolean member, boolean staff) {
        if (!PlotAccessPolicy.validQuery(flag)) return false;
        PlotPermission permission = PlotPermission.fromKey(flag);
        if (permission != null && !permission.appliesTo(this)) return false;
        if (staff || parent != null && parent.owner().equals(actor)) return true;
        return PlotAccessPolicy.allowed(this, parent, actor, member, flag);
    }

    public boolean protects(int x, int y, int z) {
        return cells.contains(PlotGeometry.Cell.at(x, y, z));
    }
}
