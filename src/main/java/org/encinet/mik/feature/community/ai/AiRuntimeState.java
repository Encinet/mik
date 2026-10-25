package org.encinet.mik.feature.community.ai;

import org.encinet.mik.feature.community.ai.config.AiConfig;
import org.encinet.mik.feature.community.ai.conversation.AiConversationService;
import org.encinet.mik.feature.community.ai.knowledge.application.KnowledgeLearningService;
import org.encinet.mik.feature.community.ai.knowledge.application.KnowledgeService;
import org.encinet.mik.feature.community.ai.runtime.AiWorkerPool;
import org.encinet.mik.feature.community.ai.tool.web.SearxngSearchTool;
import org.encinet.mik.feature.community.ai.tool.web.WebFetchTool;

/** One published AI configuration and the resources owned by that generation. */
record AiRuntimeState(
        AiConfig config,
        AiConversationService conversations,
        SearxngSearchTool webSearch,
        WebFetchTool webFetch,
        KnowledgeService knowledge,
        KnowledgeLearningService learning,
        AiWorkerPool workers
) implements AutoCloseable {
    boolean hasWebTools() {
        return webSearch != null || webFetch != null;
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (AutoCloseable resource : new AutoCloseable[]{
                conversations, webSearch, webFetch, learning, workers, knowledge}) {
            if (resource == null) continue;
            try {
                resource.close();
            } catch (Exception | LinkageError error) {
                if (failure == null) {
                    failure = new IllegalStateException("Could not close AI runtime", error);
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) throw failure;
    }
}
