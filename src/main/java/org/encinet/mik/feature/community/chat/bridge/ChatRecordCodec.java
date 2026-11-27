package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatMentions;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.chat.model.ChatPresence;
import org.encinet.mik.feature.community.chat.model.ExternalIdentity;
import org.encinet.mik.feature.community.chat.model.ExternalIdentityKey;
import org.encinet.mik.protocol.ChatDescription;
import org.encinet.mik.protocol.ChatDestination;
import org.encinet.mik.protocol.ChatEvent;
import org.encinet.mik.protocol.ChatExternalIdentity;
import org.encinet.mik.protocol.ChatIdentity;
import org.encinet.mik.protocol.ChatReference;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatPresenceNotice;
import org.encinet.mik.protocol.ChatPublication;
import org.encinet.mik.protocol.ChatTextRun;
import org.encinet.mik.protocol.ChatTextStyle;
import org.encinet.mik.protocol.ChatVisibility;
import org.encinet.mik.protocol.PaperChatDelivery;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChatRecordCodec {
    private ChatRecordCodec() { }

    public static ChatPublication publication(ChatMessage message, Optional<Set<UUID>> viewers, boolean requireRemoteRecipient) {
        if (message.origin() != ChatOrigin.Player.MINECRAFT) {
            throw new IllegalArgumentException("Paper publishes only game chat");
        }
        var sender = message.sender();
        var post = ChatPublication.Post.newBuilder().setAuthor(player(sender.minecraftId().orElseThrow(), sender.minecraftName()));
        switch (message.channel()) {
            case PUBLIC -> post.setRoom(ChatDestination.Room.PUBLIC);
            case STAFF -> post.setRoom(ChatDestination.Room.STAFF);
            case PRIVATE -> {
                var target = message.target();
                post.setDirect(ChatPublication.Direct.newBuilder()
                        .setRecipient(player(message.targetId(), target.minecraftName()))
                        .setRequireRemoteRecipient(requireRemoteRecipient));
            }
        }
        for (var node : message.content().nodes()) post.addParts(inputPart(node));
        return publication(message.id().value(), message.createdAt(), viewers).setPost(post).build();
    }

    public static ChatPublication publication(ChatPresence notice, Optional<Set<UUID>> viewers) {
        var presence = ChatPresenceNotice.newBuilder().setPlayer(player(notice.player(), notice.name()))
                .setKind(notice.joined() ? ChatPresenceNotice.Kind.JOINED : ChatPresenceNotice.Kind.LEFT);
        return publication(notice.id().value(), notice.createdAt(), viewers).setPresence(presence).build();
    }

    static ChatPresence presence(ChatEvent entry) {
        var record = entry.getRecord();
        if (!record.hasPresence() || !record.getPlatform().equals("web"))
            throw new IllegalArgumentException("Invalid routed presence");
        var notice = record.getPresence();
        boolean joined = switch (notice.getKind()) {
            case JOINED -> true;
            case LEFT -> false;
            default -> throw new IllegalArgumentException("Invalid presence change");
        };
        return new ChatPresence(new ChatMessageId(entry.getMessageId()), Instant.ofEpochMilli(record.getCreatedAtMs()),
                UUID.fromString(notice.getPlayer().getId()), notice.getPlayer().getName(), joined);
    }

    public static ChatDelivery delivery(PaperChatDelivery delivery) {
        var entry = delivery.getEvent();
        var record = entry.getRecord();
        var user = record.getUser();
        var author = playerSnapshot(user.getAuthor());
        var external = externalAccount(user.getAuthor());
        UUID sender = author.map(player -> UUID.fromString(player.getId())).orElse(null);
        ChatOrigin origin;
        if (external.isPresent()) {
            origin = new ChatOrigin.Social(record.getPlatform());
        } else if (record.getPlatform().equals("web") && sender != null) {
            origin = ChatOrigin.Player.WEB;
        } else {
            throw new IllegalArgumentException("Invalid routed player origin");
        }
        var destination = user.getDestination();
        var target = destination.hasRecipient() ? ChatSender.minecraft(UUID.fromString(destination.getRecipient().getId()),
                destination.getRecipient().getName()) : null;
        var channel = switch (destination.getTargetCase()) {
            case RECIPIENT -> ChatChannel.PRIVATE;
            case ROOM -> switch (destination.getRoom()) {
                case PUBLIC -> ChatChannel.PUBLIC;
                case STAFF -> ChatChannel.STAFF;
                default -> throw new IllegalArgumentException("Invalid routed chat room");
            };
            case TARGET_NOT_SET -> throw new IllegalArgumentException("Missing routed chat destination");
        };
        var nodes = new ArrayList<ChatNode>();
        for (var part : user.getBody().getPartsList()) {
            switch (part.getContentCase()) {
                case TEXT -> nodes.add(decodeRun(part.getText()));
                case REFERENCE -> decodeReference(part.getReference(), nodes);
                case DESCRIPTION -> {
                    var description = part.getDescription();
                    var style = decodeStyle(description.getStyle());
                    switch (description.getLabelCase()) {
                        case NAME -> nodes.add(new ChatNode.Text(gameText(description.getName()), style));
                        case KIND -> nodes.add(new ChatNode.Placeholder(ChatPlaceholder.valueOf(description.getKind().name()), style));
                        case LABEL_NOT_SET -> throw new IllegalArgumentException("Missing description label");
                    }
                }
                case CONTENT_NOT_SET -> throw new IllegalArgumentException("Missing chat part");
            }
        }
        var content = new ChatContent(nodes);
        String playerName = author.map(ChatPlayer::getName).orElse("");
        String name = origin instanceof ChatOrigin.Social
                ? external.map(ExternalIdentity::displayName).filter(value -> !value.isBlank())
                        .orElseGet(() -> compactName(external.orElseThrow().key().subject()))
                : playerName;
        var identity = new ChatSender(name, Optional.ofNullable(sender), external, playerName);
        var message = new ChatMessage(new ChatMessageId(entry.getMessageId()), origin, identity, channel, target,
                Instant.ofEpochMilli(record.getCreatedAtMs()), content);
        var mentionedPlayers = delivery.getMentionPlayerIdsList().stream().map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
        return new ChatDelivery(message, visibility(entry), new ChatMentions(mentionedPlayers, false));
    }

    static Optional<Set<UUID>> visibility(ChatEvent event) {
        return event.hasVisibility() ? Optional.of(event.getVisibility().getPlayerIdsList().stream().map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet())) : Optional.empty();
    }

    private static ChatPublication.Builder publication(String id, Instant time, Optional<Set<UUID>> viewers) {
        var entry = ChatPublication.newBuilder().setMessageId(id).setCreatedAtMs(time.toEpochMilli());
        viewers.ifPresent(scope -> entry.setVisibilityLimit(ChatVisibility.newBuilder()
                .addAllPlayerIds(scope.stream().map(UUID::toString).sorted().toList())));
        return entry;
    }

    private static String compactName(String value) {
        return value.substring(0, value.offsetByCodePoints(0, Math.min(64, value.codePointCount(0, value.length()))));
    }

    private static String gameText(String value) {
        var line = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> line.appendCodePoint(Character.isISOControl(codePoint) ? ' ' : codePoint));
        return line.toString();
    }

    private static Optional<ChatPlayer> playerSnapshot(ChatIdentity person) {
        return switch (person.getPrincipalCase()) {
            case PLAYER -> Optional.of(person.getPlayer());
            case EXTERNAL -> person.getExternal().hasBoundPlayer() ? Optional.of(person.getExternal().getBoundPlayer()) : Optional.empty();
            case PRINCIPAL_NOT_SET -> Optional.empty();
        };
    }

    private static Optional<ExternalIdentity> externalAccount(ChatIdentity person) {
        return person.hasExternal() ? Optional.of(identity(person.getExternal().getAccount())) : Optional.empty();
    }

    private static ChatPlayer player(UUID id, String name) {
        return ChatPlayer.newBuilder().setId(id.toString()).setName(name).build();
    }

    private static ExternalIdentity identity(ChatExternalIdentity identity) {
        return new ExternalIdentity(new ExternalIdentityKey(identity.getPlatform(), identity.getIssuer(),
                identity.getScope(), identity.getSubject()), identity.getDisplayName(), identity.getScopeDisplayName());
    }

    private static ChatPublication.Post.Part.Builder inputPart(ChatNode node) {
        var input = ChatPublication.Post.Part.newBuilder();
        var part = ChatPart.newBuilder();
        switch (node) {
            case ChatNode.PendingMention mention -> {
                return input.setPlayerMention(ChatPublication.Post.Part.PlayerMention.newBuilder()
                        .setPlayerName(mention.playerName()).addAllRuns(encodeRuns(mention.runs())));
            }
            case ChatNode.Text text -> part.setText(encodeRun(text));
            case ChatNode.Reference reference -> part.setReference(encodeReference(reference));
            case ChatNode.Item item -> part.setDescription(ChatDescription.newBuilder().setName(item.fallback())
                    .setStyle(style(item.style())));
            case ChatNode.Placeholder description -> part.setDescription(ChatDescription.newBuilder()
                    .setKind(ChatDescription.Kind.valueOf(description.kind().name())).setStyle(style(description.style())));
        }
        return input.setResolved(part);
    }

    private static void decodeReference(ChatReference reference, List<ChatNode> nodes) {
        var runs = decodeRuns(reference.getRunsList());
        switch (reference.getTargetCase()) {
            case LINK -> {
                var link = reference.getLink();
                nodes.add(new ChatNode.Reference(runs, new ChatNode.Reference.Link(URI.create(link.getUri()), link.getAutomatic())));
            }
            case PERSON -> {
                var person = reference.getPerson();
                switch (person.getPrincipalCase()) {
                    case PLAYER -> {
                        var player = person.getPlayer();
                        nodes.add(new ChatNode.Reference(runs,
                                new ChatNode.Reference.Person(UUID.fromString(player.getId()), player.getName())));
                    }
                    case EXTERNAL -> nodes.addAll(runs);
                    case PRINCIPAL_NOT_SET -> throw new IllegalArgumentException("Missing mentioned identity");
                }
            }
            case AUDIENCE -> nodes.addAll(runs);
            case TARGET_NOT_SET -> throw new IllegalArgumentException("Missing reference target");
        }
    }

    private static ChatReference.Builder encodeReference(ChatNode.Reference reference) {
        var encoded = ChatReference.newBuilder().addAllRuns(encodeRuns(reference.runs()));
        return switch (reference.target()) {
            case ChatNode.Reference.Link link -> encoded.setLink(ChatReference.Link.newBuilder()
                    .setUri(link.uri().toASCIIString()).setAutomatic(link.automatic()));
            case ChatNode.Reference.Person person -> encoded.setPerson(ChatIdentity.newBuilder()
                    .setPlayer(player(person.playerId(), person.playerName())));
            case ChatNode.Reference.Audience _ -> encoded.setAudience(ChatReference.Audience.getDefaultInstance());
        };
    }

    private static ChatNode.Text decodeRun(ChatTextRun run) {
        return new ChatNode.Text(gameText(run.getText()), decodeStyle(run.getStyle()));
    }

    private static List<ChatNode.Text> decodeRuns(List<ChatTextRun> runs) {
        return runs.stream().map(ChatRecordCodec::decodeRun).toList();
    }

    private static List<ChatTextRun> encodeRuns(List<ChatNode.Text> runs) {
        return runs.stream().map(run -> encodeRun(run).build()).toList();
    }

    private static ChatTextRun.Builder encodeRun(ChatNode.Text run) {
        var text = ChatTextRun.newBuilder().setText(run.text());
        if (!run.style().equals(ChatStyle.EMPTY)) text.setStyle(style(run.style()));
        return text;
    }

    private static ChatStyle decodeStyle(ChatTextStyle style) {
        return new ChatStyle(style.hasColor() ? style.getColor() : null,
                style.hasGradientEndColor() ? style.getGradientEndColor() : null, style.getBold(), style.getItalic(),
                style.getUnderlined(), style.getStrikethrough());
    }

    private static ChatTextStyle style(ChatStyle style) {
        var encoded = ChatTextStyle.newBuilder().setBold(style.bold()).setItalic(style.italic())
                .setUnderlined(style.underlined()).setStrikethrough(style.strikethrough());
        if (style.color() != null) encoded.setColor(style.color());
        if (style.gradientEndColor() != null) encoded.setGradientEndColor(style.gradientEndColor());
        return encoded.build();
    }

}
