package org.encinet.mik.module.social.api;

import org.encinet.mik.module.identity.ExternalIdentity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A trusted, normalized message emitted by a platform adapter. */
public final class SocialInboundMessage {

    private final String eventId;
    private final SocialConversation conversation;
    private final Optional<ExternalIdentity> authenticatedIdentity;
    private final Content content;
    private final boolean authorIsBot;
    private final SocialMessageReferences references;
    private final SocialReplyChannel replyChannel;

    public SocialInboundMessage(
            String eventId,
            SocialConversation conversation,
            Optional<ExternalIdentity> authenticatedIdentity,
            String body,
            boolean authorIsBot,
            SocialReplyChannel replyChannel
    ) {
        this(eventId, conversation, authenticatedIdentity,
                new Text(body), authorIsBot, SocialMessageReferences.empty(), replyChannel);
    }

    public SocialInboundMessage(
            String eventId,
            SocialConversation conversation,
            Optional<ExternalIdentity> authenticatedIdentity,
            Content content,
            boolean authorIsBot,
            SocialReplyChannel replyChannel
    ) {
        this(eventId, conversation, authenticatedIdentity, content, authorIsBot,
                SocialMessageReferences.empty(), replyChannel);
    }

    public SocialInboundMessage(
            String eventId,
            SocialConversation conversation,
            Optional<ExternalIdentity> authenticatedIdentity,
            Content content,
            boolean authorIsBot,
            SocialMessageReferences references,
            SocialReplyChannel replyChannel
    ) {
        this.eventId = requireText(eventId, "eventId");
        this.conversation = Objects.requireNonNull(conversation, "conversation");
        this.authenticatedIdentity = authenticatedIdentity == null
                ? Optional.empty() : authenticatedIdentity;
        this.content = Objects.requireNonNull(content, "content");
        this.authorIsBot = authorIsBot;
        this.references = Objects.requireNonNull(references, "references");
        this.replyChannel = Objects.requireNonNull(replyChannel, "replyChannel");
    }

    public static SocialInboundMessage nativeCommand(
            String eventId,
            SocialConversation conversation,
            Optional<ExternalIdentity> authenticatedIdentity,
            String command,
            String rawArgument,
            Map<String, String> options,
            boolean authorIsBot,
            SocialReplyChannel replyChannel
    ) {
        return new SocialInboundMessage(eventId, conversation, authenticatedIdentity,
                new NativeCommand(command, rawArgument, options),
                authorIsBot, replyChannel);
    }

    public String eventId() {
        return eventId;
    }

    public SocialConversation conversation() {
        return conversation;
    }

    public Optional<ExternalIdentity> authenticatedIdentity() {
        return authenticatedIdentity;
    }

    public Content content() {
        return content;
    }

    public boolean authorIsBot() {
        return authorIsBot;
    }

    public SocialMessageReferences references() {
        return references;
    }

    public SocialReplyChannel replyChannel() {
        return replyChannel;
    }

    public sealed interface Content permits Text, NativeCommand {
    }

    public record Text(String body) implements Content {
        public Text {
            body = Objects.requireNonNullElse(body, "").strip();
        }
    }

    public record NativeCommand(String commandId, String rawArgument,
                                Map<String, String> options) implements Content {
        public NativeCommand {
            commandId = requireText(commandId, "commandId");
            rawArgument = Objects.requireNonNullElse(rawArgument, "").strip();
            options = Map.copyOf(options == null ? Map.of() : options);
        }
    }

    private static String requireText(String value, String field) {
        String clean = Objects.requireNonNull(value, field).strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return clean;
    }
}
