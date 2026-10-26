package org.encinet.mik.feature.world.plot.integration;

import com.fastasyncworldedit.core.configuration.Settings;
import com.fastasyncworldedit.core.util.TaskManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import org.bukkit.World;
import org.encinet.mik.feature.world.plot.PlotRegistry;
import org.encinet.mik.shared.role.RolePermissions;

/** Installs protection in FAWE's queue, including edits that bypass history. */
public final class PlotFaweHook {
    private final PlotRegistry registry;
    private boolean registered;
    private boolean ownsExtentAllowance;

    public PlotFaweHook(PlotRegistry registry) {
        this.registry = registry;
    }

    public void enable() {
        if (registered) return;
        allowFallbackExtent();
        WorldEdit.getInstance().getEventBus().register(this);
        registered = true;
    }

    private void allowFallbackExtent() {
        // FAWE filters custom extents. Allow only our fallback for edits without a chunk queue.
        var allowed = Settings.settings().EXTENT.ALLOWED_PLUGINS;
        if (!allowed.contains(PlotFaweProcessor.EXTENT_NAME)) {
            allowed.add(PlotFaweProcessor.EXTENT_NAME);
            ownsExtentAllowance = true;
        }
    }

    public void disable() {
        if (registered) {
            WorldEdit.getInstance().getEventBus().unregister(this);
            registered = false;
        }
        if (ownsExtentAllowance) {
            Settings.settings().EXTENT.ALLOWED_PLUGINS.remove(PlotFaweProcessor.EXTENT_NAME);
            ownsExtentAllowance = false;
        }
    }

    @Subscribe
    public void onSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE
                || event.getActor() == null || !event.getActor().isPlayer() || event.getWorld() == null) return;
        PlotFaweProcessor processor = TaskManager.taskManager().sync(() -> {
            // A FAWE configuration reload can replace its in-memory allowance list.
            allowFallbackExtent();
            World world = BukkitAdapter.adapt(event.getWorld());
            if (world == null) return null;
            boolean staff = event.getActor().hasPermission(RolePermissions.MODERATOR)
                    || event.getActor().hasPermission(RolePermissions.CUSTODIAN);
            boolean member = staff || event.getActor().hasPermission(RolePermissions.MEMBER);
            return new PlotFaweProcessor(registry, world.getUID(), event.getActor().getUniqueId(), member, staff);
        });
        if (processor != null) event.setExtent(event.getExtent().addProcessor(processor));
    }
}
