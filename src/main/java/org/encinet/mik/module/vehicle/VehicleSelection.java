package org.encinet.mik.module.vehicle;

import org.encinet.mik.module.i18n.Message;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

record VehicleSelection(Set<UUID> ids, UUID root, Map<UUID, UUID> structure) {
    VehicleSelection {
        ids = Collections.unmodifiableSet(new LinkedHashSet<>(ids));
        structure = Map.copyOf(structure);
    }

    static VehicleSelection manual(Collection<UUID> ids) { return new VehicleSelection(new LinkedHashSet<>(ids), null, Map.of()); }
    static VehicleSelection group(VehicleDisplayGroup group) {
        return new VehicleSelection(new LinkedHashSet<>(group.displays().stream().map(display -> display.getUniqueId()).toList()), group.root(), group.parents());
    }
    void requireUnchanged(VehicleDisplayGroup group) {
        if (!group.root().equals(root) || !group.parents().equals(structure)) throw new VehicleImportException(Message.VEHICLE_GROUP_CHANGED);
    }
}
