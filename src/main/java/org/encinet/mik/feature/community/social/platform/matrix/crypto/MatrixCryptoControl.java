package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Stable console capability pointing only at the currently opened Matrix generation. */
public final class MatrixCryptoControl {
    private final AtomicReference<MatrixEncryption> current = new AtomicReference<>();

    public void opened(MatrixEncryption encryption) {
        current.set(encryption);
    }

    public void closed(MatrixEncryption encryption) {
        current.compareAndSet(encryption, null);
    }

    public CompletableFuture<MatrixCryptoResult> execute(String action, List<String> arguments) {
        MatrixEncryption encryption = current.get();
        return encryption == null ? CompletableFuture.failedFuture(new MatrixCryptoException(MatrixCryptoException.Reason.NOT_RUNNING))
                : encryption.manage(action, List.copyOf(arguments));
    }
}
