package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.identity.IdentityBinding;

import java.util.List;
import java.util.UUID;

/** Read-only port exposing bindings needed to resolve outbound social mentions. */
@FunctionalInterface
public interface SocialMentionBindingDirectory {
    List<IdentityBinding> findByPlayer(UUID playerId);
}
