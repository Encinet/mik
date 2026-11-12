package org.encinet.mik.feature.community.social.api;

import org.encinet.mik.feature.community.social.document.SocialDocument;

/** A platform-captured capability for replying to exactly one inbound event. */
@FunctionalInterface
public interface SocialReplyChannel {
    SocialPreparedReply prepare(SocialDocument document, int maximumLength);
}
