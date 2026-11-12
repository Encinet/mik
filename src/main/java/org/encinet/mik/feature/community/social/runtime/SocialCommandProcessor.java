package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.api.SocialPreparedReply;
import org.encinet.mik.feature.community.social.command.SocialCommandContext;
import org.encinet.mik.feature.community.social.command.SocialCommandDispatcher;
import org.encinet.mik.feature.community.social.command.SocialCommandResult;
import org.encinet.mik.feature.community.social.command.SocialPresentationContext;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Execution facts survive presentation, moderation and delivery failures. */
final class SocialCommandProcessor {
    private final SocialContentGuard contentGuard;
    private final Logger logger;
    private final LanguageService languages;

    SocialCommandProcessor(SocialContentGuard contentGuard, LanguageService languages, Logger logger) {
        this.contentGuard = Objects.requireNonNull(contentGuard, "contentGuard");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    void execute(SocialPlatformDescriptor platform, SocialInboundMessage message,
                 SocialCommandDispatcher.PreparedInvocation invocation, SocialOutputPolicy outputPolicy,
                 BooleanSupplier resultIsCurrent, UUID generationId) {
        if (!resultIsCurrent.getAsBoolean()) return;
        var context = new SocialCommandContext(platform, message, invocation.input(), invocation.syntax(), generationId);
        final SocialCommandResult result;
        try {
            result = invocation.execute(context);
        } catch (RuntimeException error) {
            if (resultIsCurrent.getAsBoolean()) {
                logger.log(Level.SEVERE, "Social command " + invocation.commandId() + " failed on " + platform.id(), error);
                deliver(platform, message, unavailable(context.input().languageHint().orElse(context.syntax().defaultLanguage())),
                        outputPolicy, resultIsCurrent);
            }
            return;
        }
        if (!resultIsCurrent.getAsBoolean()) return;
        Language language = result.language().fallback();
        try { language = result.language().resolve(languages); }
        catch (RuntimeException error) { logger.warning("Could not resolve social response language: " + rootMessage(error)); }
        var presentation = new SocialPresentationContext(context, language, languages);
        SocialDocument receipt = result.receipt().map(value -> value.document(presentation)).orElse(null);
        SocialDocument document;
        try { document = result.present(presentation); }
        catch (RuntimeException error) {
            logger.log(Level.WARNING, "Could not present social command " + invocation.commandId(), error);
            document = receipt == null ? unavailable(language) : receipt;
        }
        if (!resultIsCurrent.getAsBoolean()) return;
        SocialPreparedReply prepared;
        try {
            prepared = message.replyChannel().prepare(document, outputPolicy.maximumLength());
            var review = contentGuard.review(prepared.document(), outputPolicy);
            if (!review.allowed()) prepared = message.replyChannel().prepare(
                    receipt == null ? review.document() : receipt, outputPolicy.maximumLength());
        } catch (RuntimeException error) {
            logger.warning("Could not prepare social response on " + platform.id() + ": " + rootMessage(error));
            deliver(platform, message, receipt == null ? unavailable(language) : receipt, outputPolicy, resultIsCurrent);
            return;
        }
        send(platform, prepared, resultIsCurrent);
    }

    private SocialDocument unavailable(Language language) {
        return SocialDocument.message(SocialDocument.Moderation.TRUSTED, SocialDocument.Tone.WARNING,
                SocialDocument.paragraph(languages.t(language, Message.SOCIAL_REPLY_UNAVAILABLE))).localized(language);
    }

    private void deliver(SocialPlatformDescriptor platform, SocialInboundMessage message, SocialDocument document,
                         SocialOutputPolicy policy, BooleanSupplier current) {
        if (!current.getAsBoolean()) return;
        try { send(platform, message.replyChannel().prepare(document, policy.maximumLength()), current); }
        catch (RuntimeException error) { logger.warning("Could not prepare social recovery on " + platform.id() + ": " + rootMessage(error)); }
    }

    /** Admission stays held until the platform acknowledges, rejects or loses certainty. */
    private void send(SocialPlatformDescriptor platform, SocialPreparedReply prepared, BooleanSupplier current) {
        if (!current.getAsBoolean()) return;
        var future = prepared.send();
        try {
            SocialDeliveryResult result = future.get();
            if (result.status() != SocialDeliveryResult.Status.ACCEPTED && current.getAsBoolean()) {
                logger.warning("Social delivery " + result.status() + " on " + platform.id()
                        + ": " + rootMessage(result.failure().orElseThrow()));
            }
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (CancellationException error) {
            // Closing the generation intentionally cancels pending delivery.
        } catch (ExecutionException error) {
            if (current.getAsBoolean()) logger.warning("Social delivery UNKNOWN on " + platform.id() + ": " + rootMessage(error));
        }
    }

    static String rootMessage(Throwable error) {
        Throwable cause = org.encinet.mik.feature.community.social.api.SocialFutures.unwrap(error);
        return cause.getMessage() == null || cause.getMessage().isBlank() ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
