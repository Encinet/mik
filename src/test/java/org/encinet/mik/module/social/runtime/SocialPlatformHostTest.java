package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.identity.IdentityBinding;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.api.SocialReplyChannel;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.module.social.command.SocialCommand;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandDispatcher;
import org.encinet.mik.module.social.command.SocialCommandInput;
import org.encinet.mik.module.social.command.SocialCommandSpec;
import org.encinet.mik.module.social.command.SocialCommandSyntax;
import org.encinet.mik.module.social.chat.SocialChatPlatformSession;
import org.encinet.mik.module.social.chat.SocialChatOutboundPolicy;
import org.encinet.mik.module.social.chat.SocialChatRoute;
import org.encinet.mik.module.social.chat.SocialChatMentionRequest;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;
import org.encinet.mik.module.social.chat.SocialDirectedNotice;
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.safety.SocialContentSafetyFilter;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialPlatformHostTest {

    @Test
    void directedNoticeMentionsOnlyBoundPlayersInJoinedConversations() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("matrix", "!", 4);
        adapter.chatRoutes = List.of(
                new SocialChatRoute(new SocialConversation("absent", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.always()),
                new SocialChatRoute(new SocialConversation("joined", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.prefixed("#", true)));
        UUID player = UUID.randomUUID();
        IdentityBinding binding = new IdentityBinding(player, "Alex",
                new ExternalIdentityKey("matrix", "example.org", "", "@alex:example.org"),
                "Alex", java.time.Instant.EPOCH, java.time.Instant.EPOCH);
        adapter.mentionResolver = (conversation, requests) ->
                conversation.id().equals("joined")
                        ? new SocialChatMentionResolution(Map.of(player,
                        requests.getFirst().candidates()))
                        : SocialChatMentionResolution.empty();
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), guard(),
                (platform, message) -> CompletableFuture.completedFuture(null),
                id -> id.equals(player) ? List.of(binding) : List.of(),
                Logger.getAnonymousLogger())) {
            host.start();
            host.publishDirectedNotice(new SocialDirectedNotice(player, "Alex", "Read the notice"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingChat.isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(1, adapter.outgoingChat.size());
            assertTrue(adapter.outgoingChat.getFirst().content().nodes().getFirst()
                    instanceof ChatNode.PlayerMention);
            assertEquals(List.of("@alex:example.org"), adapter.outgoingMentionResolutions
                    .getFirst().targetsFor(player).stream()
                    .map(identity -> identity.key().subject()).toList());
        }
    }

    @Test
    void twoPlatformsShareCommandsButKeepPrefixesAndDeduplicationIndependent()
            throws Exception {
        MemoryAdapter first = new MemoryAdapter("first", "!", 4);
        MemoryAdapter second = new MemoryAdapter("second", "/", 4);
        try (SocialPlatformHost host = host(List.of(first, second), echoCommand(
                argument -> argument))) {
            host.start();
            Capture firstReply = new Capture();
            Capture secondReply = new Capture();

            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    first.emit("same-event", "!echo one", firstReply));
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    second.emit("same-event", "/echo two", secondReply));
            assertTrue(firstReply.sent.await(2, TimeUnit.SECONDS));
            assertTrue(secondReply.sent.await(2, TimeUnit.SECONDS));
            assertEquals("First:one", firstReply.documents.getFirst().title());
            assertEquals("Second:two", secondReply.documents.getFirst().title());
            assertEquals(SocialEventSink.Acceptance.IGNORED,
                    first.emit("same-event", "!echo duplicate", new Capture()));
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    first.emit("wrong-prefix", "/echo ignored", new Capture()));
        }
    }

    @Test
    void fullWorkerReturnsRetryAndForgetsDeduplicationKey() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MemoryAdapter adapter = new MemoryAdapter("one", "/", 1);
        SocialCommand<String, String> blocking = echoCommand(argument -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
            return argument;
        });
        try (SocialPlatformHost host = host(List.of(adapter), blocking)) {
            host.start();
            Capture first = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("first", "/echo block", first));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(SocialEventSink.Acceptance.RETRY_LATER,
                    adapter.emit("retry", "/echo retry", new Capture()));
            release.countDown();
            assertTrue(first.sent.await(2, TimeUnit.SECONDS));
            SocialEventSink.Acceptance retried = SocialEventSink.Acceptance.RETRY_LATER;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (retried == SocialEventSink.Acceptance.RETRY_LATER
                    && System.nanoTime() < deadline) {
                retried = adapter.emit("retry", "/echo retry", new Capture());
                if (retried == SocialEventSink.Acceptance.RETRY_LATER) {
                    Thread.sleep(10);
                }
            }
            assertEquals(SocialEventSink.Acceptance.ACCEPTED, retried);
        } finally {
            release.countDown();
        }
    }

    @Test
    void pendingReplyRemainsInsideTheConcurrencyLimit() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("one", "/", 1);
        CompletableFuture<Void> pendingReply = new CompletableFuture<>();
        CountDownLatch replyStarted = new CountDownLatch(1);
        SocialReplyChannel slowReply = document -> {
            replyStarted.countDown();
            return pendingReply;
        };
        try (SocialPlatformHost host = host(List.of(adapter),
                echoCommand(argument -> argument))) {
            host.start();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("first", "/echo first", slowReply));
            assertTrue(replyStarted.await(2, TimeUnit.SECONDS));

            assertEquals(SocialEventSink.Acceptance.RETRY_LATER,
                    adapter.emit("second", "/echo second", new Capture()));

            pendingReply.complete(null);
            Capture recovered = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    admitEventually(adapter, "second", "/echo second", recovered));
            assertTrue(recovered.sent.await(2, TimeUnit.SECONDS));
        } finally {
            pendingReply.complete(null);
        }
    }

    @Test
    void reloadDropsOldResultsAndIdentityLeaseTracksReadyState() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        IdentityPlatform identity = new IdentityPlatform(
                "leased", "Leased", "/bind leased {code}");
        MemoryAdapter adapter = new MemoryAdapter("leased", "/", 2, identity);
        AtomicInteger leases = new AtomicInteger();
        SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                    return argument;
                })), platform -> {
                    leases.incrementAndGet();
                    return leases::decrementAndGet;
                }, guard(), Logger.getAnonymousLogger());
        try (host) {
            host.start();
            assertEquals(1, leases.get());
            Capture oldReply = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("old", "/echo wait", oldReply));
            assertTrue(entered.await(2, TimeUnit.SECONDS));

            host.reload("leased");
            assertEquals(1, leases.get());
            release.countDown();
            assertFalse(oldReply.sent.await(200, TimeUnit.MILLISECONDS));

            adapter.status(SocialSessionStatus.starting("memory://leased"));
            assertEquals(0, leases.get());
            adapter.status(SocialSessionStatus.ready("memory://leased"));
            assertEquals(1, leases.get());
            adapter.enabled = false;
            host.reload("leased");
            assertEquals(0, leases.get());
            assertEquals(SocialPlatformState.DISABLED,
                    host.status("leased").orElseThrow().state());
        } finally {
            release.countDown();
        }
    }

    @Test
    void onePrepareFailureDoesNotPreventAnotherPlatformFromStarting() {
        MemoryAdapter failed = new MemoryAdapter("failed", "!", 1);
        failed.failPrepare = true;
        MemoryAdapter ready = new MemoryAdapter("ready", "/", 1);
        try (SocialPlatformHost host = host(List.of(failed, ready),
                echoCommand(argument -> argument))) {
            host.start();
            assertEquals(SocialPlatformState.FAILED,
                    host.status("failed").orElseThrow().state());
            assertEquals(SocialPlatformState.READY,
                    host.status("ready").orElseThrow().state());
            ready.status(SocialSessionStatus.failed("memory://ready", "transport failed"));
            assertEquals(SocialPlatformState.FAILED,
                    host.status("ready").orElseThrow().state());
            assertEquals("transport failed",
                    host.status("ready").orElseThrow().lastError());
        }
    }

    @Test
    void handlerAndAsynchronousReplyFailuresDoNotFailThePlatform() throws Exception {
        CountDownLatch failedHandlerEntered = new CountDownLatch(1);
        MemoryAdapter adapter = new MemoryAdapter("resilient", "/", 1);
        try (SocialPlatformHost host = host(List.of(adapter), echoCommand(argument -> {
            if (argument.equals("handler-failure")) {
                failedHandlerEntered.countDown();
                throw new IllegalStateException("expected handler failure");
            }
            return argument;
        }))) {
            host.start();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("handler", "/echo handler-failure", new Capture()));
            assertTrue(failedHandlerEntered.await(2, TimeUnit.SECONDS));

            SocialReplyChannel failedReply = document ->
                    CompletableFuture.failedFuture(
                            new IllegalStateException("expected reply failure"));
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    admitEventually(adapter, "reply", "/echo reply-failure", failedReply));

            Capture recovered = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    admitEventually(adapter, "recovered", "/echo recovered", recovered));
            assertTrue(recovered.sent.await(2, TimeUnit.SECONDS));
            assertEquals(SocialPlatformState.READY,
                    host.status("resilient").orElseThrow().state());
        }
    }

    @Test
    void sharedChatBoundaryDeduplicatesInboundAndPublishesOutbound() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("bridge", "!", 4);
        adapter.chatRoutes = List.of(
                new SocialChatRoute(new SocialConversation(
                        "always", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.always()),
                new SocialChatRoute(new SocialConversation(
                        "prefix", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.prefixed("#", true)));
        List<ChatSubmission> incoming = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), guard(),
                (platform, message) -> {
                    assertEquals("bridge", platform.id());
                    incoming.add(message);
                    delivered.countDown();
                    return CompletableFuture.completedFuture(null);
                }, Logger.getAnonymousLogger());
        try (host) {
            host.start();
            assertEquals(SocialEventSink.Acceptance.UNHANDLED,
                    adapter.emit("plain", "hello", new Capture()));

            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("chat-event", "always", "Alice",
                            "hello Minecraft", new Capture()));
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("hello Minecraft"), incoming.stream()
                    .map(ChatSubmission::sourceText).toList());
            assertEquals(List.of("Alice"), incoming.stream()
                    .map(message -> message.sender().displayName()).toList());
            assertEquals(SocialEventSink.Acceptance.IGNORED,
                    adapter.emit("chat-event", "always", "Alice",
                            "hello Minecraft", new Capture()));

            host.publish(ChatTestMessages.minecraft("Steve", "#hello Matrix"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingChat.isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(List.of("#hello Matrix", "hello Matrix"),
                    adapter.outgoingChat.stream()
                            .map(message -> message.submission().sourceText()).toList());
        }
    }

    @Test
    void inboundSocialChatBypassesOutboundKeywordFiltering() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("bridge", "!", 4);
        adapter.contentSafetyEnabled = true;
        List<ChatSubmission> incoming = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        SocialContentGuard directionalGuard = SocialContentGuard.available(
                SocialContentSafetyFilter.compile(List.of("js"), "test.txt"),
                SocialDocument.of("Blocked", SocialDocument.Tone.WARNING));
        SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), directionalGuard,
                (platform, message) -> {
                    incoming.add(message);
                    delivered.countDown();
                    return CompletableFuture.completedFuture(null);
                }, Logger.getAnonymousLogger());
        try (host) {
            host.start();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("js-event", "room", "Alice", "js",
                            new Capture()));
            assertTrue(delivered.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("js"), incoming.stream()
                    .map(ChatSubmission::sourceText).toList());

            host.publish(ChatTestMessages.minecraft("Steve", "js"));
            host.publish(ChatTestMessages.minecraft("Steve", "safe source",
                    ChatContent.plain("expanded js")));
            host.publish(ChatTestMessages.minecraft("Steve", "safe"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingChat.isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(List.of("safe"), adapter.outgoingChat.stream()
                    .map(message -> message.submission().sourceText()).toList());
        }
    }

    @Test
    void strippedRoutePrefixDoesNotParticipateInOutboundFiltering()
            throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("bridge", "!", 4);
        adapter.contentSafetyEnabled = true;
        adapter.chatRoutes = List.of(new SocialChatRoute(
                new SocialConversation("prefix", SocialConversation.Type.GROUP),
                SocialChatOutboundPolicy.prefixed("trigger", true)));
        SocialContentGuard directionalGuard = SocialContentGuard.available(
                SocialContentSafetyFilter.compile(List.of("trigger"), "test.txt"),
                SocialDocument.of("Blocked", SocialDocument.Tone.WARNING));
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), directionalGuard,
                Logger.getAnonymousLogger())) {
            host.start();
            host.publish(ChatTestMessages.minecraft("Steve", "trigger safe"));

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingChat.isEmpty()
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertEquals(List.of("safe"), adapter.outgoingChat.stream()
                    .map(message -> message.submission().sourceText()).toList());
        }
    }

    @Test
    void forwardedSocialOriginIsNotSentBackToItsSourcePlatform()
            throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("matrix", "!", 4);
        try (SocialPlatformHost host = host(
                List.of(adapter), echoCommand(argument -> argument))) {
            host.start();
            org.encinet.mik.module.chat.model.ChatSubmission submission =
                    new org.encinet.mik.module.chat.model.ChatSubmission(
                            org.encinet.mik.module.chat.model.ChatMessageId.external(
                                    "matrix", "$event"),
                            new org.encinet.mik.module.chat.model.ChatOrigin.Social(
                                    "matrix", "$event"),
                            new org.encinet.mik.module.chat.model.ChatSender(
                                    "Alice", Optional.empty(), Optional.empty(),
                                    "", "", ""),
                            new org.encinet.mik.module.chat.model.ChatConversation(
                                    org.encinet.mik.module.chat.model.ChatConversation.Kind.SOCIAL,
                                    "matrix:room"),
                            "hello", org.encinet.mik.module.chat.model.ChatReferences.empty(),
                            java.time.Instant.EPOCH,
                            org.encinet.mik.module.chat.model.ChatProcessingContext.external());

            host.publish(new ChatMessage(
                    submission, ChatContent.plain("hello"), java.util.Set.of()));
            Thread.sleep(50);

            assertTrue(adapter.outgoingChat.isEmpty());
        }
    }

    @Test
    void outboundMentionsAreResolvedSeparatelyForEveryRoute() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("matrix", "!", 4);
        adapter.chatRoutes = List.of(
                new SocialChatRoute(new SocialConversation(
                        "joined", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.always()),
                new SocialChatRoute(new SocialConversation(
                        "absent", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.always()));
        UUID target = UUID.randomUUID();
        IdentityBinding matrixBinding = new IdentityBinding(target, "Alex",
                new ExternalIdentityKey("matrix", "example.org", "",
                        "@alex:example.org"), "Alex Matrix",
                java.time.Instant.EPOCH, java.time.Instant.EPOCH);
        IdentityBinding otherPlatform = new IdentityBinding(target, "Alex",
                new ExternalIdentityKey("qq", "app", "group", "qq-alex"),
                "Alex QQ", java.time.Instant.EPOCH, java.time.Instant.EPOCH);
        adapter.mentionResolver = (conversation, requests) -> {
            if (!conversation.id().equals("joined")) {
                return SocialChatMentionResolution.empty();
            }
            SocialChatMentionRequest request = requests.getFirst();
            return new SocialChatMentionResolution(Map.of(
                    request.playerId(), request.candidates()));
        };
        SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), guard(),
                (platform, message) -> CompletableFuture.completedFuture(null),
                playerId -> playerId.equals(target)
                        ? List.of(matrixBinding, otherPlatform) : List.of(),
                Logger.getAnonymousLogger());
        try (host) {
            host.start();
            host.publish(ChatTestMessages.minecraft("Steve", "Alex@",
                    new ChatContent(List.of(new ChatNode.PlayerMention(
                            target, "Alex", ChatStyle.EMPTY)))));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingMentionResolutions.size() < 2
                    && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertEquals(2, adapter.outgoingMentionRequests.size());
            assertEquals(List.of("@alex:example.org"),
                    adapter.outgoingMentionRequests.getFirst().getFirst()
                            .candidates().stream()
                            .map(identity -> identity.key().subject()).toList());
            assertEquals(List.of("@alex:example.org"),
                    adapter.outgoingMentionResolutions.getFirst()
                            .targetsFor(target).stream()
                            .map(identity -> identity.key().subject()).toList());
            assertTrue(adapter.outgoingMentionResolutions.get(1)
                    .targetsFor(target).isEmpty());
        }
    }

    private static SocialCommand<String, String> echoCommand(
            java.util.function.UnaryOperator<String> handler
    ) {
        return new SocialCommand<>() {
            private final SocialCommandSpec spec =
                    SocialCommandSpec.explicit("test.echo", "echo");

            @Override
            public SocialCommandSpec spec() {
                return spec;
            }

            @Override
            public String decode(SocialCommandInput input) {
                return input.rawArgument();
            }

            @Override
            public String handle(SocialCommandContext context, String argument) {
                return handler.apply(argument);
            }

            @Override
            public SocialDocument present(SocialCommandContext context, String result) {
                return SocialDocument.of(context.platform().displayName() + ':' + result,
                        SocialDocument.Tone.INFO);
            }
        };
    }

    private static SocialCommandDispatcher dispatcher(SocialCommand<?, ?> command) {
        return new SocialCommandDispatcher(List.of(command));
    }

    private static SocialPlatformHost host(
            List<MemoryAdapter> adapters,
            SocialCommand<?, ?> command
    ) {
        return new SocialPlatformHost(adapters, dispatcher(command),
                SocialIdentityLeaseManager.unavailable(), guard(), Logger.getAnonymousLogger());
    }

    private static SocialContentGuard guard() {
        return SocialContentGuard.available(SocialContentSafetyFilter.disabled(),
                SocialDocument.of("Blocked", SocialDocument.Tone.WARNING));
    }

    private static SocialEventSink.Acceptance admitEventually(
            MemoryAdapter adapter,
            String eventId,
            String body,
            SocialReplyChannel reply
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        SocialEventSink.Acceptance acceptance;
        do {
            acceptance = adapter.emit(eventId, body, reply);
            if (acceptance == SocialEventSink.Acceptance.RETRY_LATER) {
                Thread.sleep(10);
            }
        } while (acceptance == SocialEventSink.Acceptance.RETRY_LATER
                && System.nanoTime() < deadline);
        return acceptance;
    }

    private static final class Capture implements SocialReplyChannel {
        private final List<SocialDocument> documents = new CopyOnWriteArrayList<>();
        private final CountDownLatch sent = new CountDownLatch(1);

        @Override
        public java.util.concurrent.CompletionStage<Void> send(SocialDocument document) {
            documents.add(document);
            sent.countDown();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class MemoryAdapter implements SocialPlatformAdapter {
        private final SocialPlatformDescriptor descriptor;
        private final String prefix;
        private final int concurrency;
        private volatile SocialPlatformRuntimeContext context;
        private volatile boolean enabled = true;
        private volatile boolean failPrepare;
        private volatile boolean contentSafetyEnabled;
        private final List<ChatMessage> outgoingChat =
                new CopyOnWriteArrayList<>();
        private final List<List<SocialChatMentionRequest>> outgoingMentionRequests =
                new CopyOnWriteArrayList<>();
        private final List<SocialChatMentionResolution> outgoingMentionResolutions =
                new CopyOnWriteArrayList<>();
        private volatile java.util.function.BiFunction<SocialConversation,
                List<SocialChatMentionRequest>, SocialChatMentionResolution>
                mentionResolver = (conversation, requests) ->
                SocialChatMentionResolution.empty();
        private volatile List<SocialChatRoute> chatRoutes = List.of(
                new SocialChatRoute(new SocialConversation(
                        "room", SocialConversation.Type.GROUP),
                        SocialChatOutboundPolicy.always()));

        private MemoryAdapter(String id, String prefix, int concurrency) {
            this(id, prefix, concurrency, null);
        }

        private MemoryAdapter(String id, String prefix, int concurrency,
                              IdentityPlatform identityPlatform) {
            descriptor = identityPlatform == null
                    ? new SocialPlatformDescriptor(id, capitalize(id))
                    : new SocialPlatformDescriptor(id, capitalize(id), identityPlatform);
            this.prefix = prefix;
            this.concurrency = concurrency;
        }

        @Override
        public SocialPlatformDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Optional<SocialPlatformPlan> prepare() {
            if (failPrepare) {
                throw new IllegalStateException("broken config");
            }
            if (!enabled) {
                return Optional.empty();
            }
            SocialRuntimePolicy policy = new SocialRuntimePolicy(concurrency,
                    new SocialOutputPolicy(contentSafetyEnabled, 1_000));
            return Optional.of(new SocialPlatformPlan() {
                @Override
                public SocialRuntimePolicy runtimePolicy() {
                    return policy;
                }

                @Override
                public SocialCommandSyntax commandSyntax() {
                    return SocialCommandSyntax.caseInsensitive(Language.ZH_CN, prefix);
                }

                @Override
                public SocialPlatformSession open(SocialPlatformRuntimeContext openedContext) {
                    context = openedContext;
                    openedContext.status().update(SocialSessionStatus.ready(
                            "memory://" + descriptor.id()));
                    return new SocialChatPlatformSession() {
                        @Override
                        public List<SocialChatRoute> chatRoutes() {
                            return chatRoutes;
                        }

                        @Override
                        public java.util.concurrent.CompletionStage<Void> sendChat(
                                SocialConversation conversation,
                                ChatMessage message
                        ) {
                            outgoingChat.add(message);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public SocialChatMentionResolution resolveMentions(
                                SocialConversation conversation,
                                List<SocialChatMentionRequest> requests
                        ) {
                            outgoingMentionRequests.add(List.copyOf(requests));
                            return mentionResolver.apply(conversation, requests);
                        }

                        @Override
                        public java.util.concurrent.CompletionStage<Void> sendChat(
                                SocialConversation conversation,
                                ChatMessage message,
                                SocialChatMentionResolution mentions
                        ) {
                            outgoingChat.add(message);
                            outgoingMentionResolutions.add(mentions);
                            return CompletableFuture.completedFuture(null);
                        }

                        @Override
                        public void close() {
                            openedContext.status().update(SocialSessionStatus.stopped(
                                    "memory://" + descriptor.id()));
                        }
                    };
                }
            });
        }

        private SocialEventSink.Acceptance emit(
                String eventId,
                String body,
                SocialReplyChannel reply
        ) {
            return emit(eventId, "room", "social-user", body, reply);
        }

        private SocialEventSink.Acceptance emit(
                String eventId,
                String conversationId,
                String senderDisplayName,
                String body,
                SocialReplyChannel reply
        ) {
            return context.events().accept(new SocialInboundMessage(eventId,
                    new SocialConversation(conversationId,
                            SocialConversation.Type.GROUP),
                    Optional.empty(), senderDisplayName,
                    new SocialInboundMessage.Text(body), false,
                    org.encinet.mik.module.social.api.SocialMessageReferences.empty(),
                    reply));
        }

        private void status(SocialSessionStatus status) {
            context.status().update(status);
        }

        private static String capitalize(String value) {
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }
    }
}
