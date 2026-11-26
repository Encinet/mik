package org.encinet.mik.feature.community.ai.turn;

import com.mojang.brigadier.Command;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.ai.chat.AiChat;
import org.encinet.mik.feature.community.ai.game.GameToolCatalog;
import org.encinet.mik.integration.geyser.BedrockPlayerDetector;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.AssistantAsk;
import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.AssistantFailure;
import org.encinet.mik.protocol.AssistantFailure.Reason;
import org.encinet.mik.protocol.AssistantReply;
import org.encinet.mik.protocol.AssistantResult;
import org.encinet.mik.protocol.AssistantSignal;
import org.encinet.mik.protocol.AssistantToolDefinition;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Coordinates requests, replies and delivery receipts on Paper's main thread. */
public final class AiTurns {
    private static final long REPLY_DEADLINE = Duration.ofSeconds(100).toNanos();
    private static final long SETTLE_DEADLINE = Duration.ofSeconds(30).toNanos();
    private static final long RECEIPT_DEADLINE = Duration.ofSeconds(50).toNanos();
    private static final long RETRY_INTERVAL = Duration.ofSeconds(1).toNanos();
    private static final long INITIAL_THINKING_ESTIMATE = Duration.ofSeconds(15).toNanos();
    private static final long MIN_THINKING_ESTIMATE = Duration.ofSeconds(2).toNanos();
    // A replacement connection may poll before a request on the old lane has reached mikd.
    private static final long ADMISSION_GRACE = Duration.ofSeconds(6).toNanos();
    private final AiChat chat;
    private final LanguageService languages;
    private final RequestPort requests;
    private final BedrockPlayerDetector bedrock;
    private final Predicate<Player> authorized;
    private final GameToolCatalog tools;
    private final LongSupplier nanoTime;
    private final Logger logger;
    private final Map<UUID, Turn> turns = new HashMap<>();
    private final Map<UUID, Receipt> receipts = new HashMap<>();
    private final AiFollowUps followUps;
    private long expectedThinkingTime = INITIAL_THINKING_ESTIMATE;

    public AiTurns(LanguageService languages, RequestPort requests, BedrockPlayerDetector bedrock,
                   Predicate<Player> authorized, GameToolCatalog tools, LongSupplier nanoTime, Logger logger) {
        this.chat = new AiChat(languages);
        this.languages = languages;
        this.requests = requests;
        this.bedrock = bedrock;
        this.authorized = authorized;
        this.tools = tools;
        this.nanoTime = nanoTime;
        this.logger = logger;
        this.followUps = new AiFollowUps(nanoTime, this::ask);
    }

    public void close() {
        followUps.clear();
        receipts.clear();
        turns.forEach((id, turn) -> {
            turn.hideThinking();
            discardIfFetching(id, turn);
        });
        turns.clear();
    }

    public Player toolViewer(UUID playerId, String turnId, String tool) {
        var turn = turns.get(playerId);
        return turn != null && turn.fetching() && !turn.detached && turn.id.equals(turnId)
                && !turn.expired(nanoTime.getAsLong()) && turn.tools.contains(tool) && canUse(turn.player)
                ? turn.player : null;
    }

    public static boolean permitted(Player player) {
        return player.hasPermission("mik.ai.use");
    }

    private boolean canUse(Player player) {
        return Bukkit.getPlayer(player.getUniqueId()) == player && player.isOnline()
                && permitted(player) && authorized.test(player);
    }

    public int ask(Player player, String prompt) {
        if (!canUse(player)) return 0;
        var id = player.getUniqueId();
        if (prompt.isEmpty()) {
            send(player, Message.AI_USAGE, NamedTextColor.YELLOW);
            return 0;
        }
        if (turns.containsKey(id)) return requestInProgress(player);
        // An accepted ask consumes every option on the previous reply, including manual asks.
        followUps.revoke(id);
        var turnId = UUID.randomUUID().toString();
        var catalog = tools.describe(player);
        var ask = AssistantAsk.newBuilder().setPrompt(prompt)
                .setLocale(languages.language(player).locale().toLanguageTag())
                .addAllGameTools(catalog);
        var receipt = receipts.get(id);
        if (receipt != null) ask.setPreviousDisplayedTurn(receipt.id);
        var request = command(id, turnId).setAsk(ask).build();
        long started = nanoTime.getAsLong();
        var offered = catalog.stream().map(AssistantToolDefinition::getName).collect(Collectors.toUnmodifiableSet());
        turns.put(id, new Turn(player, turnId, offered, !bedrock.isBedrockPlayer(id), requests.assistant(request), started));
        return Command.SINGLE_SUCCESS;
    }

