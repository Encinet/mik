package org.encinet.mik.module.ai;

import org.encinet.mik.module.role.RolePermissions;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.encinet.mik.module.ai.api.AiGateway;
import org.encinet.mik.module.ai.api.AiRequest;
import org.encinet.mik.module.ai.api.AiRequestException;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.conversation.AiConversationService;
import org.encinet.mik.module.ai.knowledge.application.KnowledgeService;
import org.encinet.mik.module.ai.knowledge.application.KnowledgeLearningService;
import org.encinet.mik.module.ai.knowledge.tool.KnowledgeToolPack;
import org.encinet.mik.module.ai.provider.OpenAiCompatibleClient;
import org.encinet.mik.module.ai.runtime.AiWorkerPool;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolCatalog;
import org.encinet.mik.module.ai.tool.AiToolPack;
import org.encinet.mik.module.ai.tool.game.AiGameSnapshot;
import org.encinet.mik.module.ai.tool.game.GameToolPacks;
import org.encinet.mik.module.ai.tool.utility.UtilityToolPack;
import org.encinet.mik.module.ai.tool.web.SearxngSearchTool;
import org.encinet.mik.module.ai.tool.web.WebFetchTool;
import org.encinet.mik.module.ai.tool.web.WebToolPack;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/** Bukkit composition root and command surface for the AI assistant. */
public final class AiModule implements Listener, AiGateway {
    private static final String USE_PERMISSION = "mik.ai.use";
    private static final String MANAGE_PERMISSION = RolePermissions.CUSTODIAN;
    private static final String KNOWLEDGE_MANAGE_PERMISSION = "mik.ai.knowledge.manage";
    private static final String MEMORY_MANAGE_PERMISSION = "mik.ai.memory.manage";

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final long startedAtNanos = System.nanoTime();
    private final AiKnowledgeCommands knowledgeCommands;
    private final Map<String, Permission> ownedPermissions = new LinkedHashMap<>();

    private volatile AiRuntimeState runtime;
    private volatile String configurationFailure;

    public AiModule(JavaPlugin plugin, LanguageService languages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.languages = Objects.requireNonNull(languages, "languages");
        this.knowledgeCommands = new AiKnowledgeCommands(plugin, languages, () -> runtime);
    }

