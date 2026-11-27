package org.encinet.mik.feature.community.chat.model;

/** Source platform, separate from the sender's account and game presentation. */
public sealed interface ChatOrigin permits ChatOrigin.Player, ChatOrigin.Social {
    enum Player implements ChatOrigin {
        MINECRAFT, WEB
    }

    record Social(String platform) implements ChatOrigin {
        public Social {
            platform = ExternalIdentityKey.normalizePlatform(platform);
        }
    }
}