    public int cancel(Player player) {
        if (!canUse(player)) return 0;
        var id = player.getUniqueId();
        var previous = turns.get(id);
        if (previous == null || previous.player != player || previous.detached) {
            send(player, Message.AI_NOTHING_TO_CANCEL, NamedTextColor.YELLOW);
            return Command.SINGLE_SUCCESS;
        }
        if (!previous.fetching()) return requestInProgress(player);
        previous.hideThinking();
        previous.step = Step.CANCEL;
        previous.deadline = nanoTime.getAsLong() + SETTLE_DEADLINE;
        previous.pending = discard(id, previous.id);
        return Command.SINGLE_SUCCESS;
    }

    private int requestInProgress(Player player) {
        send(player, Message.AI_REQUEST_IN_PROGRESS, NamedTextColor.YELLOW);
        return 0;
    }

    public void poll() {
        long now = nanoTime.getAsLong();
        followUps.expire(now);
        receipts.entrySet().removeIf(entry -> receiptSettled(entry.getKey(), entry.getValue(), now));
        var iterator = turns.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var turn = entry.getValue();
            boolean keep = false;
            try {
                keep = advance(entry.getKey(), turn, now);
            } catch (RuntimeException failure) {
                discardIfFetching(entry.getKey(), turn);
                throw failure;
            } finally {
                if (!keep) {
                    iterator.remove();
                    turn.hideThinking();
                }
            }
        }
    }

    private boolean advance(UUID id, Turn turn, long now) {
        var player = turn.player;
        boolean visible = !turn.detached && canUse(player);
        if (!visible) detach(id, turn);
        boolean expired = turn.expired(now);
        if (expired || (!visible && turn.fetching())) {
            if (expired && visible) log(turn, now, "deadline_exceeded");
            discardIfFetching(id, turn);
            if (visible) send(player, Message.AI_REQUEST_FAILED, NamedTextColor.RED);
            return false;
        }
        turn.updateProgress(now);
        if (turn.pending == null) {
            if (now - turn.retryAt >= 0) sendStep(id, turn);
            return true;
        }
        if (!turn.pending.isDone()) return true;

        AssistantResult result;
        try {
            result = turn.pending.join();
        } catch (CompletionException | CancellationException failure) {
            turn.ipcFailures++;
            if (turn.step == Step.ASK && RequestPort.definitelyNotSubmitted(failure)) {
                log(turn, now, "not_submitted");
                send(player, Message.AI_REQUEST_FAILED, NamedTextColor.RED);
                return false;
            }
            reschedule(turn, now);
            return true;
        }
        if (result.hasFailure()) return handleFailure(id, turn, now, visible, result.getFailure());
        return switch (turn.step) {
            case ASK -> {
                if (!result.hasAccepted()) yield unexpected(turn, now, result);
                turn.showThinking(chat.thinking(player), now, expectedThinkingTime);
                turn.step = Step.POLL;
                sendStep(id, turn);
                yield true;
            }
            case POLL -> {
                if (result.hasPending()) {
                    if (turn.admissionGrace != null) {
                        turn.showThinking(chat.thinking(player), now, expectedThinkingTime);
                    }
                    var summary = result.getPending().getReasoningSummary();
                    if (!summary.isBlank()) turn.updateThinking(chat.reasoningSummary(summary));
                    turn.admissionGrace = null;
                    reschedule(turn, now);
                    yield true;
                }
                if (!result.hasReply()) yield unexpected(turn, now, result);
                if (showReply(id, turn, result.getReply())) {
                    if (turn.thinking != null) {
                        long observed = Math.max(MIN_THINKING_ESTIMATE, now - turn.thinkingStarted);
                        expectedThinkingTime += (observed - expectedThinkingTime) / 4;
                    }
                    receipts.put(id, new Receipt(turn, now, confirm(id, turn.id)));
                } else {
                    log(turn, now, "empty_rendered_reply");
                    send(player, Message.AI_REQUEST_FAILED, NamedTextColor.RED);
                    discard(id, turn.id);
                }
                yield false;
            }
            case CANCEL -> {
                if (!result.hasCancelled()) yield unexpected(turn, now, result);
                if (visible) send(player, Message.AI_CANCELLED, NamedTextColor.GREEN);
                yield false;
            }
        };
    }

    private boolean unexpected(Turn turn, long now, AssistantResult result) {
        log(turn, now, "unexpected_" + result.getOutcomeCase().name());
        reschedule(turn, now);
        return true;
    }

    private boolean handleFailure(UUID id, Turn turn, long now, boolean visible, AssistantFailure failure) {
        var reason = failure.getReason();
        if (failure.getCauseCase() == AssistantFailure.CauseCase.CAUSE_NOT_SET) {
            log(turn, now, "missing_failure_cause");
            reschedule(turn, now);
            return true;
        }
        boolean admissionUncertain = turn.step == Step.POLL && reason == Reason.TURN_UNAVAILABLE
                && turn.admissionGrace != null && now - turn.admissionGrace < 0;
        if (admissionUncertain || (turn.step == Step.CANCEL && reason == Reason.BUSY)) {
            reschedule(turn, now);
            return true;
        }
        if (failure.hasReason() && reason != Reason.BUSY && reason != Reason.DISABLED) {
            log(turn, now, reason.name());
        }
        if (visible) turn.player.sendMessage(chat.failure(turn.player, failure));
        if (turn.step == Step.POLL) discard(id, turn.id);
        return false;
    }

    private boolean receiptSettled(UUID id, Receipt receipt, long now) {
        if (now - receipt.deadline >= 0) {
            logReceipt(receipt, now, "deadline_exceeded");
            return true;
        }
        if (receipt.pending == null) {
            if (now - receipt.retryAt >= 0) receipt.pending = confirm(id, receipt.id);
            return false;
        }
        if (!receipt.pending.isDone()) return false;
        try {
            var result = receipt.pending.join();
            if (result.hasDeliveryConfirmed()) return true;
            if (result.hasFailure()) {
                switch (result.getFailure().getReason()) {
                    case TURN_UNAVAILABLE, DISABLED, INVALID_REQUEST -> {
                        logReceipt(receipt, now, result.getFailure().getReason().name());
                        return true;
                    }
                    default -> { }
                }
            }
        } catch (CompletionException | CancellationException failure) {
            receipt.ipcFailures++;
        }
        receipt.pending = null;
        receipt.retryAt = now + RETRY_INTERVAL;
        return false;
    }

    private static void reschedule(Turn turn, long now) {
        if (turn.step == Step.ASK) {
            // Never repeat model admission after an ambiguous send or a daemon restart.
            turn.step = Step.POLL;
            turn.admissionGrace = now + ADMISSION_GRACE;
        }
        turn.pending = null;
        turn.retryAt = now + RETRY_INTERVAL;
    }

    private void sendStep(UUID id, Turn turn) {
        turn.pending = switch (turn.step) {
            case POLL -> requests.assistant(command(id, turn.id).setPollResult(AssistantSignal.getDefaultInstance()).build());
            case CANCEL -> discard(id, turn.id);
            case ASK -> throw new IllegalStateException("Cannot repeat AI admission");
        };
    }

    private CompletableFuture<AssistantResult> confirm(UUID id, String turnId) {
        return requests.assistant(command(id, turnId).setConfirmDelivery(AssistantSignal.getDefaultInstance()).build());
    }

    private void log(Turn turn, long now, String reason) {
        log(turn.id, turn.step.name(), now - turn.started, turn.ipcFailures, reason);
    }

    private void logReceipt(Receipt receipt, long now, String reason) {
        log(receipt.id, "CONFIRM", now - receipt.started, receipt.ipcFailures, reason);
    }

    private void log(String id, String operation, long elapsed, int failures, String reason) {
        logger.warning("AI turn=" + id + " operation=" + operation + " reason=" + reason
                + " elapsed_ms=" + Duration.ofNanos(elapsed).toMillis()
                + " ipc_failures=" + failures);
    }

    private boolean showReply(UUID id, Turn turn, AssistantReply reply) {
        var player = turn.player;
        var rendered = chat.reply(reply, turn.nativeInteraction,
                question -> followUps.click(id, turn.id, question));
        if (rendered == null) return false;
        player.sendMessage(rendered.component());
        if (rendered.hasCallbacks()) {
            followUps.offer(player, turn.id);
        }
        return true;
    }

    public void quit(Player player) {
        var id = player.getUniqueId();
        followUps.quit(player);
        var turn = turns.get(id);
        if (turn == null || turn.player != player) return;
        if (turn.fetching()) {
            turns.remove(id);
            turn.hideThinking();
            discard(id, turn.id);
        } else detach(id, turn);
    }

    private void detach(UUID id, Turn turn) {
        turn.detached = true;
        turn.hideThinking();
        followUps.revoke(id);
    }

    private void discardIfFetching(UUID id, Turn turn) {
        if (turn.fetching()) discard(id, turn.id);
    }

    private CompletableFuture<AssistantResult> discard(UUID player, String turnId) {
        return requests.assistant(command(player, turnId).setCancelTurn(AssistantSignal.getDefaultInstance()).build());
    }

    private static AssistantCommand.Builder command(UUID id, String turnId) {
        return AssistantCommand.newBuilder().setPlayerUuid(id.toString()).setTurnId(turnId);
    }

    private void send(Player player, Message message, NamedTextColor color, Object... arguments) {
        player.sendMessage(languages.text(languages.language(player), message, color, arguments));
    }

    private enum Step { ASK, POLL, CANCEL }

    private static final class Receipt {
        private final String id;
        private final long started;
        private final long deadline;
        private long retryAt;
        private int ipcFailures;
        private CompletableFuture<AssistantResult> pending;

        private Receipt(Turn turn, long now, CompletableFuture<AssistantResult> pending) {
            id = turn.id;
            started = turn.started;
            deadline = now + RECEIPT_DEADLINE;
            this.pending = pending;
        }
    }

    private static final class Turn {
        private final Player player;
        private final String id;
        private final Set<String> tools;
        private final boolean nativeInteraction;
        private final long started;
        private long deadline;
        private long retryAt;
        private Long admissionGrace;
        private CompletableFuture<AssistantResult> pending;
        private BossBar thinking;
        private long thinkingStarted;
        private long thinkingEstimate;
        private Step step = Step.ASK;
        private boolean detached;
        private int ipcFailures;

        private Turn(Player player, String id, Set<String> tools, boolean nativeInteraction,
                     CompletableFuture<AssistantResult> pending, long started) {
            this.player = player;
            this.id = id;
            this.tools = tools;
            this.nativeInteraction = nativeInteraction;
            this.pending = pending;
            this.started = started;
            this.deadline = started + REPLY_DEADLINE;
        }

        private boolean fetching() {
            return step == Step.ASK || step == Step.POLL;
        }

        private void showThinking(Component label, long now, long estimate) {
            thinkingStarted = now;
            thinkingEstimate = estimate;
            thinking = BossBar.bossBar(label, BossBar.MIN_PROGRESS, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            player.showBossBar(thinking);
            player.sendMessage(label);
        }

        private void updateThinking(Component label) {
            if (thinking != null && !thinking.name().equals(label)) thinking.name(label);
        }

        private void updateProgress(long now) {
            if (thinking == null) return;
            double elapsed = (double) (now - thinkingStarted) / thinkingEstimate;
            double percent = elapsed <= 1 ? 80 * elapsed : 80 + 15 * (elapsed - 1) / elapsed;
            float progress = (int) percent / 100.0f;
            if (progress > thinking.progress()) thinking.progress(progress);
        }

        private void hideThinking() {
            if (thinking == null) return;
            player.hideBossBar(thinking);
            thinking = null;
        }

        private boolean expired(long now) {
            return now - deadline >= 0;
        }
    }
}
