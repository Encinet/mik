package org.encinet.mik.shared.menu.ui;

import java.util.function.IntConsumer;
import org.encinet.mik.shared.menu.FloatingMenuPage;

/** Builds controls from a measured page without changing feature state; layout may call it repeatedly. */
@FunctionalInterface
public interface UiPageControls {
    UiNode build(FloatingMenuPage page, IntConsumer selectPage);
}
