package org.encinet.mik.feature.community.social.runtime;

import org.encinet.mik.feature.community.social.SocialTestReplies;
import org.encinet.mik.feature.community.social.api.SocialConversation;
import org.encinet.mik.feature.community.social.api.SocialInboundMessage;
import org.encinet.mik.feature.community.social.api.SocialPlatformDescriptor;
import org.encinet.mik.feature.community.social.command.*;
import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.safety.SocialContentModerator;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.REQUIRED;
import static org.encinet.mik.feature.community.social.document.SocialDocument.Moderation.TRUSTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SocialCommandProcessorTest {
    @Test void completedActionKeepsItsReceiptWhenReviewIsBlockedOrUnavailable() {
        verifyReceipt(_ -> false, false, false);
        verifyReceipt(_ -> { throw new IllegalStateException("offline"); }, false, false);
    }
    @Test void presentationFailureDoesNotLoseTheCommittedAction() { verifyReceipt(_ -> true, true, false); }
    @Test void failedDeliveryDoesNotRetryTheMessageOrTheAction() { verifyReceipt(_ -> true, false, true); }

    private static void verifyReceipt(SocialContentModerator moderator, boolean presentationFails, boolean deliveryFails) {
        var actions = new AtomicInteger();
        var languageLookups = new AtomicInteger();
        var delivered = new ArrayList<SocialDocument>();
        var fluent = FluentTestSupport.languages();
        LanguageService languages = new LanguageService(null) {
            @Override public Optional<Language> preferredLanguage(UUID id) {
                return Optional.of(languageLookups.getAndIncrement() == 0 ? Language.EN_US : Language.JA_JP);
            }
            @Override public String t(Language language, Message message, Object... args) { return fluent.t(language, message, args); }
        };
        SocialCommand<NoArguments, Boolean> command = new SocialCommand<>() {
            @Override public SocialCommandDefinition<NoArguments> definition() {
                return new SocialCommandDefinition<>(SocialCommandSpec.explicit("binding.link", "bind"), Optional.empty(), SocialArguments.none());
            }
            @Override public SocialCommandExecution<Boolean> execute(SocialCommandContext context, NoArguments input) {
                actions.incrementAndGet();
                return new SocialCommandExecution<>(true, new SocialCommandExecution.LanguageTarget(Language.ZH_CN, Optional.of(UUID.randomUUID())),
                        Optional.of(SocialCompletionReceipt.BINDING_LINKED));
            }
            @Override public SocialDocument present(SocialPresentationContext context, Boolean result) {
                if (presentationFails) throw new IllegalStateException("renderer failed");
                return SocialDocument.message(REQUIRED, SocialDocument.Tone.SUCCESS,
                        SocialDocument.paragraph("Account linked to a dynamic name.")).localized(context.language());
            }
        };
        var message = new SocialInboundMessage("event", new SocialConversation("group", SocialConversation.Type.GROUP),
                Optional.empty(), "/bind", false, SocialTestReplies.channel(document -> {
                    delivered.add(document);
                    return deliveryFails ? CompletableFuture.failedFuture(new IllegalStateException("network"))
                            : CompletableFuture.completedFuture(null);
                }));
        var dispatcher = SocialCommandTestSupport.dispatcher(List.of(command));
        var invocation = dispatcher.resolve(SocialCommandSyntax.caseInsensitive(Language.ZH_CN, "/"), message).orElseThrow();
        try (var guard = SocialContentGuard.available(moderator, SocialDocument.of(TRUSTED, "Hidden", SocialDocument.Tone.WARNING))) {
            new SocialCommandProcessor(guard, languages, Logger.getAnonymousLogger()).execute(
                    new SocialPlatformDescriptor("qq", "QQ", 0x55FFFF), message, invocation,
                    new SocialOutputPolicy(true, 1_000), () -> true, UUID.randomUUID());
        }
        assertEquals(1, actions.get());
        assertEquals(1, languageLookups.get(), "the receipt and main response must share one language snapshot");
        assertEquals(1, delivered.size());
        assertEquals(Language.EN_US, delivered.getFirst().language());
        if (!deliveryFails) assertEquals(fluent.t(Language.EN_US, Message.SOCIAL_BINDING_LINK_COMPLETED), delivered.getFirst().plainText());
    }
}
