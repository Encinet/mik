package org.encinet.mik.feature.player.security;

/** Safe, typed failures; messages never contain submitted credentials. */
public final class PasswordException extends RuntimeException {
    public enum Code { INVALID, MISMATCH, WEAK, CONFLICT, REQUIRED_ROLE, KEEP_ROLE, REQUIRED_APPLICATION, DENIED, SESSION_EXPIRED, STORAGE, BUSY, COOLDOWN }
    private final Code code;
    private final long retryAfterSeconds;

    public PasswordException(Code code) {
        super(code.name());
        this.code = code;
        this.retryAfterSeconds = 0;
    }

    public PasswordException(Code code, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.retryAfterSeconds = 0;
    }

    public PasswordException(Code code, long retryAfterSeconds) {
        super(code.name());
        if (code != Code.COOLDOWN || retryAfterSeconds < 1) throw new IllegalArgumentException("Invalid retry delay");
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Code code() { return code; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
