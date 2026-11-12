package org.encinet.mik.feature.community.social.api;

import org.encinet.mik.feature.community.social.document.SocialDocument;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Immutable layout plus a single-use delivery capability; preparing it performs no I/O. */
public final class SocialPreparedReply {
    private final SocialDocument document;
    private final Supplier<? extends CompletionStage<SocialDeliveryResult>> sender;
    private CompletableFuture<SocialDeliveryResult> delivery;

    public SocialPreparedReply(SocialDocument document,
                               Supplier<? extends CompletionStage<SocialDeliveryResult>> sender) {
        this.document = Objects.requireNonNull(document, "document");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public SocialDocument document() { return document; }

    public synchronized CompletableFuture<SocialDeliveryResult> send() {
        if (delivery == null) {
            try { delivery = Objects.requireNonNull(sender.get(), "delivery").toCompletableFuture(); }
            catch (RuntimeException error) { delivery = CompletableFuture.completedFuture(SocialDeliveryResult.unknown(error)); }
        }
        return delivery;
    }

}
