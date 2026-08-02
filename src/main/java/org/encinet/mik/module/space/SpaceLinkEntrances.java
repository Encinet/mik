package org.encinet.mik.module.space;

/**
 * Selects which configured {@code through} faces can initiate traversal.
 * Every enabled entrance always receives its exact inverse route.
 */
public enum SpaceLinkEntrances {

    /** Only the first surface's configured face initiates; its return route is automatic. */
    FIRST,

    /** Both surfaces' configured faces initiate, producing two reciprocal route pairs. */
    BOTH
}
