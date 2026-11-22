package org.encinet.mik.shared.menu.runtime.render;

import java.util.Objects;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiThemeColors;

/** Tracks the paint context actually sent to an entity, independently of its unchanged logical content. */
public final class MenuMetadataState {
    public record Context(double coordinateScale, double typographyScale, UiRotation rotation, UiThemeColors theme) {
        public Context {
            if (!Double.isFinite(coordinateScale) || coordinateScale <= 0
                    || !Double.isFinite(typographyScale) || typographyScale <= 0)
                throw new IllegalArgumentException("Invalid menu paint scale");
            Objects.requireNonNull(rotation);
            Objects.requireNonNull(theme);
        }

        public static Context node(double parentScale, FloatingMenuPose pose, double typography, UiThemeColors theme) {
            return new Context(parentScale * pose.scale(), typography, pose.rotation(), theme);
        }

        public Context inFrame(UiRotation parent) { return new Context(coordinateScale,typographyScale,parent.compose(rotation),theme); }

        public static Context decoration(FloatingMenuDecoration definition, FloatingMenuPose pose, UiRotation rotation,
                                  double parentScale, double typography, UiThemeColors theme) {
            return new Context(definition.worldAnchored() ? 1 : parentScale * pose.scale(),
                    definition.worldAnchored() ? 1 : typography, rotation, theme);
        }
    }

    private Context published;
    private boolean invalidated = true;

    public void invalidate() { invalidated = true; }

    public boolean needsUpdate(Context current) { return invalidated || !current.equals(published); }

    /** Commit only after the complete metadata write succeeds; a failed send remains eligible for retry. */
    public void publish(Context current, Runnable send) {
        Objects.requireNonNull(current);
        // A group can send some packets before failing. Neither the previous nor the new context is then complete.
        invalidated = true;
        send.run();
        published = current;
        invalidated = false;
    }
}
