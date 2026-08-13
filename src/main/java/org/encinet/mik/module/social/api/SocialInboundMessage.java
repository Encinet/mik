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
    private final String senderDisplayName;
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
                inferredDisplayName(authenticatedIdentity), new Text(body), authorIsBot,
                SocialMessageReferences.empty(), replyChannel);
    }

    public SocialInboundMessage(
            String eventId,
            SocialConversation conversation,
            Optional<ExternalIdentity> authenticatedIdentity,
            Content content,
            boolean authorIsBot,
            SocialReplyChannel replyChannel
    ) {
        this(eventId, conversation, authenticatedIdentity,
                inferredDisplayName(authenticatedIdentity), content, authorIsBot,
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
        this.senderDisplayName = inferredDisplayName(authenticatedIdentity);
        this.content = Objects.requireNonNull(content, "content");
        this.authorIsBot = authorIsBot;
        this.references = Objects.requireNonNull(references, "references");
        this.replyChannel = Objects.requireNonNull(replyChannel, "replyChannel");
        validateMentionSpans(content, this.references);
    }

    public SocialInboundMessage(
            String eventId,
            SocialConversation conversation,
            Optional<ExternalIdentity> authenticatedIdentity,
            String senderDisplayName,
            Content content,
            boolean authorIsBot,
            SocialMessageReferences references,
            SocialReplyChannel replyChannel
    ) {
        this.eventId = requireText(eventId, "eventId");
        this.conversation = Objects.requireNonNull(conversation, "conversation");
        this.authenticatedIdentity = authenticatedIdentity == null
                ? Optional.empty() : authenticatedIdentity;
        this.senderDisplayName = requireText(
                senderDisplayName, "senderDisplayName");
        this.content = Objects.requireNonNull(content, "content");
        this.authorIsBot = authorIsBot;
        this.references = Objects.requireNonNull(references, "references");
        this.replyChannel = Objects.requireNonNull(replyChannel, "replyChannel");
        validateMentionSpans(content, this.references);
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

    public String senderDisplayName() {
        return senderDisplayName;
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

    private static void validateMentionSpans(
            Content content,
            SocialMessageReferences references
    ) {
        if (references.mentionSpans().isEmpty()) {
            return;
        }
        if (!(content instanceof Text text)) {
            throw new IllegalArgumentException(
                    "Only text messages may contain mention spans");
        }
        java.util.Set<org.encinet.mik.module.identity.ExternalIdentityKey> mentioned =
                references.mentions().stream().map(ExternalIdentity::key)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (SocialMessageReferences.MentionSpan span : references.mentionSpans()) {
            if (span.end() > text.body().length()) {
                throw new IllegalArgumentException(
                        "Mention span exceeds the text body");
            }
            if (!mentioned.contains(span.identity().key())) {
                throw new IllegalArgumentException(
                        "Mention span identity is missing from references");
            }
        }
    }

    private static String inferredDisplayName(
            Optional<ExternalIdentity> identity
    ) {
        Optional<ExternalIdentity> checked = identity == null
                ? Optional.empty() : identity;
        return checked.map(ExternalIdentity::displayName)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> checked.map(value -> value.key().subject())
                        .filter(value -> !value.isBlank())
                        .orElse("social-user"));
    }
}
