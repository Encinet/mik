package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class MatrixCryptoPresenterTest {
    @Test
    void statusLocalizesStatesWithoutChangingFingerprintsOrSasComparisonValues() {
        var languages = FluentTestSupport.languages();
        MatrixCryptoPresenter presenter = new MatrixCryptoPresenter(languages);
        var transaction = new MatrixCryptoResult.Transaction("txn", "@bob:example.org", "BOB",
                MatrixCryptoResult.Phase.COMPARISON, List.of(1234, 2345, 3456), List.of("🐶", "🌳"));
        var status = new MatrixCryptoResult.Status("@bot:example.org", "DEVICE", "exact+signing/key",
                MatrixCryptoResult.Verification.VERIFIED, Optional.empty(), Optional.of(transaction), Optional.empty());
        String output = presenter.render(status);
        assertTrue(output.contains("已验证"), output);
        assertTrue(output.contains("等待比对"), output);
        assertTrue(output.contains("exact+signing/key"), output);
        assertTrue(output.contains("SAS: 1234 2345 3456 · 🐶 🌳"), output);
        assertFalse(output.contains("AlreadyCrossSigned"), output);
        for (Language language : Language.values()) {
            String unknown = languages.t(language, Message.SOCIAL_CRYPTO_STATE, "unknown");
            List<Enum<?>> states = new java.util.ArrayList<>();
            states.addAll(List.of(MatrixCryptoResult.Verification.values()));
            states.addAll(List.of(MatrixCryptoResult.Phase.values()));
            states.addAll(List.of(MatrixCryptoResult.Trust.values()));
            for (var state : states) {
                String localized = languages.t(language, Message.SOCIAL_CRYPTO_STATE,
                        state.name().toLowerCase(java.util.Locale.ROOT));
                assertFalse(localized.isBlank());
                if (!state.name().equals("UNKNOWN")) assertNotEquals(unknown, localized,
                        language + " / " + state);
            }
        }
    }

    @Test
    void expectedErrorsAreTranslatedAndUnexpectedErrorsKeepInternalDetailsInLogs() {
        var presenter = new MatrixCryptoPresenter(FluentTestSupport.languages());
        assertTrue(presenter.failure(new CompletionException(new MatrixCryptoException(
                MatrixCryptoException.Reason.TRANSACTION_REQUIRED))).contains("完整 ID"));
        String failure = presenter.failure(new IllegalStateException("private SDK state"));
        assertTrue(failure.contains("服务器日志"));
        assertFalse(failure.contains("private SDK state"));
    }
}
