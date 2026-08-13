package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.social.api.SocialPlatformAdapter;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.api.SocialPlatformPlan;
import org.encinet.mik.module.social.chat.SocialChatGateway;
import org.encinet.mik.module.social.chat.SocialChatPublisher;
import org.encinet.mik.module.social.chat.SocialChatPublishReport;
import org.encinet.mik.module.social.chat.SocialMentionBindingDirectory;
import org.encinet.mik.module.social.command.SocialCommandDispatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Coordinates installed adapters while each generation owns its runtime resources. */
public final class SocialPlatformHost implements SocialPlatformAdmin,
        SocialChatPublisher, AutoCloseable {

    private final Map<String, SocialPlatformAdapter> adapters = new LinkedHashMap<>();
    private final Map<String, SocialPlatformGeneration> generations = new LinkedHashMap<>();
    private final SocialCommandDispatcher commands;
    private final SocialIdentityLeaseManager identityLeases;
    private final SocialContentGuard contentGuard;
    private final SocialChatGateway chatGateway;
    private final SocialMentionBindingDirectory mentionBindings;
    private final Logger logger;
    private boolean closed;

    public SocialPlatformHost(
            Collection<? extends SocialPlatformAdapter> adapters,
            SocialCommandDispatcher commands,
            SocialIdentityLeaseManager identityLeases,
            SocialContentGuard contentGuard,
            Logger logger
    ) {
        this(adapters, commands, identityLeases, contentGuard,
                (platform, message) ->
                        java.util.concurrent.CompletableFuture.completedFuture(null),
                playerId -> List.of(), logger);
    }

    public SocialPlatformHost(
            Collection<? extends SocialPlatformAdapter> adapters,
            SocialCommandDispatcher commands,
            SocialIdentityLeaseManager identityLeases,
            SocialContentGuard contentGuard,
            SocialChatGateway chatGateway,
            Logger logger
    ) {
        this(adapters, commands, identityLeases, contentGuard, chatGateway,
                playerId -> List.of(), logger);
    }

    public SocialPlatformHost(
            Collection<? extends SocialPlatformAdapter> adapters,
            SocialCommandDispatcher commands,
            SocialIdentityLeaseManager identityLeases,
            SocialContentGuard contentGuard,
            SocialChatGateway chatGateway,
            SocialMentionBindingDirectory mentionBindings,
            Logger logger
    ) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.identityLeases = Objects.requireNonNull(identityLeases, "identityLeases");
        this.contentGuard = Objects.requireNonNull(contentGuard, "contentGuard");
        this.chatGateway = Objects.requireNonNull(chatGateway, "chatGateway");
        this.mentionBindings = Objects.requireNonNull(
                mentionBindings, "mentionBindings");
        this.logger = Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(adapters, "adapters").forEach(this::addAdapter);
    }

    public synchronized void start() {
        ensureOpen();
        for (String platformId : List.copyOf(adapters.keySet())) {
            reload(platformId);
        }
    }

    @Override
    public synchronized void reloadAll() {
        start();
    }

    @Override
    public synchronized boolean reload(String platformId) {
        ensureOpen();
        String id = normalizePlatformId(platformId);
        SocialPlatformAdapter adapter = adapters.get(id);
        if (adapter == null) {
            return false;
        }
        SocialPlatformGeneration previous = generations.get(id);
        long number = previous == null ? 1 : previous.snapshot().generation() + 1;
        if (previous != null) {
            previous.close();
        }
        SocialPlatformDescriptor descriptor = adapter.descriptor();
        SocialPlatformGeneration next = new SocialPlatformGeneration(
                descriptor, number, commands, identityLeases, contentGuard,
                chatGateway, mentionBindings, logger);
        generations.put(id, next);
        try {
            Optional<SocialPlatformPlan> plan = adapter.prepare();
            if (plan.isEmpty()) {
                next.disable();
            } else {
                next.start(plan.get());
            }
        } catch (RuntimeException error) {
            next.fail(error);
            logger.log(Level.SEVERE, "Could not start social platform " + id, error);
        }
        return true;
    }

    @Override
    public synchronized Optional<SocialPlatformSnapshot> status(String platformId) {
        return Optional.ofNullable(generations.get(normalizePlatformId(platformId)))
                .map(SocialPlatformGeneration::snapshot);
    }

    @Override
    public synchronized List<SocialPlatformSnapshot> platforms() {
        List<SocialPlatformSnapshot> result = new ArrayList<>(adapters.size());
        for (SocialPlatformAdapter adapter : adapters.values()) {
            SocialPlatformGeneration generation = generations.get(adapter.descriptor().id());
            result.add(generation == null
                    ? new SocialPlatformSnapshot(adapter.descriptor(), 0,
                    SocialPlatformState.STOPPED, "", "", 0)
                    : generation.snapshot());
        }
        return List.copyOf(result);
    }

    @Override
    public synchronized Set<String> conversations(String platformId) {
        SocialPlatformGeneration generation = generations.get(
                normalizePlatformId(platformId));
        return generation == null ? Set.of() : generation.conversations();
    }

    @Override
    public synchronized SocialChatPublishReport publish(ChatMessage message) {
        Objects.requireNonNull(message, "message");
        if (closed) {
            return SocialChatPublishReport.empty();
        }
        SocialChatPublishReport report = SocialChatPublishReport.empty();
        for (SocialPlatformGeneration generation : generations.values()) {
            report = report.plus(generation.publishChat(message));
        }
        return report;
    }

    private void addAdapter(SocialPlatformAdapter adapter) {
        SocialPlatformAdapter checked = Objects.requireNonNull(adapter, "adapter");
        String id = checked.descriptor().id();
        if (adapters.putIfAbsent(id, checked) != null) {
            throw new IllegalArgumentException("Duplicate social platform id: " + id);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        generations.values().forEach(SocialPlatformGeneration::close);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Social platform host is closed");
        }
    }

    private static String normalizePlatformId(String value) {
        return Objects.requireNonNull(value, "platformId").strip()
                .toLowerCase(Locale.ROOT);
    }
}
