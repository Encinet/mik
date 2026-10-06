package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.chat.model.ChatConversation;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatMessageId;
import org.encinet.mik.module.chat.model.ChatOrigin;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.chat.model.ChatReferences;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.module.social.chat.SocialChatGateway;
import org.encinet.mik.module.social.chat.SocialChatPlatformSession;
import org.encinet.mik.module.social.chat.SocialChatMentionRequest;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;
import org.encinet.mik.module.social.chat.SocialMentionBindingDirectory;
import org.encinet.mik.module.social.chat.SocialChatRoute;
import org.encinet.mik.module.chat.bridge.SocialChatPublishReport;
import org.encinet.mik.module.social.chat.SocialDirectedNotice;
import org.encinet.mik.module.social.command.SocialCommandDispatcher;

import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

/** Owns every mutable resource belonging to exactly one platform generation. */
final class SocialPlatformGeneration implements AutoCloseable {
    private static final int MAXIMUM_OBSERVED_CONVERSATIONS = 2_048;

    private final Object lock = new Object();
    private final SocialPlatformDescriptor descriptor;
    private final long number;
    private final SocialCommandDispatcher commands;
    private final SocialIdentityLeaseManager identityLeases;
    private final SocialContentGuard contentGuard;
    private final SocialChatGateway chatGateway;
    private final SocialMentionBindingDirectory mentionBindings;
    private final SocialCommandProcessor processor;
    private final Logger logger;
    private final LinkedHashSet<String> conversations = new LinkedHashSet<>();
    private final LongAdder inboundAccepted = new LongAdder();
    private final LongAdder inboundBackpressured = new LongAdder();
    private final LongAdder outboundAccepted = new LongAdder();
    private final LongAdder outboundBackpressured = new LongAdder();
    private final LongAdder deliveryFailures = new LongAdder();

    private SocialPlatformState state = SocialPlatformState.STOPPED;
    private String endpoint = "";
    private String lastError = "";
    private SocialPlatformPlan plan;
    private SocialPlatformSession session;
    private SocialTaskExecutor executor;
    private SocialEventDeduplicator deduplicator;
    private AutoCloseable identityLease;
    private boolean closed;

