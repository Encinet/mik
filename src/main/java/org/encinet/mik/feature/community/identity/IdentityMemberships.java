package org.encinet.mik.feature.community.identity;

/** Community membership evidence supplied by an adapter, independently of bindings. */
public interface IdentityMemberships {
    enum State { JOINED, ABSENT, UNKNOWN }

    /** Whether this source covers the identity's namespace, including while its data is unavailable. */
    boolean owns(ExternalIdentityKey identity);

    /** UNKNOWN includes unrelated authorities and incomplete or unavailable membership data.
     * ABSENT requires evidence of absence in every configured community; unobserved communities remain unknown. */
    State state(ExternalIdentityKey identity);

    /** Notifications follow committed snapshots. Closing detaches the listener; in-flight notifications may finish. */
    AutoCloseable subscribe(Runnable changed);
}
