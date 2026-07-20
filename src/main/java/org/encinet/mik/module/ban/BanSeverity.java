package org.encinet.mik.module.ban;

import org.encinet.mik.module.i18n.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The moderator-facing ban levels. Their ids are persisted in the reason field
 * so existing ban storage remains compatible while the rendered text is localized.
 */
public enum BanSeverity {
    WARNING("warning", Duration.ofDays(3), Message.BAN_SEVERITY_WARNING),
    EXCUSABLE("excusable", Duration.ofDays(30), Message.BAN_SEVERITY_EXCUSABLE),
    MINOR("minor", Duration.ofDays(150), Message.BAN_SEVERITY_MINOR),
    SEVERE("severe", Duration.ofDays(360), Message.BAN_SEVERITY_SEVERE),
    EXTREME("extreme", Duration.ofDays(1500), Message.BAN_SEVERITY_EXTREME);

    private static final String REASON_PREFIX = "severity:";
    private static final String REASON_SEPARATOR = "\n";

    private final String id;
    private final Duration duration;
    private final Message label;

    BanSeverity(String id, Duration duration, Message label) {
        this.id = id;
        this.duration = duration;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public Message label() {
        return label;
    }

    public Instant expiresAt(Instant now) {
        return now.plus(duration);
    }

    String storedReason() {
        return REASON_PREFIX + id;
    }

    String storedReason(String reason) {
        return storedReason() + REASON_SEPARATOR + reason;
    }

    static Optional<BanSeverity> fromId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(severity -> severity.id.equals(normalized)).findFirst();
    }

    static Optional<BanSeverity> fromStoredReason(String reason) {
        if (reason == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(severity -> reason.equals(severity.storedReason())
                        || reason.startsWith(severity.storedReason() + REASON_SEPARATOR))
                .findFirst();
    }

    public static String userReason(String storedReason) {
        BanSeverity severity = fromStoredReason(storedReason).orElse(null);
        if (severity == null) {
            return storedReason;
        }
        String prefix = severity.storedReason();
        return storedReason.length() == prefix.length()
                ? ""
                : storedReason.substring(prefix.length() + REASON_SEPARATOR.length());
    }
}
