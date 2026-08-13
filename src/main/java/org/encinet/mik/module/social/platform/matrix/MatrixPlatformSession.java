package org.encinet.mik.module.social.platform.matrix;

import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.module.social.chat.SocialChatPlatformSession;
import org.encinet.mik.module.social.chat.SocialChatRoute;
import org.encinet.mik.module.social.chat.SocialChatMentionRequest;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;
import org.encinet.mik.module.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.module.social.platform.matrix.client.MatrixHttpException;
import org.encinet.mik.module.social.platform.matrix.render.MatrixChatRenderer;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixRoomMessage;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixMemberDirectory;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixSyncProcessor;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixSyncProtocol;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Owns Matrix authentication, /sync long polling, cursor recovery and shutdown. */
final class MatrixPlatformSession implements SocialChatPlatformSession {
    private final Object lock = new Object();
    private final HttpClient httpClient;
    private final MatrixClient client;
    private final MatrixPlatformConfig config;
    private final MatrixInboundMapper inbound;
    private final Consumer<SocialSessionStatus> statusUpdates;
    private final Logger logger;
    private final MatrixMemberDirectory members = new MatrixMemberDirectory();
    private final MatrixSyncProcessor syncProcessor = new MatrixSyncProcessor(members);

    private SocialSessionStatus.State state = SocialSessionStatus.State.STOPPED;
    private String lastError = "";
    private boolean closed;
    private Thread pollingThread;
    private volatile String authenticatedUserId = "";

