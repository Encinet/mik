package org.encinet.mik.feature.player.security;

/** Immutable current credential. Object identity guards work started before a replacement. */
final class PasswordCredential {
    static final PasswordCredential NONE = new PasswordCredential(null, 0);
    private final String hash;
    private final long changedAt;

    PasswordCredential(String hash, long changedAt) {
        if (hash != null) PasswordHasher.validateEncoding(hash);
        this.hash = hash;
        this.changedAt = changedAt;
    }

    String hash() { return hash; }
    long changedAt() { return changedAt; }
    boolean present() { return hash != null; }
    @Override public String toString() { return "PasswordCredential[present=" + present() + "]"; }
}
