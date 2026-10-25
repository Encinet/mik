package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.social.api.SocialConversation;

import java.util.Objects;

/** A platform conversation linked to public Minecraft chat. */
public record SocialChatRoute(
        SocialConversation conversation,
        SocialChatOutboundPolicy outbound
) {
    public SocialChatRoute {
        conversation = Objects.requireNonNull(conversation, "conversation");
        outbound = Objects.requireNonNull(outbound, "outbound");
    }
}
