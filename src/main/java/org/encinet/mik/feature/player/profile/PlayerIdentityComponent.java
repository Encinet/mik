package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Atomic account name tag: prefix, interactive player name and suffix stay together. */
public record PlayerIdentityComponent(Component component) {

    public PlayerIdentityComponent {
        Objects.requireNonNull(component, "component");
    }

    public Component chat(PlayerSource source) { return source.before(component); }

    public Component tabList(PlayerSource source) { return source.after(component); }
}
