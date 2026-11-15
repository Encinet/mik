package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Atomic account name tag: prefix, interactive player name and suffix stay together. */
public record PlayerIdentityComponent(Component component, Component undecorated) {

    public PlayerIdentityComponent(Component component) {
        this(component, component);
    }

    public PlayerIdentityComponent {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(undecorated, "undecorated");
    }

    public Component chat(PlayerSource source) { return source.before(component); }

    public Component tabList(PlayerSource source) { return source.after(component); }
}
