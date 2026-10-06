package org.encinet.mik.module.ai.conversation;

import org.encinet.mik.module.ai.tool.AiTool;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Transport boundary for one model completion. */
public interface AiCompletionClient {
    CompletableFuture<AiChatMessage> complete(
            List<AiChatMessage> messages,
            List<AiTool> tools);
}
