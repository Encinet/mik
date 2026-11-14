package org.encinet.mik.feature.community.social.safety;

/** Blocking moderation capability, called only by the social workers. */
@FunctionalInterface
public interface SocialContentModerator extends AutoCloseable {
    Verdict review(String text);

    record Verdict(boolean allowed, String reason) {
        public Verdict {
            java.util.Objects.requireNonNull(reason, "reason");
            if (allowed != reason.isBlank()) {
                throw new IllegalArgumentException("Only rejected verdicts must carry a reason");
            }
        }

        public static Verdict accepted() { return new Verdict(true, ""); }
        public static Verdict rejected(String reason) { return new Verdict(false, reason); }
    }

    @Override
    default void close() {
    }
}
