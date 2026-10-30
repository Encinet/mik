package org.encinet.mik.feature.player.security;

/** Immutable current credential. Object identity guards work started before a replacement. */
final class PasswordCredential {
    static final PasswordCredential NONE = new PasswordCredential(null);
    private final String hash;

    PasswordCredential(String hash) {
        if (hash != null) PasswordHasher.validateEncoding(hash);
        this.hash = hash;
    }

    String hash() { return hash; }
    boolean present() { return hash != null; }
    @Override public String toString() { return "PasswordCredential[present=" + present() + "]"; }
}
