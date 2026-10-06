package org.encinet.mik.module.ai.conversation;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.encinet.mik.module.ai.api.AiRequestException;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolTrace;
import org.encinet.mik.module.ai.tool.AiToolCatalog;
import org.encinet.mik.module.ai.tool.AiToolRegistry;
import org.encinet.mik.module.ai.tool.AiToolCall;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** Owns bounded conversations, concurrency, and the model/tool call loop. */
public final class AiConversationService implements AutoCloseable {
    private static final String TOOL_LIMIT_RESULT = """
            {"ok":false,"error":"tool_round_limit","message":"No more tool calls are allowed; answer using the results already available."}
            """.strip();

    private final AiCompletionClient client;
    private final AiConfig config;
    private final Object historiesLock = new Object();
    private final LinkedHashMap<String, Deque<AiChatMessage>> histories =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Set<String> activeConversations = ConcurrentHashMap.newKeySet();
    private final Semaphore capacity;
    private volatile boolean closed;

    public AiConversationService(AiCompletionClient client, AiConfig config) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        capacity = new Semaphore(config.limits().maxConcurrentRequests());
    }

    public CompletableFuture<String> ask(
            String conversationId,
            String requesterName,
            String language,
            String prompt,
            AiToolCatalog tools
    ) {
        return ask(conversationId, requesterName, language, prompt, null, tools);
    }

    public CompletableFuture<String> ask(
            String conversationId,
            String requesterName,
            String language,
            String prompt,
            String memoryContext,
            AiToolCatalog tools
    ) {
        return askDetailed(conversationId, requesterName, language, prompt,
                memoryContext, tools).thenApply(AiTurnResult::answer);
    }

    public CompletableFuture<AiTurnResult> askDetailed(
            String conversationId,
            String requesterName,
            String language,
            String prompt,
            String memoryContext,
            AiToolCatalog tools
    ) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(tools, "tools");
        String question = Objects.requireNonNullElse(prompt, "").strip();
        if (closed) {
            return rejected(AiRequestException.Reason.CLOSED, "AI service is closed");
        }
        if (question.isEmpty()) {
            return rejected(AiRequestException.Reason.PROMPT_EMPTY, "Prompt is empty");
        }
        if (question.length() > config.limits().maxPromptCharacters()) {
            return rejected(AiRequestException.Reason.PROMPT_TOO_LONG,
                    "Prompt exceeds " + config.limits().maxPromptCharacters() + " characters");
        }
        if (!activeConversations.add(conversationId)) {
            return rejected(AiRequestException.Reason.CONVERSATION_BUSY,
                    "This conversation already has a request in progress");
        }
        if (!capacity.tryAcquire()) {
            activeConversations.remove(conversationId);
            return rejected(AiRequestException.Reason.SERVER_BUSY,
                    "The AI request limit has been reached");
        }

        AiToolRegistry registry = new AiToolRegistry(tools,
                config.limits().maxToolOutputCharacters());
        List<AiChatMessage> messages = initialMessages(
                conversationId, requesterName, language, question, memoryContext);
        CompletableFuture<AiTurnResult> request;
        try {
            request = continueConversation(messages, registry, 0)
                    .thenApply(this::normalizeAnswer)
                    .thenApply(answer -> appendKnowledgeSources(
                            answer, language, registry.trace()))
                    .thenApply(answer -> {
                        remember(conversationId, question, answer);
                        return new AiTurnResult(question, answer, language, registry.trace(),
                                memoryContext != null && !memoryContext.isBlank());
                    });
        } catch (RuntimeException error) {
            request = CompletableFuture.failedFuture(error);
        }
        return request.whenComplete((ignored, error) -> {
            capacity.release();
            activeConversations.remove(conversationId);
        });
    }

    public void clear(String conversationId) {
        synchronized (historiesLock) {
            histories.remove(Objects.requireNonNull(conversationId, "conversationId"));
        }
    }

    public int conversationCount() {
        synchronized (historiesLock) {
            return histories.size();
        }
    }

    private List<AiChatMessage> initialMessages(
            String conversationId,
            String requesterName,
            String language,
            String question,
            String memoryContext
    ) {
        List<AiChatMessage> messages = new ArrayList<>();
        messages.add(AiChatMessage.system(config.systemPrompt(language)));
        String safeRequester = Objects.requireNonNullElse(requesterName, "unknown")
                .replaceAll("[\\p{Cntrl}]", " ").strip();
        if (safeRequester.length() > 64) {
            safeRequester = safeRequester.substring(0, 64);
        }
        JsonObject requestContext = new JsonObject();
        requestContext.addProperty("requester", safeRequester);
        requestContext.addProperty("preferred_interface_language",
                Objects.requireNonNullElse(language, "default"));
        messages.add(AiChatMessage.system("REQUEST_CONTEXT_DATA " + requestContext));
        messages.add(AiChatMessage.system(AiKnowledgeInstructions.forLanguage(language)));
        synchronized (historiesLock) {
            Deque<AiChatMessage> history = histories.get(conversationId);
            if (history != null) {
                messages.addAll(history);
            }
        }
        messages.add(AiChatMessage.user(question));
        if (memoryContext != null && !memoryContext.isBlank()) {
            String callId = "host_memory_recall";
            messages.add(AiChatMessage.assistant(null, List.of(
                    new AiToolCall(callId, "memory_recall", "{}"))));
            messages.add(AiChatMessage.tool(callId, memoryContext));
        }
        return messages;
    }

    private CompletableFuture<String> continueConversation(
            List<AiChatMessage> messages,
            AiToolRegistry registry,
            int completedToolRounds
    ) {
        List<AiTool> available = config.limits().maxToolRounds() == 0
                ? List.of() : registry.definitions();
        return client.complete(List.copyOf(messages), available).thenCompose(reply -> {
            if (reply.toolCalls().isEmpty()) {
                return textResponse(reply);
            }
            messages.add(reply);
            if (completedToolRounds >= config.limits().maxToolRounds()) {
                reply.toolCalls().forEach(call -> messages.add(
                        AiChatMessage.tool(call.id(), TOOL_LIMIT_RESULT)));
                return client.complete(List.copyOf(messages), List.of())
                        .thenCompose(this::textResponse);
            }

            return registry.executeAll(reply.toolCalls())
                    .thenCompose(outputs -> {
                        for (int index = 0; index < outputs.size(); index++) {
                            messages.add(AiChatMessage.tool(
                                    reply.toolCalls().get(index).id(),
                                    outputs.get(index)));
                        }
                        return continueConversation(messages, registry,
                                completedToolRounds + 1);
                    });
        });
    }

    private CompletableFuture<String> textResponse(AiChatMessage reply) {
        if (reply.content() == null || reply.content().isBlank()) {
            return rejected(AiRequestException.Reason.EMPTY_RESPONSE,
                    "Provider did not return a text response");
        }
        return CompletableFuture.completedFuture(reply.content());
    }

    private String normalizeAnswer(String response) {
        String answer = response.replace("\u0000", "").strip();
        int maximum = config.limits().maxResponseCharacters();
        return answer.length() <= maximum ? answer
                : answer.substring(0, maximum) + "\n…";
    }

    private String appendKnowledgeSources(
            String answer,
            String language,
            List<AiToolTrace> trace
    ) {
        if (answer.contains("knowledge://")) {
            return answer;
        }
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        for (AiToolTrace event : trace) {
            if (!event.tool().equals("search_knowledge")) {
                continue;
            }
            try {
                JsonObject output = JsonParser.parseString(event.output()).getAsJsonObject();
                JsonElement value = output.get("results");
                if (value == null || !value.isJsonArray()) {
                    continue;
                }
                for (JsonElement result : value.getAsJsonArray()) {
                    if (!result.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = result.getAsJsonObject();
                    String uri = primitive(item, "uri");
                    String markdown = primitive(item, "citation_markdown");
                    if (!uri.isBlank() && !markdown.isBlank()) {
                        sources.putIfAbsent(uri, markdown);
                    }
                }
            } catch (RuntimeException ignored) {
                // A malformed or truncated tool result cannot become a citation.
            }
        }
        if (sources.isEmpty()) {
            return answer;
        }
        String header = sourceHeader(language);
        return answer + "\n\n" + header + "\n" + sources.values().stream()
                .limit(8).map(value -> "- " + value)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String primitive(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static String sourceHeader(String language) {
        String normalized = Objects.requireNonNullElse(language, "en_us")
                .toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "zh_cn" -> "参考资料：";
            case "zh_hk", "zh_tw" -> "參考資料：";
            case "lzh" -> "所據：";
            case "de_de" -> "Quellen:";
            case "es_es" -> "Fuentes:";
            case "fr_fr" -> "Sources :";
            case "it_it" -> "Fonti:";
            case "ja_jp" -> "参考資料：";
            case "ko_kr" -> "참고 자료:";
            case "nl_nl" -> "Bronnen:";
            case "pt_br" -> "Fontes:";
            case "ru_ru" -> "Источники:";
            case "th_th" -> "แหล่งอ้างอิง:";
            case "uk_ua" -> "Джерела:";
            default -> "Sources:";
        };
    }

    private void remember(String conversationId, String question, String answer) {
        int maximum = config.limits().maxHistoryMessages();
        if (closed || maximum == 0) {
            return;
        }
        synchronized (historiesLock) {
            Deque<AiChatMessage> history = histories.computeIfAbsent(
                    conversationId, ignored -> new ArrayDeque<>());
            history.addLast(AiChatMessage.user(question));
            history.addLast(AiChatMessage.assistant(answer));
            while (history.size() > maximum) {
                history.removeFirst();
            }
            while (histories.size() > config.limits().maxConversations()) {
                var oldest = histories.entrySet().iterator();
                oldest.next();
                oldest.remove();
            }
        }
    }

    private static <T> CompletableFuture<T> rejected(
            AiRequestException.Reason reason,
            String message
    ) {
        return CompletableFuture.failedFuture(new AiRequestException(reason, message));
    }

    @Override
    public void close() {
        closed = true;
        synchronized (historiesLock) {
            histories.clear();
        }
    }
}
