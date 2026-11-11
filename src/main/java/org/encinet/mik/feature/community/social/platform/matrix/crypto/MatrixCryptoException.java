package org.encinet.mik.feature.community.social.platform.matrix.crypto;

/** Expected operator errors, separate from transport and SDK failures. */
public final class MatrixCryptoException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public enum Reason { NOT_RUNNING, USAGE, NO_ACTIVE, TRANSACTION_REQUIRED, NOT_ACCEPTING, COMPARISON_NOT_READY }
    private final Reason reason;

    public MatrixCryptoException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
