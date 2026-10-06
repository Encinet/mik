package org.encinet.mik.module.governance.platform.paper.command;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

final class GovernanceDurationParser {

    private GovernanceDurationParser() {
    }

    static Optional<Duration> parse(String source) {
        if (source == null || source.length() < 2) return Optional.empty();
        String normalized = source.strip().toLowerCase(Locale.ROOT);
        try {
            long amount = Long.parseLong(normalized.substring(0, normalized.length() - 1));
            if (amount <= 0) return Optional.empty();
            return switch (normalized.charAt(normalized.length() - 1)) {
                case 'm' -> Optional.of(Duration.ofMinutes(amount));
                case 'h' -> Optional.of(Duration.ofHours(amount));
                case 'd' -> Optional.of(Duration.ofDays(amount));
                default -> Optional.empty();
            };
        } catch (ArithmeticException | NumberFormatException error) {
            return Optional.empty();
        }
    }
}
