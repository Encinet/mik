package org.encinet.mik.feature.community.chat.runtime;

import org.encinet.mik.feature.community.chat.bridge.ChatIngress;
import org.encinet.mik.feature.community.chat.bridge.ChatMessagePublisher;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.SocialChatPublisher;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Shared semantic preparation and main-thread commit; outputs cannot change acceptance. */
public final class ChatService implements ChatIngress, ChatMessagePublisher {
    private final ChatProcessor processor = new ChatProcessor();
    private final UnaryOperator<ChatSender> playerIdentity;
    private final Consumer<Runnable> primaryThread;
    private final SocialChatPublisher socialOutput;
    private final BiConsumer<String, RuntimeException> failure;
    private volatile Routing routing;

    private record Routing(ChatParticipants participants, BiConsumer<ChatDelivery, Set<UUID>> gameOutput,
                           ChatMessagePublisher webOutput) {
        private Routing {
            Objects.requireNonNull(participants);
            Objects.requireNonNull(gameOutput);
            Objects.requireNonNull(webOutput);
        }
    }

    public ChatService(UnaryOperator<ChatSender> playerIdentity, Consumer<Runnable> primaryThread,
            SocialChatPublisher socialOutput,
            BiConsumer<String, RuntimeException> failure) {
        this.playerIdentity = Objects.requireNonNull(playerIdentity);
        this.primaryThread = Objects.requireNonNull(primaryThread);
        this.socialOutput = Objects.requireNonNull(socialOutput);
        this.failure = Objects.requireNonNull(failure);
    }
    public void connect(ChatParticipants participants, BiConsumer<ChatDelivery, Set<UUID>> gameOutput,
            ChatMessagePublisher webOutput) {
        routing = new Routing(participants, gameOutput, webOutput);
    }
    public void disconnect() { routing = null; }
    public ChatMessage process(ChatSubmission submission) {
        Objects.requireNonNull(submission);
        if (submission.origin() instanceof ChatOrigin.Social
                && submission.conversation().channel() != ChatChannel.PUBLIC)
            throw new IllegalArgumentException("Social ingress requires public chat");
        if (submission.origin() instanceof ChatOrigin.Plugin)
            throw new IllegalArgumentException("Server output uses the trusted server publisher");
        if (submission.origin() instanceof ChatOrigin.Player) {
            submission = new ChatSubmission(submission.id(), submission.origin(), playerIdentity.apply(submission.sender()),
                    submission.conversation(), submission.sourceText(), submission.references(),
                    submission.receivedAt(), submission.processingContext());
        }
        return processor.process(submission);
    }
    public ChatMessage acceptFinalContent(ChatMessage prepared, ChatContent finalContent) {
        return processor.acceptFinalContent(prepared, finalContent);
    }
    @Override public void accept(ChatSubmission submission) {
        if (!(submission.origin() instanceof ChatOrigin.Social))
            throw new IllegalArgumentException("External ingress requires a verified social source");
        Routing admitted = routing;
        if (admitted == null) return;
        primaryThread.accept(() -> {
            if (routing == admitted)
                commit(ChatDelivery.managed(process(submission), null, null), admitted);
        });
    }
    @Override public void publish(ChatDelivery delivery) {
        Objects.requireNonNull(delivery);
        Routing admitted = routing;
        if (admitted == null) return;
        primaryThread.accept(() -> {
            if (routing == admitted) commit(delivery, admitted);
        });
    }
    private void commit(ChatDelivery delivery, Routing admitted) {
        if (routing != admitted) return;
        Set<UUID> expected = Set.copyOf(admitted.participants().gameRecipients(delivery.audience()));
        // A native sender must still pass game authentication and channel permissions.
        // A Social author's optional player binding is never an online requirement.
        if (delivery.message().submission().origin() instanceof ChatOrigin.Minecraft source
                && !expected.contains(source.playerId())) return;
        Set<UUID> viewers = delivery.game() instanceof ChatDelivery.Native nativeGame
                ? nativeGame.viewers() : expected;
        boolean relay = delivery.game() instanceof ChatDelivery.Managed
                || (delivery.game() instanceof ChatDelivery.Native nativeGame
                && viewers.containsAll(nativeGame.channelAudience()) && viewers.containsAll(expected));
        attempt("game", () -> admitted.gameOutput().accept(delivery, viewers));
        if (!relay || routing != admitted) return;
        attempt("web", () -> admitted.webOutput().publish(delivery));
        if (routing != admitted) return;
        if (delivery.message().submission().conversation().channel() == ChatChannel.PUBLIC) {
            attempt("social", () -> {
                var report = socialOutput.publish(delivery.message());
                if (report.backpressured() > 0)
                    failure.accept("social", new IllegalStateException("Backpressured for " + report.backpressured() + " platform(s)"));
            });
        }
    }
    private void attempt(String output, Runnable action) {
        try { action.run(); } catch (RuntimeException error) { failure.accept(output, error); }
    }
}
