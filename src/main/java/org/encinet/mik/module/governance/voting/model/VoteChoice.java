package org.encinet.mik.module.governance.voting.model;

import java.util.Locale;
import java.util.Optional;

public enum VoteChoice {
    YES,
    NO,
    ABSTAIN;

    public static Optional<VoteChoice> parse(String value) {
        if (value == null) return Optional.empty();
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "yes", "approve", "赞成", "同意" -> Optional.of(YES);
            case "no", "reject", "反对", "不同意" -> Optional.of(NO);
            case "abstain", "弃权" -> Optional.of(ABSTAIN);
            default -> Optional.empty();
        };
    }
}
