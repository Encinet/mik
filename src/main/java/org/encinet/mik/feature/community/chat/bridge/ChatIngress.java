package org.encinet.mik.feature.community.chat.bridge;

import org.encinet.mik.feature.community.chat.model.ChatSubmission;

/** Main-thread admission of trusted platform input into the shared chat pipeline. */
@FunctionalInterface
public interface ChatIngress {
    void accept(ChatSubmission submission);
}
