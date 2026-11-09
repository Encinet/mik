package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionResolution;
import org.encinet.mik.feature.community.social.safety.SocialContentModerator;
import org.encinet.mik.shared.i18n.Language;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

/** Shared moderation lifecycle and the common blocked-output document. */
public final class SocialContentGuard implements AutoCloseable {
    private final Supplier<? extends SocialContentModerator> loader;
    private final Function<Language, SocialDocument> blockedDocument;
    private volatile State state;
    private boolean closed;

    private SocialContentGuard(
            Supplier<? extends SocialContentModerator> loader,
            Function<Language, SocialDocument> blockedDocument,
            State state
    ) {
        this.loader = loader;
        this.blockedDocument = Objects.requireNonNull(blockedDocument, "blockedDocument");
        this.state = state;
    }

    public static SocialContentGuard available(
            SocialContentModerator moderator,
            SocialDocument blockedDocument
    ) {
        Objects.requireNonNull(blockedDocument, "blockedDocument");
        return available(moderator, _ -> blockedDocument);
    }

    public static SocialContentGuard available(
            SocialContentModerator moderator,
            Function<Language, SocialDocument> blockedDocument
    ) {
        return new SocialContentGuard(null, blockedDocument,
                new State(Objects.requireNonNull(moderator, "moderator"), null));
    }

    public static SocialContentGuard reloadable(
            Supplier<? extends SocialContentModerator> loader,
            Function<Language, SocialDocument> blockedDocument
    ) {
        return new SocialContentGuard(Objects.requireNonNull(loader, "loader"),
                blockedDocument, new State(null,
                new IllegalStateException("Social content safety has not been loaded")));
    }

    /** Replaces the client and reports configuration failure without stopping trusted replies. */
    public synchronized Optional<String> reload() {
        if (closed) throw new IllegalStateException("Social content guard is closed");
        if (loader == null) return Optional.empty();
        State previous = state;
        try (var loading = Executors.newVirtualThreadPerTaskExecutor()) {
            // Configuration file access runs off the Bukkit thread and finishes before replacement.
            state = new State(Objects.requireNonNull(
                    CompletableFuture.supplyAsync(loader, loading).join(), "moderator"), null);
        } catch (RuntimeException error) {
            RuntimeException failure = error instanceof CompletionException
                    && error.getCause() instanceof RuntimeException cause ? cause : error;
            state = new State(null, failure);
        }
        if (previous.moderator() != null) previous.moderator().close();
        return Optional.ofNullable(state.unavailable()).map(SocialCommandProcessor::rootMessage);
    }

    public SocialDocument apply(SocialDocument document, SocialOutputPolicy policy) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(policy, "policy");
        SocialDocument bounded = document.truncated(policy.maximumLength());
        SocialDocument safe = !allowsOutboundText(bounded.plainText(), bounded.moderation(), policy)
                ? Objects.requireNonNull(blockedDocument.apply(document.language()),
                "Blocked-document factory returned null").localized(document.language())
                : bounded;
        return safe.truncated(policy.maximumLength());
    }

    /** Applies platform-output safety rules; inbound social chat must not call this. */
    public boolean allowsOutboundText(
            String text,
            SocialDocument.Moderation moderation,
            SocialOutputPolicy policy
    ) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(moderation, "moderation");
        Objects.requireNonNull(policy, "policy");
        if (moderation == SocialDocument.Moderation.TRUSTED
                || !policy.contentSafetyEnabled()) return true;
        SocialContentModerator moderator = state.requireModerator();
        return text.isBlank() || moderator.allows(text);
    }

    /** Natural text with sender, expanded mentions and link destinations. */
    static String outboundChatText(ChatMessage message, SocialChatMentionResolution mentions) {
        StringBuilder text = new StringBuilder(message.submission().sender().displayName())
                .append(": ");
        for (ChatNode node : message.content().nodes()) {
            if (node instanceof ChatNode.PlayerMention mention
                    && !mentions.targetsFor(mention.playerId()).isEmpty()) {
                text.append(SocialChatMentionResolution.playerMentionText(
                        mention, mentions.targetsFor(mention.playerId())));
            } else {
                text.append(node.visibleText());
            }
            if (node instanceof ChatNode.Link link
                    && !link.label().equals(link.target().toString())) {
                text.append(" (").append(link.target()).append(')');
            }
        }
        return text.toString();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        State previous = state;
        state = new State(null, new IllegalStateException("Social content guard is closed"));
        if (previous.moderator() != null) previous.moderator().close();
    }

    private record State(SocialContentModerator moderator, RuntimeException unavailable) {
        SocialContentModerator requireModerator() {
            if (unavailable != null) {
                throw new IllegalStateException("Shared social content safety is unavailable: "
                        + unavailable.getMessage(), unavailable);
            }
            return moderator;
        }
    }
}
