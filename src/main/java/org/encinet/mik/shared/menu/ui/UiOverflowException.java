package org.encinet.mik.shared.menu.ui;

/** A valid component needs another allocation or an explicit alternative layout. */
public final class UiOverflowException extends IllegalArgumentException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public UiOverflowException(String message) { super(message); }
    public UiOverflowException(String message, Throwable cause) { super(message, cause); }
}
