package org.encinet.mik.module.world.regen;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Selects the current block types that an asynchronous regeneration may replace.
 * An empty set means that every block inside the selection is eligible.
 */
public record RegenBlockFilter(Set<Material> materials) {

    public RegenBlockFilter {
        materials = Set.copyOf(materials);
        if (materials.stream().anyMatch(material -> !material.isBlock())) {
            throw new IllegalArgumentException("Regeneration filters may only contain blocks");
        }
    }

    public static RegenBlockFilter all() {
        return new RegenBlockFilter(Set.of());
    }

    public static RegenBlockFilter parse(String input) throws UnknownBlocksException {
        if (input == null || input.isBlank()) {
            return all();
        }

        Set<Material> parsed = new LinkedHashSet<>();
        List<String> unknown = Arrays.stream(input.trim().split("[,\\s]+"))
                .filter(token -> !token.isBlank())
                .filter(token -> {
                    Material material = Material.matchMaterial(token.toLowerCase(Locale.ROOT));
                    if (material == null || !material.isBlock()) {
                        return true;
                    }
                    parsed.add(material);
                    return false;
                })
                .distinct()
                .toList();
        if (!unknown.isEmpty()) {
            throw new UnknownBlocksException(unknown);
        }
        if (parsed.isEmpty()) {
            throw new UnknownBlocksException(List.of(input.trim()));
        }
        return new RegenBlockFilter(parsed);
    }

    public boolean replacesAll() {
        return materials.isEmpty();
    }

    public boolean includes(Material current) {
        return replacesAll() || materials.contains(current);
    }

    public List<String> ids() {
        return materials.stream()
                .map(material -> material.key().asString())
                .sorted()
                .toList();
    }

    public static final class UnknownBlocksException extends Exception {

        private final List<String> blockIds;

        private UnknownBlocksException(List<String> blockIds) {
            super(String.join(", ", blockIds));
            this.blockIds = List.copyOf(blockIds);
        }

        public List<String> blockIds() {
            return blockIds;
        }
    }
}
