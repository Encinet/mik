package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.social.api.SocialInboundMessage;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.command.SocialCommandContext;
import org.encinet.mik.module.social.command.SocialCommandDispatcher;
import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executes a prepared command and applies the shared outbound pipeline. */
final class SocialCommandProcessor {
    private final SocialContentGuard contentGuard;
    private final Logger logger;

    SocialCommandProcessor(SocialContentGuard contentGuard, Logger logger) {
        this.contentGuard = Objects.requireNonNull(contentGuard, "contentGuard");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    void execute(
            SocialPlatformDescriptor platform,
            SocialInboundMessage message,
            SocialCommandDispatcher.PreparedInvocation invocation,
            SocialOutputPolicy outputPolicy,
            BooleanSupplier resultIsCurrent
    ) {
        try {
            if (!resultIsCurrent.getAsBoolean()) {
                return;
            }
            SocialDocument document = invocation.execute(
                    new SocialCommandContext(
                            platform, message, invocation.input(), invocation.syntax()));
            if (!resultIsCurrent.getAsBoolean()) {
                return;
            }
            document = contentGuard.apply(document, outputPolicy);
            if (!resultIsCurrent.getAsBoolean()) {
                return;
            }
            CompletionStage<Void> reply = Objects.requireNonNull(
                    message.replyChannel().send(document),
                    "Social reply channel returned null");
            awaitReply(platform, reply, resultIsCurrent);
        } catch (RuntimeException error) {
            if (resultIsCurrent.getAsBoolean()) {
                logger.log(Level.SEVERE,
                        "Social command " + invocation.commandId()
                                + " failed on platform " + platform.id(), error);
            }
        }
    }

    /**
     * Keeps the worker admission permit until the platform has completed its reply. This makes
     * the configured concurrency limit cover the whole command, rather than allowing an
     * unbounded number of HTTP replies to accumulate after fast handlers return.
     */
    private void awaitReply(
            SocialPlatformDescriptor platform,
            CompletionStage<Void> reply,
            BooleanSupplier resultIsCurrent
    ) {
        var future = reply.toCompletableFuture();
        try {
            future.get();
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (CancellationException error) {
            // Closing or replacing a generation intentionally cancels pending replies.
        } catch (ExecutionException error) {
            if (resultIsCurrent.getAsBoolean()) {
                logger.warning("Could not reply on social platform " + platform.id()
                        + ": " + rootMessage(error));
            }
        }
    }

    static String rootMessage(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
