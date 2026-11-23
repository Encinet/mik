package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatConversation;
import org.encinet.mik.feature.community.chat.model.ChatEffect;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.chat.model.ExternalIdentity;
import org.encinet.mik.feature.community.chat.model.ExternalIdentityKey;
import org.encinet.mik.protocol.ChatAuthor;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatDescription;
import org.encinet.mik.protocol.ChatExternalIdentity;
import org.encinet.mik.protocol.ChatLink;
import org.encinet.mik.protocol.ChatNoticeRecord;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatPlayerMention;
import org.encinet.mik.protocol.ChatPresenceNotice;
import org.encinet.mik.protocol.ChatRecord;
import org.encinet.mik.protocol.ChatTextStyle;
import org.encinet.mik.protocol.ChatUserRecord;
import org.encinet.mik.protocol.HistoryChannel;
import org.encinet.mik.protocol.HistoryEntry;

import java.util.Optional;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.net.URI;
import java.time.Instant;

public final class ChatRecordCodec {
    private ChatRecordCodec() { }

    public static HistoryEntry publication(ChatDelivery delivery) {
        var submission = delivery.message().submission();
        if (!(submission.origin() instanceof ChatOrigin.Minecraft)) {
            throw new IllegalArgumentException("Paper publishes only game chat");
        }
        return event(submission.id().value(), delivery.audience(), Optional.empty(), user(delivery));
    }

    public static HistoryEntry gamePresence(UUID id, String name, boolean joined, Optional<Set<UUID>> viewers) {
        var notice = ChatNoticeRecord.newBuilder().setSubject(player(id, name))
                .setPresence(ChatPresenceNotice.newBuilder().setName(name)
                        .setKind(joined ? ChatPresenceNotice.Kind.GAME_JOINED : ChatPresenceNotice.Kind.GAME_LEFT));
        var record = ChatRecord.newBuilder().setCreatedAt(Instant.now().toEpochMilli()).setNotice(notice).build();
        return event(ChatMessageId.random().value(), new ChatAudience(ChatChannel.PUBLIC, Set.of()), viewers, record);
    }

    public static ChatDelivery delivery(HistoryEntry entry) {
        var user = entry.getRecord().getUser();
        var author = user.getAuthor().getPlayer();
        UUID sender = user.getAuthor().hasPlayer() ? UUID.fromString(author.getId()) : null;
        var origin = switch (user.getSource()) {
            case WEB -> new ChatOrigin.Web(sender);
            case SOCIAL -> {
                var social = user.getSocial();
                yield new ChatOrigin.Social(social.getPlatformName(), social.getColor());
            }
            default -> throw new IllegalArgumentException("Invalid routed chat source");
        };
        ChatSender target = user.hasTarget() ? ChatSender.minecraft(UUID.fromString(user.getTarget().getId()), user.getTarget().getName()) : null;
        var conversation = switch (entry.getChannel()) {
            case HISTORY_PUBLIC -> ChatConversation.minecraftPublic();
            case HISTORY_STAFF -> ChatConversation.minecraftStaff();
            case HISTORY_PRIVATE -> ChatConversation.minecraftPrivate(sender, target.minecraftId().orElseThrow());
            default -> throw new IllegalArgumentException("Invalid routed chat channel");
        };
        var nodes = new ArrayList<ChatNode>();
        for (var part : user.getBody().getPartsList()) {
            var style = part.getStyle();
            var nativeStyle = new ChatStyle(style.hasColor() ? style.getColor() : null,
                    style.hasGradientEndColor() ? style.getGradientEndColor() : null, style.getBold(), style.getItalic(),
                    style.getUnderlined(), style.getStrikethrough());
            switch (part.getContentCase()) {
                case TEXT -> nodes.add(new ChatNode.Text(part.getText(), nativeStyle));
                case LINK -> {
                    var link = part.getLink();
                    var resource = switch (link.getResource()) {
                        case RESOURCE_UNSPECIFIED, UNRECOGNIZED -> ChatNode.Link.Resource.GENERIC;
                        default -> ChatNode.Link.Resource.valueOf(link.getResource().name());
                    };
                    nodes.add(new ChatNode.Link(link.getLabel(), URI.create(link.getUrl()), nativeStyle, resource));
                }
                case PLAYER_MENTION -> {
                    var player = part.getPlayerMention().getPlayer();
                    UUID id = UUID.fromString(player.getId());
                    var mention = part.getPlayerMention();
                    nodes.add(new ChatNode.PlayerMention(id, player.getName(), mention.hasSourceIdentity()
                            ? Optional.of(identity(mention.getSourceIdentity())) : Optional.empty(), nativeStyle));
                }
                case ITEM_LABEL -> nodes.add(new ChatNode.Text(part.getItemLabel(), nativeStyle));
                case DESCRIPTION -> {
                    var description = part.getDescription();
                    nodes.add(new ChatNode.Placeholder(org.encinet.mik.feature.community.chat.model.ChatPlaceholder
                            .valueOf(description.getKind().name()), description.getFallback(), nativeStyle));
                }
                case BROADCAST -> nodes.add(new ChatNode.Text(part.getBroadcast(), nativeStyle));
                case EXTERNAL_MENTION -> nodes.add(new ChatNode.ExternalMention(identity(part.getExternalMention()), nativeStyle));
                case CONTENT_NOT_SET -> { }
            }
        }
        var content = new ChatContent(nodes);
        var identity = new ChatSender(user.getAuthor().getDisplayName(), Optional.ofNullable(sender),
                user.getAuthor().hasExternal() ? Optional.of(identity(user.getAuthor().getExternal())) : Optional.empty(),
                author.getName(), "", "", 0xFFFFFF);
        var submission = new ChatSubmission(new ChatMessageId(entry.getMessageId()), origin,
                identity, conversation, content.plainText(),
                Instant.ofEpochMilli(entry.getRecord().getCreatedAt()), null);
        return ChatDelivery.managed(new ChatMessage(submission, content, ChatEffect.fromContent(content)), target);
    }

