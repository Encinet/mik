package org.encinet.mik.test;

import org.encinet.mik.feature.community.chat.bridge.ChatRecordCodec;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatRecord;
import org.encinet.mik.protocol.ChatUserRecord;
import org.encinet.mik.protocol.ChatDestination;
import org.encinet.mik.protocol.ChatEvent;
import org.encinet.mik.protocol.ChatExternalActor;
import org.encinet.mik.protocol.ChatExternalIdentity;
import org.encinet.mik.protocol.ChatIdentity;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.PaperChatDelivery;

import java.time.Instant;
import java.util.UUID;

public final class ChatTestMessages {
    private ChatTestMessages() {
    }

    public static ChatMessage minecraft(String senderName, ChatContent content) {
        return new ChatMessage(ChatMessageId.random(), ChatOrigin.Player.MINECRAFT, ChatSender.minecraft(UUID.randomUUID(), senderName),
                ChatChannel.PUBLIC, null, Instant.EPOCH, content);
    }

    public static ChatDelivery routedSocial(ChatSender sender,
                                            ChatDestination.Room room, ChatBody.Builder body) {
        var external = sender.externalIdentity().orElseThrow();
        var key = external.key();
        var actor = ChatExternalActor.newBuilder().setAccount(ChatExternalIdentity.newBuilder()
                .setPlatform(key.platform()).setIssuer(key.issuer()).setScope(key.scope()).setSubject(key.subject())
                .setDisplayName(external.displayName()).setScopeDisplayName(external.scopeDisplayName()));
        sender.minecraftId().ifPresent(id -> actor.setBoundPlayer(ChatPlayer.newBuilder()
                .setId(id.toString()).setName(sender.minecraftName())));
        return ChatRecordCodec.delivery(PaperChatDelivery.newBuilder().setEvent(ChatEvent.newBuilder()
                .setMessageId(ChatMessageId.random().value())
                .setRecord(ChatRecord.newBuilder().setPlatform(key.platform()).setUser(ChatUserRecord.newBuilder()
                        .setDestination(ChatDestination.newBuilder().setRoom(room))
                        .setAuthor(ChatIdentity.newBuilder().setExternal(actor)).setBody(body))))
                .build());
    }
}
