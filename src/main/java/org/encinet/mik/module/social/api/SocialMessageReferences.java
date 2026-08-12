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
        Optional<ExternalIdentity> repliedAuthor
) {
    private static final int MAX_MENTIONS = 32;

    public SocialMessageReferences {
        Objects.requireNonNull(mentions, "mentions");
        if (mentions.size() > MAX_MENTIONS) {
            throw new IllegalArgumentException("Too many referenced identities");
        }
        mentions = List.copyOf(mentions);
        repliedAuthor = repliedAuthor == null ? Optional.empty() : repliedAuthor;
    }

    public static SocialMessageReferences empty() {
        return new SocialMessageReferences(List.of(), Optional.empty());
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
}
