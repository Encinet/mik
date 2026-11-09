package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.chat.model.ChatSubmission;

import java.util.concurrent.CompletionStage;

/** Platform-neutral boundary used to deliver external chat into the game. */
@FunctionalInterface
public interface SocialChatGateway {
    CompletionStage<Void> deliver(ChatSubmission submission);
}
