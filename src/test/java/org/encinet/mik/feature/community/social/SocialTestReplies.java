package org.encinet.mik.feature.community.social;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.api.SocialReplyChannel;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialDocumentLayout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** In-memory transport double; collecting a response happens only after review and send. */
public final class SocialTestReplies {
    private SocialTestReplies() { }

    public static SocialPreparedReply accepted(SocialDocument document, int maximumLength) {
        return new SocialPreparedReply(SocialDocumentLayout.limit(document, maximumLength),
                () -> CompletableFuture.completedFuture(SocialDeliveryResult.accepted()));
    }

    public static SocialReplyChannel channel(Function<SocialDocument, CompletionStage<Void>> sender) {
        return (document, maximumLength) -> {
            var bounded = SocialDocumentLayout.limit(document, maximumLength);
            return new SocialPreparedReply(bounded, () -> SocialDeliveryResult.observe(sender.apply(bounded),
                    _ -> SocialDeliveryResult.Status.UNKNOWN));
        };
    }
}
