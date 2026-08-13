package org.encinet.mik.module.social.runtime;

import org.encinet.mik.module.social.document.SocialDocument;
import org.encinet.mik.module.social.safety.SocialContentSafetyFilter;
import org.encinet.mik.module.i18n.Language;

import java.util.Objects;
import java.util.function.Function;

/** Shared compiled safety rules and the common blocked-output document. */
public final class SocialContentGuard {
    private final SocialContentSafetyFilter filter;
    private final Function<Language, SocialDocument> blockedDocument;
    private final RuntimeException unavailable;

    private SocialContentGuard(
            SocialContentSafetyFilter filter,
            Function<Language, SocialDocument> blockedDocument,
            RuntimeException unavailable
    ) {
        this.filter = filter;
        this.blockedDocument = Objects.requireNonNull(blockedDocument, "blockedDocument");
        this.unavailable = unavailable;
    }

    public static SocialContentGuard available(
            SocialContentSafetyFilter filter,
            SocialDocument blockedDocument
    ) {
        return new SocialContentGuard(Objects.requireNonNull(filter, "filter"),
                ignored -> Objects.requireNonNull(blockedDocument, "blockedDocument"), null);
    }

    public static SocialContentGuard available(
            SocialContentSafetyFilter filter,
            Function<Language, SocialDocument> blockedDocument
    ) {
        return new SocialContentGuard(Objects.requireNonNull(filter, "filter"),
                blockedDocument, null);
    }

    public static SocialContentGuard unavailable(
            RuntimeException error,
            Function<Language, SocialDocument> blockedDocument
    ) {
        return new SocialContentGuard(null, blockedDocument,
                Objects.requireNonNull(error, "error"));
    }

    public void validate(SocialOutputPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        if (policy.contentSafetyEnabled() && unavailable != null) {
            throw new IllegalStateException("Shared social content safety is unavailable",
                    unavailable);
        }
    }

    public SocialDocument apply(SocialDocument document, SocialOutputPolicy policy) {
        Objects.requireNonNull(document, "document");
        validate(policy);
        String untrustedText = document.untrustedPlainText();
        SocialDocument safe = policy.contentSafetyEnabled()
                && !untrustedText.isBlank()
                && filter.match(untrustedText).isPresent()
                ? Objects.requireNonNull(blockedDocument.apply(document.language()),
                "Blocked-document factory returned null").localized(document.language())
                : document;
        return safe.truncated(policy.maximumLength());
    }

    /** Applies platform-output safety rules; inbound social chat must not call this. */
    public boolean allowsOutboundText(String text, SocialOutputPolicy policy) {
        Objects.requireNonNull(text, "text");
        validate(policy);
        return !policy.contentSafetyEnabled() || filter.match(text).isEmpty();
    }
}
