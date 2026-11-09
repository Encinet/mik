package org.encinet.mik.shared.menu.runtime;

import org.bukkit.util.Transformation;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The client's bottom/corner anchors are transformed with their content, including local roll. */
final class MenuDisplayTransforms {
    // TextDisplayRenderer: a four-pixel space has background x = [-.05, .075],
    // y = [0, .25]. The extra pixel is on the right, so width and center both matter.
    private static final float BLANK_WIDTH = .125f;
    private static final float BLANK_CENTER_X = .0125f;
    private static final float BLANK_HEIGHT = .25f;

    private MenuDisplayTransforms() { }
    static Transformation text(float scale, UiRotation rotation) { return create(0, 0, 0, scale, scale, scale, rotation); }
    static Transformation surface(float width, float height, UiRotation rotation) {
        float scaleX = width / BLANK_WIDTH;
        return create(-BLANK_CENTER_X * scaleX, -height / 2, 0, scaleX, height / BLANK_HEIGHT, 1, rotation);
    }
    static Transformation visual(float scale, boolean block, UiRotation rotation) {
        float origin = block ? -scale / 2 : 0;
        return create(origin, origin, origin, scale, scale, scale, rotation);
    }
    static Transformation volume(float width, float height, float depth, UiRotation rotation) {
        return create(-width / 2, -height / 2, -depth / 2, width, height, depth, rotation);
    }
    private static Transformation create(float x, float y, float z, float sx, float sy, float sz, UiRotation rotation) {
        if (!Float.isFinite(sx) || !Float.isFinite(sy) || !Float.isFinite(sz) || sx < 0 || sy < 0 || sz < 0)
            throw new IllegalArgumentException("Invalid display transform");
        UiVector3 translated = rotation.apply(new UiVector3(x, y, z));
        return new Transformation(new Vector3f((float) translated.right(), (float) translated.up(), (float) translated.forward()),
                new Quaternionf((float) rotation.x(), (float) rotation.y(), (float) rotation.z(), (float) rotation.w()),
                new Vector3f(sx, sy, sz), new Quaternionf());
    }
}
