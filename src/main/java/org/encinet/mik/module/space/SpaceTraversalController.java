package org.encinet.mik.module.space;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Bridges the immutable topology to Paper movement for independent entities and vehicle trees. */
final class SpaceTraversalController implements Listener, AutoCloseable {

    private static final long WARNING_INTERVAL_MILLIS = 30_000L;
    private static final double SAME_POSITION_EPSILON_SQUARED = 1.0E-8;

    private final JavaPlugin plugin;
    private final Supplier<SpaceNetwork> network;
    private final Map<String, Long> warningTimes = new HashMap<>();
    private final Set<UUID> applyingRoots = new HashSet<>();

    SpaceTraversalController(JavaPlugin plugin, Supplier<SpaceNetwork> network) {
        this.plugin = plugin;
        this.network = network;
    }

    void networkChanged() {
        warningTimes.clear();
        applyingRoots.clear();
    }

    @Override
    public void close() {
        warningTimes.clear();
        applyingRoots.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !event.hasExplicitlyChangedPosition()) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getVehicle() != null) {
            return;
        }
        traverse(player, event.getFrom(), to, new MovementControl() {
            @Override
            public boolean moveTo(Location destination) {
                // Paper implements a changed PlayerMoveEvent destination by performing
                // another teleport after listeners return. That teleport uses a zero
                // velocity transition and would erase the mapped vector applied below.
                // Move here instead, then SpaceEntityTree writes the transformed velocity
                // after the relocation has completed in this same tick.
                return player.teleport(
                        destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            @Override
            public boolean reject() {
                event.setCancelled(true);
                return true;
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {
        if (!event.hasExplicitlyChangedPosition()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.getVehicle() != null) {
            return;
        }
        traverse(entity, event.getFrom(), event.getTo(), new MovementControl() {
            @Override
            public boolean moveTo(Location destination) {
                // A Paper same-world entity teleport recursively positions the complete
                // passenger tree in the same operation. Merely changing EntityMoveEvent's
                // destination would snap only this living root after listeners return and
                // leave mounted players waiting for a later seat-position update.
                return entity.teleport(
                        destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            @Override
            public boolean reject() {
                event.setCancelled(true);
                return true;
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        // Rideable living entities are already handled by EntityMoveEvent.
        if (vehicle instanceof LivingEntity || vehicle.getVehicle() != null) {
            return;
        }
        Location source = event.getFrom();
        traverse(vehicle, source, event.getTo(), new MovementControl() {
            @Override
            public boolean moveTo(Location destination) {
                return vehicle.teleport(
                        destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            @Override
            public boolean reject() {
                SpaceEntityTree.capture(vehicle).stop();
                Location current = vehicle.getLocation();
                if (samePosition(current, source)) {
                    return true;
                }
                return vehicle.teleport(
                        source, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        });
    }

    private void traverse(
            Entity root,
            Location from,
            Location to,
            MovementControl movementControl
    ) {
        if (to.getWorld() == null
                || root.getVehicle() != null
                || applyingRoots.contains(root.getUniqueId())) {
            return;
        }

        Optional<SpaceTransition> traced = trace(
                to.getWorld(), point(from), point(to),
                direction(to), vector(root.getVelocity()));
        if (traced.isEmpty()) {
            return;
        }
        SpaceTransition transition = traced.get();

        EntitySpaceTraverseEvent traverseEvent = new EntitySpaceTraverseEvent(root, transition);
        Bukkit.getPluginManager().callEvent(traverseEvent);
        if (traverseEvent.isCancelled()) {
            reject(root, movementControl, transition.linkId() + ":cancelled");
            return;
        }

        World destinationWorld = SpaceWorldResolver.resolve(transition.destinationWorld());
        if (destinationWorld == null || !destinationWorld.equals(to.getWorld())) {
            reject(root, movementControl, transition.linkId() + ":world");
            warn(transition.linkId() + ":world",
                    "Spatial link '" + transition.linkId()
                            + "' cannot resolve both surfaces to the entity's current world");
            return;
        }

        Location destination = location(
                destinationWorld, transition.position(), transition.lookDirection());
        if (!destinationWorld.getWorldBorder().isInside(destination)) {
            reject(root, movementControl, transition.linkId() + ":border");
            warn(transition.linkId() + ":border",
                    "Spatial link '" + transition.linkId()
                            + "' resolves outside the " + destinationWorld.getName()
                            + " world border");
            return;
        }

        int chunkX = destination.getBlockX() >> 4;
        int chunkZ = destination.getBlockZ() >> 4;
        if (!destinationWorld.isChunkLoaded(chunkX, chunkZ)) {
            reject(root, movementControl, transition.linkId() + ":chunk");
            warn(transition.linkId() + ":chunk",
                    "Spatial link '" + transition.linkId()
                            + "' is waiting for its destination chunk to become ready");
            return;
        }

        SpaceEntityTree entityTree;
        try {
            entityTree = SpaceEntityTree.capture(root);
        } catch (RuntimeException error) {
            reject(root, movementControl, transition.linkId() + ":passengers");
            warn(transition.linkId() + ":passengers",
                    "Spatial link '" + transition.linkId()
                            + "' rejected an invalid passenger hierarchy: " + error.getMessage());
            return;
        }
        SpaceEntityShape entityShape = entityTree.collisionShape();
        Optional<SpaceAperturePlacement.Result> fitted = SpaceAperturePlacement.fit(
                transition.transform().destination(),
                transition.destinationAperture(),
                transition.entryPosition(),
                transition.position(),
                entityShape);
        if (fitted.isEmpty()) {
            reject(root, movementControl, transition.linkId() + ":aperture-fit");
            warn(transition.linkId() + ":aperture-fit",
                    "Spatial link '" + transition.linkId()
                            + "' cannot fit entity " + root.getUniqueId()
                            + " inside destination surface '"
                            + transition.destinationSurfaceId() + "'");
            return;
        }
        SpaceAperturePlacement.Result placement = fitted.get();
        SpaceVector exitDirection = transition.transform().destination().forward();
        Optional<SpaceMovementResolver.Result> resolved = SpaceMovementResolver.resolve(
                placement.entry(),
                placement.desired(),
                exitDirection,
                entityShape.entryClearance(exitDirection),
                stepHeight(root),
                position -> collides(root, destinationWorld, position,
                        transition.lookDirection()));
        if (resolved.isEmpty()) {
            reject(root, movementControl, transition.linkId() + ":entry-collision");
            warn(transition.linkId() + ":entry-collision",
                    "Spatial link '" + transition.linkId()
                            + "' has no collision-free entry on surface '"
                            + transition.destinationSurfaceId() + "'");
            return;
        }

        SpaceMovementResolver.Result movement = resolved.get();
        Location resolvedDestination = location(
                destinationWorld, movement.position(), transition.lookDirection());

        applyingRoots.add(root.getUniqueId());
        try {
            if (!movementControl.moveTo(resolvedDestination)) {
                movementControl.reject();
                warn(transition.linkId() + ":move-failed",
                        "Spatial link '" + transition.linkId()
                                + "' could not move entity " + root.getUniqueId());
                return;
            }
            entityTree.apply(transition, movement);
            SpaceRemoteEntitySnap.resetInterpolation(entityTree);
        } finally {
            applyingRoots.remove(root.getUniqueId());
        }
    }

    private void reject(Entity root, MovementControl control, String warningKey) {
        if (!applyingRoots.add(root.getUniqueId())) {
            return;
        }
        try {
            if (!control.reject()) {
                warn(warningKey + ":rollback-failed",
                        "Could not hold entity " + root.getUniqueId()
                                + " at a blocked spatial link");
            }
        } finally {
            applyingRoots.remove(root.getUniqueId());
        }
    }

    private boolean collides(
            Entity entity,
            World world,
            SpaceVector position,
            SpaceVector look
    ) {
        return !(entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)
                && entity.collidesAt(location(world, position, look));
    }

    private double stepHeight(Entity entity) {
        if (!(entity instanceof LivingEntity living)) {
            return 0.0;
        }
        var attribute = living.getAttribute(Attribute.STEP_HEIGHT);
        if (attribute != null) {
            return Math.max(0.0, attribute.getValue());
        }
        return entity instanceof Player ? 0.6 : 0.0;
    }

    private Optional<SpaceTransition> trace(
            World world,
            SpaceVector from,
            SpaceVector to,
            SpaceVector look,
            SpaceVector velocity
    ) {
        SpaceNetwork snapshot = network.get();
        Optional<SpaceTransition> transition = snapshot.trace(
                world.getName(), from, to, look, velocity);
        if (transition.isPresent()) {
            return transition;
        }
        transition = snapshot.trace(
                world.getKey().asString(), from, to, look, velocity);
        if (transition.isPresent()) {
            return transition;
        }
        return snapshot.trace(
                world.getUID().toString(), from, to, look, velocity);
    }

    private Location location(
            World world,
            SpaceVector position,
            SpaceVector look
    ) {
        Location result = new Location(world, position.x(), position.y(), position.z());
        result.setDirection(new Vector(look.x(), look.y(), look.z()));
        return result;
    }

    private SpaceVector point(Location location) {
        return new SpaceVector(location.getX(), location.getY(), location.getZ());
    }

    private SpaceVector direction(Location location) {
        return vector(location.getDirection());
    }

    private SpaceVector vector(Vector vector) {
        return new SpaceVector(vector.getX(), vector.getY(), vector.getZ());
    }

    private boolean samePosition(Location first, Location second) {
        return first.getWorld() != null
                && first.getWorld().equals(second.getWorld())
                && first.distanceSquared(second) <= SAME_POSITION_EPSILON_SQUARED;
    }

    private void warn(String key, String message) {
        long now = System.currentTimeMillis();
        Long previous = warningTimes.get(key);
        if (previous != null && now - previous < WARNING_INTERVAL_MILLIS) {
            return;
        }
        warningTimes.put(key, now);
        plugin.getLogger().warning(message);
    }

    private interface MovementControl {

        boolean moveTo(Location destination);

        boolean reject();
    }
}
