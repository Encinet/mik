package org.encinet.mik.module.ai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Request-local tool registry with skills-like progressive pack activation. */
public final class AiToolRegistry {
    private static final String DISCOVERY_TOOL = "tool_search";

    private final AiToolCatalog catalog;
    private final Map<String, AiTool> byName;
    private final Map<String, String> packByTool;
    private final Set<String> activePacks = new LinkedHashSet<>();
    private final int maximumOutputCharacters;
    private final AiTool discoveryTool;
    private final boolean progressive;
    private final List<AiToolTrace> trace = new ArrayList<>();

    public AiToolRegistry(AiToolCatalog catalog, int maximumOutputCharacters) {
        this(catalog, maximumOutputCharacters, false);
    }

    /** Convenience constructor for focused tests or callers that need every tool immediately. */
    public AiToolRegistry(List<AiTool> tools, int maximumOutputCharacters) {
        this(new AiToolCatalog(tools.isEmpty() ? List.of() : List.of(new AiToolPack(
                "direct", "Directly loaded tools", List.of(), tools))),
                maximumOutputCharacters, true);
    }

    private AiToolRegistry(
            AiToolCatalog catalog,
            int maximumOutputCharacters,
            boolean activateEverything
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.maximumOutputCharacters = maximumOutputCharacters;
        LinkedHashMap<String, AiTool> tools = new LinkedHashMap<>();
        LinkedHashMap<String, String> owners = new LinkedHashMap<>();
        for (AiToolPack pack : catalog.packs()) {
            if (activateEverything) {
                activePacks.add(pack.id());
            }
            for (AiTool tool : pack.tools()) {
                tools.put(tool.name(), tool);
                owners.put(tool.name(), pack.id());
            }
        }
        byName = Map.copyOf(tools);
        packByTool = Map.copyOf(owners);
        progressive = !activateEverything && !catalog.packs().isEmpty();
        discoveryTool = new ToolSearchTool();
    }

    public synchronized List<AiTool> definitions() {
        List<AiTool> result = new ArrayList<>();
        if (progressive) {
            result.add(discoveryTool);
        }
        for (AiToolPack pack : catalog.packs()) {
            if (activePacks.contains(pack.id())) {
                result.addAll(pack.tools());
            }
        }
        return List.copyOf(result);
    }

    public CompletableFuture<String> execute(AiToolCall call) {
        AiTool tool;
        synchronized (this) {
            tool = DISCOVERY_TOOL.equals(call.name()) && progressive
                    ? discoveryTool : byName.get(call.name());
            String owner = packByTool.get(call.name());
            if (tool != discoveryTool && (owner == null || !activePacks.contains(owner))) {
                tool = null;
            }
        }
        if (tool == null) {
            return CompletableFuture.completedFuture(error("unknown_or_inactive_tool",
                    "Search and activate the relevant capability pack first"));
        }
        JsonObject arguments;
        try {
            arguments = JsonParser.parseString(call.arguments()).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            return CompletableFuture.completedFuture(error("invalid_arguments",
                    "Tool arguments must be a JSON object"));
        }
        CompletableFuture<String> execution;
        try {
            execution = Objects.requireNonNull(tool.execute(arguments),
                    "Tool returned a null future: " + tool.name());
        } catch (RuntimeException error) {
            execution = CompletableFuture.failedFuture(error);
        }
        return execution.handle((output, failure) -> {
            String result = failure == null
                    ? Objects.requireNonNullElse(output, "null")
                    : error("tool_failed", safeMessage(failure));
            String bounded = result.length() <= maximumOutputCharacters
                    ? result : result.substring(0, maximumOutputCharacters)
                    + "\n[tool output truncated]";
            if (!DISCOVERY_TOOL.equals(call.name())) {
                synchronized (this) {
                    trace.add(new AiToolTrace(call.name(), bounded));
                }
            }
            return bounded;
        });
    }

    public synchronized List<AiToolTrace> trace() {
        return List.copyOf(trace);
    }

