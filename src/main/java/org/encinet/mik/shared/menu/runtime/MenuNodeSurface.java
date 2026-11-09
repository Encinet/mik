package org.encinet.mik.shared.menu.runtime;

import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuEasing;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiRotation;

/** One immutable current surface shared by node rendering, input, tooltips and companion attachment. */
final class MenuNodeSurface {
    private MenuNodeSurface() { }

    record Feedback(double hover, double press, double reveal, boolean reducedMotion) {
        Feedback {
            if (!progress(hover) || !progress(press) || !progress(reveal))
                throw new IllegalArgumentException("Invalid node feedback sample");
        }
        private static boolean progress(double value) { return Double.isFinite(value) && value >= 0 && value <= 1; }
    }

    static MenuSurfaceAttachment.Surface sample(FloatingMenuPose pose, Vector target, UiRotation parent, double parentScale,
                                                 FloatingMenuSize size, FloatingMenuAnimation animation, Feedback feedback) {
        double scale = parentScale * pose.scale();
        var rotation = parent.compose(pose.rotation());
        var forward = rotation.apply(new org.encinet.mik.shared.menu.ui.UiVector3(0,0,1));
        var normal = new Vector(forward.right(),forward.up(),forward.forward());
        double offset = feedback.reducedMotion() ? 0 : FloatingMenuMotion.entryDepth(feedback.reveal(), animation) * scale
                + FloatingMenuNodeGeometry.scaledDistance(animation.hoverOffset() * FloatingMenuEasing.CUBIC_OUT.apply(feedback.hover())
                - animation.pressOffset() * feedback.press(), scale);
        var center = target.clone().add(normal.multiply(offset));
        return new MenuSurfaceAttachment.Surface(MenuSurfaceAttachment.point(center),
                rotation, size, scale);
    }
}
