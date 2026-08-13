package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;

/** Main-thread game display boundary for an admitted external chat message. */
@FunctionalInterface
public interface SocialChatGameSink {
    void display(
            SocialPlatformDescriptor platform,
            ChatSubmission submission
    );
}
