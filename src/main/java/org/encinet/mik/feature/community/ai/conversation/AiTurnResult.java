package org.encinet.mik.feature.community.ai.conversation;

import org.encinet.mik.feature.community.ai.tool.AiToolTrace;

import java.util.List;
import java.util.Objects;

/** Final answer plus the ephemeral evidence needed for optional post-turn learning. */
public record AiTurnResult(
        String question,
        String answer,
        String language,
        List<AiToolTrace> tools,
        boolean privateMemoryUsed
) {
    public AiTurnResult {
        question = Objects.requireNonNull(question, "question");
        answer = Objects.requireNonNull(answer, "answer");
        language = Objects.requireNonNullElse(language, "default");
        tools = List.copyOf(Objects.requireNonNullElse(tools, List.of()));
    }
}
