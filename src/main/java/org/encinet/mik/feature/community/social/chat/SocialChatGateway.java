package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;

import java.util.concurrent.CompletionStage;

/** Platform-neutral boundary used to deliver external chat into the game. */
@FunctionalInterface
public interface SocialChatGateway {
    CompletionStage<Void> deliver(
            SocialPlatformDescriptor platform,
            ChatSubmission submission
    );
}
