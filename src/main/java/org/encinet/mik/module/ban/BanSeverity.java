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
    EXCUSABLE("excusable", Duration.ofDays(1), Message.BAN_SEVERITY_EXCUSABLE),
    MINOR("minor", Duration.ofDays(7), Message.BAN_SEVERITY_MINOR),
    SEVERE("severe", Duration.ofDays(30), Message.BAN_SEVERITY_SEVERE),
    EXTREME("extreme", Duration.ofDays(3000), Message.BAN_SEVERITY_EXTREME);

    private static final String REASON_PREFIX = "severity:";

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

    static Optional<BanSeverity> fromId(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(severity -> severity.id.equals(normalized)).findFirst();
    }

    static Optional<BanSeverity> fromStoredReason(String reason) {
        if (reason == null || !reason.startsWith(REASON_PREFIX)) {
            return Optional.empty();
        }
        return fromId(reason.substring(REASON_PREFIX.length()));
    }
}
