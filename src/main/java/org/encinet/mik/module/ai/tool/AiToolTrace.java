package org.encinet.mik.module.ai.tool;

import java.util.Objects;

/** Bounded request-local evidence from one executed model tool call. */
public record AiToolTrace(String tool, String output) {
    public AiToolTrace {
        tool = Objects.requireNonNull(tool, "tool");
        output = Objects.requireNonNullElse(output, "");
    }
}
