package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.feature.community.social.document.SocialDocument;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Type-erased invocation result retaining the receipt even if its presenter fails. */
public record SocialCommandResult(SocialCommandExecution.LanguageTarget language,
                                  Optional<SocialCompletionReceipt> receipt,
                                  Function<SocialPresentationContext, SocialDocument> presentation) {
    public SocialCommandResult {
        language = Objects.requireNonNull(language, "language");
        receipt = Objects.requireNonNull(receipt, "receipt");
        presentation = Objects.requireNonNull(presentation, "presentation");
    }

    public SocialDocument present(SocialPresentationContext context) {
        SocialDocument document = Objects.requireNonNull(presentation.apply(context), "presented document");
        if (document.language() != context.language()) {
            throw new IllegalStateException("Presenter changed the resolved response language");
        }
        return document;
    }
}
