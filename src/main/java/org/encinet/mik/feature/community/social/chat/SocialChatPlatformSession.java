package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialPlatformSession;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Optional session capability for sending Minecraft chat to a platform. */
public interface SocialChatPlatformSession extends SocialPlatformSession {
    List<SocialChatRoute> chatRoutes();

    CompletionStage<Void> sendChat(
            SocialConversation conversation,
            ChatMessage message
    );

    /** Resolves only identities currently joined in the exact destination conversation. */
    default SocialChatMentionResolution resolveMentions(
            SocialConversation conversation,
            List<SocialChatMentionRequest> requests
    ) {
        return SocialChatMentionResolution.empty();
    }

    default CompletionStage<Void> sendChat(
            SocialConversation conversation,
            ChatMessage message,
            SocialChatMentionResolution mentions
    ) {
        return sendChat(conversation, message);
    }
}
