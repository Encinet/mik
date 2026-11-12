package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.social.command.SocialCommandDefinition;
import org.encinet.mik.feature.community.social.command.SocialCommandExecution;
import org.encinet.mik.feature.community.social.command.SocialArguments;
import org.encinet.mik.feature.community.social.command.SocialPresentationContext;
import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.SocialTestReplies;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.feature.community.identity.IdentityPlatform;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.identity.IdentityBinding;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialMessageReferences;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.feature.community.social.api.SocialPlatformSession;
import org.encinet.mik.feature.community.social.api.SocialReplyChannel;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.feature.community.social.command.SocialCommand;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandDispatcher;
import org.encinet.mik.feature.community.social.command.SocialCommandSpec;
import org.encinet.mik.feature.community.social.command.SocialCommandSyntax;
import org.encinet.mik.feature.community.social.chat.SocialChatPlatformSession;
import org.encinet.mik.feature.community.social.chat.SocialChatPolicy;
import org.encinet.mik.feature.community.social.chat.SocialChatRoute;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionRequest;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionResolution;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.Map;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialPlatformHostTest {

    @Test
    void eachDestinationLocalizesSemanticContentUsingItsOwnLanguage() throws Exception {
        MemoryAdapter chinese = new MemoryAdapter("qq", "/", 4);
        MemoryAdapter german = new MemoryAdapter("matrix", "!", 4);
        german.defaultLanguage = Language.DE_DE;
        chinese.chatRoutes = german.chatRoutes = List.of(new SocialChatRoute(
                new SocialConversation("room", SocialConversation.Type.GROUP),
                SocialChatPolicy.always(), SocialChatPolicy.prefixed("#", true)));
        try (var host = new SocialPlatformHost(List.of(chinese, german), dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), guard(),
                org.encinet.mik.shared.i18n.FluentTestSupport.languages(), Logger.getAnonymousLogger())) {
            host.start();
            var content = new org.encinet.mik.feature.community.chat.model.ChatContent(List.of(
                    new org.encinet.mik.feature.community.chat.model.ChatNode.Text("# [image] ",
                            org.encinet.mik.feature.community.chat.model.ChatStyle.EMPTY),
                    new org.encinet.mik.feature.community.chat.model.ChatNode.Placeholder(
                            org.encinet.mik.feature.community.chat.model.ChatPlaceholder.IMAGE,
                            org.encinet.mik.feature.community.chat.model.ChatStyle.EMPTY)));
            var original = org.encinet.mik.test.ChatTestMessages.minecraft("Alice", "# [image] [image]", content).submission();
            var metadata = new org.encinet.mik.feature.community.chat.model.ChatReferences(
                    Optional.empty(), Optional.empty(), List.of(),
                    List.of(new org.encinet.mik.feature.community.chat.model.ChatPlaceholder.Span(10, 17,
                            org.encinet.mik.feature.community.chat.model.ChatPlaceholder.IMAGE)));
            var source = new ChatSubmission(original.id(), original.origin(), original.sender(), original.conversation(),
                    original.sourceText(), metadata, original.receivedAt(), original.processingContext());
            host.publish(new ChatMessage(source, content, java.util.Set.of()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((chinese.outgoingChat.isEmpty() || german.outgoingChat.isEmpty()) && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("[image] [图片]", chinese.outgoingChat.getFirst().content().plainText());
            assertEquals("[image] [Bild]", german.outgoingChat.getFirst().content().plainText());
            assertEquals("# [image] [image]", content.plainText());
            assertEquals(8, german.outgoingChat.getFirst().submission().references().placeholders().getFirst().start());
        }
    }

    @Test
    void trustedRepliesWorkAfterStartupAndReloadWithUnavailableModeration() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("one", "!", 4);
        adapter.contentSafetyEnabled = true;
        SocialContentGuard unavailable = SocialContentGuard.reloadable(() -> {
            throw new IllegalArgumentException("Invalid moderation configuration");
        }, _ -> SocialDocument.of(TRUSTED, "Hidden", SocialDocument.Tone.WARNING));
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(_ -> "Trusted help")),
                SocialIdentityLeaseManager.unavailable(), unavailable,
                new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
            host.start();
            assertEquals(SocialPlatformState.READY, host.status("one").orElseThrow().state());
            Capture first = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("first", "!echo", first));
            assertTrue(first.sent.await(2, TimeUnit.SECONDS));
            assertEquals("One:Trusted help", first.documents.getFirst().title());

            host.reload("one");
            assertEquals(SocialPlatformState.READY, host.status("one").orElseThrow().state());
            Capture afterReload = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("second", "!echo", afterReload));
            assertTrue(afterReload.sent.await(2, TimeUnit.SECONDS));
            assertEquals("One:Trusted help", afterReload.documents.getFirst().title());
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
        SocialReplyChannel slowReply = SocialTestReplies.channel(_ -> {
            replyStarted.countDown();
            return pendingReply;
        });
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
    void identityAdaptersWithoutMembershipMonitoringCannotBecomeReady() {
        var adapter = new MemoryAdapter("unmonitored", "/", 2,
                new IdentityPlatform("unmonitored", "Unmonitored", "/bind {code}"));
        adapter.membershipMonitoring = false;
        var leases = new AtomicInteger();
        try (var host = new SocialPlatformHost(List.of(adapter), dispatcher(echoCommand(argument -> argument)),
                (_, _) -> { leases.incrementAndGet(); return leases::decrementAndGet; }, guard(), new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
            host.start();
            var status = host.status("unmonitored").orElseThrow();
            assertEquals(SocialPlatformState.FAILED, status.state());
            assertTrue(status.lastError().contains("community membership monitoring"));
            assertEquals(0, leases.get());
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
                })), (_, _) -> {
                    leases.incrementAndGet();
                    return leases::decrementAndGet;
                }, guard(), new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger());
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

            SocialReplyChannel failedReply = SocialTestReplies.channel(_ ->
                    CompletableFuture.failedFuture(new IllegalStateException("expected reply failure")));
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
                        SocialChatPolicy.always(), SocialChatPolicy.always()),
                new SocialChatRoute(new SocialConversation(
                        "prefix", SocialConversation.Type.GROUP),
                        SocialChatPolicy.always(), SocialChatPolicy.prefixed("#", true)));
        List<ChatSubmission> incoming = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), guard(),
                message -> {
                    assertEquals("bridge", ((org.encinet.mik.feature.community.chat.model.ChatOrigin.Social) message.origin()).platformId());
                    incoming.add(message);
                    delivered.countDown();
                    return CompletableFuture.completedFuture(null);
                }, new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger());
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
            var origin = assertInstanceOf(org.encinet.mik.feature.community.chat.model.ChatOrigin.Social.class,
                    incoming.getFirst().origin());
            assertEquals("Bridge", origin.platformName());
            assertEquals(0x123456, origin.sourceColor());
            assertEquals("always", origin.conversationId());
            assertEquals("chat-event", origin.eventId());
            assertEquals(org.encinet.mik.feature.community.chat.model.ChatConversation.minecraftPublic(),
                    incoming.getFirst().conversation());
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
    void inboundSocialChatBypassesOutboundModeration() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("bridge", "!", 4);
        adapter.contentSafetyEnabled = true;
        List<ChatSubmission> incoming = new CopyOnWriteArrayList<>();
        List<String> moderated = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        SocialContentGuard directionalGuard = SocialContentGuard.available(
                text -> {
                    moderated.add(text);
                    return !text.contains("js");
                },
                SocialDocument.of(TRUSTED, "Blocked", SocialDocument.Tone.WARNING));
        SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), directionalGuard,
                message -> {
                    incoming.add(message);
                    delivered.countDown();
                    return CompletableFuture.completedFuture(null);
                }, new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger());
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
            assertEquals(List.of("Steve: js", "Steve: expanded js", "Steve: safe"), moderated);
        }
    }

    @Test
    void qqInboundPrefixFiltersChatWhileCommandsAndOutboundKeepTheirOwnRules() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString("""
                chat-bridge:
                  routes:
                    - group-openid: stripped
                      inbound:
                        mode: prefix
                        prefix: '#'
                        strip-prefix: true
                      outbound:
                        mode: always
                    - group-openid: preserved
                      inbound:
                        mode: prefix
                        prefix: '~'
                        strip-prefix: false
                      outbound:
                        mode: prefix
                        prefix: '>'
                """);
        MemoryAdapter adapter = new MemoryAdapter("qq", "/", 4);
        adapter.chatRoutes = org.encinet.mik.feature.community.social.platform.qq.QqPlatformConfig
                .from(yaml).chatRoutes();
        LinkedBlockingQueue<ChatSubmission> incoming = new LinkedBlockingQueue<>();
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)), SocialIdentityLeaseManager.unavailable(),
                guard(), message -> {
                    incoming.add(message);
                    return CompletableFuture.completedFuture(null);
                }, new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
            host.start();
            for (String body : List.of("ordinary text", "#   ", ">wrong direction")) {
                assertEquals(SocialEventSink.Acceptance.UNHANDLED,
                        adapter.emit("filtered-" + body, "stripped", "Alice", body, new Capture()));
            }
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("prefixed", "stripped", "Alice", "# 你好", new Capture()));
            ChatSubmission stripped = incoming.poll(2, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(stripped);
            assertEquals("你好", stripped.sourceText());
            assertEquals(SocialEventSink.Acceptance.IGNORED,
                    adapter.emit("prefixed", "stripped", "Alice", "# 你好", new Capture()));
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("kept", "preserved", "Alice", "~hello", new Capture()));
            ChatSubmission preserved = incoming.poll(2, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(preserved);
            assertEquals("~hello", preserved.sourceText());

            Capture command = new Capture();
            assertEquals(SocialEventSink.Acceptance.ACCEPTED,
                    adapter.emit("command", "stripped", "Alice", "/echo works", command));
            assertTrue(command.sent.await(2, TimeUnit.SECONDS));
            assertEquals("Qq:works", command.documents.getFirst().title());
            assertTrue(incoming.isEmpty());

            host.publish(ChatTestMessages.minecraft("Steve", "ordinary outbound"));
            host.publish(ChatTestMessages.minecraft("Steve", ">selected outbound"));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingChat.size() < 3 && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(List.of("ordinary outbound", ">selected outbound", "selected outbound"),
                    adapter.outgoingChat.stream().map(message -> message.submission().sourceText()).toList());
        }
    }

    @Test
    void strippingInboundPrefixRelocatesTrustedMentionsAndRetainsReplyTarget() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("qq", "/", 4);
        SocialConversation conversation = new SocialConversation("room", SocialConversation.Type.GROUP);
        adapter.chatRoutes = List.of(new SocialChatRoute(conversation,
                SocialChatPolicy.prefixed("#", true), SocialChatPolicy.always()));
        ExternalIdentity trigger = new ExternalIdentity(new ExternalIdentityKey("qq", "app", "room", "trigger"), "Trigger");
        ExternalIdentity alex = new ExternalIdentity(new ExternalIdentityKey("qq", "app", "room", "alex"), "Alex");
        ExternalIdentity replied = new ExternalIdentity(new ExternalIdentityKey("qq", "app", "room", "bob"), "Bob");
        LinkedBlockingQueue<ChatSubmission> incoming = new LinkedBlockingQueue<>();
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)), SocialIdentityLeaseManager.unavailable(),
                guard(), message -> {
                    incoming.add(message);
                    return CompletableFuture.completedFuture(null);
                }, new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
            host.start();
            SocialInboundMessage message = new SocialInboundMessage("mentions", conversation,
                    Optional.empty(), "Alice", new SocialInboundMessage.Text("#  @Alex hi"), false,
                    new SocialMessageReferences(List.of(trigger, alex), Optional.of(replied), List.of(
                            new SocialMessageReferences.MentionSpan(0, 1, trigger),
                            new SocialMessageReferences.MentionSpan(3, 8, alex))), new Capture());
            assertEquals(SocialEventSink.Acceptance.ACCEPTED, adapter.context.events().accept(message));
            ChatSubmission selected = incoming.poll(2, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(selected);
            assertEquals("@Alex hi", selected.sourceText());
            assertEquals(List.of(new org.encinet.mik.feature.community.chat.model.ChatReferences.Mention(0, 5, alex)),
                    selected.references().mentions());
            assertEquals("bob", selected.references().replyTo().orElseThrow().id());
            assertEquals("Bob", selected.references().replyTo().orElseThrow().displayName());
        }
    }

    @Test
    void richMessagesBridgeWithoutRunningQuotedCommandsAndRespectRoutePrefixes() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("qq", "/", 4);
        SocialConversation room = new SocialConversation("room", SocialConversation.Type.GROUP);
        SocialConversation prefixed = new SocialConversation("prefixed", SocialConversation.Type.GROUP);
        adapter.chatRoutes = List.of(new SocialChatRoute(room, SocialChatPolicy.always(), SocialChatPolicy.always()),
                new SocialChatRoute(prefixed, SocialChatPolicy.prefixed("#", true), SocialChatPolicy.always()));
        LinkedBlockingQueue<ChatSubmission> incoming = new LinkedBlockingQueue<>();
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(_ -> { throw new AssertionError("Quoted commands must not execute"); })),
                SocialIdentityLeaseManager.unavailable(), guard(), message -> {
                    incoming.add(message);
                    return CompletableFuture.completedFuture(null);
                }, new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
            host.start();
            String media = "📎 https://cdn.example.org/file.zip";
            SocialInboundMessage quote = new SocialInboundMessage("quote", room, Optional.empty(),
                    new SocialInboundMessage.Text("/echo quoted " + media, ""), false, new Capture());
            assertEquals(SocialEventSink.Acceptance.ACCEPTED, adapter.context.events().accept(quote));
            ChatSubmission forwarded = incoming.poll(2, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(forwarded);
            assertEquals("/echo quoted " + media, forwarded.sourceText());
            SocialInboundMessage attachment = new SocialInboundMessage("attachment", prefixed, Optional.empty(),
                    new SocialInboundMessage.Text("# " + media, "#"), false, new Capture());
            assertEquals(SocialEventSink.Acceptance.ACCEPTED, adapter.context.events().accept(attachment));
            ChatSubmission selected = incoming.poll(2, TimeUnit.SECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(selected);
            assertEquals(media, selected.sourceText());
        }
    }

    @Test
    void serverNoticesReachEveryConfiguredDestinationInEachPlatformsLanguage() throws Exception {
        MemoryAdapter qq = new MemoryAdapter("qq", "/", 4);
        MemoryAdapter matrix = new MemoryAdapter("matrix", "!", 4);
        matrix.defaultLanguage = Language.EN_US;
        for (MemoryAdapter adapter : List.of(qq, matrix)) {
            adapter.chatRoutes = List.of(
                    new SocialChatRoute(new SocialConversation("first", SocialConversation.Type.GROUP),
                            SocialChatPolicy.always(), SocialChatPolicy.always()),
                    new SocialChatRoute(new SocialConversation("second", SocialConversation.Type.GROUP),
                            SocialChatPolicy.prefixed("#", true), SocialChatPolicy.prefixed("#", true)));
        }
        try (SocialPlatformHost host = host(List.of(qq, matrix), echoCommand(argument -> argument))) {
            host.start();
            var report = host.publishNotice(language -> SocialDocument.of(
                    SocialDocument.Moderation.REQUIRED, language.id(), SocialDocument.Tone.INFO,
                    SocialDocument.paragraph("New announcement\nFull body")).localized(language));
            assertEquals(2, report.accepted());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((qq.outgoingNotices.size() < 2 || matrix.outgoingNotices.size() < 2)
                    && System.nanoTime() < deadline) Thread.sleep(10);
            for (MemoryAdapter adapter : List.of(qq, matrix)) {
                assertEquals(List.of("first", "second"), adapter.outgoingNotices.stream()
                        .map(notice -> notice.conversation().id()).toList());
                assertTrue(adapter.outgoingNotices.stream().allMatch(notice ->
                        notice.document().title().equals(adapter.defaultLanguage.id())
                                && notice.document().plainText().contains("Full body")
                                && notice.document().language() == adapter.defaultLanguage));
                assertTrue(adapter.outgoingChat.isEmpty());
            }
        }
    }

    @Test
    void failedNoticeDestinationDoesNotSuppressOtherDestinationsOrPlatforms() throws Exception {
        MemoryAdapter qq = new MemoryAdapter("qq", "/", 4);
        qq.chatRoutes = List.of(
                new SocialChatRoute(new SocialConversation("fails", SocialConversation.Type.GROUP),
                        SocialChatPolicy.always(), SocialChatPolicy.always()),
                new SocialChatRoute(new SocialConversation("works", SocialConversation.Type.GROUP),
                        SocialChatPolicy.always(), SocialChatPolicy.always()));
        qq.noticeSender = notice -> notice.conversation().id().equals("fails")
                ? CompletableFuture.failedFuture(new IllegalStateException("destination unavailable"))
                : CompletableFuture.completedFuture(null);
        MemoryAdapter matrix = new MemoryAdapter("matrix", "!", 4);
        try (SocialPlatformHost host = host(List.of(qq, matrix), echoCommand(argument -> argument))) {
            host.start();
            host.publishNotice(_ -> SocialDocument.of(SocialDocument.Moderation.REQUIRED,
                    "Announcement", SocialDocument.Tone.INFO, SocialDocument.paragraph("Body")));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((qq.outgoingNotices.size() < 2 || matrix.outgoingNotices.isEmpty()
                    || host.status("qq").orElseThrow().deliveryFailures() == 0)
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(List.of("fails", "works"), qq.outgoingNotices.stream()
                    .map(notice -> notice.conversation().id()).toList());
            assertEquals(1, matrix.outgoingNotices.size());
            assertEquals(1, host.status("qq").orElseThrow().deliveryFailures());
            assertEquals(0, host.status("matrix").orElseThrow().deliveryFailures());
        }
    }

    @Test
    void noticeModerationUsesEachPlatformsPolicyAndDoesNotEmitBlockedPlaceholders() throws Exception {
        MemoryAdapter guarded = new MemoryAdapter("qq", "/", 4);
        guarded.contentSafetyEnabled = true;
        MemoryAdapter unguarded = new MemoryAdapter("matrix", "!", 4);
        List<String> checks = new CopyOnWriteArrayList<>();
        SocialContentGuard rejecting = SocialContentGuard.available(text -> {
            checks.add(text);
            return false;
        }, SocialDocument.of(TRUSTED, "Blocked", SocialDocument.Tone.WARNING));
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(guarded, unguarded),
                dispatcher(echoCommand(argument -> argument)), SocialIdentityLeaseManager.unavailable(),
                rejecting, new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
            host.start();
            host.publishNotice(_ -> SocialDocument.of(SocialDocument.Moderation.REQUIRED,
                    "Announcement", SocialDocument.Tone.INFO, SocialDocument.paragraph("Review this")));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while ((checks.isEmpty() || unguarded.outgoingNotices.isEmpty())
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals(List.of("Announcement\n\nReview this"), checks);
            assertTrue(guarded.outgoingNotices.isEmpty());
            assertEquals(1, unguarded.outgoingNotices.size());
        }
    }

    @Test
    void reloadDiscardsQueuedNoticesFromTheOldPlatformGeneration() throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("qq", "/", 1);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CompletableFuture<Void> firstSend = new CompletableFuture<>();
        adapter.noticeSender = notice -> {
            if (notice.document().title().equals("old-first")) {
                firstStarted.countDown();
                return firstSend;
            }
            return CompletableFuture.completedFuture(null);
        };
        AtomicInteger discardedFactoryCalls = new AtomicInteger();
        try (SocialPlatformHost host = host(List.of(adapter), echoCommand(argument -> argument))) {
            host.start();
            host.publishNotice(_ -> SocialDocument.of(TRUSTED, "old-first", SocialDocument.Tone.INFO));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            host.publishNotice(_ -> {
                discardedFactoryCalls.incrementAndGet();
                return SocialDocument.of(TRUSTED, "old-queued", SocialDocument.Tone.INFO);
            });
            assertTrue(host.reload("qq"));
            host.publishNotice(_ -> SocialDocument.of(TRUSTED, "new", SocialDocument.Tone.INFO));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (adapter.outgoingNotices.size() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
            firstSend.complete(null);
            assertEquals(List.of("old-first", "new"), adapter.outgoingNotices.stream()
                    .map(notice -> notice.document().title()).toList());
            assertEquals(0, discardedFactoryCalls.get());
        } finally {
            firstSend.complete(null);
        }
    }

    @Test
    void strippedRoutePrefixDoesNotParticipateInOutboundFiltering()
            throws Exception {
        MemoryAdapter adapter = new MemoryAdapter("bridge", "!", 4);
        adapter.contentSafetyEnabled = true;
        adapter.chatRoutes = List.of(new SocialChatRoute(
                new SocialConversation("prefix", SocialConversation.Type.GROUP),
                SocialChatPolicy.always(), SocialChatPolicy.prefixed("trigger", true)));
        SocialContentGuard directionalGuard = SocialContentGuard.available(
                text -> !text.contains("trigger"),
                SocialDocument.of(TRUSTED, "Blocked", SocialDocument.Tone.WARNING));
        try (SocialPlatformHost host = new SocialPlatformHost(List.of(adapter),
                dispatcher(echoCommand(argument -> argument)),
                SocialIdentityLeaseManager.unavailable(), directionalGuard,
                new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger())) {
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
            org.encinet.mik.feature.community.chat.model.ChatSubmission submission =
                    new org.encinet.mik.feature.community.chat.model.ChatSubmission(
                            org.encinet.mik.feature.community.chat.model.ChatMessageId.external(
                                    "matrix", "!room:example.org", "$event"),
                            new org.encinet.mik.feature.community.chat.model.ChatOrigin.Social(
                                    "matrix", "Matrix", 0x55FFFF, "!room:example.org", "$event"),
                            new org.encinet.mik.feature.community.chat.model.ChatSender(
                                    "Alice", Optional.empty(), Optional.empty(),
                                    "", "", "", 0xFFFFFF),
                            org.encinet.mik.feature.community.chat.model.ChatConversation.minecraftPublic(),
                            "hello", org.encinet.mik.feature.community.chat.model.ChatReferences.empty(),
                            java.time.Instant.EPOCH,
                            org.encinet.mik.feature.community.chat.model.ChatProcessingContext.external());

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
                        SocialChatPolicy.always(), SocialChatPolicy.always()),
                new SocialChatRoute(new SocialConversation(
                        "absent", SocialConversation.Type.GROUP),
                        SocialChatPolicy.always(), SocialChatPolicy.always()));
        UUID target = UUID.randomUUID();
        IdentityBinding matrixBinding = new IdentityBinding(UUID.randomUUID(), target, "Alex",
                new ExternalIdentityKey("matrix", "example.org", "",
                        "@alex:example.org"), "Alex Matrix", "",
                java.time.Instant.EPOCH, java.time.Instant.EPOCH);
        IdentityBinding otherPlatform = new IdentityBinding(UUID.randomUUID(), target, "Alex",
                new ExternalIdentityKey("qq", "app", "group", "qq-alex"),
                "Alex QQ", "", java.time.Instant.EPOCH, java.time.Instant.EPOCH);
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
                _ -> CompletableFuture.completedFuture(null),
                playerId -> playerId.equals(target)
                        ? List.of(matrixBinding, otherPlatform) : List.of(),
                new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger());
        try (host) {
            host.start();
            host.publish(ChatTestMessages.minecraft("Steve", "@Alex",
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

            @Override public SocialCommandDefinition<String> definition() {
                return new SocialCommandDefinition<>(spec, Optional.empty(),
                        SocialArguments.text(org.encinet.mik.shared.i18n.Message.AI_SOCIAL_COMMAND_PARAMETERS));
            }
            @Override public SocialCommandExecution<String> execute(SocialCommandContext context, String argument) {
                return SocialCommandExecution.localized(handler.apply(argument), context.syntax().defaultLanguage());
            }
            @Override public SocialDocument present(SocialPresentationContext context, String result) {
                return SocialDocument.of(TRUSTED, context.platform().displayName() + ':' + result,
                        SocialDocument.Tone.INFO).localized(context.language());
            }
        };
    }

    private static SocialCommandDispatcher dispatcher(SocialCommand<?, ?> command) {
        return org.encinet.mik.feature.community.social.command.SocialCommandTestSupport.dispatcher(List.of(command));
    }

    private static SocialPlatformHost host(
            List<MemoryAdapter> adapters,
            SocialCommand<?, ?> command
    ) {
        return new SocialPlatformHost(adapters, dispatcher(command),
                SocialIdentityLeaseManager.unavailable(), guard(), new org.encinet.mik.shared.i18n.LanguageService(null), Logger.getAnonymousLogger());
    }

    private static SocialContentGuard guard() {
        return SocialContentGuard.available(_ -> true,
                SocialDocument.of(TRUSTED, "Blocked", SocialDocument.Tone.WARNING));
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
        public SocialPreparedReply prepare(SocialDocument document, int maximumLength) {
            return SocialTestReplies.channel(value -> {
                documents.add(value);
                sent.countDown();
                return CompletableFuture.completedFuture(null);
            }).prepare(document, maximumLength);
        }
    }

    private static final class MemoryAdapter implements SocialPlatformAdapter {
        private final SocialPlatformDescriptor descriptor;
        private final String prefix;
        private final int concurrency;
        private volatile SocialPlatformRuntimeContext context;
        private volatile boolean enabled = true;
        private volatile boolean failPrepare;
        private boolean membershipMonitoring = true;
        private volatile boolean contentSafetyEnabled;
        private volatile Language defaultLanguage = Language.ZH_CN;
        private final List<NoticeCapture> outgoingNotices = new CopyOnWriteArrayList<>();
        private volatile java.util.function.Function<NoticeCapture, CompletableFuture<Void>> noticeSender =
                _ -> CompletableFuture.completedFuture(null);
        private final List<ChatMessage> outgoingChat =
                new CopyOnWriteArrayList<>();
        private final List<List<SocialChatMentionRequest>> outgoingMentionRequests =
                new CopyOnWriteArrayList<>();
        private final List<SocialChatMentionResolution> outgoingMentionResolutions =
                new CopyOnWriteArrayList<>();
        private volatile java.util.function.BiFunction<SocialConversation,
                List<SocialChatMentionRequest>, SocialChatMentionResolution>
                mentionResolver = (_, _) ->
                SocialChatMentionResolution.empty();
        private volatile List<SocialChatRoute> chatRoutes = List.of(
                new SocialChatRoute(new SocialConversation(
                        "room", SocialConversation.Type.GROUP),
                        SocialChatPolicy.always(), SocialChatPolicy.always()));

        private MemoryAdapter(String id, String prefix, int concurrency) {
            this(id, prefix, concurrency, null);
        }

        private MemoryAdapter(String id, String prefix, int concurrency,
                              IdentityPlatform identityPlatform) {
            descriptor = identityPlatform == null
                    ? new SocialPlatformDescriptor(id, capitalize(id), 0x123456)
                    : new SocialPlatformDescriptor(id, capitalize(id), 0x123456, identityPlatform);
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
                    return SocialCommandSyntax.caseInsensitive(defaultLanguage, prefix);
                }

                @Override
                public SocialPlatformSession open(SocialPlatformRuntimeContext openedContext) {
                    context = openedContext;
                    openedContext.status().update(SocialSessionStatus.ready("memory://" + descriptor.id()));
                    return new SocialChatPlatformSession() {
                        @Override public Optional<org.encinet.mik.feature.community.identity.IdentityMemberships> memberships() {
                            return membershipMonitoring
                                    ? Optional.of(org.mockito.Mockito.mock(org.encinet.mik.feature.community.identity.IdentityMemberships.class))
                                    : Optional.empty();
                        }
                        @Override
                        public List<SocialChatRoute> chatRoutes() {
                            return chatRoutes;
                        }

                        @Override
                        public SocialPreparedReply prepareNotice(SocialConversation conversation, SocialDocument document, int maximumLength) {
                            return SocialTestReplies.channel(value -> {
                                NoticeCapture notice = new NoticeCapture(conversation, value);
                                outgoingNotices.add(notice);
                                return noticeSender.apply(notice);
                            }).prepare(document, maximumLength);
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
                    org.encinet.mik.feature.community.social.api.SocialMessageReferences.empty(),
                    reply));
        }

        private void status(SocialSessionStatus status) {
            context.status().update(status);
        }

        private static String capitalize(String value) {
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }
    }

    private record NoticeCapture(SocialConversation conversation, SocialDocument document) { }
}
