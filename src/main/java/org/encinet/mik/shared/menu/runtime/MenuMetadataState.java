package org.encinet.mik.shared.menu.runtime;

import java.util.Objects;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiThemeColors;

/** Tracks the paint context actually sent to an entity, independently of its unchanged logical content. */
final class MenuMetadataState {
    record Context(double coordinateScale, double typographyScale, UiRotation rotation, UiThemeColors theme) {
        Context {
            if (!Double.isFinite(coordinateScale) || coordinateScale <= 0
                    || !Double.isFinite(typographyScale) || typographyScale <= 0)
                throw new IllegalArgumentException("Invalid menu paint scale");
            Objects.requireNonNull(rotation);
            Objects.requireNonNull(theme);
        }

        static Context node(double parentScale, FloatingMenuPose pose, double typography, UiThemeColors theme) {
            return new Context(parentScale * pose.scale(), typography, pose.rotation(), theme);
        }

        Context inFrame(UiRotation parent) { return new Context(coordinateScale,typographyScale,parent.compose(rotation),theme); }

        static Context decoration(FloatingMenuDecoration definition, FloatingMenuPose pose, UiRotation rotation,
                                  double parentScale, double typography, UiThemeColors theme) {
            return new Context(definition.worldAnchored() ? 1 : parentScale * pose.scale(),
                    definition.worldAnchored() ? 1 : typography, rotation, theme);
        }
    }

    private Context published;
    private boolean invalidated = true;

    void invalidate() { invalidated = true; }

    boolean needsUpdate(Context current) { return invalidated || !current.equals(published); }

    /** Commit only after the complete metadata write succeeds; a failed send remains eligible for retry. */
    void publish(Context current, Runnable send) {
        Objects.requireNonNull(current);
        // A group can send some packets before failing. Neither the previous nor the new context is then complete.
        invalidated = true;
        send.run();
        published = current;
        invalidated = false;
    }
}
