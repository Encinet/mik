package org.encinet.mik.module.ai.knowledge.application;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.encinet.mik.module.ai.api.AiRequest;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.conversation.AiChatMessage;
import org.encinet.mik.module.ai.tool.AiToolTrace;
import org.encinet.mik.module.ai.conversation.AiTurnResult;
import org.encinet.mik.module.ai.knowledge.adapter.markdown.MarkdownKnowledgeRepository;
import org.encinet.mik.module.ai.knowledge.adapter.sqlite.KnowledgeStateStore;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeHit;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeSource;
import org.encinet.mik.module.ai.conversation.AiCompletionClient;
import org.encinet.mik.module.ai.runtime.AiWorkerPool;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolCall;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Non-blocking post-turn extraction followed by bounded, autonomous Markdown curation. */
public final class KnowledgeLearningService implements AutoCloseable {
    private static final Pattern SECRET = Pattern.compile(
            "(?is)(-----BEGIN [^-\\r\\n]{0,40}PRIVATE KEY-----|"
                    + "\\b(?:api[_ -]?key|access[_ -]?token|password|secret)"
                    + "\\s*[:=]\\s*\\S{6,})");
    private static final Set<String> PRIVATE_EVIDENCE_TOOLS = Set.of(
            "get_my_inventory", "get_player_info", "get_player_statistics",
            "get_nearby_entities");

    private final KnowledgeService knowledge;
    private final AiCompletionClient client;
    private final AiConfig.Knowledge config;
    private final Logger logger;
    private final KnowledgeStateStore state;
    private final AiWorkerPool extractors;
    private final AiWorkerPool curationWorker;
    private final ScheduledThreadPoolExecutor scheduler;
    private final AtomicBoolean curating = new AtomicBoolean();
    private final AtomicInteger droppedCandidates = new AtomicInteger();
    private final AtomicInteger quarantinedCandidates = new AtomicInteger();
    private volatile boolean closed;
    private volatile Instant lastCuration;
    private volatile String lastFailure = "";

    public KnowledgeLearningService(
            Path pluginDataDirectory,
            KnowledgeService knowledge,
            AiCompletionClient client,
            AiConfig.Knowledge config,
            Logger logger
    ) {
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge");
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.logger = Objects.requireNonNull(logger, "logger");
        state = new KnowledgeStateStore(pluginDataDirectory);
        AiConfig.KnowledgeLearning learning = config.learning();
        extractors = new AiWorkerPool("mik-ai-knowledge-extract-",
                learning.maxConcurrentExtractions(), learning.candidateQueueCapacity());
        curationWorker = new AiWorkerPool("mik-ai-knowledge-curate-", 1, 1);
        scheduler = new ScheduledThreadPoolExecutor(
                1, timerThread("mik-ai-knowledge-timer"));
        scheduler.setRemoveOnCancelPolicy(true);
        long interval = learning.curationInterval().toMinutes();
        scheduler.scheduleWithFixedDelay(
                () -> curationWorker.tryExecute(this::curateSafely),
                interval, interval, TimeUnit.MINUTES);
    }

