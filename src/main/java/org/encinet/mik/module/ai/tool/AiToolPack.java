package org.encinet.mik.module.ai.tool;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A skills-like capability pack whose concrete tools can be loaded on demand. */
public record AiToolPack(
        String id,
        String description,
        List<String> keywords,
        List<AiTool> tools
) {
    public AiToolPack {
        id = Objects.requireNonNull(id, "id").strip().toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Invalid AI tool pack id: " + id);
        }
        description = Objects.requireNonNull(description, "description").strip();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("AI tool pack description must not be blank");
        }
        keywords = List.copyOf(Objects.requireNonNullElse(keywords, List.of()));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools"));
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("AI tool pack must contain tools: " + id);
        }
    }
}
