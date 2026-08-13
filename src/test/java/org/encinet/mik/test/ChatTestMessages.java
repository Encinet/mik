package org.encinet.mik.test;

import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatConversation;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatMessageId;
import org.encinet.mik.module.chat.model.ChatOrigin;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.chat.model.ChatReferences;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.chat.model.ChatSubmission;

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
                ChatReferences.empty(), Instant.EPOCH,
                ChatProcessingContext.external());
        return new ChatMessage(submission, content, Set.of());
    }
}
