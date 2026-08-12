package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.identity.IdentityPlatform;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformAdapter;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.api.SocialPlatformPlan;
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
import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.safety.SocialContentSafetyFilter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialPlatformHostTest {

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
            assertEquals(SocialEventSink.Acceptance.IGNORED,
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
                    new SocialOutputPolicy(false, 1_000));
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
                    return () -> openedContext.status().update(
                            SocialSessionStatus.stopped("memory://" + descriptor.id()));
                }
            });
        }

        private SocialEventSink.Acceptance emit(
                String eventId,
                String body,
                SocialReplyChannel reply
        ) {
            return context.events().accept(new SocialInboundMessage(eventId,
                    new SocialConversation("room", SocialConversation.Type.GROUP),
                    Optional.empty(), body, false, reply));
        }

        private void status(SocialSessionStatus status) {
            context.status().update(status);
        }

        private static String capitalize(String value) {
            return Character.toUpperCase(value.charAt(0)) + value.substring(1);
        }
    }
}
