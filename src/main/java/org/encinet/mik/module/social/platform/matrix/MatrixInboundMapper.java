package org.encinet.mik.module.social.platform.matrix;

import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.social.api.SocialConversation;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialMessageReferences;
import org.encinet.mik.module.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.module.social.platform.matrix.sync.MatrixRoomMessage;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/** Maps trusted Matrix /sync events into the platform-neutral inbound contract. */
final class MatrixInboundMapper {
    private final MatrixPlatformConfig config;
    private final MatrixClient client;
    private final SocialEventSink events;

    MatrixInboundMapper(
            MatrixPlatformConfig config,
            MatrixClient client,
            SocialEventSink events
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.client = Objects.requireNonNull(client, "client");
        this.events = Objects.requireNonNull(events, "events");
    }

    SocialEventSink.Acceptance accept(MatrixRoomMessage event, String botUserId) {
        if (!config.acceptsRoom(event.roomId())
                || event.sender().userId().equals(botUserId)) {
            return SocialEventSink.Acceptance.IGNORED;
        }
        Optional<ExternalIdentity> sender = identity(event.sender());
        LeadingBody leadingBody = stripLeadingBotMention(
                event.body(), event.mentions(), botUserId);
        LinkedHashMap<ExternalIdentityKey, ExternalIdentity> mentions =
                new LinkedHashMap<>();
        List<SocialMessageReferences.MentionSpan> mentionSpans =
                leadingBody.mentions().stream()
                        .map(mention -> identity(mention.member())
                                .filter(identity -> !mention.member().userId()
                                        .equals(botUserId))
                                .map(identity -> {
                                    mentions.putIfAbsent(identity.key(), identity);
                                    return new SocialMessageReferences.MentionSpan(
                                            mention.start(), mention.end(), identity);
                                }))
                        .flatMap(Optional::stream)
                        .toList();
        Optional<ExternalIdentity> repliedAuthor = event.repliedAuthor()
                .filter(member -> !member.userId().equals(botUserId))
                .flatMap(this::identity);
        return events.accept(new SocialInboundMessage(event.eventId(),
                new SocialConversation(event.roomId(), SocialConversation.Type.GROUP),
                sender, event.sender().displayName(),
                new SocialInboundMessage.Text(leadingBody.body()), false,
                new SocialMessageReferences(List.copyOf(mentions.values()),
                        repliedAuthor, mentionSpans),
                new MatrixReplyChannel(client, event, config.maxOutboundLength())));
    }

    private Optional<ExternalIdentity> identity(MatrixRoomMessage.MatrixMember member) {
        String userId = member.userId();
        int separator = userId.indexOf(':', 1);
        if (!userId.startsWith("@") || separator < 2
                || separator == userId.length() - 1 || userId.length() > 255
                || userId.codePoints().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ExternalIdentity(new ExternalIdentityKey(
                    MatrixPlatformConfig.PLATFORM_ID,
                    userId.substring(separator + 1), "", userId),
                    member.displayName()));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static LeadingBody stripLeadingBotMention(
            String body,
            List<MatrixRoomMessage.MatrixMention> mentions,
            String botUserId
    ) {
        int offset = 0;
        MatrixRoomMessage.MatrixMention leadingBot = mentions.stream()
                .filter(mention -> mention.start() == 0
                        && mention.member().userId().equals(botUserId))
                .findFirst().orElse(null);
        if (leadingBot != null) {
            offset = leadingBot.end();
            while (offset < body.length()
                    && Character.isWhitespace(body.charAt(offset))) {
                offset++;
            }
        } else if (body.equals(botUserId)) {
            offset = body.length();
        } else if (body.startsWith(botUserId)
                && body.length() > botUserId.length()
                && Character.isWhitespace(body.charAt(botUserId.length()))) {
            offset = botUserId.length();
            while (offset < body.length()
                    && Character.isWhitespace(body.charAt(offset))) {
                offset++;
            }
        }
        int end = body.length();
        while (end > offset && Character.isWhitespace(body.charAt(end - 1))) {
            end--;
        }
        int bodyOffset = offset;
        int bodyEnd = end;
        List<MatrixRoomMessage.MatrixMention> adjusted = mentions.stream()
                .filter(mention -> mention.end() <= bodyEnd
                        && mention.start() >= bodyOffset)
                .map(mention -> new MatrixRoomMessage.MatrixMention(
                        mention.member(), mention.start() - bodyOffset,
                        mention.end() - bodyOffset))
                .toList();
        return new LeadingBody(body.substring(bodyOffset, bodyEnd), adjusted);
    }

    private record LeadingBody(
            String body,
            List<MatrixRoomMessage.MatrixMention> mentions
    ) {
    }
}
