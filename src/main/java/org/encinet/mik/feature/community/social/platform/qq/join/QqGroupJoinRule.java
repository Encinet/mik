package org.encinet.mik.feature.community.social.platform.qq.join;

import java.util.Objects;

/** One group's independent TOTP verifier; the secret is never included in diagnostics. */
public record QqGroupJoinRule(String groupOpenId, QqTotp totp) {
    public QqGroupJoinRule {
        Objects.requireNonNull(groupOpenId, "groupOpenId");
        Objects.requireNonNull(totp, "totp");
    }
}
