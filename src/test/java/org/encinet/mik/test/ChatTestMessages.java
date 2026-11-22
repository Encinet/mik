package org.encinet.mik.test;

import org.encinet.mik.feature.community.chat.bridge.ChatRecordCodec;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatConversation;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatRecord;
import org.encinet.mik.protocol.ChatSocialSource;
import org.encinet.mik.protocol.ChatUserRecord;
import org.encinet.mik.protocol.HistoryChannel;
import org.encinet.mik.protocol.HistoryEntry;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class ChatTestMessages {
    private ChatTestMessages() {
    }

    public static ChatMessage minecraft(String senderName, String body) {
        return minecraft(senderName, body, ChatContent.plain(body));
    }

    public static ChatMessage minecraft(
            String senderName,
            String body,
            ChatContent content
    ) {
        UUID senderId = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Minecraft(senderId),
                ChatSender.minecraft(senderId, senderName),
                ChatConversation.minecraftPublic(), body,
                Instant.EPOCH,
                ChatProcessingContext.external());
        return new ChatMessage(submission, content, Set.of());
    }

    public static ChatDelivery routedSocial(ChatSocialSource.Builder source, ChatSender sender,
                                            HistoryChannel channel, ChatBody.Builder body) {
        return ChatRecordCodec.delivery(HistoryEntry.newBuilder()
                .setMessageId(ChatMessageId.random().value()).setChannel(channel)
                .setRecord(ChatRecord.newBuilder().setUser(ChatUserRecord.newBuilder()
                        .setSource(ChatUserRecord.Source.SOCIAL).setSocial(source)
                        .setAuthor(ChatRecordCodec.author(sender)).setBody(body)))
                .build());
    }
}
