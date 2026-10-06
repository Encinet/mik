package org.encinet.mik.module.plot.integration;

import org.encinet.mik.module.role.RolePermissions;
import org.encinet.mik.module.plot.PlotRegistry;
import org.encinet.mik.module.plot.PlotPermission;
import org.encinet.mik.module.plot.protection.PlotEntityEditActions;

import com.moulberry.axiom.integration.Box;
import com.moulberry.axiom.integration.Integration;
import com.moulberry.axiom.integration.SectionPermissionChecker;
import com.moulberry.axiom.event.AxiomManipulateEntityEvent;
import com.moulberry.axiom.event.AxiomRemoveEntityEvent;
import com.moulberry.axiom.event.AxiomSpawnEntityEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Axiom's section coordinates and callbacks are local 0..15 block coordinates. */
public final class PlotAxiomHook implements Integration.CustomIntegration, Listener {
    private static final Integration.CustomIntegration INACTIVE = new Integration.CustomIntegration() {
        @Override public boolean canBreakBlock(Player player, Block block) { return true; }
        @Override public boolean canPlaceBlock(Player player, Location location) { return true; }
        @Override public SectionPermissionChecker checkSection(Player player, World world,
                                                                int sectionX, int sectionY, int sectionZ) {
            return SectionPermissionChecker.ALL_ALLOWED;
        }
    };

    private final JavaPlugin plugin;
    private final PlotRegistry registry;
    private boolean registered;

    public PlotAxiomHook(JavaPlugin plugin, PlotRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void enable() {
        if (registered) return;
        Integration.registerCustomIntegration(plugin, this);
        registered = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        try {
            if (registered) {
                // Axiom has no unregister API. Replace its retained callback with one
                // that neither restricts editing nor holds the plot registry.
                Integration.registerCustomIntegration(plugin, INACTIVE);
                registered = false;
            }
        } finally {
            HandlerList.unregisterAll(this);
        }
    }

    @Override public boolean canBreakBlock(Player player, Block block) {
        return allowed(player, block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @Override public boolean canPlaceBlock(Player player, Location location) {
        return allowed(player, location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override public SectionPermissionChecker checkSection(Player player, World world,
                                                            int sectionX, int sectionY, int sectionZ) {
        UUID actor = player.getUniqueId();
        UUID worldId = world.getUID();
        boolean staff = RolePermissions.canModerate(player);
        boolean member = RolePermissions.isMember(player);
        return new SectionPermissionChecker() {
            @Override public boolean allAllowed() { return false; }
            @Override public boolean noneAllowed() { return false; }
            @Override public Box bounds() { return SectionPermissionChecker.FULL_BOUNDS; }
            @Override public boolean allowed(int x, int y, int z) {
                return registry.allowed(worldId, sectionX * 16 + x, sectionY * 16 + y,
                        sectionZ * 16 + z, actor, member, staff, "build");
            }
        };
    }

    private boolean allowed(Player player, World world, int x, int y, int z) {
        return registry.allowed(world.getUID(), x, y, z, player.getUniqueId(),
                RolePermissions.isMember(player), RolePermissions.canModerate(player), "build");
    }

    @EventHandler(ignoreCancelled = true)
    public void manipulate(AxiomManipulateEntityEvent event) {
        if (!allowedEntity(event.getPlayer(), event.getEntity(), PlotEntityEditActions.Operation.MODIFY))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void remove(AxiomRemoveEntityEvent event) {
        if (!allowedEntity(event.getPlayer(), event.getEntity(), PlotEntityEditActions.Operation.REMOVE))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void spawn(AxiomSpawnEntityEvent event) {
        if (!allowedEntity(event.getPlayer(), event.getEntity(), PlotEntityEditActions.Operation.PLACE))
            event.setCancelled(true);
    }

    private boolean allowedEntity(Player player, Entity entity, PlotEntityEditActions.Operation operation) {
        Location location = entity.getLocation();
        for (PlotPermission action : PlotEntityEditActions.required(entity, operation)) {
            if (!allowed(player, location.getWorld(), location.getBlockX(), location.getBlockY(),
                    location.getBlockZ(), action.key())) return false;
        }
        return true;
    }

    private boolean allowed(Player player, World world, int x, int y, int z, String flag) {
        return registry.allowed(world.getUID(), x, y, z, player.getUniqueId(),
                RolePermissions.isMember(player), RolePermissions.canModerate(player), flag);
    }
}
