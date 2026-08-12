package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.api.SocialPlatformPlan;
import org.encinet.mik.module.social.api.SocialPlatformRuntimeContext;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.module.social.command.SocialCommandDispatcher;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
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
    private final SocialCommandProcessor processor;
    private final Logger logger;
    private final LinkedHashSet<String> conversations = new LinkedHashSet<>();

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
            Logger logger
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.number = number;
        this.commands = Objects.requireNonNull(commands, "commands");
        this.identityLeases = Objects.requireNonNull(identityLeases, "identityLeases");
        this.contentGuard = Objects.requireNonNull(contentGuard, "contentGuard");
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
                            new SocialPlatformRuntimeContext(this::accept, this::updateStatus)),
                    "Social platform plan returned a null session");
        } catch (RuntimeException error) {
            fail(error);
            throw error;
        }
        boolean closeImmediately;
        synchronized (lock) {
            session = opened;
            closeImmediately = closed || terminalState();
        }
        if (closeImmediately) {
            closeSession(opened);
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
                    endpoint, lastError, conversations.size());
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
            if (invocation == null) {
                return SocialEventSink.Acceptance.IGNORED;
            }
            deduplicationKey = descriptor.id() + '\u0000'
                    + message.conversation().id() + '\u0000' + message.eventId();
            if (!deduplicator.accept(deduplicationKey)) {
                return SocialEventSink.Acceptance.IGNORED;
            }
            boolean submitted;
            try {
                submitted = executor.submit(() -> processor.execute(
                        descriptor, message, invocation, plan.runtimePolicy().output(),
                        this::resultIsCurrent));
            } catch (RuntimeException error) {
                deduplicator.forget(deduplicationKey);
                throw error;
            }
            if (!submitted) {
                deduplicator.forget(deduplicationKey);
                return SocialEventSink.Acceptance.RETRY_LATER;
            }
        }
        return SocialEventSink.Acceptance.ACCEPTED;
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
