package org.encinet.mik.feature.server.prison;

import org.bukkit.Location;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

record PrisonRecord(String name, UUID owner, PrisonLocation spawn, PrisonLocation exit,
                    PrisonLocation corner1, PrisonLocation corner2, Map<String, PrisonLocation> cells) {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");

    PrisonRecord {
        name = normalize(name);
        if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid prison name: " + name);
        Map<String, PrisonLocation> normalizedCells = new LinkedHashMap<>();
        cells.forEach((cellName, location) -> {
            String key = normalize(cellName);
            if (!NAME.matcher(key).matches() || location == null || normalizedCells.putIfAbsent(key, location) != null)
                throw new IllegalArgumentException("Invalid prison cell: " + cellName);
        });
        cells = Collections.unmodifiableMap(normalizedCells);
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    boolean hasRegion() {
        return corner1 != null && corner2 != null && corner1.world().equals(corner2.world());
    }

    boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !hasRegion()
                || !location.getWorld().getUID().equals(corner1.world())) return false;
        int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
        return x >= Math.min(corner1BlockX(), corner2BlockX()) && x <= Math.max(corner1BlockX(), corner2BlockX())
                && y >= Math.min(corner1BlockY(), corner2BlockY()) && y <= Math.max(corner1BlockY(), corner2BlockY())
                && z >= Math.min(corner1BlockZ(), corner2BlockZ()) && z <= Math.max(corner1BlockZ(), corner2BlockZ());
    }

    PrisonRecord spawn(PrisonLocation value) { return new PrisonRecord(name, owner, value, exit, corner1, corner2, cells); }
    PrisonRecord exit(PrisonLocation value) { return new PrisonRecord(name, owner, spawn, value, corner1, corner2, cells); }
    PrisonRecord corner1(PrisonLocation value) { return new PrisonRecord(name, owner, spawn, exit, value, corner2, cells); }
    PrisonRecord corner2(PrisonLocation value) { return new PrisonRecord(name, owner, spawn, exit, corner1, value, cells); }
    PrisonRecord owner(UUID value) { return new PrisonRecord(name, value, spawn, exit, corner1, corner2, cells); }

    PrisonRecord addCell(String cellName, PrisonLocation location) {
        String key = normalize(cellName);
        if (!NAME.matcher(key).matches() || cells.containsKey(key)) throw new IllegalArgumentException("Invalid or existing cell");
        Map<String, PrisonLocation> updated = new LinkedHashMap<>(cells);
        updated.put(key, location);
        return new PrisonRecord(name, owner, spawn, exit, corner1, corner2, updated);
    }

    PrisonRecord removeCell(String cellName) {
        Map<String, PrisonLocation> updated = new LinkedHashMap<>(cells);
        if (updated.remove(normalize(cellName)) == null) throw new IllegalArgumentException("Cell does not exist");
        return new PrisonRecord(name, owner, spawn, exit, corner1, corner2, updated);
    }

    private int corner1BlockX() { return (int) Math.floor(corner1.x()); }
    private int corner1BlockY() { return (int) Math.floor(corner1.y()); }
    private int corner1BlockZ() { return (int) Math.floor(corner1.z()); }
    private int corner2BlockX() { return (int) Math.floor(corner2.x()); }
    private int corner2BlockY() { return (int) Math.floor(corner2.y()); }
    private int corner2BlockZ() { return (int) Math.floor(corner2.z()); }
}
