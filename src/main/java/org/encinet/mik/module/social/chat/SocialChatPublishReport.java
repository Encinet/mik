package org.encinet.mik.module.social.chat;

/** Immediate bounded-queue admission result for one chat publication. */
public record SocialChatPublishReport(
        int platformsConsidered,
        int accepted,
        int unavailable,
        int backpressured
) {
    public SocialChatPublishReport {
        if (platformsConsidered < 0 || accepted < 0 || unavailable < 0
                || backpressured < 0
                || accepted + unavailable + backpressured != platformsConsidered) {
            throw new IllegalArgumentException("Invalid social publish counters");
        }
    }

    public static SocialChatPublishReport empty() {
        return new SocialChatPublishReport(0, 0, 0, 0);
    }

    public SocialChatPublishReport plus(Admission admission) {
        return switch (admission) {
            case ACCEPTED -> new SocialChatPublishReport(
                    platformsConsidered + 1, accepted + 1,
                    unavailable, backpressured);
            case UNAVAILABLE -> new SocialChatPublishReport(
                    platformsConsidered + 1, accepted,
                    unavailable + 1, backpressured);
            case BACKPRESSURED -> new SocialChatPublishReport(
                    platformsConsidered + 1, accepted,
                    unavailable, backpressured + 1);
        };
    }

    public enum Admission {
        ACCEPTED,
        UNAVAILABLE,
        BACKPRESSURED
    }
}
