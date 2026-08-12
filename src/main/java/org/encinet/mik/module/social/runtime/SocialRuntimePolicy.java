package org.encinet.mik.module.social.runtime;

import java.time.Duration;
import java.util.Objects;

/** Per-platform execution, output and safety limits prepared with its configuration. */
public record SocialRuntimePolicy(
        int maximumConcurrentTasks,
        Duration deduplicationTtl,
        int maximumDeduplicationEntries,
        SocialOutputPolicy output
) {
    public SocialRuntimePolicy {
        if (maximumConcurrentTasks < 1 || maximumConcurrentTasks > 4_096) {
            throw new IllegalArgumentException("maximumConcurrentTasks is out of range");
        }
        deduplicationTtl = Objects.requireNonNull(deduplicationTtl, "deduplicationTtl");
        if (deduplicationTtl.isZero() || deduplicationTtl.isNegative()) {
            throw new IllegalArgumentException("deduplicationTtl must be positive");
        }
        if (maximumDeduplicationEntries < 1) {
            throw new IllegalArgumentException("maximumDeduplicationEntries must be positive");
        }
        output = Objects.requireNonNull(output, "output");
    }

    public SocialRuntimePolicy(
            int maximumConcurrentTasks,
            SocialOutputPolicy output
    ) {
        this(maximumConcurrentTasks, Duration.ofMinutes(10), 4_096, output);
    }
}
