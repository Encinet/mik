package org.encinet.mik.shared.menu;

import java.util.Objects;
import java.util.function.Consumer;
import org.encinet.mik.shared.menu.ui.UiKey;

/** UI-owned policies for an independent surface. Business identity remains opaque to the runtime. */
public record MenuWindowSpec(UiKey key, Modality modality, Lifetime lifetime, Position position,
                             MenuWindowPolicy policy) {
    public enum Modality { NONE, OWNER, WORKSPACE }
    public enum Lifetime { OWNER, PAGE }
    public enum Position { CENTER, LEFT, RIGHT, BOTTOM_LEFT }

    public MenuWindowSpec {
        Objects.requireNonNull(key); Objects.requireNonNull(modality); Objects.requireNonNull(lifetime);
        Objects.requireNonNull(position); Objects.requireNonNull(policy);
        if (key.equals(UiKey.AUTO)) throw new IllegalArgumentException("A window needs an explicit identity key");
        if (modality != Modality.NONE && policy.minimizable())
            throw new IllegalArgumentException("A modal window cannot be minimized independently");
    }

    public static MenuWindowSpec tool(UiKey key) {
        return new MenuWindowSpec(key, Modality.NONE, Lifetime.OWNER, Position.RIGHT, MenuWindowPolicy.STANDARD);
    }

    public static MenuWindowSpec task(UiKey key) {
        return new MenuWindowSpec(key, Modality.OWNER, Lifetime.OWNER, Position.CENTER, MenuWindowPolicy.TASK);
    }

    public MenuWindowSpec modality(Modality value) {
        return new MenuWindowSpec(key, value, lifetime, position, value == Modality.NONE ? policy : policy.minimizable(false));
    }

    public MenuWindowSpec lifetime(Lifetime value) {
        return new MenuWindowSpec(key, modality, value, position, policy);
    }

    public MenuWindowSpec position(Position value) {
        return new MenuWindowSpec(key, modality, lifetime, value, policy);
    }

    public MenuWindowSpec policy(MenuWindowPolicy value) { return new MenuWindowSpec(key,modality,lifetime,position,value); }

    public MenuWindowSpec onCloseRequested(Consumer<MenuCloseRequest> value) {
        return policy(policy.onCloseRequested(value));
    }
}
