package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Client picking assistance; actions are always resolved against the actual published visual surface. */
final class MenuInteractionProxy {
    private static final double REACH = 2.5;
    private MenuInteractionProxy() { }

    record Bounds(UiVector3 base, double width, double height) { }

    static Bounds project(UiVector3 eye, UiVector3 center, FloatingMenuSize size) {
        var delta = center.subtract(eye);
        double distance = Math.hypot(Math.hypot(delta.right(),delta.up()),delta.forward());
        double factor = distance > REACH ? REACH / distance : 1;
        var at = eye.add(delta.multiply(factor));
        return new Bounds(at.subtract(new UiVector3(0,size.height() * factor / 2,0)),size.width() * factor,size.height() * factor);
    }
}
