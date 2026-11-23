package org.encinet.mik.feature.community.chat.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.ChatRelay;
import org.encinet.mik.feature.community.chat.bridge.ChatRelay.Outcome;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/** Local game delivery and asynchronous cross-endpoint submission. */
public final class GameChatService {
    private final ChatProcessor processor;
    private final UnaryOperator<ChatSender> playerIdentity;
    private final Consumer<Runnable> primaryThread;
    private final BiConsumer<String, RuntimeException> failure;
    private volatile Routing routing;

    private record Routing(ChatParticipants participants, ChatRelay relay, Function<Player, BooleanSupplier> authentication) {
        private Routing {
            Objects.requireNonNull(participants);
            Objects.requireNonNull(relay);
            Objects.requireNonNull(authentication);
        }
    }

    public GameChatService(ChatProcessor processor, UnaryOperator<ChatSender> playerIdentity, Consumer<Runnable> primaryThread,
            BiConsumer<String, RuntimeException> failure) {
        this.processor = Objects.requireNonNull(processor);
        this.playerIdentity = Objects.requireNonNull(playerIdentity);
        this.primaryThread = Objects.requireNonNull(primaryThread);
        this.failure = Objects.requireNonNull(failure);
    }
    public void connect(ChatParticipants participants, ChatRelay relay, Function<Player, BooleanSupplier> authentication) {
        routing = new Routing(participants, relay, authentication);
    }
    public void disconnect() { routing = null; }
    public ChatMessage process(ChatSubmission submission) {
        return processor.process(new ChatSubmission(submission.id(), submission.origin(), playerIdentity.apply(submission.sender()),
                submission.conversation(), submission.sourceText(),
                submission.receivedAt(), submission.processingContext()));
    }
    public ChatMessage acceptFinalContent(ChatMessage prepared, ChatContent finalContent) {
        return processor.acceptFinalContent(prepared, finalContent);
    }
    public void publish(ChatDelivery delivery, Player sender, BiConsumer<ChatDelivery, Set<UUID>> gameOutput,
            Consumer<Outcome> sharedFeedback) {
        var admitted = routing;
        if (admitted == null) return;
        if (!(delivery.message().submission().origin() instanceof ChatOrigin.Minecraft source)) {
            throw new IllegalArgumentException("A game publication requires its Minecraft sender");
        }
        primaryThread.accept(() -> {
            if (routing != admitted || !current(sender, source.playerId())) return;
            var session = admitted.authentication().apply(sender);
            BooleanSupplier authorized = () -> routing == admitted && session.getAsBoolean();
            if (!authorized.getAsBoolean()) return;
            commit(delivery, sender, source.playerId(), admitted, authorized, gameOutput, sharedFeedback);
        });
    }

    private void commit(ChatDelivery delivery, Player sender, UUID senderId, Routing admitted, BooleanSupplier authorized,
            BiConsumer<ChatDelivery, Set<UUID>> gameOutput, Consumer<Outcome> sharedFeedback) {
        var audience = delivery.audience();
        var expected = Set.copyOf(admitted.participants().gameRecipients(audience));
        if (!expected.contains(senderId)) return;
        var viewers = delivery.game() instanceof ChatDelivery.Native nativeGame ? nativeGame.viewers() : expected;
        // Never expand the final audience of a native chat event into a new platform route.
        boolean canShare = !(delivery.game() instanceof ChatDelivery.Native nativeGame)
                || (viewers.containsAll(nativeGame.channelAudience()) && viewers.containsAll(expected)
                && (audience.participants().isEmpty() || audience.participants().containsAll(viewers)));
        UUID target = delivery.target() == null ? null : delivery.target().minecraftId().orElseThrow();
        boolean needsWeb = target != null && !(expected.contains(target) && viewers.contains(target));
        if (!needsWeb) {
            deliverGame(delivery, viewers, gameOutput);
            if (canShare && authorized.getAsBoolean()) relay(admitted.relay(), delivery, false, authorized);
            return;
        }
        if (!canShare) {
            if (authorized.getAsBoolean()) sharedFeedback.accept(Outcome.NOT_SUBMITTED);
            return;
        }
        relay(admitted.relay(), delivery, true, authorized).whenComplete((outcome, error) -> {
            if (routing != admitted) return;
            primaryThread.accept(() -> {
                if (!authorized.getAsBoolean() || !current(sender, senderId)) return;
                Outcome confirmed = error == null && outcome != null ? outcome : Outcome.UNCONFIRMED;
                if (confirmed == Outcome.SAVED) deliverGame(delivery, Set.of(senderId), gameOutput);
                sharedFeedback.accept(confirmed);
            });
        });
    }

    private static boolean current(Player sender, UUID id) {
        return sender.isOnline() && Bukkit.getPlayer(id) == sender;
    }

    private CompletionStage<Outcome> relay(ChatRelay relay, ChatDelivery delivery, boolean needsWeb, BooleanSupplier authorized) {
        try {
            return relay.publish(delivery, needsWeb, authorized);
        } catch (RuntimeException error) {
            failure.accept("relay", error);
            return CompletableFuture.completedFuture(Outcome.UNCONFIRMED);
        }
    }

    private void deliverGame(ChatDelivery delivery, Set<UUID> viewers, BiConsumer<ChatDelivery, Set<UUID>> output) {
        try { output.accept(delivery, viewers); }
        catch (RuntimeException error) { failure.accept("game", error); }
    }
}
