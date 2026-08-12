package org.encinet.mik.module.social.api;

import java.util.Objects;

/** Transport status pushed from a platform session into its current generation. */
public record SocialSessionStatus(State state, String endpoint, String lastError) {
    public SocialSessionStatus {
        state = Objects.requireNonNull(state, "state");
        endpoint = endpoint == null ? "" : endpoint;
        lastError = lastError == null ? "" : lastError;
    }

    public static SocialSessionStatus starting(String endpoint) {
        return new SocialSessionStatus(State.STARTING, endpoint, "");
    }

    public static SocialSessionStatus ready(String endpoint) {
        return new SocialSessionStatus(State.READY, endpoint, "");
    }

    public static SocialSessionStatus failed(String endpoint, String error) {
        return new SocialSessionStatus(State.FAILED, endpoint, error);
    }

    public static SocialSessionStatus stopped(String endpoint) {
        return new SocialSessionStatus(State.STOPPED, endpoint, "");
    }

    public enum State {
        STOPPED,
        STARTING,
        READY,
        FAILED
    }
}
