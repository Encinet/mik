package org.encinet.mik.module.ai.api;

import java.util.concurrent.CompletableFuture;

/** Shared boundary used by Minecraft commands and external social platforms. */
public interface AiGateway {
    boolean available();

    int maximumPromptCharacters();

    CompletableFuture<String> ask(AiRequest request);

    void clear(String conversationId);
}
