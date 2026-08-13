package org.encinet.mik.module.social.api;

import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Authenticated identities referenced by mentions or a replied-to message. */
public record SocialMessageReferences(
        List<ExternalIdentity> mentions,
        Optional<ExternalIdentity> repliedAuthor,
        List<MentionSpan> mentionSpans
) {
    private static final int MAX_MENTIONS = 32;

    public SocialMessageReferences {
        Objects.requireNonNull(mentions, "mentions");
        if (mentions.size() > MAX_MENTIONS) {
            throw new IllegalArgumentException("Too many referenced identities");
        }
        mentions = List.copyOf(mentions);
        repliedAuthor = repliedAuthor == null ? Optional.empty() : repliedAuthor;
        mentionSpans = List.copyOf(Objects.requireNonNullElse(
                mentionSpans, List.of()));
        if (mentionSpans.size() > MAX_MENTIONS) {
            throw new IllegalArgumentException("Too many mention spans");
        }
        int previousEnd = -1;
        for (MentionSpan span : mentionSpans) {
            Objects.requireNonNull(span, "mentionSpan");
            if (span.start() < previousEnd) {
                throw new IllegalArgumentException(
                        "Mention spans must be ordered and non-overlapping");
            }
            previousEnd = span.end();
        }
    }

    public SocialMessageReferences(
            List<ExternalIdentity> mentions,
            Optional<ExternalIdentity> repliedAuthor
    ) {
        this(mentions, repliedAuthor, List.of());
    }

    public static SocialMessageReferences empty() {
        return new SocialMessageReferences(
                List.of(), Optional.empty(), List.of());
    }

    /** Reply target first, followed by mentions, with duplicate principals removed. */
    public List<ExternalIdentity> targetIdentities() {
        LinkedHashMap<ExternalIdentityKey, ExternalIdentity> unique = new LinkedHashMap<>();
        repliedAuthor.ifPresent(identity -> unique.put(identity.key(), identity));
        for (ExternalIdentity identity : mentions) {
            ExternalIdentity checked = Objects.requireNonNull(identity, "mention");
            unique.putIfAbsent(checked.key(), checked);
        }
        return List.copyOf(new ArrayList<>(unique.values()));
    }

    /** Trusted source-text range associated with one platform-authenticated principal. */
    public record MentionSpan(int start, int end, ExternalIdentity identity) {
        public MentionSpan {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("Mention span range is invalid");
            }
            identity = Objects.requireNonNull(identity, "identity");
        }
    }
}
