package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;

import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.IdentityMemberships;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.api.SocialSessionStatus;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionRequest;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionResolution;
import org.encinet.mik.feature.community.social.chat.SocialChatPlatformSession;
import org.encinet.mik.feature.community.social.chat.SocialChatRoute;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixApiErrors;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixHttpException;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixEncryption;
import org.encinet.mik.feature.community.social.platform.matrix.crypto.MatrixEncryptedInbox;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixChatRenderer;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixHtmlRenderer;
import org.encinet.mik.feature.community.social.platform.matrix.render.MatrixPlainTextRenderer;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomDirectory;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncProtocol;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixSyncRequest;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    private final MatrixRoomDirectory rooms;
    private final Consumer<SocialSessionStatus> statusUpdates;
    private final Logger logger;
    private final MatrixSyncState syncState;
    private final MatrixEncryption encryption;
    private final MatrixEncryptedInbox encryptedInbox;
    private final Runnable onClosed;

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
            MatrixRoomDirectory rooms,
            MatrixEncryption encryption,
            Runnable onClosed,
            Consumer<SocialSessionStatus> statusUpdates,
            Logger logger
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.inbound = Objects.requireNonNull(inbound, "inbound");
        this.rooms = Objects.requireNonNull(rooms, "rooms");
        this.encryption = Objects.requireNonNull(encryption, "encryption");
        this.onClosed = Objects.requireNonNull(onClosed, "onClosed");
        this.statusUpdates = Objects.requireNonNull(statusUpdates, "statusUpdates");
        this.logger = Objects.requireNonNull(logger, "logger");
        syncState = new MatrixSyncState(config.rooms(), rooms);
        encryptedInbox = new MatrixEncryptedInbox(encryption, syncState::decrypted,
                message -> inbound.accept(message, authenticatedUserId), this::isReady,
                config.reconnectBaseDelayMillis(), logger);
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
        Optional<String> since = Optional.empty();
        int reconnectAttempts = 0;
        boolean initialFullState = true;

        while (!isClosed()) {
            try {
                if (botUserId == null) {
                    String identifiedUser = client.whoAmI();
                    encryption.initialize(identifiedUser);
                    botUserId = identifiedUser;
                    since = encryption.cursor();
                    authenticatedUserId = botUserId;
                }
                MatrixSyncRequest request = new MatrixSyncRequest(since,
                        since.isPresent() ? config.syncTimeoutMillis() : 0,
                        initialFullState || syncState.requiresFullState());
                JsonObject response = client.sync(request);
                MatrixSyncProtocol.Batch batch = MatrixSyncProtocol.parse(response);
                if (isClosed()) {
                    return;
                }
                rooms.beginCryptoSync();
                List<MatrixRoomMessage> messages = syncState.apply(batch, request, botUserId);
                encryption.receiveSync(response, request.fullState());
                rooms.endCryptoSync();
                encryptedInbox.retainRooms(rooms::joinedMessageRoom);
                if (since.isEmpty()) {
                    encryption.commit(batch.nextBatch());
                    since = Optional.of(batch.nextBatch());
                    initialFullState = false;
                    reconnectAttempts = 0;
                    ready();
                    continue;
                }

                reconnectAttempts = 0;
                // A recovered generation must become READY before the host can admit events.
                ready();
                boolean retryBatch = false;
                for (MatrixSyncProtocol.RoomBatch room : batch.rooms()) {
                    if (rooms.security(room.roomId()) != MatrixRoomDirectory.Security.ENCRYPTED
                            || !config.rooms().acceptsMessages(room.roomId()) || !syncState.live(room.roomId(), request)) continue;
                    for (MatrixSyncProtocol.Event event : room.timelineEvents()) {
                        if ("m.room.encrypted".equals(event.type()) && !event.eventId().isBlank()
                                && !encryptedInbox.offer(room.roomId(), event.eventId())) retryBatch = true;
                    }
                }
                for (MatrixRoomMessage message : messages) {
                    if (isClosed()) {
                        return;
                    }
                    if (!rooms.allowsPlaintext(message.roomId())) {
                        continue;
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
                encryption.commit(batch.nextBatch());
                since = Optional.of(batch.nextBatch());
                initialFullState = false;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                if (isClosed()) {
                    return;
                }
                fail(error);
                return;
            } catch (IOException | RuntimeException error) {
                Throwable cause = MatrixApiErrors.unwrap(error);
                if (cause instanceof MatrixHttpException httpError) {
                    if (httpError.isRateLimited()) {
                        starting(httpError);
                        long delay = httpError.retryAfterMillis() > 0
                                ? httpError.retryAfterMillis()
                                : config.reconnectBaseDelayMillis();
                        try {
                            sleep(delay);
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
                    if ("M_UNKNOWN_POS".equals(httpError.errorCode()) && since.isPresent()) {
                        since = Optional.empty();
                        initialFullState = true;
                        if (!resetEncryptionCursor()) return;
                        syncState.reset();
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
                        + config.maxReconnectAttempts() + "): " + MatrixApiErrors.describe(cause));
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
        encryption.disconnected();
        syncState.disconnected();
        synchronized (lock) {
            if (closed) {
                return;
            }
            state = SocialSessionStatus.State.STARTING;
            lastError = MatrixApiErrors.describe(error);
        }
        publishStatus();
    }

    private void fail(Throwable error) {
        encryption.disconnected();
        syncState.disconnected();
        synchronized (lock) {
            if (closed) {
                return;
            }
            state = SocialSessionStatus.State.FAILED;
            lastError = MatrixApiErrors.describe(error);
        }
        publishStatus();
        logger.warning("Matrix bot failed: " + MatrixApiErrors.describe(error));
    }

    @Override
    public Optional<IdentityMemberships> memberships() {
        return Optional.of(syncState.memberships());
    }

    @Override
    public List<SocialChatRoute> chatRoutes() {
        return config.rooms().chatRoutes();
    }

    @Override
    public SocialPreparedReply prepareNotice(SocialConversation conversation, SocialDocument document, int maximumLength) {
        Objects.requireNonNull(conversation, "conversation");
        var bounded = MatrixReplyChannel.layout(document, Math.min(maximumLength, config.maxOutboundLength()));
        String plain = MatrixPlainTextRenderer.encode(bounded);
        String html = MatrixHtmlRenderer.render(bounded);
        return new SocialPreparedReply(bounded, () -> {
            synchronized (lock) {
                if (closed || state != SocialSessionStatus.State.READY) return CompletableFuture.completedFuture(
                        SocialDeliveryResult.notSent(new IllegalStateException("Matrix session not ready")));
            }
            return SocialDeliveryResult.observe(client.sendFormattedText(conversation.id(), plain, html), MatrixApiErrors::deliveryStatus);
        });
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
        Map<UUID, List<ExternalIdentity>>
                resolved = new LinkedHashMap<>();
        for (SocialChatMentionRequest request : List.copyOf(requests)) {
            LinkedHashMap<Object, ExternalIdentity>
                    joined = new LinkedHashMap<>();
            for (ExternalIdentity candidate
                    : request.candidates()) {
                String userId = candidate.key().subject();
                if (MatrixAccountIdentity.owns(candidate.key()) && !userId.equals(authenticatedUserId)) {
                    syncState.joinedMember(conversation.id(), userId)
                            .ifPresent(member -> joined.putIfAbsent(
                                    candidate.key(),
                                    new ExternalIdentity(
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
        encryptedInbox.close();
        onClosed.run();
        encryption.close();
        syncState.close();
        authenticatedUserId = "";
        publishStatus();
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

    private boolean isReady() {
        synchronized (lock) {
            return !closed && state == SocialSessionStatus.State.READY;
        }
    }

    private boolean resetEncryptionCursor() {
        try {
            encryption.resetCursor();
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (!isClosed()) fail(error);
            return false;
        } catch (RuntimeException error) {
            if (!isClosed()) fail(error);
            return false;
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

}