    MatrixPlatformSession(
            HttpClient httpClient,
            MatrixClient client,
            MatrixPlatformConfig config,
            MatrixInboundMapper inbound,
            Consumer<SocialSessionStatus> statusUpdates,
            Logger logger
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.statusUpdates = Objects.requireNonNull(statusUpdates, "statusUpdates");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void start() {
        Thread thread;
        synchronized (lock) {
            if (closed || pollingThread != null) {
                return;
            }
            state = SocialSessionStatus.State.STARTING;
            lastError = "";
            thread = Thread.ofVirtual()
                    .name("mik-social-matrix-sync")
                    .unstarted(this::poll);
            pollingThread = thread;
        }
        publishStatus();
        thread.start();
    }

    private void poll() {
        String botUserId = null;
        String since = null;
        boolean initialized = false;
        boolean membersInitialized = false;
        int reconnectAttempts = 0;

        while (!isClosed()) {
            try {
                if (botUserId == null) {
                    botUserId = client.whoAmI();
                    authenticatedUserId = botUserId;
                }
                if (!membersInitialized) {
                    loadJoinedMembers();
                    membersInitialized = true;
                }
                MatrixSyncProtocol.Batch batch = MatrixSyncProtocol.parse(
                        client.sync(since, initialized ? config.syncTimeoutMillis() : 0));
                List<MatrixRoomMessage> messages = syncProcessor.process(batch);
                if (!initialized) {
                    since = batch.nextBatch();
                    initialized = true;
                    reconnectAttempts = 0;
                    ready();
                    continue;
                }

                reconnectAttempts = 0;
                // A recovered generation must become READY before the host can admit events.
                ready();
                boolean retryBatch = false;
                for (MatrixRoomMessage message : messages) {
                    if (isClosed()) {
                        return;
                    }
                    try {
                        if (inbound.accept(message, botUserId)
                                == SocialEventSink.Acceptance.RETRY_LATER) {
                            retryBatch = true;
                        }
                    } catch (RuntimeException error) {
                        retryBatch = true;
                        logger.log(Level.WARNING,
                                "Failed to admit a Matrix /sync event", error);
                    }
                }
                if (retryBatch) {
                    sleep(config.reconnectBaseDelayMillis());
                    continue;
                }
                since = batch.nextBatch();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                if (isClosed()) {
                    return;
                }
                fail(error);
                return;
            } catch (IOException | RuntimeException error) {
                Throwable cause = unwrap(error);
                if (cause instanceof MatrixHttpException httpError) {
                    if (httpError.isRateLimited()) {
                        long delay = httpError.retryAfterMillis() > 0
                                ? httpError.retryAfterMillis()
                                : config.reconnectBaseDelayMillis();
                        try {
                            sleep(Math.min(delay, config.reconnectMaxDelayMillis()));
                            continue;
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            if (isClosed()) {
                                return;
                            }
                            fail(interrupted);
                            return;
                        }
                    }
                    if ("M_UNKNOWN_POS".equals(httpError.errorCode()) && initialized) {
                        since = null;
                        initialized = false;
                        membersInitialized = false;
                        syncProcessor.clear();
                        starting(httpError);
                        continue;
                    }
                    if (httpError.statusCode() >= 400 && httpError.statusCode() < 500) {
                        fail(httpError);
                        return;
                    }
                }
                if (reconnectAttempts >= config.maxReconnectAttempts()) {
                    fail(cause);
                    return;
                }
                reconnectAttempts++;
                starting(cause);
                long delay = reconnectDelay(reconnectAttempts);
                logger.warning("Matrix /sync disconnected; reconnecting in " + delay
                        + " ms (attempt " + reconnectAttempts + "/"
                        + config.maxReconnectAttempts() + "): " + errorMessage(cause));
                try {
                    sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    if (isClosed()) {
                        return;
                    }
                    fail(interrupted);
                    return;
                }
            }
        }
    }

    private void ready() {
        boolean changed;
        synchronized (lock) {
            if (closed) {
                return;
            }
            changed = state != SocialSessionStatus.State.READY;
            state = SocialSessionStatus.State.READY;
            lastError = "";
        }
        if (changed) {
            publishStatus();
            logger.info("Matrix bot is ready on " + config.homeserverUri());
        }
    }

    private void starting(Throwable error) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            state = SocialSessionStatus.State.STARTING;
            lastError = errorMessage(error);
        }
        publishStatus();
    }

    private void fail(Throwable error) {
        synchronized (lock) {
            if (closed) {
                return;
            }
            state = SocialSessionStatus.State.FAILED;
            lastError = errorMessage(error);
        }
        publishStatus();
        logger.warning("Matrix bot failed: " + errorMessage(error));
    }

    @Override
    public List<SocialChatRoute> chatRoutes() {
        return config.chatRoutes();
    }

    @Override
    public CompletionStage<Void> sendChat(
            SocialConversation conversation,
            ChatMessage message
    ) {
        return sendChat(conversation, message,
                SocialChatMentionResolution.empty());
    }

    @Override
    public SocialChatMentionResolution resolveMentions(
            SocialConversation conversation,
            List<SocialChatMentionRequest> requests
    ) {
        Objects.requireNonNull(conversation, "conversation");
        Map<java.util.UUID, List<org.encinet.mik.module.identity.ExternalIdentity>>
                resolved = new LinkedHashMap<>();
        for (SocialChatMentionRequest request : List.copyOf(requests)) {
            LinkedHashMap<Object, org.encinet.mik.module.identity.ExternalIdentity>
                    joined = new LinkedHashMap<>();
            for (org.encinet.mik.module.identity.ExternalIdentity candidate
                    : request.candidates()) {
                String userId = candidate.key().subject();
                if (candidate.key().platform().equals(
                        MatrixPlatformAdapter.DESCRIPTOR.id())
                        && !userId.equals(authenticatedUserId)) {
                    members.joinedMember(conversation.id(), userId)
                            .ifPresent(member -> joined.putIfAbsent(
                                    candidate.key(),
                                    new org.encinet.mik.module.identity.ExternalIdentity(
                                            candidate.key(), member.displayName())));
                }
            }
            if (!joined.isEmpty()) {
                resolved.put(request.playerId(), List.copyOf(joined.values()));
            }
        }
        return new SocialChatMentionResolution(resolved);
    }

    @Override
    public CompletionStage<Void> sendChat(
            SocialConversation conversation,
            ChatMessage message,
            SocialChatMentionResolution mentions
    ) {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(mentions, "mentions");
        synchronized (lock) {
            if (closed || state != SocialSessionStatus.State.READY) {
                return CompletableFuture.completedFuture(null);
            }
        }
        MatrixChatRenderer.RenderedChat rendered = MatrixChatRenderer.render(
                message, mentions, config.maxOutboundLength());
        return client.sendFormattedText(conversation.id(),
                rendered.plainText(), rendered.html(),
                rendered.mentionedUserIds());
    }

    @Override
    public void close() {
        Thread thread;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            state = SocialSessionStatus.State.STOPPED;
            lastError = "";
            thread = pollingThread;
            pollingThread = null;
        }
        if (thread != null) {
            thread.interrupt();
        }
        httpClient.shutdownNow();
        members.clear();
        authenticatedUserId = "";
        publishStatus();
    }

    private void loadJoinedMembers() throws IOException, InterruptedException {
        LinkedHashSet<String> rooms = new LinkedHashSet<>();
        config.chatRoutes().forEach(route -> rooms.add(
                route.conversation().id()));
        for (String roomId : rooms) {
            members.replaceJoined(roomId, client.joinedMembers(roomId));
        }
    }

    private long reconnectDelay(int attempt) {
        long delay = config.reconnectBaseDelayMillis();
        for (int index = 1; index < attempt; index++) {
            if (delay >= config.reconnectMaxDelayMillis() / 2) {
                return config.reconnectMaxDelayMillis();
            }
            delay *= 2;
        }
        return Math.min(delay, config.reconnectMaxDelayMillis());
    }

    private void sleep(long delayMillis) throws InterruptedException {
        Thread.sleep(delayMillis);
    }

    private boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    private void publishStatus() {
        SocialSessionStatus status;
        synchronized (lock) {
            status = new SocialSessionStatus(state,
                    config.homeserverUri().toString(), lastError);
        }
        try {
            statusUpdates.accept(status);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING, "Could not publish Matrix status", error);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static String errorMessage(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        String result = message == null || message.isBlank()
                ? cause.getClass().getSimpleName() : message.strip();
        return result.length() <= 300 ? result : result.substring(0, 300);
    }

}
