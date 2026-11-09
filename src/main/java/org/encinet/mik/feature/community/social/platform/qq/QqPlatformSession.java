package org.encinet.mik.feature.community.social.platform.qq;

import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionRequest;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionResolution;
import org.encinet.mik.feature.community.social.chat.SocialChatPlatformSession;
import org.encinet.mik.feature.community.social.chat.SocialChatRoute;
import org.encinet.mik.feature.community.social.platform.qq.client.QqOpenApiClient;
import org.encinet.mik.feature.community.social.platform.qq.gateway.QqGatewaySession;
import org.encinet.mik.feature.community.social.platform.qq.join.QqGroupJoinApproval;
import org.encinet.mik.feature.community.social.platform.qq.render.QqChatRenderer;
import org.encinet.mik.feature.community.social.platform.qq.render.QqMarkdownRenderer;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.shared.util.ShutdownSequence;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** QQ Gateway lifecycle plus the optional group-chat interoperability capability. */
final class QqPlatformSession implements SocialChatPlatformSession {
    private final QqGatewaySession gateway;
    private final HttpClient httpClient;
    private final QqOpenApiClient api;
    private final QqPlatformConfig config;
    private final QqGroupJoinApproval joinApproval;
    private final AtomicBoolean closed = new AtomicBoolean();

    QqPlatformSession(
            QqGatewaySession gateway,
            HttpClient httpClient,
            QqOpenApiClient api,
            QqPlatformConfig config,
            QqGroupJoinApproval joinApproval
    ) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.api = Objects.requireNonNull(api, "api");
        this.config = Objects.requireNonNull(config, "config");
        this.joinApproval = Objects.requireNonNull(joinApproval, "joinApproval");
    }

    @Override
    public void start() {
        if (!closed.get()) {
            gateway.start();
        }
    }

    @Override
    public List<SocialChatRoute> chatRoutes() {
        return config.chatRoutes();
    }

    @Override
    public CompletionStage<Void> sendNotice(SocialConversation conversation, SocialDocument document) {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(document, "document");
        if (closed.get()) return CompletableFuture.completedFuture(null);
        return api.sendMarkdown(conversation.id(), QqMarkdownRenderer.render(document, config.maxOutboundLength()));
    }

    @Override
    public SocialChatMentionResolution resolveMentions(
            SocialConversation conversation,
            List<SocialChatMentionRequest> requests
    ) {
        Objects.requireNonNull(conversation, "conversation");
        Map<UUID, List<ExternalIdentity>> resolved = new LinkedHashMap<>();
        for (SocialChatMentionRequest request : List.copyOf(requests)) {
            List<ExternalIdentity> targets = request.candidates().stream()
                    .filter(identity -> identity.key().platform().equals(
                            QqPlatformConfig.PLATFORM_ID))
                    .filter(identity -> identity.key().issuer().equals(config.appId()))
                    .filter(identity -> identity.key().scope().equals(conversation.id()))
                    .toList();
            if (!targets.isEmpty()) {
                resolved.put(request.playerId(), targets);
            }
        }
        return new SocialChatMentionResolution(resolved);
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
    public CompletionStage<Void> sendChat(
            SocialConversation conversation,
            ChatMessage message,
            SocialChatMentionResolution mentions
    ) {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(mentions, "mentions");
        if (closed.get()) {
            return CompletableFuture.completedFuture(null);
        }
        QqChatRenderer.RenderedChat rendered = QqChatRenderer.render(
                message, mentions, config.maxOutboundLength());
        return api.sendMarkdown(conversation.id(), rendered.markdown());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("QQ Gateway", gateway::close);
        shutdown.attempt("QQ group join approval", joinApproval::close);
        shutdown.attempt("QQ HTTP client", httpClient::shutdownNow);
        shutdown.finish("QQ platform session");
    }
}
