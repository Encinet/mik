package org.encinet.mik.feature.world.heads.catalog;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable identity and appearance. A saved entry may outlive its catalog or profile. */
public record HeadEntry(String id, String name, String texture, String category, Set<String> tags,
                        Set<String> collections) {
    private static final String SLUG = "[a-z0-9]+(?:[-_][a-z0-9]+)*";
    private static final Pattern ID = Pattern.compile("(?:[1-9][0-9]{0,9}|custom:" + SLUG
            + "|player:[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})");
    private static final Pattern TEXTURE = Pattern.compile("[a-f0-9]{1,64}");
    private static final Pattern FACET = Pattern.compile(SLUG);

    public HeadEntry {
        if (!validId(id)) throw new IllegalArgumentException("Invalid head ID: " + id);
        if (name == null || name.isBlank() || name.length() > 160 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid name for head " + id);
        if (texture != null && !validTexture(texture)) throw new IllegalArgumentException("Invalid texture for head " + id);
        if (category != null) requireFacet(category);
        tags = facets(tags);
        collections = facets(collections);
    }

    public static HeadEntry player(UUID id, String name, String texture) {
        return new HeadEntry("player:" + id, name, texture, null, null, null);
    }

    public static HeadEntry saved(String id, String name) { return new HeadEntry(id, name, null, null, null, null); }

    public UUID playerId() { return id.startsWith("player:") ? UUID.fromString(id.substring(7)) : null; }

    public static boolean validId(String id) { return id != null && id.length() <= 72 && ID.matcher(id).matches(); }
    public static boolean validTexture(String hash) { return hash != null && TEXTURE.matcher(hash).matches(); }

    static void requireFacet(String key) {
        if (key == null || key.length() > 100 || !FACET.matcher(key).matches())
            throw new IllegalArgumentException("Invalid head catalog label ID: " + key);
    }

    private static Set<String> facets(Set<String> values) {
        if (values == null) return Set.of();
        if (values.size() > 100) throw new IllegalArgumentException("Too many head catalog labels on one entry");
        values.forEach(HeadEntry::requireFacet);
        return Set.copyOf(values);
    }
}
