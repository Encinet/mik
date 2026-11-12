package org.encinet.mik.feature.community.social.platform.matrix;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialEventSink;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialMessageReferences;
import org.encinet.mik.feature.community.social.platform.matrix.client.MatrixClient;
import org.encinet.mik.feature.community.social.platform.matrix.sync.MatrixRoomMessage;

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
        if (!config.rooms().acceptsMessages(event.roomId())
                || event.sender().userId().equals(botUserId)) {
            return SocialEventSink.Acceptance.IGNORED;
        }
        Optional<ExternalIdentity> sender = MatrixAccountIdentity.from(event.sender());
        LeadingBody leadingBody = stripLeadingBotMention(
                event.body(), event.mentions(), botUserId);
        LinkedHashMap<ExternalIdentityKey, ExternalIdentity> mentions =
                new LinkedHashMap<>();
        List<SocialMessageReferences.MentionSpan> mentionSpans =
                leadingBody.mentions().stream()
                        .map(mention -> MatrixAccountIdentity.from(mention.member())
                                .filter(_ -> !mention.member().userId()
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
                .flatMap(MatrixAccountIdentity::from);
        String commandBody = config.rooms().commandRoomIds().contains(event.roomId()) && !event.commandBody().isEmpty()
                ? stripLeadingBotMention(event.commandBody(), event.mentions(), botUserId).body() : "";
        return events.accept(new SocialInboundMessage(event.eventId(),
                new SocialConversation(event.roomId(), SocialConversation.Type.GROUP),
                sender, event.sender().displayName(),
                new SocialInboundMessage.Text(leadingBody.body(), commandBody, event.placeholders().stream()
                        .filter(span -> span.start() >= leadingBody.offset()
                                && span.end() <= leadingBody.offset() + leadingBody.body().length())
                        .map(span -> span.shifted(-leadingBody.offset())).toList()), false,
                new SocialMessageReferences(List.copyOf(mentions.values()),
                        repliedAuthor, mentionSpans),
                new MatrixReplyChannel(client, event)));
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
        return new LeadingBody(body.substring(bodyOffset, bodyEnd), adjusted, bodyOffset);
    }

    private record LeadingBody(
            String body,
            List<MatrixRoomMessage.MatrixMention> mentions,
            int offset
    ) {
    }
}
