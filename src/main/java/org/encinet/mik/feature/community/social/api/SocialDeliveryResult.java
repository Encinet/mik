package org.encinet.mik.feature.community.social.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/** Accepted means the platform acknowledged the message, not that a person read it. */
public record SocialDeliveryResult(Status status, Optional<Throwable> failure) {
    public SocialDeliveryResult {
        status = Objects.requireNonNull(status, "status");
        failure = Objects.requireNonNull(failure, "failure");
        if ((status == Status.ACCEPTED) == failure.isPresent()) {
            throw new IllegalArgumentException("Only unsuccessful deliveries have a failure");
        }
    }

    public enum Status { ACCEPTED, NOT_SENT, UNKNOWN }

    public static SocialDeliveryResult accepted() { return new SocialDeliveryResult(Status.ACCEPTED, Optional.empty()); }
    public static SocialDeliveryResult notSent(Throwable error) { return new SocialDeliveryResult(Status.NOT_SENT, Optional.of(error)); }
    public static SocialDeliveryResult unknown(Throwable error) { return new SocialDeliveryResult(Status.UNKNOWN, Optional.of(error)); }

    /** Records uncertainty and propagates lifecycle cancellation to the active HTTP operation. */
    public static CompletableFuture<SocialDeliveryResult> observe(CompletionStage<Void> operation,
                                                                  Function<Throwable, Status> classification) {
        Objects.requireNonNull(classification, "classification");
        var source = operation.toCompletableFuture();
        var result = new CompletableFuture<SocialDeliveryResult>();
        result.whenComplete((_, _) -> { if (result.isCancelled()) source.cancel(true); });
        source.whenComplete((_, error) -> {
            if (error == null) result.complete(accepted());
            else {
                Throwable cause = SocialFutures.unwrap(error);
                if (cause instanceof CancellationException) result.cancel(false);
                else {
                    try { result.complete(new SocialDeliveryResult(classification.apply(cause), Optional.of(cause))); }
                    catch (RuntimeException failure) { result.completeExceptionally(failure); }
                }
            }
        });
        return result;
    }
}