    private static HistoryEntry event(String id, ChatAudience audience, Optional<Set<UUID>> viewers, ChatRecord record) {
        var channel = switch (audience.channel()) {
            case PUBLIC -> HistoryChannel.HISTORY_PUBLIC;
            case STAFF -> HistoryChannel.HISTORY_STAFF;
            case PRIVATE -> HistoryChannel.HISTORY_PRIVATE;
        };
        var entry = HistoryEntry.newBuilder().setMessageId(id).setChannel(channel).setRecord(record)
                .addAllParticipants(audience.participants().stream().map(UUID::toString).sorted().toList());
        viewers.ifPresent(scope -> entry.setRestricted(true).addAllViewers(scope.stream().map(UUID::toString).toList()));
        return entry.build();
    }

    private static ChatRecord user(ChatDelivery delivery) {
        var submission = delivery.message().submission();
        var user = ChatUserRecord.newBuilder().setSource(ChatUserRecord.Source.GAME)
                .setAuthor(author(submission.sender())).setBody(body(delivery.message().content()));
        if (delivery.target() != null) {
            var target = delivery.target();
            user.setTarget(player(target.minecraftId().orElseThrow(), target.minecraftName()));
        }
        return ChatRecord.newBuilder().setCreatedAt(submission.receivedAt().toEpochMilli()).setUser(user).build();
    }

    public static ChatAuthor author(ChatSender sender) {
        var author = ChatAuthor.newBuilder().setDisplayName(sender.displayName());
        sender.minecraftId().ifPresent(id -> author.setPlayer(player(id, sender.minecraftName())));
        sender.externalIdentity().ifPresent(identity -> author.setExternal(identity(identity)));
        return author.build();
    }

    private static ChatPlayer player(UUID id, String name) {
        return ChatPlayer.newBuilder().setId(id.toString()).setName(name).build();
    }

    private static ChatExternalIdentity identity(ExternalIdentity identity) {
        var key = identity.key();
        return ChatExternalIdentity.newBuilder().setPlatform(key.platform()).setIssuer(key.issuer())
                .setScope(key.scope()).setSubject(key.subject()).setDisplayName(identity.displayName())
                .setScopeDisplayName(identity.scopeDisplayName()).build();
    }

    private static ExternalIdentity identity(ChatExternalIdentity identity) {
        return new ExternalIdentity(new ExternalIdentityKey(identity.getPlatform(), identity.getIssuer(),
                identity.getScope(), identity.getSubject()), identity.getDisplayName(), identity.getScopeDisplayName());
    }

    private static ChatBody body(ChatContent content) {
        var body = ChatBody.newBuilder();
        for (ChatNode node : content.nodes()) {
            var part = ChatPart.newBuilder();
            if (!ChatStyle.EMPTY.equals(node.style())) part.setStyle(style(node.style()));
            switch (node) {
                case ChatNode.Text text -> part.setText(text.text());
                case ChatNode.Link link -> part.setLink(ChatLink.newBuilder().setLabel(link.label()).setUrl(link.target().toString()));
                case ChatNode.PlayerMention mention -> {
                    var target = ChatPlayerMention.newBuilder().setPlayer(player(mention.playerId(), mention.playerName()));
                    mention.sourceIdentity().ifPresent(source -> target.setSourceIdentity(identity(source)));
                    part.setPlayerMention(target);
                }
                case ChatNode.ExternalMention mention -> part.setExternalMention(identity(mention.identity()));
                case ChatNode.BroadcastMention broadcast -> part.setBroadcast(broadcast.label());
                case ChatNode.Item item -> part.setItemLabel(item.fallback());
                case ChatNode.Placeholder description -> part.setDescription(ChatDescription.newBuilder()
                        .setKind(ChatDescription.Kind.valueOf(description.kind().name())).setFallback(description.text()));
            }
            body.addParts(part);
        }
        return body.build();
    }

    private static ChatTextStyle style(ChatStyle style) {
        var encoded = ChatTextStyle.newBuilder().setBold(style.bold()).setItalic(style.italic())
                .setUnderlined(style.underlined()).setStrikethrough(style.strikethrough());
        if (style.color() != null) encoded.setColor(style.color());
        if (style.gradientEndColor() != null) encoded.setGradientEndColor(style.gradientEndColor());
        return encoded.build();
    }

}
