package org.encinet.mik.module.world.regen;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Command-facing integration for WorldEdit selections and the asynchronous regeneration engine. */
public final class AsyncRegenModule implements AsyncRegenService.Listener {

    static final String COMMAND_PERMISSION = "group." + Mik.GROUP_MANAGER;
    private static final int MAX_EMPTY_FILTER_SUGGESTIONS = 200;
    private static final int MAX_FILTER_IDS_IN_LABEL = 6;
    private static final Duration PLAN_CONFIRMATION_TTL = Duration.ofMinutes(2);
    private static final Duration STANDALONE_VISUALIZATION_TTL = Duration.ofSeconds(30);
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(PLAN_CONFIRMATION_TTL)
            .build();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final AsyncRegenService service;
    private final RegenSelectionVisualizer visualizer;
    private final List<String> blockIds;
    private final Map<UUID, PreviewRequest> previewRequests = new HashMap<>();
    private final Map<UUID, PendingPlan> pendingPlans = new HashMap<>();

    private RegenSelectionProvider selectionProvider;

    public AsyncRegenModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.service = new AsyncRegenService(plugin, this);
        this.visualizer = new RegenSelectionVisualizer(plugin);
        this.blockIds = searchableBlockIds();
    }

    /**
     * Bukkit keeps LEGACY_* enum constants for compatibility, but deliberately rejects
     * namespaced-key access for them. They are not valid command suggestions or modern
     * block filters, so exclude them before asking the Keyed API for a key.
     */
    static List<String> searchableBlockIds() {
        return searchableBlockIds(
                Arrays.asList(Material.values()),
                Material::isLegacy,
                Material::isBlock,
                material -> material.key().asString());
    }

    static <T> List<String> searchableBlockIds(
            List<T> values,
            Predicate<T> legacy,
            Predicate<T> block,
            Function<T, String> key
    ) {
        return values.stream()
                .filter(value -> !legacy.test(value))
                .filter(block)
                .map(key)
                .distinct()
                .sorted()
                .toList();
    }

    public void enable() {
        if (Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            try {
                selectionProvider = new WorldEditSelectionProvider();
                plugin.getLogger().info("Async regeneration enabled with WorldEdit selection support");
            } catch (LinkageError error) {
                selectionProvider = null;
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "WorldEdit is present but its API is incompatible; /regen is unavailable", error);
            }
        } else {
            selectionProvider = null;
            plugin.getLogger().warning("WorldEdit not found; /regen will remain unavailable");
        }
        service.start();
        visualizer.start();
    }

    public void disable() {
        previewRequests.clear();
        pendingPlans.clear();
        visualizer.close();
        service.close();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("regen")
                        .requires(source -> source.getSender().hasPermission(COMMAND_PERMISSION))
                        .executes(context -> usage(context.getSource()))
                        .then(Commands.literal("preview")
                                .executes(context -> previewBlocks(context.getSource(), ""))
                                .then(Commands.literal("blocks")
                                        .executes(context -> previewBlocks(context.getSource(), ""))
                                        .then(Commands.argument("types", StringArgumentType.greedyString())
                                                .suggests((context, builder) -> suggestBlocks(builder))
                                                .executes(context -> previewBlocks(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "types")))))
                                .then(Commands.literal("upgrade")
                                        .executes(context -> previewUpgrade(
                                                context.getSource(),
                                                RegenUpgradeScope.TERRAIN_AND_BIOMES)))
                                .then(Commands.literal("biomes")
                                        .executes(context -> previewUpgrade(
                                                context.getSource(), RegenUpgradeScope.BIOMES_ONLY))))
                        .then(Commands.literal("show")
                                .executes(context -> visualize(context.getSource())))
                        .then(Commands.literal("apply")
                                .executes(context -> applyPlan(context.getSource())))
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("cancel")
                                .executes(context -> cancel(context.getSource())))
                        .build(),
                languageService.t(Language.DEFAULT, Message.ASYNC_REGEN_COMMAND_DESCRIPTION)));
    }

    private int usage(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player player) {
            player.sendMessage(languageService.text(
                    player, Message.ASYNC_REGEN_USAGE, NamedTextColor.AQUA));
        } else {
            source.getSender().sendMessage(languageService.text(
                    Language.DEFAULT, Message.ASYNC_REGEN_USAGE, NamedTextColor.AQUA));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int previewBlocks(CommandSourceStack source, String requestedBlocks) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        RegenBlockFilter filter;
        try {
            filter = RegenBlockFilter.parse(requestedBlocks);
        } catch (RegenBlockFilter.UnknownBlocksException error) {
            player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_INVALID_BLOCKS,
                    NamedTextColor.RED, String.join(", ", error.blockIds())));
            return 0;
        }
        return startPreview(player, RegenOperation.blocks(filter));
    }

    private int previewUpgrade(CommandSourceStack source, RegenUpgradeScope scope) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        return startPreview(player, RegenOperation.upgrade(scope));
    }

    private int startPreview(Player player, RegenOperation operation) {
        RegenSelection selection = readSelection(player);
        if (selection == null) {
            return 0;
        }

        try {
            AsyncRegenService.SubmitResult result = operation.upgrade()
                    ? service.previewUpgrade(player.getUniqueId(), selection, operation.upgradeScope())
                    : service.previewRegeneration(
                            player.getUniqueId(), selection, operation.blockFilter());
            if (result
                    == AsyncRegenService.SubmitResult.ALREADY_RUNNING) {
                sendAlreadyRunning(player);
                return 0;
            }
        } catch (RuntimeException error) {
            logStartFailure(player, "regeneration preview", error);
            return 0;
        }

        pendingPlans.remove(player.getUniqueId());
        previewRequests.put(player.getUniqueId(), new PreviewRequest(selection, operation));
        visualizer.show(player, selection, RegenPreviewSamples.EMPTY,
                RegenSelectionVisualizer.NO_EXPIRY);
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_PREVIEW_STARTED,
                NamedTextColor.GREEN,
                Long.toString(selection.volume()),
                Integer.toString(selection.chunks().size()),
                operationLabel(player, operation)));
        sendSelectionVisualization(player, selection);
        return Command.SINGLE_SUCCESS;
    }

    private int visualize(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        UUID playerId = player.getUniqueId();
        PendingPlan pending = pendingPlan(player, false);
        if (pending != null) {
            visualizer.show(player, pending.selection(), pending.preview().previewSamples(),
                    pending.expiresAtNanos());
            player.sendMessage(previewSummary(player, pending.operation(), pending.preview()));
            sendVisualizationLegend(player, pending.selection());
            showPlanDialog(player, pending);
            return Command.SINGLE_SUCCESS;
        }
        PreviewRequest request = previewRequests.get(playerId);
        if (request != null) {
            visualizer.show(player, request.selection(), RegenPreviewSamples.EMPTY,
                    RegenSelectionVisualizer.NO_EXPIRY);
            sendSelectionVisualization(player, request.selection());
            return Command.SINGLE_SUCCESS;
        }
        RegenSelection selection = readSelection(player);
        if (selection == null) {
            return 0;
        }
        visualizer.show(player, selection, RegenPreviewSamples.EMPTY,
                System.nanoTime() + STANDALONE_VISUALIZATION_TTL.toNanos());
        sendSelectionVisualization(player, selection);
        return Command.SINGLE_SUCCESS;
    }

    private RegenSelection readSelection(Player player) {
        if (selectionProvider == null) {
            player.sendMessage(languageService.text(
                    player, Message.ASYNC_REGEN_WORLD_EDIT_REQUIRED, NamedTextColor.RED));
            return null;
        }
        try {
            return selectionProvider.selection(player);
        } catch (RegenSelectionProvider.IncompleteSelectionException error) {
            player.sendMessage(languageService.text(
                    player, Message.ASYNC_REGEN_SELECTION_REQUIRED, NamedTextColor.RED));
            return null;
        } catch (RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not read WorldEdit selection for " + player.getUniqueId(), error);
            player.sendMessage(languageService.text(
                    player, Message.ASYNC_REGEN_START_FAILED, NamedTextColor.RED));
            return null;
        }
    }

    private int status(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        RegenTaskSnapshot snapshot = service.snapshot(player.getUniqueId());
        if (snapshot != null) {
            if (snapshot.kind().preview()
                    && !visualizer.visible(player.getUniqueId())) {
                PreviewRequest request = previewRequests.get(player.getUniqueId());
                if (request != null) {
                    visualizer.show(player, request.selection(), RegenPreviewSamples.EMPTY,
                            RegenSelectionVisualizer.NO_EXPIRY);
                }
            }
            sendStatus(player, snapshot, false);
            return Command.SINGLE_SUCCESS;
        }
        PendingPlan pending = pendingPlan(player, false);
        if (pending != null) {
            if (!visualizer.visible(player.getUniqueId())) {
                visualizer.show(player, pending.selection(), pending.preview().previewSamples(),
                        pending.expiresAtNanos());
            }
            player.sendMessage(languageService.text(player,
                    Message.ASYNC_REGEN_AWAITING_APPLY, NamedTextColor.YELLOW,
                    operationLabel(player, pending.operation()),
                    describeSelection(pending.selection()),
                    Long.toString(remainingSeconds(pending))));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(languageService.text(
                player, Message.ASYNC_REGEN_NOT_RUNNING, NamedTextColor.YELLOW));
        return 0;
    }

    private int cancel(CommandSourceStack source) {
        Player player = requirePlayer(source);
        if (player == null) {
            return 0;
        }
        if (service.cancel(player.getUniqueId())) {
            return Command.SINGLE_SUCCESS;
        }
        PendingPlan pending = pendingPlan(player, false);
        if (pending != null && pendingPlans.remove(player.getUniqueId(), pending)) {
            visualizer.hide(player.getUniqueId());
            player.sendMessage(languageService.text(player,
                    Message.ASYNC_REGEN_PREVIEW_CANCELLED, NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        player.sendMessage(languageService.text(
                player, Message.ASYNC_REGEN_NOT_RUNNING, NamedTextColor.YELLOW));
        return 0;
    }

    private int applyPlan(CommandSourceStack source) {
        Player player = requirePlayer(source);
        return player != null && applyPlan(player, null) ? Command.SINGLE_SUCCESS : 0;
    }

    private boolean applyPlan(Player player, UUID expectedToken) {
        PendingPlan pending = pendingPlan(player, true);
        if (pending == null || expectedToken != null && !pending.token().equals(expectedToken)) {
            if (pending != null) {
                player.sendMessage(languageService.text(player,
                        Message.ASYNC_REGEN_APPLY_EXPIRED, NamedTextColor.RED));
            }
            return false;
        }
        try {
            RegenOperation operation = pending.operation();
            AsyncRegenService.SubmitResult result = operation.upgrade()
                    ? service.applyUpgrade(
                            player.getUniqueId(),
                            pending.selection(),
                            operation.upgradeScope(),
                            pending.preview().metadataEligibleStructures())
                    : service.applyRegeneration(
                            player.getUniqueId(), pending.selection(), operation.blockFilter());
            if (result
                    == AsyncRegenService.SubmitResult.ALREADY_RUNNING) {
                sendAlreadyRunning(player);
                return false;
            }
        } catch (RuntimeException error) {
            logStartFailure(player, "regeneration plan application", error);
            return false;
        }

        pendingPlans.remove(player.getUniqueId(), pending);
        visualizer.hide(player.getUniqueId());
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_APPLY_STARTED,
                NamedTextColor.GREEN,
                operationLabel(player, pending.operation()),
                describeSelection(pending.selection())));
        return true;
    }

    private void cancelPendingPlan(Player player, UUID expectedToken) {
        PendingPlan pending = pendingPlans.get(player.getUniqueId());
        if (pending == null || !pending.token().equals(expectedToken)) {
            return;
        }
        pendingPlans.remove(player.getUniqueId(), pending);
        visualizer.hide(player.getUniqueId());
        player.sendMessage(languageService.text(player,
                Message.ASYNC_REGEN_PREVIEW_CANCELLED, NamedTextColor.YELLOW));
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBlocks(
            SuggestionsBuilder builder
    ) {
        String remaining = builder.getRemaining();
        int tokenStart = 0;
        for (int index = remaining.length() - 1; index >= 0; index--) {
            char character = remaining.charAt(index);
            if (character == ',' || Character.isWhitespace(character)) {
                tokenStart = index + 1;
                break;
            }
        }
        String token = remaining.substring(tokenStart).toLowerCase(Locale.ROOT);
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + tokenStart);
        int limit = token.isEmpty() ? MAX_EMPTY_FILTER_SUGGESTIONS : Integer.MAX_VALUE;
        blockIds.stream()
                .filter(id -> id.startsWith(token)
                        || id.substring(id.indexOf(':') + 1).startsWith(token))
                .limit(limit)
                .forEach(tokenBuilder::suggest);
        return tokenBuilder.buildFuture();
    }

    @Override
    public void progress(RegenTaskSnapshot snapshot) {
        Player player = Bukkit.getPlayer(snapshot.owner());
        if (player != null) {
            sendStatus(player, snapshot, true);
        }
    }

    private void sendStatus(Player player, RegenTaskSnapshot snapshot, boolean actionBar) {
        Component message;
        if (snapshot.kind().preview()) {
            if (snapshot.kind().upgrade()) {
                message = languageService.text(player, Message.ASYNC_REGEN_UPGRADE_PREVIEW_PROGRESS,
                        NamedTextColor.AQUA,
                        Integer.toString(snapshot.plannedChunks()),
                        Integer.toString(snapshot.totalChunks()),
                        Long.toString(snapshot.discoveredChanges()),
                        Long.toString(snapshot.discoveredBiomeChanges()),
                        Integer.toString(snapshot.structures()));
            } else {
                message = languageService.text(player, Message.ASYNC_REGEN_BLOCK_PREVIEW_PROGRESS,
                        NamedTextColor.AQUA,
                        Integer.toString(snapshot.plannedChunks()),
                        Integer.toString(snapshot.totalChunks()),
                        Long.toString(snapshot.discoveredChanges()),
                        Long.toString(snapshot.atRiskBlockEntities()));
            }
        } else if (snapshot.kind() == RegenTaskKind.UPGRADE_APPLY) {
            message = languageService.text(player, Message.ASYNC_REGEN_UPGRADE_APPLY_PROGRESS,
                    NamedTextColor.AQUA,
                    stateLabel(player, snapshot.state()),
                    Integer.toString(snapshot.plannedChunks()),
                    Integer.toString(snapshot.totalChunks()),
                    Long.toString(snapshot.appliedChanges()),
                    Long.toString(snapshot.appliedBiomeChanges()));
        } else if (actionBar) {
            message = languageService.text(player, Message.ASYNC_REGEN_PROGRESS,
                    NamedTextColor.AQUA,
                    stateLabel(player, snapshot.state()),
                    Integer.toString(snapshot.plannedChunks()),
                    Integer.toString(snapshot.totalChunks()),
                    Long.toString(snapshot.appliedChanges()));
        } else {
            message = languageService.text(player, Message.ASYNC_REGEN_STATUS,
                    NamedTextColor.AQUA,
                    stateLabel(player, snapshot.state()),
                    Integer.toString(snapshot.plannedChunks()),
                    Integer.toString(snapshot.totalChunks()),
                    Long.toString(snapshot.appliedChanges()),
                    Long.toString(snapshot.discoveredChanges()));
        }
        message = Component.text(operationLabel(player, snapshot) + " · ", NamedTextColor.AQUA)
                .append(message);
        if (actionBar) {
            player.sendActionBar(message);
        } else {
            player.sendMessage(message);
        }
    }

    @Override
    public void completed(RegenTaskSnapshot snapshot) {
        if (snapshot.kind().preview()) {
            completePreview(snapshot);
            return;
        }
        Player player = Bukkit.getPlayer(snapshot.owner());
        if (player == null) {
            return;
        }
        if (snapshot.kind() == RegenTaskKind.UPGRADE_APPLY) {
            player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_UPGRADE_COMPLETED,
                    NamedTextColor.GREEN,
                    Long.toString(snapshot.appliedChanges()),
                    Long.toString(snapshot.appliedBiomeChanges()),
                    formatDuration(snapshot.elapsed())));
            if (snapshot.appliedStructureStarts() > 0 || snapshot.appliedStructureReferences() > 0) {
                player.sendMessage(languageService.text(player,
                        Message.ASYNC_REGEN_STRUCTURE_METADATA_APPLIED, NamedTextColor.GREEN,
                        Long.toString(snapshot.appliedStructureStarts()),
                        Long.toString(snapshot.appliedStructureReferences())));
            }
        } else {
            player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_COMPLETED,
                    NamedTextColor.GREEN,
                    Long.toString(snapshot.appliedChanges()),
                    formatDuration(snapshot.elapsed())));
        }
        sendSkippedUngenerated(player, snapshot);
    }

    private void completePreview(RegenTaskSnapshot snapshot) {
        PreviewRequest request = previewRequests.remove(snapshot.owner());
        if (request == null) {
            return;
        }
        if (!hasApplicableChanges(snapshot, request.operation())) {
            visualizer.hide(snapshot.owner());
            Player player = Bukkit.getPlayer(snapshot.owner());
            if (player != null) {
                player.sendMessage(previewSummary(player, request.operation(), snapshot));
                if (request.operation().upgrade() && snapshot.skippedBoundaryBiomeCells() > 0) {
                    player.sendMessage(languageService.text(player,
                            Message.ASYNC_REGEN_UPGRADE_BOUNDARY_NOTE, NamedTextColor.YELLOW,
                            Long.toString(snapshot.skippedBoundaryBiomeCells())));
                }
                sendSkippedUngenerated(player, snapshot);
                player.sendMessage(languageService.text(player,
                        Message.ASYNC_REGEN_NOTHING_TO_APPLY, NamedTextColor.GREEN));
            }
            return;
        }
        PendingPlan pending = new PendingPlan(
                UUID.randomUUID(),
                request.selection(),
                request.operation(),
                snapshot,
                System.nanoTime() + PLAN_CONFIRMATION_TTL.toNanos());
        pendingPlans.put(snapshot.owner(), pending);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    if (pendingPlans.remove(snapshot.owner(), pending)) {
                        visualizer.hide(snapshot.owner());
                    }
                },
                PLAN_CONFIRMATION_TTL.toSeconds() * 20L + 1L);

        Player player = Bukkit.getPlayer(snapshot.owner());
        if (player == null) {
            return;
        }
        visualizer.show(player, pending.selection(), snapshot.previewSamples(), pending.expiresAtNanos());
        player.sendMessage(previewSummary(player, pending.operation(), snapshot));
        if (pending.operation().upgrade() && snapshot.skippedBoundaryBiomeCells() > 0) {
            player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_UPGRADE_BOUNDARY_NOTE,
                    NamedTextColor.YELLOW,
                    Long.toString(snapshot.skippedBoundaryBiomeCells())));
        }
        sendSkippedUngenerated(player, snapshot);
        showPlanDialog(player, pending);
    }

    private void showPlanDialog(Player player, PendingPlan pending) {
        RegenTaskSnapshot preview = pending.preview();
        var bodyBuilder = Component.text()
                .append(Component.text(operationLabel(player, pending.operation()),
                        NamedTextColor.GOLD))
                .appendNewline()
                .append(previewSummary(player, pending.operation(), preview))
                .appendNewline()
                .append(Component.text(describeSelection(pending.selection()), NamedTextColor.AQUA))
                .appendNewline()
                .append(languageService.text(player, Message.ASYNC_REGEN_VISUALIZATION_LEGEND,
                        NamedTextColor.GRAY,
                        pending.selection().world().key().asString()));
        if (pending.operation().upgrade() && preview.skippedBoundaryBiomeCells() > 0) {
            bodyBuilder.appendNewline()
                    .append(languageService.text(player, Message.ASYNC_REGEN_UPGRADE_BOUNDARY_NOTE,
                            NamedTextColor.YELLOW,
                            Long.toString(preview.skippedBoundaryBiomeCells())));
        }
        if (preview.skippedUngeneratedChunks() > 0) {
            bodyBuilder.appendNewline()
                    .append(languageService.text(player, Message.ASYNC_REGEN_SKIPPED_UNGENERATED,
                            NamedTextColor.YELLOW,
                            Integer.toString(preview.skippedUngeneratedChunks())));
        }
        if (pending.operation().blocks()) {
            bodyBuilder.appendNewline()
                    .appendNewline()
                    .append(languageService.text(player, Message.ASYNC_REGEN_SAFETY_NOTE,
                            NamedTextColor.RED));
        }
        Component body = bodyBuilder.build();

        ActionButton confirm = ActionButton.create(
                languageService.text(player, Message.ASYNC_REGEN_APPLY_PLAN, NamedTextColor.GREEN),
                Component.text(operationLabel(player, pending.operation())
                        + " · " + describeSelection(pending.selection()), NamedTextColor.GRAY),
                135,
                DialogAction.customClick((response, audience) -> runOnMain(audience,
                        target -> applyPlan(target, pending.token())), CALLBACK_OPTIONS));
        ActionButton cancel = ActionButton.create(
                languageService.text(player, Message.ASYNC_REGEN_PLAN_CANCEL, NamedTextColor.GRAY),
                null,
                100,
                DialogAction.customClick((response, audience) -> runOnMain(audience,
                        target -> cancelPendingPlan(target, pending.token())), CALLBACK_OPTIONS));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(languageService.text(player,
                                Message.ASYNC_REGEN_PLAN_DIALOG_TITLE, NamedTextColor.GOLD))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(body, 430)))
                        .inputs(List.of())
                        .build())
                .type(DialogType.confirmation(confirm, cancel)));
        player.showDialog(dialog);
    }

    private Component previewSummary(
            Player player,
            RegenOperation operation,
            RegenTaskSnapshot snapshot
    ) {
        if (!operation.upgrade()) {
            return languageService.text(player, Message.ASYNC_REGEN_BLOCK_PREVIEW_SUMMARY,
                    NamedTextColor.AQUA,
                    Long.toString(snapshot.discoveredChanges()),
                    Long.toString(snapshot.atRiskBlockEntities()),
                    blockFilterLabel(player, operation.blockFilter()));
        }
        return languageService.text(player, Message.ASYNC_REGEN_UPGRADE_PREVIEW_SUMMARY,
                NamedTextColor.AQUA,
                Long.toString(snapshot.discoveredChanges()),
                Long.toString(snapshot.discoveredBiomeChanges()),
                Integer.toString(snapshot.structures()),
                Integer.toString(snapshot.metadataEligibleStructures().size()),
                Long.toString(snapshot.atRiskBlockEntities()));
    }

    private static boolean hasApplicableChanges(
            RegenTaskSnapshot snapshot,
            RegenOperation operation
    ) {
        return snapshot.discoveredChanges() > 0
                || operation.upgrade() && (snapshot.discoveredBiomeChanges() > 0
                        || !snapshot.metadataEligibleStructures().isEmpty());
    }

    private void sendSkippedUngenerated(Player player, RegenTaskSnapshot snapshot) {
        if (snapshot.skippedUngeneratedChunks() <= 0) {
            return;
        }
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_SKIPPED_UNGENERATED,
                NamedTextColor.YELLOW,
                Integer.toString(snapshot.skippedUngeneratedChunks())));
    }

    @Override
    public void cancelled(RegenTaskSnapshot snapshot) {
        previewRequests.remove(snapshot.owner());
        visualizer.hide(snapshot.owner());
        Player player = Bukkit.getPlayer(snapshot.owner());
        if (player == null) {
            return;
        }
        if (snapshot.kind().preview()) {
            player.sendMessage(languageService.text(player,
                    Message.ASYNC_REGEN_PREVIEW_CANCELLED, NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_CANCELLED,
                NamedTextColor.YELLOW,
                Long.toString(snapshot.appliedChanges()
                        + snapshot.appliedBiomeChanges()
                        + snapshot.appliedStructureStarts()
                        + snapshot.appliedStructureReferences())));
    }

    @Override
    public void failed(RegenTaskSnapshot snapshot, Throwable error) {
        previewRequests.remove(snapshot.owner());
        visualizer.hide(snapshot.owner());
        Player player = Bukkit.getPlayer(snapshot.owner());
        if (player == null) {
            return;
        }
        if (snapshot.kind().preview()) {
            player.sendMessage(languageService.text(
                    player, Message.ASYNC_REGEN_START_FAILED, NamedTextColor.RED));
            return;
        }
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_FAILED,
                NamedTextColor.RED,
                Long.toString(snapshot.appliedChanges()
                        + snapshot.appliedBiomeChanges()
                        + snapshot.appliedStructureStarts()
                        + snapshot.appliedStructureReferences())));
    }

    private PendingPlan pendingPlan(Player player, boolean reportExpiration) {
        PendingPlan pending = pendingPlans.get(player.getUniqueId());
        if (pending == null) {
            if (reportExpiration) {
                player.sendMessage(languageService.text(player,
                        Message.ASYNC_REGEN_APPLY_EXPIRED, NamedTextColor.RED));
            }
            return null;
        }
        if (System.nanoTime() <= pending.expiresAtNanos()) {
            return pending;
        }
        pendingPlans.remove(player.getUniqueId(), pending);
        visualizer.hide(player.getUniqueId());
        if (reportExpiration) {
            player.sendMessage(languageService.text(player,
                    Message.ASYNC_REGEN_APPLY_EXPIRED, NamedTextColor.RED));
        }
        return null;
    }

    private void sendVisualizationLegend(Player player, RegenSelection selection) {
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_VISUALIZATION_LEGEND,
                NamedTextColor.GRAY, selection.world().key().asString()));
    }

    private void sendSelectionVisualization(Player player, RegenSelection selection) {
        player.sendMessage(languageService.text(player, Message.ASYNC_REGEN_VISUALIZATION_SELECTION,
                NamedTextColor.GRAY, selection.world().key().asString()));
    }

    private void logStartFailure(Player player, String operation, RuntimeException error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                "Could not start asynchronous " + operation + " for " + player.getUniqueId(), error);
        player.sendMessage(languageService.text(
                player, Message.ASYNC_REGEN_START_FAILED, NamedTextColor.RED));
    }

    private void sendAlreadyRunning(Player player) {
        player.sendMessage(languageService.text(
                player, Message.ASYNC_REGEN_ALREADY_RUNNING, NamedTextColor.YELLOW));
    }

    private String stateLabel(Player player, RegenTaskState state) {
        Message message = switch (state) {
            case PREPARING -> Message.ASYNC_REGEN_STATE_PREPARING;
            case GENERATING -> Message.ASYNC_REGEN_STATE_GENERATING;
            case APPLYING -> Message.ASYNC_REGEN_STATE_APPLYING;
            case COMPLETED -> Message.ASYNC_REGEN_STATE_COMPLETED;
            case CANCELLED -> Message.ASYNC_REGEN_STATE_CANCELLED;
            case FAILED -> Message.ASYNC_REGEN_STATE_FAILED;
        };
        return languageService.t(player, message);
    }

    private String operationLabel(Player player, RegenOperation operation) {
        if (!operation.upgrade()) {
            return languageService.t(player, Message.ASYNC_REGEN_SCOPE_BLOCKS,
                    blockFilterLabel(player, operation.blockFilter()));
        }
        return languageService.t(player,
                operation.upgradeScope() == RegenUpgradeScope.BIOMES_ONLY
                        ? Message.ASYNC_REGEN_UPGRADE_SCOPE_BIOMES
                        : Message.ASYNC_REGEN_UPGRADE_SCOPE_TERRAIN);
    }

    private String operationLabel(Player player, RegenTaskSnapshot snapshot) {
        if (snapshot.kind().upgrade()) {
            return languageService.t(player,
                    snapshot.upgradeScope() == RegenUpgradeScope.BIOMES_ONLY
                            ? Message.ASYNC_REGEN_UPGRADE_SCOPE_BIOMES
                            : Message.ASYNC_REGEN_UPGRADE_SCOPE_TERRAIN);
        }
        String filter = snapshot.blockFilterIds().isEmpty()
                ? languageService.t(player, Message.ASYNC_REGEN_FILTER_ALL)
                : formatBlockIds(snapshot.blockFilterIds());
        return languageService.t(player, Message.ASYNC_REGEN_SCOPE_BLOCKS, filter);
    }

    private String blockFilterLabel(Player player, RegenBlockFilter filter) {
        return filter.replacesAll()
                ? languageService.t(player, Message.ASYNC_REGEN_FILTER_ALL)
                : formatBlockIds(filter.ids());
    }

    private static String formatBlockIds(List<String> ids) {
        if (ids.size() <= MAX_FILTER_IDS_IN_LABEL) {
            return String.join(", ", ids);
        }
        return String.join(", ", ids.subList(0, MAX_FILTER_IDS_IN_LABEL))
                + " +" + (ids.size() - MAX_FILTER_IDS_IN_LABEL);
    }

    private static long remainingSeconds(PendingPlan pending) {
        long remainingNanos = Math.max(0L, pending.expiresAtNanos() - System.nanoTime());
        return Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L);
    }

    private Player requirePlayer(CommandSourceStack source) {
        if (source.getExecutor() instanceof Player player) {
            return player;
        }
        CommandSender sender = source.getSender();
        sender.sendMessage(languageService.text(
                Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED));
        return null;
    }

    private void runOnMain(Audience audience, Consumer<Player> action) {
        if (!(audience instanceof Player player)) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.accept(player);
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> action.accept(player));
        }
    }

    private static String describeSelection(RegenSelection selection) {
        return selection.world().key().asString()
                + " [" + selection.minX() + ", " + selection.minY() + ", " + selection.minZ() + "]"
                + " → [" + selection.maxX() + ", " + selection.maxY() + ", " + selection.maxZ() + "]";
    }

    private static String formatDuration(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 1_000L) {
            return millis + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1_000.0D);
    }

    private record PreviewRequest(RegenSelection selection, RegenOperation operation) {
    }

    private record PendingPlan(
            UUID token,
            RegenSelection selection,
            RegenOperation operation,
            RegenTaskSnapshot preview,
            long expiresAtNanos
    ) {
    }
}
