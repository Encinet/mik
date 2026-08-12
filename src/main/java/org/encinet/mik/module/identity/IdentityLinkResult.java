package org.encinet.mik.module.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record IdentityLinkResult(
        Status status,
        IdentityBinding binding,
        Instant retryAt
) {

    public IdentityLinkResult {
        status = Objects.requireNonNull(status, "status");
        switch (status) {
            case LINKED, ALREADY_LINKED -> {
                if (binding == null || retryAt != null) {
                    throw new IllegalArgumentException(
                            status + " requires a binding and no retry time");
                }
            }
            case RATE_LIMITED -> {
                if (binding != null || retryAt == null) {
                    throw new IllegalArgumentException(
                            "RATE_LIMITED requires a retry time and no binding");
                }
            }
            default -> {
                if (binding != null || retryAt != null) {
                    throw new IllegalArgumentException(
                            status + " must not contain a binding or retry time");
                }
            }
        }
    }

    public enum Status {
        LINKED,
        ALREADY_LINKED,
        INVALID_OR_EXPIRED_CODE,
        PLATFORM_MISMATCH,
        EXTERNAL_IDENTITY_IN_USE,
        PLAYER_SCOPE_IN_USE,
        RATE_LIMITED
    }

    public Optional<IdentityBinding> bindingOptional() {
        return Optional.ofNullable(binding);
    }

    public Optional<Instant> retryAtOptional() {
        return Optional.ofNullable(retryAt);
    }
}
