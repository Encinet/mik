package org.encinet.mik.module.plot.integration;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.World;
import org.encinet.mik.module.role.RolePermissions;
import org.encinet.mik.module.plot.PlotRegistry;
import org.encinet.mik.module.plot.protection.PlotEntityEditActions;

import java.util.UUID;

/** Wraps both WorldEdit stages so history, raw changes and normal edits obey protection. */
public final class PlotWorldEditHook {
    private final PlotRegistry registry;

    public PlotWorldEditHook(PlotRegistry registry) {
        this.registry = registry;
    }

    public void enable() { WorldEdit.getInstance().getEventBus().register(this); }
    public void disable() { WorldEdit.getInstance().getEventBus().unregister(this); }

    @Subscribe
    public void onSession(EditSessionEvent event) {
        if (event.getActor() == null || !event.getActor().isPlayer() || event.getWorld() == null) return;
        if (event.getStage() != EditSession.Stage.BEFORE_HISTORY
                && event.getStage() != EditSession.Stage.BEFORE_CHANGE) return;
        World world = BukkitAdapter.adapt(event.getWorld());
        if (world == null) return;
        UUID actor = event.getActor().getUniqueId();
        boolean staff = event.getActor().hasPermission(RolePermissions.MODERATOR)
                || event.getActor().hasPermission(RolePermissions.CUSTODIAN);
        boolean member = staff || event.getActor().hasPermission(RolePermissions.MEMBER);
        event.setExtent(new Guard(event.getExtent(), world.getUID(), actor, member, staff));
    }

    private final class Guard extends AbstractDelegateExtent {
        private final UUID world;
        private final UUID actor;
        private final boolean member;
        private final boolean staff;

        Guard(Extent parent, UUID world, UUID actor, boolean member,
              boolean staff) {
            super(parent);
            this.world = world;
            this.actor = actor;
            this.member = member;
            this.staff = staff;
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 point, T block)
                throws WorldEditException {
            if (!registry.allowed(world, point.x(), point.y(), point.z(), actor,
                    member, staff, "build")) return false;
            return super.setBlock(point, block);
        }

        @Override
        public boolean setBiome(BlockVector3 point, com.sk89q.worldedit.world.biome.BiomeType biome) {
            return registry.allowed(world, point.x(), point.y(), point.z(), actor,
                    member, staff, "build")
                    && super.setBiome(point, biome);
        }

        @Override
        public com.sk89q.worldedit.entity.Entity createEntity(com.sk89q.worldedit.util.Location location,
                                                                com.sk89q.worldedit.entity.BaseEntity entity) {
            BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            String type = entity.getType().id();
            String permission = PlotEntityEditActions.placement(type).key();
            return registry.allowed(world, point.x(), point.y(), point.z(), actor,
                    member, staff, permission)
                    ? super.createEntity(location, entity) : null;
        }
    }
}
