package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Locale;

/** Console presentation, including translated protocol states and expected errors. */
final class MatrixCryptoPresenter {
    private final LanguageService languages;
    MatrixCryptoPresenter(LanguageService languages) { this.languages = languages; }

    String render(MatrixCryptoResult result) {
        return switch (result) {
            case MatrixCryptoResult.Requested requested -> text(Message.SOCIAL_CRYPTO_REQUESTED, requested.transactionId());
            case MatrixCryptoResult.Updated _ -> text(Message.SOCIAL_CRYPTO_UPDATED);
            case MatrixCryptoResult.Status status -> {
                StringBuilder body = new StringBuilder(text(Message.SOCIAL_CRYPTO_DEVICE,
                        status.userId(), status.deviceId(), status.signingKey()));
                body.append('\n').append(text(Message.SOCIAL_CRYPTO_STATUS, state(status.verification()),
                        status.backupVersion().orElseGet(() -> text(Message.SOCIAL_CRYPTO_UNAVAILABLE))));
                status.transaction().ifPresent(transaction -> {
                    body.append('\n').append(text(Message.SOCIAL_CRYPTO_TRANSACTION, transaction.id(),
                            transaction.userId(), transaction.deviceId(), state(transaction.state())));
                    // SAS integers and glyphs are protocol comparison values; preserve their exact form.
                    if (!transaction.decimals().isEmpty()) body.append("\nSAS: ")
                            .append(String.join(" ", transaction.decimals().stream().map(String::valueOf).toList()))
                            .append(" · ").append(String.join(" ", transaction.emojis()));
                });
                status.target().ifPresent(target -> body.append('\n').append(text(Message.SOCIAL_CRYPTO_TARGET,
                        target.userId(), target.deviceId(), state(target.trust()),
                        state(target.verified() ? MatrixCryptoResult.Verification.VERIFIED : MatrixCryptoResult.Verification.UNVERIFIED))));
                yield body.toString();
            }
        };
    }

    String failure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        if (!(cause instanceof MatrixCryptoException expected)) return text(Message.SOCIAL_CRYPTO_FAILED);
        return text(switch (expected.reason()) {
            case NOT_RUNNING -> Message.SOCIAL_CRYPTO_NOT_RUNNING;
            case USAGE -> Message.SOCIAL_CRYPTO_USAGE;
            case NO_ACTIVE -> Message.SOCIAL_CRYPTO_NO_ACTIVE;
            case TRANSACTION_REQUIRED -> Message.SOCIAL_CRYPTO_TRANSACTION_REQUIRED;
            case NOT_ACCEPTING -> Message.SOCIAL_CRYPTO_NOT_ACCEPTING;
            case COMPARISON_NOT_READY -> Message.SOCIAL_CRYPTO_COMPARISON_NOT_READY;
        });
    }

    private String state(Enum<?> state) {
        return text(Message.SOCIAL_CRYPTO_STATE, state.name().toLowerCase(Locale.ROOT));
    }

    private String text(Message message, Object... arguments) {
        return languages.t(Language.DEFAULT, message, arguments);
    }
}
