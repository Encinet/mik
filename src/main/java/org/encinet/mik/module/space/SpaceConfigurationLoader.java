package org.encinet.mik.module.space;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict, all-or-nothing loader for the immersive surface/link format. */
final class SpaceConfigurationLoader {

    static final int FORMAT_VERSION = 3;

    private static final Set<String> ROOT_KEYS =
            Set.of("version", "enabled", "surfaces", "links");
    private static final Set<String> SURFACE_KEYS =
            Set.of("world", "area", "through", "up");
    private static final Set<String> AREA_KEYS = Set.of("corner-a", "corner-b");
    private static final Set<String> LINK_KEYS = Set.of("connect", "entrances");

    SpaceConfiguration load(Path path)
            throws IOException, InvalidConfigurationException, SpaceConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        return parse(yaml);
    }

    SpaceConfiguration parse(ConfigurationSection root) throws SpaceConfigurationException {
        List<String> problems = new ArrayList<>();
        rejectUnknownKeys(root, ROOT_KEYS, "root", problems);
        Object rawVersion = root.get("version");
        boolean validVersion = rawVersion instanceof Number number
                && Double.compare(number.doubleValue(), FORMAT_VERSION) == 0;
        if (!validVersion) {
            problems.add("version must be " + FORMAT_VERSION + " (found " + rawVersion
                    + "); older spatial formats are intentionally unsupported");
        }

        Map<String, SpaceSurface> surfaces = parseSurfaces(
                requiredMap(root, "surfaces", "root", problems), problems);
        List<SpaceLink> links = parseLinks(
                requiredMap(root, "links", "root", problems), surfaces, problems);
        boolean enabled = optionalBoolean(root, "enabled", true, "root", problems);
        validateEverySurfaceIsLinked(surfaces, links, problems);
        if (!problems.isEmpty()) {
            throw new SpaceConfigurationException(problems);
        }
        return new SpaceConfiguration(enabled, links);
    }

    private Map<String, SpaceSurface> parseSurfaces(
            ConfigurationSection root,
            List<String> problems
    ) {
        Map<String, SpaceSurface> surfaces = new LinkedHashMap<>();
        if (root == null) {
            return surfaces;
        }
        for (String id : root.getKeys(false)) {
            String path = "surfaces." + id;
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                problems.add(path + " must be a map");
                continue;
            }
            rejectUnknownKeys(section, SURFACE_KEYS, path, problems);
            int before = problems.size();
            String world = requiredText(section, "world", path, problems);
            ConfigurationSection area = requiredMap(section, "area", path, problems);
            SpaceVector cornerA = area == null ? null : vector(
                    area.getList("corner-a"), 3, path + ".area.corner-a", problems);
            SpaceVector cornerB = area == null ? null : vector(
                    area.getList("corner-b"), 3, path + ".area.corner-b", problems);
            if (area != null) {
                rejectUnknownKeys(area, AREA_KEYS, path + ".area", problems);
            }
            SpaceVector through = direction(
                    section.get("through"), path + ".through", problems);
            SpaceVector up = section.contains("up")
                    ? direction(section.get("up"), path + ".up", problems)
                    : defaultUp(through);
            if (problems.size() != before) {
                continue;
            }
            try {
                surfaces.put(id, SpaceSurface.betweenCorners(
                        id, world, cornerA, cornerB, through, up));
            } catch (IllegalArgumentException error) {
                problems.add(path + ": " + error.getMessage());
            }
        }
        return surfaces;
    }

    private List<SpaceLink> parseLinks(
            ConfigurationSection root,
            Map<String, SpaceSurface> surfaces,
            List<String> problems
    ) {
        List<SpaceLink> links = new ArrayList<>();
        Map<String, String> surfaceOwners = new LinkedHashMap<>();
        if (root == null) {
            return links;
        }
        for (String id : root.getKeys(false)) {
            String path = "links." + id;
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                problems.add(path + " must be a map");
                continue;
            }
            rejectUnknownKeys(section, LINK_KEYS, path, problems);
            List<?> connected = section.getList("connect");
            if (connected == null || connected.size() != 2
                    || !(connected.get(0) instanceof String firstId)
                    || !(connected.get(1) instanceof String secondId)) {
                problems.add(path + ".connect must contain exactly two surface ids");
                continue;
            }
            SpaceSurface first = surfaces.get(firstId);
            SpaceSurface second = surfaces.get(secondId);
            if (first == null) {
                problems.add(path + ".connect references unknown surface '" + firstId + "'");
            }
            if (second == null) {
                problems.add(path + ".connect references unknown surface '" + secondId + "'");
            }
            if (first == null || second == null) {
                continue;
            }
            SpaceLinkEntrances entrances = entrances(
                    section.get("entrances"), path + ".entrances", problems);
            if (entrances == null) {
                continue;
            }
            SpaceLink link;
            try {
                link = new SpaceLink(id, first, second, entrances);
            } catch (IllegalArgumentException error) {
                problems.add(path + ": " + error.getMessage());
                continue;
            }
            boolean firstAvailable = surfaceAvailable(
                    firstId, surfaceOwners, path, problems);
            boolean secondAvailable = surfaceAvailable(
                    secondId, surfaceOwners, path, problems);
            if (!firstAvailable || !secondAvailable) {
                continue;
            }
            surfaceOwners.put(firstId, id);
            surfaceOwners.put(secondId, id);
            links.add(link);
        }
        return links;
    }

    private boolean surfaceAvailable(
            String surface,
            Map<String, String> owners,
            String path,
            List<String> problems
    ) {
        String previous = owners.get(surface);
        if (previous == null) {
            return true;
        }
        problems.add(path + " reuses surface '" + surface
                + "' already connected by links." + previous);
        return false;
    }

    private void validateEverySurfaceIsLinked(
            Map<String, SpaceSurface> surfaces,
            List<SpaceLink> links,
            List<String> problems
    ) {
        Set<String> linked = new HashSet<>();
        for (SpaceLink link : links) {
            linked.add(link.first().id());
            linked.add(link.second().id());
        }
        for (String surface : surfaces.keySet()) {
            if (!linked.contains(surface)) {
                problems.add("surfaces." + surface + " is not connected by any link");
            }
        }
    }

    private boolean optionalBoolean(
            ConfigurationSection section,
            String key,
            boolean fallback,
            String path,
            List<String> problems
    ) {
        if (!section.contains(key)) {
            return fallback;
        }
        Object raw = section.get(key);
        if (raw instanceof Boolean value) {
            return value;
        }
        problems.add(path + "." + key + " must be true or false");
        return fallback;
    }

    private SpaceVector vector(
            List<?> values,
            int dimensions,
            String path,
            List<String> problems
    ) {
        if (values == null || values.size() != dimensions) {
            problems.add(path + " must be a list of " + dimensions + " finite numbers");
            return null;
        }
        double[] parsed = new double[3];
        for (int index = 0; index < dimensions; index++) {
            Object raw = values.get(index);
            if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                problems.add(path + " must be a list of " + dimensions + " finite numbers");
                return null;
            }
            parsed[index] = number.doubleValue();
        }
        return new SpaceVector(parsed[0], parsed[1], parsed[2]);
    }

    private SpaceVector direction(Object raw, String path, List<String> problems) {
        if (raw instanceof String cardinal) {
            SpaceVector direction = cardinalDirection(cardinal);
            if (direction == null) {
                problems.add(path
                        + " must be north, south, east, west, up, down, or [x, y, z]");
            }
            return direction;
        }
        if (raw instanceof List<?> values) {
            return vector(values, 3, path, problems);
        }
        problems.add(path + " must be a cardinal direction or [x, y, z]");
        return null;
    }

    private SpaceLinkEntrances entrances(
            Object raw,
            String path,
            List<String> problems
    ) {
        if (!(raw instanceof String value)) {
            problems.add(path + " must be first or both");
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "first" -> SpaceLinkEntrances.FIRST;
            case "both" -> SpaceLinkEntrances.BOTH;
            default -> {
                problems.add(path + " must be first or both");
                yield null;
            }
        };
    }

    private ConfigurationSection requiredMap(
            ConfigurationSection parent,
            String key,
            String path,
            List<String> problems
    ) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            problems.add(path + "." + key + " must be a map");
        }
        return section;
    }

    private String requiredText(
            ConfigurationSection section,
            String key,
            String path,
            List<String> problems
    ) {
        Object raw = section.get(key);
        if (!(raw instanceof String value) || value.isBlank()) {
            problems.add(path + "." + key + " must be non-blank text");
            return "";
        }
        return value.trim();
    }

    private void rejectUnknownKeys(
            ConfigurationSection section,
            Set<String> allowed,
            String path,
            List<String> problems
    ) {
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) {
                problems.add(path + " contains unknown key '" + key + "'");
            }
        }
    }

    private SpaceVector cardinalDirection(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> new SpaceVector(0.0, 0.0, -1.0);
            case "south" -> new SpaceVector(0.0, 0.0, 1.0);
            case "east" -> new SpaceVector(1.0, 0.0, 0.0);
            case "west" -> new SpaceVector(-1.0, 0.0, 0.0);
            case "up" -> new SpaceVector(0.0, 1.0, 0.0);
            case "down" -> new SpaceVector(0.0, -1.0, 0.0);
            default -> null;
        };
    }

    private SpaceVector defaultUp(SpaceVector through) {
        if (through != null && through.lengthSquared() > 0.0
                && Math.abs(through.normalized().y()) > 0.99) {
            return new SpaceVector(0.0, 0.0, -1.0);
        }
        return new SpaceVector(0.0, 1.0, 0.0);
    }
}
