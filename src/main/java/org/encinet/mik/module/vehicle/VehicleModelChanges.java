package org.encinet.mik.module.vehicle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class VehicleModelChanges {
    static Map<UUID, VehicleInstance> replace(Map<UUID, VehicleInstance> instances, VehicleDefinition replacement) {
        Map<UUID, VehicleInstance> proposed = new LinkedHashMap<>(instances);
        for (VehicleInstance instance : instances.values()) {
            if (!instance.body.definition.id().equals(replacement.id())) continue;
            requireStopped(instance.body, instance.entities != null && instance.entities.occupied());
            proposed.put(instance.id, instance.snapshot().restore(replacement));
        }
        return proposed;
    }

    static void requireStopped(VehicleBody body, boolean occupied) {
        if (body.engineRunning || body.velocity.length() > 0.1 || body.angularVelocity.length() > 0.1 || occupied)
            throw new IllegalArgumentException("Stop engines and empty vehicles using this model before saving");
    }

    static void requireUnused(Iterable<VehicleInstance> instances, String id) {
        for (VehicleInstance instance : instances) if (instance.body.definition.id().equals(id))
            throw new IllegalArgumentException("Remove all vehicles using this model before deleting it");
    }
}