    SocialPlatformGeneration(
            SocialPlatformDescriptor descriptor,
            long number,
            SocialCommandDispatcher commands,
            SocialIdentityLeaseManager identityLeases,
            SocialContentGuard contentGuard,
            SocialChatGateway chatGateway,
            SocialMentionBindingDirectory mentionBindings,
            Logger logger
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.number = number;
        this.commands = Objects.requireNonNull(commands, "commands");
        this.identityLeases = Objects.requireNonNull(identityLeases, "identityLeases");
        this.contentGuard = Objects.requireNonNull(contentGuard, "contentGuard");
        this.chatGateway = Objects.requireNonNull(chatGateway, "chatGateway");
        this.mentionBindings = Objects.requireNonNull(
                mentionBindings, "mentionBindings");
        this.processor = new SocialCommandProcessor(contentGuard, logger);
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void disable() {
        synchronized (lock) {
            ensureNotStarted();
            state = SocialPlatformState.DISABLED;
        }
    }

    void start(SocialPlatformPlan preparedPlan) {
        SocialPlatformPlan checkedPlan = Objects.requireNonNull(preparedPlan, "plan");
        SocialPlatformSession opened;
        synchronized (lock) {
            ensureNotStarted();
            contentGuard.validate(checkedPlan.runtimePolicy().output());
            plan = checkedPlan;
            deduplicator = new SocialEventDeduplicator(
                    checkedPlan.runtimePolicy().maximumDeduplicationEntries(),
                    checkedPlan.runtimePolicy().deduplicationTtl());
            executor = new SocialTaskExecutor(
                    checkedPlan.runtimePolicy().maximumConcurrentTasks(),
                    "mik-social-" + descriptor.id() + '-' + number + '-');
            state = SocialPlatformState.STARTING;
        }
        try {
            opened = Objects.requireNonNull(checkedPlan.open(
                            new SocialPlatformRuntimeContext(
                                    this::accept, this::updateStatus)),
                    "Social platform plan returned a null session");
        } catch (RuntimeException error) {
            synchronized (lock) {
                state = SocialPlatformState.FAILED;
                lastError = SocialCommandProcessor.rootMessage(error);
            }
            closeRuntime(true);
            throw error;
        }
        boolean closeImmediately;
        synchronized (lock) {
            session = opened;
            closeImmediately = closed || terminalState();
        }
        if (closeImmediately) {
            closeSession(opened);
            return;
        }
        try {
            opened.start();
        } catch (RuntimeException error) {
            fail(error);
            throw error;
        }
    }

    void fail(RuntimeException error) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            state = SocialPlatformState.FAILED;
            lastError = SocialCommandProcessor.rootMessage(error);
        }
        closeRuntime(false);
    }

    SocialPlatformSnapshot snapshot() {
        synchronized (lock) {
            return new SocialPlatformSnapshot(descriptor, number, state,
                    endpoint, lastError, conversations.size(),
                    inboundAccepted.sum(), inboundBackpressured.sum(),
                    outboundAccepted.sum(), outboundBackpressured.sum(),
                    deliveryFailures.sum());
        }
    }

    Set<String> conversations() {
        synchronized (lock) {
            return Set.copyOf(conversations);
        }
    }

    private SocialEventSink.Acceptance accept(SocialInboundMessage message) {
        Objects.requireNonNull(message, "message");
        final SocialCommandDispatcher.PreparedInvocation invocation;
        final ChatSubmission chat;
        final String deduplicationKey;
        synchronized (lock) {
            if (closed) {
                return SocialEventSink.Acceptance.IGNORED;
            }
            if (message.authorIsBot()) {
                return SocialEventSink.Acceptance.IGNORED;
            }
            if (state != SocialPlatformState.READY) {
                return SocialEventSink.Acceptance.RETRY_LATER;
            }
            rememberConversation(message.conversation().id());
            invocation = commands.resolve(plan.commandSyntax(), message).orElse(null);
            chat = invocation == null ? chatSubmission(message) : null;
            if (invocation == null && chat == null) {
                return SocialEventSink.Acceptance.UNHANDLED;
            }
            deduplicationKey = deduplicationKey(
                    message.conversation().id(), message.eventId());
            if (!deduplicator.accept(deduplicationKey)) {
                return SocialEventSink.Acceptance.IGNORED;
            }
            boolean submitted;
            try {
                submitted = invocation == null
                        ? executor.submitOrdered(
                        "inbound-chat\u0000" + message.conversation().id(),
                        () -> deliverChat(chat))
                        : executor.submit(() -> processor.execute(
                        descriptor, message, invocation,
                        plan.runtimePolicy().output(), this::resultIsCurrent));
            } catch (RuntimeException error) {
                deduplicator.forget(deduplicationKey);
                throw error;
            }
            if (!submitted) {
                deduplicator.forget(deduplicationKey);
                inboundBackpressured.increment();
                return SocialEventSink.Acceptance.RETRY_LATER;
            }
            inboundAccepted.increment();
        }
        return SocialEventSink.Acceptance.ACCEPTED;
    }

    SocialChatPublishReport.Admission publishChat(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        synchronized (lock) {
            if (closed || state != SocialPlatformState.READY
                    || !(session instanceof SocialChatPlatformSession chatSession)) {
                return SocialChatPublishReport.Admission.UNAVAILABLE;
            }
            boolean admitted = executor.submitOrdered(
                    "outbound\u0000" + message.submission().conversation().id(),
                    () -> sendChat(chatSession, message));
            if (admitted) {
                outboundAccepted.increment();
                return SocialChatPublishReport.Admission.ACCEPTED;
            }
            outboundBackpressured.increment();
            return SocialChatPublishReport.Admission.BACKPRESSURED;
        }
    }

    SocialChatPublishReport.Admission publishDirectedNotice(SocialDirectedNotice notice) {
        Objects.requireNonNull(notice, "notice");
        synchronized (lock) {
            if (closed || state != SocialPlatformState.READY
                    || !(session instanceof SocialChatPlatformSession chatSession)) {
                return SocialChatPublishReport.Admission.UNAVAILABLE;
            }
            boolean admitted = executor.submitOrdered("outbound-board-notice",
                    () -> sendDirectedNotice(chatSession, notice));
            if (admitted) {
                outboundAccepted.increment();
                return SocialChatPublishReport.Admission.ACCEPTED;
            }
            outboundBackpressured.increment();
            return SocialChatPublishReport.Admission.BACKPRESSURED;
        }
    }

    private void sendDirectedNotice(SocialChatPlatformSession chatSession,
                                    SocialDirectedNotice notice) {
        if (!chatSessionIsCurrent(chatSession)) return;
        try {
            if (!contentGuard.allowsOutboundText(notice.body(), plan.runtimePolicy().output()))
                return;
            List<ExternalIdentity> candidates = mentionBindings.findByPlayer(notice.playerId())
                    .stream()
                    .filter(binding -> binding.externalKey().platform().equals(descriptor.id()))
                    .map(binding -> new ExternalIdentity(binding.externalKey(),
                            binding.externalDisplayName()))
                    .toList();
            if (candidates.isEmpty()) return;
            ChatContent content = new ChatContent(List.of(
                    new ChatNode.PlayerMention(notice.playerId(), notice.playerName(),
                            ChatStyle.EMPTY),
                    new ChatNode.Text(" " + notice.body(), ChatStyle.EMPTY)));
            ChatSubmission submission = new ChatSubmission(
                    ChatMessageId.random(), new ChatOrigin.Plugin("community-board", Set.of()),
                    new ChatSender("Mik", Optional.empty(), Optional.empty(), "", "", ""),
                    ChatConversation.minecraftPublic(), content.plainText(),
                    ChatReferences.empty(), Instant.now(), ChatProcessingContext.external());
            ChatMessage message = new ChatMessage(submission, content, Set.of());
            SocialChatMentionRequest request = new SocialChatMentionRequest(
                    notice.playerId(), notice.playerName(), candidates);
            for (SocialChatRoute route : List.copyOf(chatSession.chatRoutes())) {
                SocialChatMentionResolution resolution = chatSession.resolveMentions(
                        route.conversation(), List.of(request));
                if (resolution.targetsFor(notice.playerId()).isEmpty()) continue;
                chatSession.sendChat(route.conversation(), message, resolution)
                        .toCompletableFuture().join();
                break;
            }
        } catch (RuntimeException error) {
            deliveryFailures.increment();
            if (chatSessionIsCurrent(chatSession))
                logger.warning("Could not deliver a directed community notice to "
                        + descriptor.displayName() + ": "
                        + SocialCommandProcessor.rootMessage(error));
        }
    }

    private void deliverChat(ChatSubmission message) {
        try {
            Objects.requireNonNull(chatGateway.deliver(descriptor, message),
                    "Social chat gateway returned null").toCompletableFuture().join();
        } catch (RuntimeException error) {
            deliveryFailures.increment();
            if (resultIsCurrent()) {
                logger.warning("Could not deliver " + descriptor.displayName()
                        + " chat to Minecraft: "
                        + SocialCommandProcessor.rootMessage(error));
            }
        }
    }

    private void sendChat(
            SocialChatPlatformSession chatSession,
            ChatMessage message
    ) {
        if (!chatSessionIsCurrent(chatSession)) {
            return;
        }
        try {
            List<CompletableFuture<Void>> sends = new ArrayList<>();
            for (SocialChatRoute route : List.copyOf(chatSession.chatRoutes())) {
                if (message.submission().origin().visitedPlatforms()
                        .contains(descriptor.id())) {
                    continue;
                }
                route.outbound().select(message.submission().sourceText()).ifPresent(body -> {
                    var richBody = route.outbound().mode()
                            == org.encinet.mik.module.social.chat.SocialChatOutboundPolicy.Mode.PREFIX
                            && route.outbound().stripPrefix()
                            ? message.content().withoutLeadingTrigger(
                            route.outbound().prefix())
                            : message.content();
                    if (!contentGuard.allowsOutboundText(
                            body, plan.runtimePolicy().output())
                            || !contentGuard.allowsOutboundText(
                            richBody.plainText(),
                            plan.runtimePolicy().output())) {
                        return;
                    }
                    ChatSubmission original = message.submission();
                    ChatSubmission routed = new ChatSubmission(
                            original.id(), original.origin(), original.sender(),
                            original.conversation(), body, original.references(),
                            original.receivedAt(), original.processingContext());
                    ChatMessage selected = new ChatMessage(
                            routed, richBody, message.effects());
                    List<SocialChatMentionRequest> requests = mentionRequests(selected);
                    SocialChatMentionResolution mentions = Objects.requireNonNull(
                            chatSession.resolveMentions(
                                    route.conversation(), requests),
                            "Social chat session returned null mention resolution");
                    sends.add(Objects.requireNonNull(
                            chatSession.sendChat(
                                    route.conversation(), selected, mentions),
                            "Social chat session returned null").toCompletableFuture());
                });
            }
            CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException error) {
            deliveryFailures.increment();
            if (chatSessionIsCurrent(chatSession)) {
                logger.warning("Could not deliver Minecraft chat to "
                        + descriptor.displayName() + ": "
                        + SocialCommandProcessor.rootMessage(error));
            }
        }
    }

    private List<SocialChatMentionRequest> mentionRequests(ChatMessage message) {
        LinkedHashMap<UUID, SocialChatMentionRequest> requests =
                new LinkedHashMap<>();
        message.content().nodes().stream()
                .filter(org.encinet.mik.module.chat.model.ChatNode.PlayerMention.class::isInstance)
                .map(org.encinet.mik.module.chat.model.ChatNode.PlayerMention.class::cast)
                .forEach(mention -> requests.computeIfAbsent(
                        mention.playerId(), playerId -> {
                            List<ExternalIdentity> candidates = mentionBindings
                                    .findByPlayer(playerId).stream()
                                    .filter(binding -> binding.externalKey().platform()
                                            .equals(descriptor.id()))
                                    .map(binding -> new ExternalIdentity(
                                            binding.externalKey(),
                                            binding.externalDisplayName()))
                                    .toList();
                            return new SocialChatMentionRequest(playerId,
                                    mention.playerName(), candidates);
                        }));
        return List.copyOf(requests.values());
    }

    private ChatSubmission chatSubmission(SocialInboundMessage message) {
        if (!(message.content() instanceof SocialInboundMessage.Text text)
                || text.body().isBlank()
                || !(session instanceof SocialChatPlatformSession chatSession)
                || chatSession.chatRoutes().stream().noneMatch(route ->
                route.conversation().id().equals(message.conversation().id()))) {
            return null;
        }
        Optional<ExternalIdentity> identity = message.authenticatedIdentity();
        ChatSender sender = new ChatSender(
                message.senderDisplayName(), Optional.empty(), identity,
                "", "", "");
        return new ChatSubmission(
                ChatMessageId.external(descriptor.id(), message.eventId()),
                new ChatOrigin.Social(descriptor.id(), message.eventId()),
                sender,
                new ChatConversation(ChatConversation.Kind.SOCIAL,
                        descriptor.id() + ':' + message.conversation().id()),
                text.body(), references(message), Instant.now(),
                ChatProcessingContext.external());
    }

    private ChatReferences references(SocialInboundMessage message) {
        Optional<ChatReferences.Reference> reply = message.references()
                .repliedAuthor().map(this::reference);
        List<ChatReferences.Mention> mentions = message.references()
                .mentionSpans().stream()
                .map(span -> new ChatReferences.Mention(
                        span.start(), span.end(), span.identity()))
                .toList();
        return new ChatReferences(reply, Optional.empty(), mentions);
    }

    private ChatReferences.Reference reference(ExternalIdentity identity) {
        String displayName = identity.displayName().isBlank()
                ? identity.key().subject() : identity.displayName();
        return new ChatReferences.Reference(
                identity.key().subject(), displayName);
    }

    private boolean chatSessionIsCurrent(SocialChatPlatformSession candidate) {
        synchronized (lock) {
            return !closed && state == SocialPlatformState.READY
                    && session == candidate;
        }
    }

    private String deduplicationKey(String conversationId, String eventId) {
        return descriptor.id() + '\u0000' + conversationId + '\u0000' + eventId;
    }

    private void updateStatus(SocialSessionStatus status) {
        Objects.requireNonNull(status, "status");
        boolean terminal = false;
        synchronized (lock) {
            if (closed || terminalState()) {
                return;
            }
            endpoint = status.endpoint();
            lastError = status.lastError();
            switch (status.state()) {
                case STARTING -> {
                    releaseIdentityLease();
                    state = SocialPlatformState.STARTING;
                }
                case READY -> {
                    try {
                        acquireIdentityLease();
                        state = SocialPlatformState.READY;
                        lastError = "";
                    } catch (RuntimeException error) {
                        state = SocialPlatformState.FAILED;
                        lastError = SocialCommandProcessor.rootMessage(error);
                        terminal = true;
                    }
                }
                case FAILED -> {
                    releaseIdentityLease();
                    state = SocialPlatformState.FAILED;
                    terminal = true;
                }
                case STOPPED -> {
                    releaseIdentityLease();
                    state = SocialPlatformState.STOPPED;
                    terminal = true;
                }
            }
        }
        if (terminal) {
            closeRuntime(false);
        }
    }

    private boolean resultIsCurrent() {
        synchronized (lock) {
            return !closed && state != SocialPlatformState.FAILED
                    && state != SocialPlatformState.STOPPED
                    && state != SocialPlatformState.DISABLED;
        }
    }

    private void rememberConversation(String conversationId) {
        conversations.remove(conversationId);
        while (conversations.size() >= MAXIMUM_OBSERVED_CONVERSATIONS) {
            conversations.remove(conversations.iterator().next());
        }
        conversations.add(conversationId);
    }

    private void acquireIdentityLease() {
        if (identityLease == null) {
            descriptor.identityPlatform().ifPresent(platform ->
                    identityLease = identityLeases.register(platform));
        }
    }

    private void releaseIdentityLease() {
        AutoCloseable lease = identityLease;
        identityLease = null;
        if (lease == null) {
            return;
        }
        try {
            lease.close();
        } catch (Exception error) {
            logger.warning("Could not release identity platform " + descriptor.id()
                    + ": " + SocialCommandProcessor.rootMessage(error));
        }
    }

    private boolean terminalState() {
        return state == SocialPlatformState.FAILED
                || state == SocialPlatformState.STOPPED
                || state == SocialPlatformState.DISABLED;
    }

    private void ensureNotStarted() {
        if (closed || plan != null || state != SocialPlatformState.STOPPED) {
            throw new IllegalStateException("Social platform generation has already started");
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            state = SocialPlatformState.STOPPED;
            conversations.clear();
        }
        closeRuntime(true);
    }

    private void closeRuntime(boolean closeTransport) {
        final SocialPlatformSession currentSession;
        final SocialTaskExecutor currentExecutor;
        synchronized (lock) {
            releaseIdentityLease();
            currentSession = closeTransport || terminalState() ? session : null;
            if (currentSession != null) {
                session = null;
            }
            currentExecutor = terminalState() || closed ? executor : null;
            if (currentExecutor != null) {
                executor = null;
            }
            if (deduplicator != null && (terminalState() || closed)) {
                deduplicator.clear();
            }
        }
        if (currentSession != null) {
            closeSession(currentSession);
        }
        if (currentExecutor != null) {
            currentExecutor.close();
        }
    }

    private void closeSession(SocialPlatformSession currentSession) {
        try {
            currentSession.close();
        } catch (RuntimeException error) {
            logger.warning("Could not close social platform " + descriptor.id()
                    + ": " + SocialCommandProcessor.rootMessage(error));
        }
    }
}
