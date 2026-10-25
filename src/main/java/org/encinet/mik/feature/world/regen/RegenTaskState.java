package org.encinet.mik.feature.world.regen;

public enum RegenTaskState {
    PREPARING,
    GENERATING,
    APPLYING,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
