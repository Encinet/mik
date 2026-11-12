package org.encinet.mik.feature.community.social.command;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Holds execution facts before any potentially failing localization or presentation. */
public record SocialCommandExecution<R>(R value, LanguageTarget language,
                                        Optional<SocialCompletionReceipt> receipt) {
    public SocialCommandExecution {
        value = Objects.requireNonNull(value, "value");
        language = Objects.requireNonNull(language, "language");
        receipt = Objects.requireNonNull(receipt, "receipt");
    }

    public static <R> SocialCommandExecution<R> result(R value, LanguageTarget language) {
        return new SocialCommandExecution<>(value, language, Optional.empty());
    }

    public static <R> SocialCommandExecution<R> localized(R value, Language language) {
        return result(value, LanguageTarget.fixed(language));
    }

    public record LanguageTarget(Language fallback, Optional<UUID> playerId) {
        public LanguageTarget {
            fallback = Objects.requireNonNull(fallback, "fallback");
            playerId = Objects.requireNonNull(playerId, "playerId");
        }

        public static LanguageTarget fixed(Language language) {
            return new LanguageTarget(language, Optional.empty());
        }

        public Language resolve(LanguageService languages) {
            return playerId.flatMap(languages::preferredLanguage).orElse(fallback);
        }
    }
}
