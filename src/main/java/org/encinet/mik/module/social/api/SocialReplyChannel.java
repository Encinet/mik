package org.encinet.mik.module.social.api;

import org.encinet.mik.module.social.document.SocialDocument;

import java.util.concurrent.CompletionStage;

/** A platform-captured capability for replying to exactly one inbound event. */
@FunctionalInterface
public interface SocialReplyChannel {
    CompletionStage<Void> send(SocialDocument document);
}
