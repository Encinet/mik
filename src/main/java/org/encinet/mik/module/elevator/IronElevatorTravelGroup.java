package org.encinet.mik.module.elevator;

import io.papermc.paper.entity.Leashable;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** A bounded leash and riding component, freshly measured before every trip. */
final class IronElevatorTravelGroup {
    private static final int MAX_MEMBERS = 64;
    private static final int MAX_NEARBY = 1_024;
    private static final double LEASH_MARGIN = 16;

    private record Member(Entity entity, Location origin, Entity holder, Entity vehicle,
                          List<Entity> passengers, Vector velocity, float fallDistance) { }
    private final List<Member> members;
    private final int sourceHeight;

    private IronElevatorTravelGroup(List<Member> members, int sourceHeight) {
        this.members = List.copyOf(members);
        this.sourceHeight = sourceHeight;
    }

    static Block platformAt(Player player, Location feet) {
        Entity root = player;
        Set<UUID> visited = new HashSet<>();
        while (root.isInsideVehicle()) {
            if (!visited.add(root.getUniqueId()) || visited.size() > MAX_MEMBERS) return null;
            root = root.getVehicle();
            if (root == null) return null;
        }
        return IronElevatorLanding.platformAt(root == player ? feet : root.getLocation());
    }

    static IronElevatorTravelGroup collect(Player player, IronElevatorPlatform source) {
        if (source == null) return null;
        BoundingBox area = BoundingBox.of(source.anchor()).expand(LEASH_MARGIN);
        for (var block : source.blocks()) area.union(BoundingBox.of(block).expand(LEASH_MARGIN));
        var nearby = player.getWorld().getNearbyEntities(area);
        if (nearby.size() > MAX_NEARBY) return null;
        Map<UUID, List<Entity>> children = new HashMap<>();
        for (Entity entity : nearby) {
            Entity holder = holder(entity);
            if (holder != null) children.computeIfAbsent(holder.getUniqueId(), ignored -> new ArrayList<>()).add(entity);
        }
        Map<UUID, Member> found = new LinkedHashMap<>();
        ArrayDeque<Entity> pending = new ArrayDeque<>();
        pending.add(player);
        while (!pending.isEmpty()) {
            Entity entity = pending.removeFirst();
            if (found.containsKey(entity.getUniqueId())) continue;
            if (found.size() >= MAX_MEMBERS || !entity.isValid() || entity.isDead()) return null;
            Location origin = entity.getLocation();
            Entity vehicle = entity.getVehicle();
            if (vehicle == null && !source.contains(IronElevatorLanding.platformAt(origin))) return null;
            Entity holder = holder(entity);
            found.put(entity.getUniqueId(), new Member(entity, origin, holder,
                    vehicle, List.copyOf(entity.getPassengers()), entity.getVelocity().clone(), entity.getFallDistance()));
            if (vehicle != null) pending.addLast(vehicle);
            pending.addAll(entity.getPassengers());
            if (holder != null) pending.addLast(holder);
            pending.addAll(children.getOrDefault(entity.getUniqueId(), List.of()));
        }
        // Every riding tree must have a supported root; reject malformed vehicle cycles.
        for (Member member : found.values()) {
            Set<UUID> ancestors = new HashSet<>();
            Entity cursor = member.entity();
            while (cursor != null) {
                if (!ancestors.add(cursor.getUniqueId())) return null;
                cursor = cursor.getVehicle();
            }
        }
        return new IronElevatorTravelGroup(new ArrayList<>(found.values()), source.height());
    }

