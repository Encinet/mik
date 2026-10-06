package org.encinet.mik.module.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable catalog of enabled capability packs for one AI request. */
public final class AiToolCatalog {
    private final List<AiToolPack> packs;
    private final Map<String, AiToolPack> byId;

    public AiToolCatalog(List<AiToolPack> packs) {
        this.packs = List.copyOf(Objects.requireNonNull(packs, "packs"));
        LinkedHashMap<String, AiToolPack> indexed = new LinkedHashMap<>();
        LinkedHashMap<String, String> toolOwners = new LinkedHashMap<>();
        for (AiToolPack pack : packs) {
            if (indexed.putIfAbsent(pack.id(), pack) != null) {
                throw new IllegalArgumentException("Duplicate AI tool pack: " + pack.id());
            }
            for (AiTool tool : pack.tools()) {
                String previous = toolOwners.putIfAbsent(tool.name(), pack.id());
                if (previous != null) {
                    throw new IllegalArgumentException("AI tool " + tool.name()
                            + " belongs to both " + previous + " and " + pack.id());
                }
            }
        }
        byId = Map.copyOf(indexed);
    }

    public List<AiToolPack> packs() {
        return packs;
    }

    public Set<String> packIds() {
        return byId.keySet();
    }
}