    /** Queues an ephemeral turn snapshot and returns immediately. */
    public boolean capture(AiRequest request, AiTurnResult turn) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(turn, "turn");
        if (closed || !config.learning().enabled()) {
            return false;
        }
        return extractors.tryExecute(() -> extract(request, turn));
    }

    public CompletableFuture<CurateResult> curateNow() {
        if (closed) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Knowledge learning is closed"));
        }
        return curationWorker.submit(this::curateBatch);
    }

    public Status status() {
        AiWorkerPool.Status extraction = extractors.status();
        return new Status(knowledge.repository().candidateCount(),
                extraction.queued(), extraction.running(), extraction.dropped(),
                droppedCandidates.get(), quarantinedCandidates.get(),
                curating.get(), Optional.ofNullable(lastCuration), lastFailure);
    }

    private void extract(AiRequest request, AiTurnResult turn) {
        if (closed) {
            return;
        }
        try {
            JsonObject input = new JsonObject();
            input.addProperty("has_bound_player", request.playerId().isPresent());
            input.addProperty("public_learning_allowed", publicLearningAllowed(turn));
            input.addProperty("request_language", turn.language());
            input.addProperty("question", bounded(turn.question(), 4_000));
            input.addProperty("answer", bounded(turn.answer(), 12_000));
            JsonArray tools = new JsonArray();
            int evidenceBudget = 12_000;
            for (AiToolTrace trace : turn.tools()) {
                if (evidenceBudget <= 0) {
                    break;
                }
                JsonObject item = new JsonObject();
                item.addProperty("tool", trace.tool());
                String output = bounded(trace.output(), Math.min(4_000, evidenceBudget));
                item.addProperty("output", output);
                evidenceBudget -= output.length();
                tools.add(item);
            }
            input.add("tool_evidence", tools);
            AiChatMessage reply = await(client.complete(List.of(
                    AiChatMessage.system(KnowledgeLearningPrompts.extraction(turn.language())),
                    AiChatMessage.user(input.toString())),
                    List.of(new SubmitCandidatesTool(config.learning()
                            .maxCandidatesPerTurn()))));
            List<KnowledgeDocument> candidates = candidates(
                    reply.toolCalls(), request, turn);
            for (KnowledgeDocument candidate : candidates) {
                if (knowledge.repository().candidateCount()
                        >= config.learning().candidateQueueCapacity()) {
                    droppedCandidates.incrementAndGet();
                    break;
                }
                knowledge.repository().enqueueCandidate(candidate);
            }
            state.state("last_extraction_at", Instant.now().toString());
        } catch (RuntimeException error) {
            recordFailure("extraction", error);
        }
    }

    private List<KnowledgeDocument> candidates(
            List<AiToolCall> calls,
            AiRequest request,
            AiTurnResult turn
    ) {
        List<KnowledgeDocument> result = new ArrayList<>();
        int maximum = config.learning().maxCandidatesPerTurn();
        boolean publicAllowed = publicLearningAllowed(turn);
        for (AiToolCall call : calls) {
            if (!call.name().equals("submit_learning_candidates")) {
                continue;
            }
            JsonObject arguments = json(call.arguments());
            JsonElement serialized = arguments.get("candidates");
            if (serialized == null || !serialized.isJsonArray()) {
                continue;
            }
            for (JsonElement element : serialized.getAsJsonArray()) {
                if (result.size() >= maximum || !element.isJsonObject()) {
                    break;
                }
                JsonObject candidate = element.getAsJsonObject();
                KnowledgeScope scope = "user".equalsIgnoreCase(string(candidate, "scope"))
                        ? KnowledgeScope.USER : KnowledgeScope.PUBLIC;
                if (scope == KnowledgeScope.USER && request.playerId().isEmpty()) {
                    continue;
                }
                if (scope == KnowledgeScope.PUBLIC && !publicAllowed) {
                    continue;
                }
                String body = bounded(string(candidate, "body"), 16_000)
                        .replace("\u0000", "").strip();
                String title = bounded(string(candidate, "title"), 200).strip();
                if (title.isEmpty() || body.isEmpty() || SECRET.matcher(body).find()) {
                    continue;
                }
                if (scope == KnowledgeScope.PUBLIC
                        && !request.requesterName().isBlank()
                        && body.toLowerCase(Locale.ROOT).contains(
                        request.requesterName().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Instant now = Instant.now();
                result.add(new KnowledgeDocument(
                        "candidate-" + UUID.randomUUID(),
                        scope,
                        scope == KnowledgeScope.USER
                                ? request.playerId() : Optional.empty(),
                        title,
                        fallback(string(candidate, "language"), turn.language()),
                        strings(candidate.get("aliases"), 12, 200),
                        strings(candidate.get("tags"), 12, 64),
                        fallback(string(candidate, "kind"),
                                scope == KnowledgeScope.USER ? "profile" : "note"),
                        sources(candidate.get("sources"), uri -> turn.tools().stream()
                                .anyMatch(trace -> trace.output().contains(uri.toString())
                                        || trace.output().contains(uri.toASCIIString()))),
                        false, 1, now, now,
                        optionalInstant(string(candidate, "expires_at")), body));
            }
        }
        return List.copyOf(result);
    }

    private static boolean publicLearningAllowed(AiTurnResult turn) {
        return !turn.privateMemoryUsed() && turn.tools().stream()
                .map(AiToolTrace::tool).noneMatch(PRIVATE_EVIDENCE_TOOLS::contains);
    }

    private void curateSafely() {
        try {
            curateBatch();
        } catch (RuntimeException error) {
            recordFailure("curation", error);
        }
    }

    private CurateResult curateBatch() {
        if (!curating.compareAndSet(false, true)) {
            return new CurateResult(0, 0, 0, 0, 0, true);
        }
        int processed = 0;
        int applied = 0;
        int skipped = 0;
        int failed = 0;
        int quarantined = 0;
        try {
            List<MarkdownKnowledgeRepository.CandidateEntry> candidates =
                    knowledge.repository().candidates(
                            config.learning().curationBatchSize());
            for (MarkdownKnowledgeRepository.CandidateEntry candidate : candidates) {
                if (closed) {
                    break;
                }
                processed++;
                try {
                    CurationOutcome outcome = curate(candidate);
                    state.removeState(retryKey(candidate));
                    if (outcome == CurationOutcome.APPLIED) {
                        applied++;
                    } else if (outcome == CurationOutcome.SKIPPED) {
                        skipped++;
                    }
                } catch (RuntimeException error) {
                    failed++;
                    recordFailure("curation candidate "
                            + candidate.document().id(), error);
                    if (retryOrQuarantine(candidate)) {
                        quarantined++;
                    }
                }
            }
            lastCuration = Instant.now();
            if (failed == 0) {
                lastFailure = "";
            }
            state.state("last_curation_at", lastCuration.toString());
            return new CurateResult(processed, applied, skipped, failed, quarantined, false);
        } finally {
            curating.set(false);
        }
    }

    private boolean retryOrQuarantine(MarkdownKnowledgeRepository.CandidateEntry candidate) {
        String key = retryKey(candidate);
        try {
            int attempts = state.state(key).map(value -> {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }).orElse(0) + 1;
            if (attempts < 3) {
                state.state(key, Integer.toString(attempts));
                return false;
            }
            knowledge.repository().rejectCandidate(candidate);
            state.removeState(key);
            quarantinedCandidates.incrementAndGet();
            return true;
        } catch (RuntimeException failure) {
            logger.log(Level.FINE,
                    "Could not update failed knowledge candidate state", failure);
            return false;
        }
    }

    private static String retryKey(MarkdownKnowledgeRepository.CandidateEntry candidate) {
        return "candidate_attempts:" + candidate.document().subjectKey();
    }

    private CurationOutcome curate(
            MarkdownKnowledgeRepository.CandidateEntry entry
    ) {
        KnowledgeDocument candidate = entry.document();
        List<KnowledgeHit> related = candidate.scope() == KnowledgeScope.PUBLIC
                ? knowledge.searchPublic(candidateQueries(candidate), candidate.tags(), 6)
                : knowledge.recall(candidate.owner().orElseThrow(),
                String.join(" ", candidateQueries(candidate)), 6);
        JsonObject input = new JsonObject();
        input.addProperty("scope", candidate.scope().name().toLowerCase(Locale.ROOT));
        candidate.owner().ifPresent(owner -> input.addProperty("owner", owner.toString()));
        input.add("candidate", documentJson(candidate, 16_000));
        JsonArray existing = new JsonArray();
        Set<String> allowedTargets = new LinkedHashSet<>();
        Map<String, KnowledgeSource> allowedSources = new LinkedHashMap<>();
        candidate.sources().forEach(source -> allowedSources.put(
                source.url().normalize().toASCIIString(), source));
        for (KnowledgeHit hit : related) {
            Optional<KnowledgeDocument> document = candidate.scope() == KnowledgeScope.PUBLIC
                    ? knowledge.repository().findPublic(hit.documentId())
                    : knowledge.repository().findUser(
                    candidate.owner().orElseThrow(), hit.documentId());
            document.ifPresent(value -> {
                allowedTargets.add(value.id());
                existing.add(documentJson(value, 6_000));
                value.sources().forEach(source -> allowedSources.putIfAbsent(
                        source.url().normalize().toASCIIString(), source));
            });
        }
        input.add("related_documents", existing);

        AiChatMessage reply = await(client.complete(List.of(
                AiChatMessage.system(KnowledgeLearningPrompts.curation(
                        candidate.language())), AiChatMessage.user(input.toString())),
                List.of(new ApplyKnowledgeChangeTool())));
        AiToolCall call = reply.toolCalls().stream()
                .filter(value -> value.name().equals("apply_knowledge_change"))
                .findFirst().orElse(null);
        if (call == null) {
            knowledge.repository().completeCandidate(entry);
            return CurationOutcome.SKIPPED;
        }
        JsonObject change = json(call.arguments());
        if ("skip".equalsIgnoreCase(string(change, "action"))) {
            knowledge.repository().completeCandidate(entry);
            return CurationOutcome.SKIPPED;
        }
        if (!"upsert".equalsIgnoreCase(string(change, "action"))) {
            throw new IllegalArgumentException("Unknown knowledge curation action");
        }

        String targetId = normalizedId(fallback(string(change, "target_id"),
                slug(string(change, "title"))));
        Optional<KnowledgeDocument> current = candidate.scope() == KnowledgeScope.PUBLIC
                ? knowledge.repository().findPublic(targetId)
                : knowledge.repository().findUser(candidate.owner().orElseThrow(), targetId);
        if (current.isPresent() && !allowedTargets.contains(targetId)) {
            throw new IllegalArgumentException(
                    "Curation attempted to update an unrelated document");
        }
        if (current.isEmpty() && candidate.scope() == KnowledgeScope.USER
                && userDocumentCount(candidate.owner().orElseThrow())
                >= config.memory().maxDocumentsPerPlayer()) {
            knowledge.repository().completeCandidate(entry);
            return CurationOutcome.SKIPPED;
        }
        String body = bounded(fallback(string(change, "body"), candidate.body()),
                100_000).replace("\u0000", "").strip();
        if (body.isEmpty() || SECRET.matcher(body).find()) {
            throw new IllegalArgumentException("Curated knowledge failed content validation");
        }
        Instant now = Instant.now();
        List<KnowledgeSource> curatedSources = change.has("sources")
                ? sources(change.get("sources"), uri -> allowedSources.containsKey(
                uri.normalize().toASCIIString()))
                : inheritedSources(candidate, current);
        KnowledgeDocument requested = new KnowledgeDocument(
                targetId, candidate.scope(), candidate.owner(),
                fallback(string(change, "title"), candidate.title()),
                fallback(string(change, "language"), candidate.language()),
                fallbackStrings(change.get("aliases"), candidate.aliases(), 16, 200),
                fallbackStrings(change.get("tags"), candidate.tags(), 16, 64),
                fallback(string(change, "kind"), candidate.kind()),
                curatedSources,
                current.map(KnowledgeDocument::protectedDocument).orElse(false),
                current.map(KnowledgeDocument::revision).orElse(1L),
                current.map(KnowledgeDocument::createdAt).orElse(now), now,
                optionalInstant(string(change, "expires_at"))
                        .or(() -> candidate.expiresAt()), body);
        KnowledgeDocument saved = knowledge.save(requested,
                current.map(KnowledgeDocument::revision).orElse(0L));
        state.audit(current.isPresent() ? "update" : "create", saved,
                "automatic curation");

        for (String archiveId : strings(change.get("archive_ids"), 8, 128)) {
            if (archiveId.equals(saved.id()) || !allowedTargets.contains(archiveId)) {
                continue;
            }
            Optional<KnowledgeDocument> archived = candidate.scope() == KnowledgeScope.PUBLIC
                    ? knowledge.repository().findPublic(archiveId)
                    : knowledge.repository().findUser(
                    candidate.owner().orElseThrow(), archiveId);
            if (archived.isPresent() && !archived.orElseThrow().protectedDocument()) {
                KnowledgeDocument value = archived.orElseThrow();
                knowledge.archive(value.scope(), value.owner(), value.id(), value.revision());
                state.audit("archive", value, "merged into " + saved.id());
            }
        }
        knowledge.repository().completeCandidate(entry);
        return CurationOutcome.APPLIED;
    }

    private int userDocumentCount(UUID owner) {
        return (int) knowledge.repository().scan().stream()
                .filter(value -> value.scope() == KnowledgeScope.USER)
                .filter(value -> value.owner().filter(owner::equals).isPresent()).count();
    }

    private static List<String> candidateQueries(KnowledgeDocument candidate) {
        List<String> result = new ArrayList<>();
        result.add(candidate.title());
        result.addAll(candidate.aliases());
        if (result.size() < 4) {
            result.add(bounded(candidate.body(), 300));
        }
        return result.stream().filter(value -> !value.isBlank()).distinct().limit(4).toList();
    }

    private static List<KnowledgeSource> inheritedSources(
            KnowledgeDocument candidate,
            Optional<KnowledgeDocument> current
    ) {
        LinkedHashMap<String, KnowledgeSource> result = new LinkedHashMap<>();
        current.stream().flatMap(value -> value.sources().stream()).forEach(source ->
                result.put(source.url().normalize().toASCIIString(), source));
        candidate.sources().forEach(source -> result.putIfAbsent(
                source.url().normalize().toASCIIString(), source));
        return List.copyOf(result.values());
    }

    private static JsonObject documentJson(
            KnowledgeDocument document,
            int maximumBodyCharacters
    ) {
        JsonObject result = new JsonObject();
        result.addProperty("id", document.id());
        result.addProperty("title", document.title());
        result.addProperty("language", document.language());
        result.add("aliases", new com.google.gson.Gson().toJsonTree(document.aliases()));
        result.add("tags", new com.google.gson.Gson().toJsonTree(document.tags()));
        result.addProperty("kind", document.kind());
        result.addProperty("protected", document.protectedDocument());
        result.addProperty("revision", document.revision());
        if (maximumBodyCharacters > 0) {
            result.addProperty("body", bounded(document.body(), maximumBodyCharacters));
        }
        JsonArray sources = new JsonArray();
        for (KnowledgeSource source : document.sources()) {
            JsonObject item = new JsonObject();
            item.addProperty("title", source.title());
            item.addProperty("url", source.url().toASCIIString());
            sources.add(item);
        }
        result.add("sources", sources);
        return result;
    }

    private void recordFailure(String operation, RuntimeException error) {
        lastFailure = operation + ": " + Objects.requireNonNullElse(
                error.getMessage(), error.getClass().getSimpleName());
        try {
            state.state("last_failure", bounded(lastFailure, 500));
        } catch (RuntimeException stateFailure) {
            error.addSuppressed(stateFailure);
        }
        logger.log(Level.FINE, "AI knowledge " + operation + " failed", error);
    }

    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
        curationWorker.close();
        extractors.close();
        state.close();
    }

    private static ThreadFactory timerThread(String name) {
        return task -> {
            Thread thread = Thread.ofPlatform().unstarted(task);
            thread.setDaemon(true);
            thread.setName(name);
            return thread;
        };
    }

    private static JsonObject json(String value) {
        try {
            return JsonParser.parseString(value).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            throw new IllegalArgumentException("Model returned invalid tool arguments", error);
        }
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CompletionException("Knowledge worker was interrupted", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new CompletionException(cause);
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString().strip() : "";
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value;
    }

    private static List<String> fallbackStrings(
            JsonElement value,
            List<String> fallback,
            int maximum,
            int maximumCharacters
    ) {
        List<String> parsed = strings(value, maximum, maximumCharacters);
        return parsed.isEmpty() ? fallback : parsed;
    }

    private static List<String> strings(
            JsonElement value,
            int maximum,
            int maximumCharacters
    ) {
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (result.size() >= maximum) {
                break;
            }
            if (item.isJsonPrimitive()) {
                String text = bounded(item.getAsString(), maximumCharacters).strip();
                if (!text.isEmpty() && !result.contains(text)) {
                    result.add(text);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<KnowledgeSource> sources(
            JsonElement value,
            Predicate<URI> allowed
    ) {
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        List<KnowledgeSource> result = new ArrayList<>();
        for (JsonElement item : value.getAsJsonArray()) {
            if (result.size() >= 12 || !item.isJsonObject()) {
                continue;
            }
            JsonObject source = item.getAsJsonObject();
            String url = string(source, "url");
            if (url.isBlank()) {
                continue;
            }
            try {
                URI parsed = URI.create(url).normalize();
                if (allowed.test(parsed)) {
                    result.add(new KnowledgeSource(string(source, "title"), parsed));
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid model-supplied sources are discarded without rejecting the candidate.
            }
        }
        return List.copyOf(result);
    }

    private static Optional<Instant> optionalInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static String normalizedId(String value) {
        String id = Objects.requireNonNullElse(value, "").strip()
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (id.isEmpty()) {
            id = "learned-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return id.length() <= 128 ? id : id.substring(0, 128);
    }

    private static String slug(String title) {
        return normalizedId(Objects.requireNonNullElse(title, ""));
    }

    private static String bounded(String value, int maximum) {
        String text = Objects.requireNonNullElse(value, "");
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private abstract static class DefinitionOnlyTool implements AiTool {
        @Override
        public String description() {
            return "Submit the required structured result.";
        }

        @Override
        public CompletableFuture<String> execute(JsonObject arguments) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "Internal structured-output tools are not executable"));
        }

        protected static JsonObject objectSchema() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.addProperty("additionalProperties", false);
            schema.add("properties", new JsonObject());
            return schema;
        }

        protected static JsonObject stringProperty() {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "string");
            return schema;
        }

        protected static JsonObject stringArray(int maximum) {
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "array");
            schema.addProperty("maxItems", maximum);
            schema.add("items", stringProperty());
            return schema;
        }
    }

    private static final class SubmitCandidatesTool extends DefinitionOnlyTool {
        private final int maximum;

        private SubmitCandidatesTool(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public String name() {
            return "submit_learning_candidates";
        }

        @Override
        public JsonObject parameters() {
            JsonObject candidate = objectSchema();
            JsonObject fields = candidate.getAsJsonObject("properties");
            JsonObject scope = stringProperty();
            JsonArray scopes = new JsonArray();
            scopes.add("public");
            scopes.add("user");
            scope.add("enum", scopes);
            fields.add("scope", scope);
            for (String name : List.of("title", "language", "kind", "body", "expires_at")) {
                fields.add(name, stringProperty());
            }
            fields.add("aliases", stringArray(12));
            fields.add("tags", stringArray(12));
            JsonObject source = objectSchema();
            source.getAsJsonObject("properties").add("title", stringProperty());
            source.getAsJsonObject("properties").add("url", stringProperty());
            JsonObject sources = new JsonObject();
            sources.addProperty("type", "array");
            sources.addProperty("maxItems", 12);
            sources.add("items", source);
            fields.add("sources", sources);
            JsonArray requiredCandidate = new JsonArray();
            for (String required : List.of("scope", "title", "language", "kind", "body")) {
                requiredCandidate.add(required);
            }
            candidate.add("required", requiredCandidate);
            JsonObject candidates = new JsonObject();
            candidates.addProperty("type", "array");
            candidates.addProperty("minItems", 1);
            candidates.addProperty("maxItems", maximum);
            candidates.add("items", candidate);
            JsonObject root = objectSchema();
            root.getAsJsonObject("properties").add("candidates", candidates);
            JsonArray required = new JsonArray();
            required.add("candidates");
            root.add("required", required);
            return root;
        }
    }

    private static final class ApplyKnowledgeChangeTool extends DefinitionOnlyTool {
        @Override
        public String name() {
            return "apply_knowledge_change";
        }

        @Override
        public JsonObject parameters() {
            JsonObject root = objectSchema();
            JsonObject fields = root.getAsJsonObject("properties");
            JsonObject action = stringProperty();
            JsonArray actions = new JsonArray();
            actions.add("skip");
            actions.add("upsert");
            action.add("enum", actions);
            fields.add("action", action);
            for (String name : List.of("target_id", "title", "language", "kind",
                    "body", "expires_at")) {
                fields.add(name, stringProperty());
            }
            fields.add("aliases", stringArray(16));
            fields.add("tags", stringArray(16));
            fields.add("archive_ids", stringArray(8));
            JsonObject source = objectSchema();
            source.getAsJsonObject("properties").add("title", stringProperty());
            source.getAsJsonObject("properties").add("url", stringProperty());
            JsonObject sources = new JsonObject();
            sources.addProperty("type", "array");
            sources.addProperty("maxItems", 12);
            sources.add("items", source);
            fields.add("sources", sources);
            JsonArray required = new JsonArray();
            required.add("action");
            root.add("required", required);
            return root;
        }
    }

    private enum CurationOutcome {
        APPLIED,
        SKIPPED
    }

    public record CurateResult(
            int processed,
            int applied,
            int skipped,
            int failed,
            int quarantined,
            boolean alreadyRunning
    ) {
    }

    public record Status(
            int persistedCandidates,
            int queuedExtractions,
            int activeExtractions,
            int droppedExtractions,
            int droppedCandidates,
            int quarantinedCandidates,
            boolean curating,
            Optional<Instant> lastCuration,
            String lastFailure
    ) {
    }
}