    Location landing(IronElevatorPlatform target) {
        Location playerLanding = null;
        for (Member member : members) {
            Entity entity = member.entity();
            BoundingBox body = entity.getBoundingBox();
            IronElevatorLanding.Size size = entity instanceof Player player ? IronElevatorModule.size(player)
                    : new IronElevatorLanding.Size(body.getWidthX(), body.getHeight(), body.getWidthZ());
            Location location = member.origin().clone().add(0, target.height() - sourceHeight, 0);
            if (member.vehicle() == null) {
                location = IronElevatorLanding.on(target, member.origin(), size, entity::wouldCollideUsing);
            } else if (!IronElevatorLanding.isSafe(location, size, entity::wouldCollideUsing)) {
                return null;
            }
            if (location == null) return null;
            if (playerLanding == null) playerLanding = location;
        }
        return playerLanding;
    }

    boolean teleport(Location destination, Consumer<String> warning) {
        double deltaY = destination.getY() - members.getFirst().origin().getY();
        List<Member> attempted = new ArrayList<>();
        try {
            for (Member member : members) {
                if (member.vehicle() != null) continue;
                attempted.add(member);
                Location target = member.origin().clone().add(0, deltaY, 0);
                if (!member.entity().teleport(target, TeleportCause.PLUGIN)
                        || !at(member.entity(), target)) {
                    rollback(attempted, warning);
                    return false;
                }
            }
            if (members.stream().anyMatch(member -> !at(member.entity(), member.origin().clone().add(0, deltaY, 0))
                    || !Objects.equals(member.vehicle(), member.entity().getVehicle())
                    || !member.passengers().equals(member.entity().getPassengers()))
                    || !restoreLeashes()) {
                rollback(attempted, warning);
                return false;
            }
            for (Member member : members) {
                member.entity().setVelocity(new Vector());
                member.entity().setFallDistance(0);
            }
            return true;
        } catch (RuntimeException error) {
            rollback(attempted, warning);
            warning.accept("Elevator group teleport failed: " + error.getMessage());
            return false;
        }
    }

    private void rollback(List<Member> attempted, Consumer<String> warning) {
        // Teleports fire plugin events individually; rollback is best effort too.
        for (Member member : attempted.reversed()) {
            try {
                if (!at(member.entity(), member.origin())
                        && (!member.entity().teleport(member.origin(), TeleportCause.PLUGIN)
                        || !at(member.entity(), member.origin()))) {
                    warning.accept("Could not return elevator passenger " + member.entity().getUniqueId());
                }
            } catch (RuntimeException error) {
                warning.accept("Could not return elevator passenger: " + error.getMessage());
            }
        }
        for (Member member : members) {
            try {
                if (member.vehicle() != null && !member.vehicle().equals(member.entity().getVehicle())
                        && !member.vehicle().addPassenger(member.entity())) {
                    warning.accept("Could not restore elevator riding relationship " + member.entity().getUniqueId());
                }
                member.entity().setVelocity(member.velocity());
                member.entity().setFallDistance(member.fallDistance());
            } catch (RuntimeException error) {
                warning.accept("Could not restore elevator passenger state: " + error.getMessage());
            }
        }
        if (!restoreLeashes()) warning.accept("Could not restore all elevator leashes after rollback");
    }

    private boolean restoreLeashes() {
        boolean restored = true;
        for (Member member : members) {
            try {
                if (member.holder() != null && !member.holder().equals(holder(member.entity()))) {
                    if (member.entity() instanceof LivingEntity living) {
                        restored &= living.setLeashHolder(member.holder());
                    } else if (member.entity() instanceof Leashable leashable) {
                        restored &= leashable.setLeashHolder(member.holder());
                    } else {
                        restored = false;
                    }
                }
            } catch (RuntimeException error) {
                restored = false;
            }
        }
        return restored;
    }

    private static boolean at(Entity entity, Location target) {
        Location actual = entity.getLocation();
        return target.getWorld().equals(actual.getWorld()) && actual.distanceSquared(target) < 1.0E-8;
    }

    private static Entity holder(Entity entity) {
        if (entity instanceof LivingEntity living) return living.isLeashed() ? living.getLeashHolder() : null;
        if (entity instanceof Leashable leashable) return leashable.isLeashed() ? leashable.getLeashHolder() : null;
        return null;
    }
}
