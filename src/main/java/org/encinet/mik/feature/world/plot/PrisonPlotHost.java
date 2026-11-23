package org.encinet.mik.feature.world.plot;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Capability exposed to the prison feature: which plots run the prison add-on and how to toggle it. */
public interface PrisonPlotHost {
    String PRISON_ADDON_FLAG = "addon.prison";

    /** Plots with the add-on enabled, deterministically ordered by name then id. */
    List<Plot> enabled();

    Plot find(UUID plotId);

    boolean isEnabled(UUID plotId);

    /** Sub-plots of a prison plot, one cell each; already ordered. */
    List<Plot> subplots(UUID plotId);

    /** The top-level plot with the add-on flag containing the block, or null when none does. */
    Plot plotAt(UUID world, int blockX, int blockY, int blockZ);

    boolean canManage(Plot plot, UUID actor, boolean staff);

    /** Toggles the add-on flag on the plot. Requires MANAGE_SETTINGS; disabling is vetoed by guards. */
    void setEnabled(UUID plotId, boolean enabled, UUID actor, boolean staff) throws SQLException;

    /** True when the plot's add-on may be turned off; registered guards can veto. */
    boolean mayToggleOff(Plot plot);

    /** Vetoed only while the add-on is on and a guard objects; used by the plot delete path. */
    boolean mayDelete(Plot plot);

    /** Guards reject toggling off (and deleting) a plot whose add-on is on. */
    void addToggleGuard(java.util.function.Predicate<Plot> guard);
}
