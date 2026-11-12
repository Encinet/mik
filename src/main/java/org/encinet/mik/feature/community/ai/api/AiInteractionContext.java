package org.encinet.mik.feature.community.ai.api;

import java.util.List;
import java.util.Objects;

/** Trusted descriptions of the current entry point; these do not grant tool permissions. */
public record AiInteractionContext(String surface, BindingStatus binding, List<Operation> operations) {
    public AiInteractionContext {
        surface = Objects.requireNonNull(surface, "surface");
        binding = Objects.requireNonNull(binding, "binding");
        operations = List.copyOf(operations);
        if (surface.isBlank() || surface.length() > 100 || operations.size() > 32) {
            throw new IllegalArgumentException("invalid interaction context");
        }
    }

    public static AiInteractionContext game() {
        return new AiInteractionContext("Minecraft", BindingStatus.NOT_APPLICABLE, List.of());
    }

    public enum BindingStatus { BOUND, UNBOUND, UNAVAILABLE, NOT_APPLICABLE }

    public record Operation(String invocation, String description) {
        public Operation {
            invocation = Objects.requireNonNull(invocation, "invocation");
            description = Objects.requireNonNull(description, "description");
            if (invocation.isBlank() || invocation.length() > 256 || description.length() > 1_000) {
                throw new IllegalArgumentException("invalid operation description");
            }
        }
    }
}
