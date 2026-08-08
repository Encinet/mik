package org.encinet.mik.module.player.identity;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * A fully composed player identity.
 *
 * <p>The platform badge and the LuckPerms prefix/name/suffix are intentionally
 * kept atomic so surrounding UI markers cannot be inserted between them.</p>
 */
public record PlayerIdentityComponent(Component component) {

    public PlayerIdentityComponent {
        Objects.requireNonNull(component, "component");
    }
}
