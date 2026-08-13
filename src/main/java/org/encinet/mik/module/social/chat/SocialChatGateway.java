package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;

import java.util.concurrent.CompletionStage;

/** Platform-neutral boundary used to deliver external chat into the game. */
@FunctionalInterface
public interface SocialChatGateway {
    CompletionStage<Void> deliver(
            SocialPlatformDescriptor platform,
            ChatSubmission submission
    );
}
