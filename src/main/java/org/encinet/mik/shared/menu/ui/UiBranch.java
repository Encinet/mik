package org.encinet.mik.shared.menu.ui;

import java.util.List;

/** A logical owner with a bounded local layout, independent of its world presentation. */
public sealed interface UiBranch extends UiNode permits UiContainer, UiSurface, UiSpatialGroup, UiPager {
    UiLayout layout();
    List<UiChild> children();
}