    public void enable() {
        registerPermission(KNOWLEDGE_MANAGE_PERMISSION);
        registerPermission(MEMORY_MANAGE_PERMISSION);
        reloadRuntime();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void registerPermission(String name) {
        if (Bukkit.getPluginManager().getPermission(name) == null) {
            Permission permission = new Permission(name, PermissionDefault.OP);
            Bukkit.getPluginManager().addPermission(permission);
            ownedPermissions.put(name, permission);
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        AiRuntimeState previous = runtime;
        runtime = null;
        try {
            if (previous != null) previous.close();
        } finally {
            for (Permission permission : ownedPermissions.values()) {
                if (Bukkit.getPluginManager().getPermission(permission.getName()) == permission) {
                    Bukkit.getPluginManager().removePermission(permission);
                }
            }
            ownedPermissions.clear();
        }
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("ai")
                        .requires(source -> canUse(source.getSender()))
                        .executes(context -> usage(context.getSource().getSender()))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource().getSender())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource().getSender())))
                        .then(Commands.literal("reload")
                                .requires(source -> source.getSender()
                                        .hasPermission(MANAGE_PERMISSION))
                                .executes(context -> reload(context.getSource().getSender())))
                        .then(Commands.literal("knowledge")
                                .requires(source -> canManageKnowledge(source.getSender()))
                                .executes(context -> knowledgeCommands.knowledgeStatus(
                                        context.getSource().getSender()))
                                .then(Commands.literal("status")
                                        .executes(context -> knowledgeCommands.knowledgeStatus(
                                                context.getSource().getSender())))
                                .then(Commands.literal("sync")
                                        .executes(context -> knowledgeCommands.knowledgeSync(
                                                context.getSource().getSender())))
                                .then(Commands.literal("reindex")
                                        .executes(context -> knowledgeCommands.knowledgeSync(
                                                context.getSource().getSender())))
                                .then(Commands.literal("curate")
                                        .executes(context -> knowledgeCommands.knowledgeCurate(
                                                context.getSource().getSender())))
                                .then(Commands.literal("protect")
                                        .then(Commands.argument("document",
                                                        StringArgumentType.word())
                                                .executes(context -> knowledgeCommands.knowledgeProtection(
                                                        context.getSource().getSender(),
                                                        StringArgumentType.getString(
                                                                context, "document"), true))))
                                .then(Commands.literal("unprotect")
                                        .then(Commands.argument("document",
                                                        StringArgumentType.word())
                                                .executes(context -> knowledgeCommands.knowledgeProtection(
                                                        context.getSource().getSender(),
                                                        StringArgumentType.getString(
                                                                context, "document"), false))))
                                .then(Commands.literal("rollback")
                                        .then(Commands.argument("document",
                                                        StringArgumentType.word())
                                                .then(Commands.argument("revision",
                                                                LongArgumentType.longArg(1))
                                                        .executes(context -> knowledgeCommands.knowledgeRollback(
                                                                context.getSource().getSender(),
                                                                StringArgumentType.getString(
                                                                        context, "document"),
                                                                LongArgumentType.getLong(
                                                                        context, "revision")))))))
                        .then(Commands.literal("memory")
                                .requires(source -> canManageMemory(source.getSender()))
                                .then(Commands.literal("list")
                                        .then(Commands.argument("player",
                                                        StringArgumentType.word())
                                                .executes(context -> knowledgeCommands.memoryList(
                                                        context.getSource().getSender(),
                                                        StringArgumentType.getString(
                                                                context, "player")))))
                                .then(Commands.literal("show")
                                        .then(Commands.argument("player",
                                                        StringArgumentType.word())
                                                .then(Commands.argument("document",
                                                                StringArgumentType.word())
                                                        .executes(context -> knowledgeCommands.memoryShow(
                                                                context.getSource().getSender(),
                                                                StringArgumentType.getString(
                                                                        context, "player"),
                                                                StringArgumentType.getString(
                                                                        context, "document"))))))
                                .then(Commands.literal("forget")
                                        .then(Commands.argument("player",
                                                        StringArgumentType.word())
                                                .then(Commands.argument("document",
                                                                StringArgumentType.word())
                                                        .executes(context -> knowledgeCommands.memoryForget(
                                                                context.getSource().getSender(),
                                                                StringArgumentType.getString(
                                                                        context, "player"),
                                                                StringArgumentType.getString(
                                                                        context, "document")))))))
                        .then(Commands.argument("prompt", StringArgumentType.greedyString())
                                .executes(context -> ask(
                                        context.getSource().getSender(),
                                        StringArgumentType.getString(context, "prompt"))))
                        .build(),
                languages.t(Language.DEFAULT, Message.AI_COMMAND_DESCRIPTION)));
    }

    private boolean canUse(CommandSender sender) {
        return sender.hasPermission(USE_PERMISSION)
                || sender.hasPermission(MANAGE_PERMISSION);
    }

    private boolean canManageKnowledge(CommandSender sender) {
        return sender.hasPermission(KNOWLEDGE_MANAGE_PERMISSION)
                || sender.hasPermission(MANAGE_PERMISSION);
    }

    private boolean canManageMemory(CommandSender sender) {
        return sender.hasPermission(MEMORY_MANAGE_PERMISSION)
                || sender.hasPermission(MANAGE_PERMISSION);
    }

    @Override
    public boolean available() {
        AiRuntimeState selected = runtime;
        return selected != null && selected.config().enabled()
                && selected.conversations() != null;
    }

    @Override
    public int maximumPromptCharacters() {
        AiRuntimeState selected = runtime;
        return selected == null ? 0 : selected.config().limits().maxPromptCharacters();
    }

    @Override
    public CompletableFuture<String> ask(AiRequest request) {
        Objects.requireNonNull(request, "request");
        AiRuntimeState selected = runtime;
        if (selected == null || !selected.config().enabled()
                || selected.conversations() == null) {
            return CompletableFuture.failedFuture(new AiRequestException(
                    AiRequestException.Reason.DISABLED, "AI is disabled"));
        }
        String prompt = request.prompt().strip();
        if (prompt.isEmpty()) {
            return CompletableFuture.failedFuture(new AiRequestException(
                    AiRequestException.Reason.PROMPT_EMPTY, "Prompt is empty"));
        }
        if (prompt.length() > selected.config().limits().maxPromptCharacters()) {
            return CompletableFuture.failedFuture(new AiRequestException(
                    AiRequestException.Reason.PROMPT_TOO_LONG, "Prompt is too long"));
        }
        CompletableFuture<AiToolCatalog> catalog = needsGameSnapshot(selected.config())
                ? captureCatalog(selected, request)
                : CompletableFuture.completedFuture(buildCatalog(selected, null));
        CompletableFuture<String> memory = memoryContext(selected, request);
        return catalog.thenCombine(memory, RequestContext::new).thenCompose(context -> {
            if (runtime != selected || !plugin.isEnabled()) {
                return CompletableFuture.failedFuture(new AiRequestException(
                        AiRequestException.Reason.CLOSED, "AI runtime was replaced"));
            }
            return selected.conversations().askDetailed(
                    request.conversationId(), request.requesterName(), request.language(),
                    request.prompt(), context.memory(), context.tools())
                    .thenApply(turn -> {
                        if (selected.learning() != null && runtime == selected) {
                            selected.learning().capture(request, turn);
                        }
                        return turn.answer();
                    });
        }).thenCompose(answer -> runtime == selected
                ? CompletableFuture.completedFuture(answer)
                : CompletableFuture.failedFuture(new AiRequestException(
                AiRequestException.Reason.CLOSED, "AI runtime was replaced")));
    }

    private CompletableFuture<String> memoryContext(AiRuntimeState selected, AiRequest request) {
        if (selected.knowledge() == null
                || !selected.config().knowledge().memory().enabled()
                || request.playerId().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return selected.workers().submit(() -> selected.knowledge().memoryContext(
                request.playerId().orElseThrow(), request.prompt(),
                selected.config().knowledge().memory().maxResults(),
                selected.config().knowledge().memory().maxContextCharacters()).orElse(null));
    }

    @Override
    public void clear(String conversationId) {
        AiRuntimeState selected = runtime;
        if (selected != null && selected.conversations() != null) {
            selected.conversations().clear(conversationId);
        }
    }

    private CompletableFuture<AiToolCatalog> captureCatalog(
            AiRuntimeState selected,
            AiRequest request
    ) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(buildCatalog(selected,
                        AiGameSnapshot.capture(plugin,
                                selected.config().tools().includePlayerLocations(),
                                startedAtNanos, request.playerId().orElse(null))));
            } catch (RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        }
        CompletableFuture<AiToolCatalog> result = new CompletableFuture<>();
        if (!plugin.isEnabled()) {
            result.completeExceptionally(new AiRequestException(
                    AiRequestException.Reason.CLOSED, "Plugin is disabled"));
            return result;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (runtime != selected) {
                        throw new AiRequestException(AiRequestException.Reason.CLOSED,
                                "AI runtime was replaced");
                    }
                    result.complete(buildCatalog(selected, AiGameSnapshot.capture(
                            plugin, selected.config().tools().includePlayerLocations(),
                            startedAtNanos, request.playerId().orElse(null))));
                } catch (RuntimeException error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private AiToolCatalog buildCatalog(AiRuntimeState selected, AiGameSnapshot snapshot) {
        Map<String, AiToolPack> available = new LinkedHashMap<>();
        if (snapshot != null) {
            GameToolPacks.create(snapshot).forEach(pack -> available.put(pack.id(), pack));
        }
        if (selected.config().tools().enabled("utility")) {
            AiToolPack utility = UtilityToolPack.create();
            available.put(utility.id(), utility);
        }
        List<AiTool> webTools = java.util.stream.Stream.of(
                        selected.webSearch(), selected.webFetch())
                .filter(Objects::nonNull)
                .map(AiTool.class::cast)
                .toList();
        if (selected.config().tools().enabled("web") && !webTools.isEmpty()) {
            AiToolPack web = WebToolPack.create(webTools);
            available.put(web.id(), web);
        }
        if (selected.knowledge() != null
                && selected.config().tools().enabled("knowledge")) {
            AiToolPack knowledge = KnowledgeToolPack.create(selected.knowledge(),
                    selected.config().knowledge().index().defaultResultLimit());
            available.put(knowledge.id(), knowledge);
        }
        List<AiToolPack> enabled = available.values().stream()
                .filter(pack -> selected.config().tools().enabled(pack.id()))
                .filter(Objects::nonNull)
                .toList();
        return new AiToolCatalog(enabled);
    }

    private static boolean needsGameSnapshot(AiConfig config) {
        return List.of("server", "player", "world", "minecraft").stream()
                .anyMatch(config.tools()::enabled);
    }

    private int ask(CommandSender sender, String prompt) {
        AiRuntimeState selected = runtime;
        if (selected == null || !selected.config().enabled()) {
            sender.sendMessage(languages.text(language(sender), Message.AI_DISABLED,
                    NamedTextColor.RED));
            return 0;
        }
        String conversationId = conversationId(sender);
        String language = language(sender).id();
        UUID playerId = sender instanceof Player player ? player.getUniqueId() : null;
        sender.sendMessage(Component.text("✦ ", NamedTextColor.LIGHT_PURPLE)
                .append(languages.text(language(sender), Message.AI_THINKING,
                        NamedTextColor.GRAY)));
        ask(new AiRequest(conversationId, sender.getName(), language,
                        Optional.ofNullable(playerId), prompt))
                .whenComplete((answer, failure) -> scheduleCompletion(
                        selected, sender, playerId, answer, failure));
        return Command.SINGLE_SUCCESS;
    }

    private void scheduleCompletion(
            AiRuntimeState selected,
            CommandSender sender,
            UUID playerId,
            String answer,
            Throwable failure
    ) {
        if (!plugin.isEnabled() || runtime != selected) {
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin,
                    () -> complete(selected, sender, playerId, answer, failure));
        } catch (RuntimeException ignored) {
            // The plugin was disabled between the generation check and scheduling.
        }
    }

    private void complete(
            AiRuntimeState selected,
            CommandSender originalSender,
            UUID playerId,
            String answer,
            Throwable failure
    ) {
        if (runtime != selected || !plugin.isEnabled()) {
            return;
        }
        CommandSender recipient = playerId == null
                ? originalSender : Bukkit.getPlayer(playerId);
        if (recipient == null) {
            return;
        }
        if (failure == null) {
            recipient.sendMessage(Component.text("AI › ", NamedTextColor.AQUA)
                    .append(Component.text(answer, NamedTextColor.WHITE)));
            return;
        }
        Throwable cause = unwrap(failure);
        if (cause instanceof AiRequestException request) {
            switch (request.reason()) {
                case CONVERSATION_BUSY, SERVER_BUSY -> recipient.sendMessage(
                        languages.text(language(recipient), Message.AI_BUSY,
                                NamedTextColor.YELLOW));
                case PROMPT_TOO_LONG -> recipient.sendMessage(
                        languages.text(language(recipient), Message.AI_PROMPT_TOO_LONG,
                                NamedTextColor.RED,
                                selected.config().limits().maxPromptCharacters()));
                case PROMPT_EMPTY -> usage(recipient);
                default -> recipient.sendMessage(languages.text(
                        language(recipient), Message.AI_REQUEST_FAILED, NamedTextColor.RED));
            }
            return;
        }
        plugin.getLogger().log(Level.WARNING, "AI request failed", cause);
        recipient.sendMessage(languages.text(
                language(recipient), Message.AI_REQUEST_FAILED, NamedTextColor.RED));
    }

    private int usage(CommandSender sender) {
        sender.sendMessage(languages.text(language(sender), Message.AI_USAGE,
                NamedTextColor.YELLOW));
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandSender sender) {
        clear(conversationId(sender));
        sender.sendMessage(languages.text(language(sender), Message.AI_CLEARED,
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandSender sender) {
        AiRuntimeState selected = runtime;
        if (selected == null) {
            sender.sendMessage(languages.text(language(sender), Message.AI_STATUS_DISABLED,
                    NamedTextColor.RED));
            if (configurationFailure != null && sender.hasPermission(MANAGE_PERMISSION)) {
                sender.sendMessage(Component.text(configurationFailure, NamedTextColor.DARK_RED));
            }
            return 0;
        }
        if (!selected.config().enabled()) {
            sender.sendMessage(languages.text(language(sender), Message.AI_STATUS_DISABLED,
                    NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        List<String> tools = selected.config().tools().enabledPacks().stream()
                .filter(pack -> !pack.equals("web") || selected.hasWebTools())
                .sorted().toList();
        sender.sendMessage(languages.text(language(sender), Message.AI_STATUS_ENABLED,
                NamedTextColor.GREEN,
                selected.config().activeProvider(), selected.config().provider().model(),
                tools.isEmpty() ? languages.t(language(sender), Message.AI_STATUS_NONE)
                        : String.join(", ", tools)));
        if (selected.workers() != null && sender.hasPermission(MANAGE_PERMISSION)) {
            AiWorkerPool.Status workers = selected.workers().status();
            sendLocalized(sender, NamedTextColor.GRAY, Message.AI_WORKER_STATUS,
                    workers.running(), workers.queued(), workers.dropped());
        }
        return Command.SINGLE_SUCCESS;
    }

    private void sendLocalized(
            CommandSender sender,
            NamedTextColor color,
            Message message,
            Object... args
    ) {
        sender.sendMessage(languages.text(language(sender), message, color, args));
    }

    private int reload(CommandSender sender) {
        ReloadResult result = reloadRuntime();
        if (result.success()) {
            sender.sendMessage(languages.text(language(sender), Message.AI_RELOADED,
                    NamedTextColor.GREEN, result.provider()));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(languages.text(language(sender), Message.AI_RELOAD_FAILED,
                NamedTextColor.RED, result.detail()));
        return 0;
    }

    private synchronized ReloadResult reloadRuntime() {
        AiRuntimeState replacement;
        final AiConfig config;
        try {
            config = AiConfig.load(plugin);
        } catch (RuntimeException error) {
            return reloadFailure(error);
        }

        AiConversationService conversations = null;
        SearxngSearchTool webSearch = null;
        WebFetchTool webFetch = null;
        AiWorkerPool workers = null;
        KnowledgeService knowledge = null;
        KnowledgeLearningService learning = null;
        try {
            conversations = config.enabled()
                    ? new AiConversationService(new OpenAiCompatibleClient(config.provider()), config)
                    : null;
            webSearch = config.enabled()
                    && config.tools().enabled("web")
                    && config.tools().webSearch().enabled()
                    ? new SearxngSearchTool(config.tools().webSearch()) : null;
            webFetch = config.enabled()
                    && config.tools().enabled("web")
                    && config.tools().webFetch().enabled()
                    ? new WebFetchTool(config.tools().webFetch()) : null;
            workers = config.enabled()
                    ? new AiWorkerPool("mik-ai-worker-",
                    config.limits().maxBlockingWorkers(),
                    config.limits().maxWorkerQueue()) : null;
            knowledge = config.enabled() && config.knowledge().enabled()
                    ? new KnowledgeService(plugin.getDataFolder().toPath(),
                    config.knowledge().index().maxFileBytes(),
                    config.knowledge().index().maxChunkCharacters(),
                    config.knowledge().index().overlapCharacters()) : null;
            learning = knowledge != null
                    && config.knowledge().learning().enabled()
                    ? new KnowledgeLearningService(plugin.getDataFolder().toPath(), knowledge,
                    new OpenAiCompatibleClient(config.providers().get(
                            config.knowledge().learning().provider())),
                    config.knowledge(), plugin.getLogger()) : null;
            replacement = new AiRuntimeState(config, conversations, webSearch, webFetch,
                    knowledge, learning, workers);
        } catch (RuntimeException error) {
            closeAfterFailedReload(error, learning, workers, knowledge,
                    webFetch, webSearch, conversations);
            return reloadFailure(error);
        }
        AiRuntimeState previous = runtime;
        runtime = replacement;
        configurationFailure = null;
        if (previous != null) {
            try {
                previous.close();
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not fully close the previous AI runtime", error);
            }
        }
        plugin.getLogger().info(config.enabled()
                ? "AI enabled with provider " + config.activeProvider()
                + " and model " + config.provider().model()
                : "AI is disabled in " + AiConfig.FILE_NAME);
        return new ReloadResult(true,
                config.enabled() ? config.activeProvider() : "disabled", "");
    }

    private ReloadResult reloadFailure(RuntimeException error) {
        configurationFailure = Objects.requireNonNullElse(
                error.getMessage(), error.getClass().getSimpleName());
        plugin.getLogger().log(Level.SEVERE,
                "Unable to load " + AiConfig.FILE_NAME, error);
        return new ReloadResult(false, "", configurationFailure);
    }

    private static void closeAfterFailedReload(
            RuntimeException original,
            AutoCloseable... resources
    ) {
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception | LinkageError closeFailure) {
                original.addSuppressed(closeFailure);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId().toString());
    }

    private Language language(CommandSender sender) {
        return sender instanceof Player player
                ? languages.language(player) : Language.DEFAULT;
    }

    private static String conversationId(CommandSender sender) {
        return sender instanceof Player player
                ? player.getUniqueId().toString() : "console:" + sender.getName();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record ReloadResult(boolean success, String provider, String detail) {
    }

    private record RequestContext(AiToolCatalog tools, String memory) {
    }
}