    /**
     * Executes one assistant tool-call batch while ensuring discovery calls activate packs
     * before any concrete tools requested in that same batch.
     */
    public CompletableFuture<List<String>> executeAll(List<AiToolCall> calls) {
        List<AiToolCall> requested = List.copyOf(Objects.requireNonNull(calls, "calls"));
        List<CompletableFuture<String>> results = new ArrayList<>(
                Collections.nCopies(requested.size(), null));
        List<CompletableFuture<String>> discovery = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            if (DISCOVERY_TOOL.equals(requested.get(index).name())) {
                CompletableFuture<String> result = execute(requested.get(index));
                results.set(index, result);
                discovery.add(result);
            }
        }
        return CompletableFuture.allOf(discovery.toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> {
                    List<CompletableFuture<String>> concrete = new ArrayList<>();
                    for (int index = 0; index < requested.size(); index++) {
                        if (results.get(index) == null) {
                            CompletableFuture<String> result = execute(requested.get(index));
                            results.set(index, result);
                            concrete.add(result);
                        }
                    }
                    return CompletableFuture.allOf(
                            concrete.toArray(CompletableFuture[]::new));
                }).thenApply(ignored -> results.stream()
                        .map(CompletableFuture::join).toList());
    }

    private synchronized String searchAndActivate(JsonObject arguments) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        JsonElement requestedPacks = arguments.get("packs");
        if (requestedPacks != null && requestedPacks.isJsonArray()) {
            for (JsonElement element : requestedPacks.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    String id = element.getAsString().strip().toLowerCase(Locale.ROOT);
                    if (catalog.packIds().contains(id)) {
                        selected.add(id);
                    }
                }
            }
        }
        String query = string(arguments, "query").toLowerCase(Locale.ROOT);
        if (!query.isBlank()) {
            List<String> tokens = List.of(query.split("\\s+"));
            for (AiToolPack pack : catalog.packs()) {
                String haystack = searchableText(pack);
                if (haystack.contains(query)
                        || tokens.stream().allMatch(haystack::contains)) {
                    selected.add(pack.id());
                }
            }
        }
        boolean fallback = selected.isEmpty();
        if (fallback) {
            catalog.packs().forEach(pack -> selected.add(pack.id()));
        }
        activePacks.addAll(selected);

        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        JsonArray activated = new JsonArray();
        for (AiToolPack pack : catalog.packs()) {
            if (!selected.contains(pack.id())) {
                continue;
            }
            JsonObject packJson = new JsonObject();
            packJson.addProperty("id", pack.id());
            packJson.addProperty("description", pack.description());
            JsonArray tools = new JsonArray();
            pack.tools().forEach(tool -> tools.add(tool.name()));
            packJson.add("tools", tools);
            activated.add(packJson);
        }
        result.add("activated_packs", activated);
        result.addProperty("fallback_loaded_all", fallback);
        result.addProperty("instruction",
                "The listed tools are available on the next model turn; call the one needed.");
        return result.toString();
    }

    private static String searchableText(AiToolPack pack) {
        StringBuilder text = new StringBuilder(pack.id()).append(' ')
                .append(pack.description());
        pack.keywords().forEach(keyword -> text.append(' ').append(keyword));
        pack.tools().forEach(tool -> text.append(' ').append(tool.name())
                .append(' ').append(tool.description()));
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private final class ToolSearchTool implements AiTool {
        @Override
        public String name() {
            return DISCOVERY_TOOL;
        }

        @Override
        public String description() {
            String available = catalog.packs().stream()
                    .map(pack -> pack.id() + " (" + pack.description() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            return "Search and activate skills-like capability packs before using their tools. "
                    + "Available packs: " + available
                    + ". 可按任务搜索并激活对应能力包。";
        }

        @Override
        public JsonObject parameters() {
            JsonObject query = new JsonObject();
            query.addProperty("type", "string");
            query.addProperty("description", "Natural-language capability query");
            JsonObject packItems = new JsonObject();
            packItems.addProperty("type", "string");
            JsonArray values = new JsonArray();
            catalog.packs().forEach(pack -> values.add(pack.id()));
            packItems.add("enum", values);
            JsonObject packs = new JsonObject();
            packs.addProperty("type", "array");
            packs.addProperty("description", "Exact capability pack ids to activate");
            packs.add("items", packItems);
            JsonObject properties = new JsonObject();
            properties.add("query", query);
            properties.add("packs", packs);
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", properties);
            schema.addProperty("additionalProperties", false);
            return schema;
        }

        @Override
        public CompletableFuture<String> execute(JsonObject arguments) {
            return CompletableFuture.completedFuture(searchAndActivate(arguments));
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString().strip() : "";
    }

    private static String error(String code, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.addProperty("error", code);
        result.addProperty("message", message);
        return result.toString();
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        String message = Objects.requireNonNullElse(current.getMessage(),
                current.getClass().getSimpleName());
        message = message.replaceAll("[\\p{Cntrl}]", " ").strip();
        return message.length() <= 200 ? message : message.substring(0, 200) + "…";
    }
}
