package org.encinet.mik.module.ban;

import java.util.Optional;

final class BanReason {

    private BanReason() {
    }

    static Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
