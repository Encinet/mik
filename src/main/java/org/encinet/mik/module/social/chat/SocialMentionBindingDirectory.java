package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.identity.IdentityBinding;

import java.util.List;
import java.util.UUID;

/** Read-only port exposing bindings needed to resolve outbound social mentions. */
@FunctionalInterface
public interface SocialMentionBindingDirectory {
    List<IdentityBinding> findByPlayer(UUID playerId);
}
