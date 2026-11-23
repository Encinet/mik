package org.encinet.mik.feature.world.plot;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.role.RolePermissions;

/** The local plot name and this player's plot-protection decisions for ordinary blocks at their feet. */
public record PlotPlayerContext(String plotName, Access access, boolean memberEligible,
                                boolean canPlace, boolean canBreak) {
    public enum Access { OUTSIDE, STAFF, PARENT_OWNER, OWNER, ADMIN, COLLABORATOR, MEMBER, NEWCOMER }

    static PlotPlayerContext read(PlotRegistry registry, Player player) {
        var position = player.getLocation();
        var world = position.getWorld().getUID();
        int x = position.getBlockX(), y = position.getBlockY(), z = position.getBlockZ();
        var actor = player.getUniqueId();
        boolean member = RolePermissions.isMember(player);
        boolean staff = RolePermissions.canModerate(player);
        var plot = registry.at(world, x, y, z);
        // /plot already exposes the current plot's name. Do not return any other plot or its metadata.
        var access = plot == null ? Access.OUTSIDE : switch (registry.standing(plot, actor, staff)) {
            case STAFF -> Access.STAFF;
            case PARENT_OWNER -> Access.PARENT_OWNER;
            case OWNER -> Access.OWNER;
            case ADMIN -> Access.ADMIN;
            case COLLABORATOR -> Access.COLLABORATOR;
            case OUTSIDER -> member ? Access.MEMBER : Access.NEWCOMER;
        };
        return new PlotPlayerContext(plot == null ? "" : plot.name(), access, member,
                registry.allowed(world, x, y, z, actor, member, staff, PlotPermission.PLACE.key()),
                registry.allowed(world, x, y, z, actor, member, staff, PlotPermission.BREAK.key()));
    }
}
