package org.encinet.mik.module.player.identity;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Independently composable platform and name-tag portions of a player identity. */
public record PlayerIdentityComponent(
        Component platformBadge,
        Component nameTag
) {

    public PlayerIdentityComponent {
        Objects.requireNonNull(platformBadge, "platformBadge");
        Objects.requireNonNull(nameTag, "nameTag");
    }

    public Component combined() {
        return Component.text()
                .append(platformBadge)
                .append(nameTag)
                .build();
    }
}
