package org.encinet.mik.module.governance.membership.promotion;

import java.time.Instant;
import java.util.Objects;

/** A reviewable promotion pause with all rulebook-mandated written fields. */
public record PromotionPause(
        long id,
        String reason,
        Instant startsAt,
        Instant endsAt,
        String appealPath,
        String imposedBy
) {
    public PromotionPause {
        reason = requireText(reason, "reason");
        startsAt = Objects.requireNonNull(startsAt, "startsAt");
        endsAt = Objects.requireNonNull(endsAt, "endsAt");
        appealPath = requireText(appealPath, "appealPath");
        imposedBy = requireText(imposedBy, "imposedBy");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
    }

    public boolean isActive(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
