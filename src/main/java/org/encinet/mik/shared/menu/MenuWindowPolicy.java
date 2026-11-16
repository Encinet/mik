package org.encinet.mik.shared.menu;

import java.util.Objects;
import java.util.function.Consumer;

/** Instance capabilities and close review, independent of its visual surfaces. */
public record MenuWindowPolicy(boolean minimizable, boolean movable,
                               Consumer<MenuCloseRequest> closeRequested) {
    public static final MenuWindowPolicy STANDARD = new MenuWindowPolicy(true,true,MenuCloseRequest::approve);
    public static final MenuWindowPolicy TASK = STANDARD.minimizable(false);
    public static final MenuWindowPolicy FIXED = new MenuWindowPolicy(false,false,MenuCloseRequest::approve);

    public MenuWindowPolicy { Objects.requireNonNull(closeRequested); }
    public MenuWindowPolicy minimizable(boolean value) { return new MenuWindowPolicy(value,movable,closeRequested); }
    public MenuWindowPolicy movable(boolean value) { return new MenuWindowPolicy(minimizable,value,closeRequested); }
    public MenuWindowPolicy onCloseRequested(Consumer<MenuCloseRequest> value) { return new MenuWindowPolicy(minimizable,movable,value); }
}
