package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Marker;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

record VehicleDisplayGroup(UUID root, Map<UUID, UUID> parents, List<Display> displays, Location origin) {
    private static final int MAXIMUM_MEMBERS = 256;
    private record Member(Entity entity, UUID parent) { }

    VehicleDisplayGroup {
        parents = Collections.unmodifiableMap(new LinkedHashMap<>(parents));
        displays = List.copyOf(displays);
        origin = origin.clone();
    }

    @Override public Location origin() { return origin.clone(); }

    static VehicleDisplayGroup inspect(Entity seed, Location viewer, Predicate<Entity> excluded) {
        if (seed == null) throw new VehicleImportException(Message.VEHICLE_GROUP_NOT_FOUND);
        Entity root = seed;
        Set<UUID> ancestors = new LinkedHashSet<>();
        while (true) {
            validate(root, viewer, excluded);
            if (!ancestors.add(root.getUniqueId())) throw new VehicleImportException(Message.VEHICLE_GROUP_INVALID);
            if (ancestors.size() > MAXIMUM_MEMBERS) throw new VehicleImportException(Message.VEHICLE_GROUP_LIMIT);
            Entity parent = root.getVehicle();
            if (parent == null) break;
            root = parent;
        }
        UUID rootId = root.getUniqueId();
        Map<UUID, UUID> parents = new LinkedHashMap<>();
        List<Display> displays = new ArrayList<>();
        var pending = new ArrayDeque<Member>();
        pending.add(new Member(root, rootId));
        while (!pending.isEmpty()) {
            Member member = pending.removeFirst();
            Entity entity = member.entity();
            validate(entity, viewer, excluded);
            UUID id = entity.getUniqueId();
            if (parents.putIfAbsent(id, member.parent()) != null) throw new VehicleImportException(Message.VEHICLE_GROUP_INVALID);
            if (!id.equals(rootId)) {
                Entity parent = entity.getVehicle();
                if (parent == null || !parent.getUniqueId().equals(member.parent())) throw new VehicleImportException(Message.VEHICLE_GROUP_INVALID);
            }
            if (entity instanceof Display display) displays.add(display);
            List<Entity> passengers = entity.getPassengers();
            if (parents.size() + pending.size() + passengers.size() > MAXIMUM_MEMBERS || displays.size() > 128)
                throw new VehicleImportException(Message.VEHICLE_GROUP_LIMIT);
            for (Entity passenger : passengers) pending.addLast(new Member(passenger, id));
        }
        if (displays.isEmpty() || !parents.containsKey(seed.getUniqueId())) throw new VehicleImportException(Message.VEHICLE_GROUP_NOT_FOUND);
        Location origin = root.getLocation().clone();
        origin.setPitch(0);
        return new VehicleDisplayGroup(rootId, parents, displays, origin);
    }

    private static void validate(Entity entity, Location viewer, Predicate<Entity> excluded) {
        boolean supported = entity instanceof Display || entity instanceof Marker || entity instanceof Interaction
                || entity instanceof ArmorStand stand && !stand.isVisible();
        Location location = entity.getLocation();
        if (!supported || !entity.isValid() || !entity.getWorld().equals(viewer.getWorld()) || excluded.test(entity)
                || !location.getWorld().equals(viewer.getWorld()) || location.distanceSquared(viewer) > 1024)
            throw new VehicleImportException(Message.VEHICLE_GROUP_INVALID);
    }
}
