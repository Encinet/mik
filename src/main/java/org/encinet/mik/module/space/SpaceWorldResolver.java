package org.encinet.mik.module.space;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;

final class SpaceWorldResolver {

    private SpaceWorldResolver() {
    }

    static World resolve(String reference) {
        World byName = Bukkit.getWorld(reference);
        if (byName != null) {
            return byName;
        }
        try {
            World byId = Bukkit.getWorld(UUID.fromString(reference));
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // Not a UUID; continue with namespaced keys.
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getKey().asString().equalsIgnoreCase(reference)) {
                return world;
            }
        }
        return null;
    }
}
