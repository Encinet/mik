package org.encinet.mik.module.chat.bridge;

import org.encinet.mik.module.chat.model.ChatSubmission;

/** Main-thread game display boundary for an admitted external chat message. */
@FunctionalInterface
public interface SocialChatGameSink {
    void display(
            String platformName,
            ChatSubmission submission
    );
}
