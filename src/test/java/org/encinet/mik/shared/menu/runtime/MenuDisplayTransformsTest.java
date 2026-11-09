package org.encinet.mik.shared.menu.runtime;

import org.bukkit.util.Transformation;
import org.encinet.mik.shared.menu.ui.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class MenuDisplayTransformsTest {
    private static UiVector3 apply(Transformation transform, float x, float y, float z) {
        Vector3f point = new Vector3f(x, y, z).rotate(transform.getRightRotation()).mul(transform.getScale())
                .rotate(transform.getLeftRotation()).add(transform.getTranslation());
        return new UiVector3(point.x, point.y, point.z);
    }
    private static void near(UiVector3 expected, UiVector3 actual) {
        assertVector(expected, actual, 1e-6);
    }

    /** Paper 26.3 client: TextDisplayRenderer submits (-1,-1)..(width, lines * 10 - 1). */
    private static UiVector3 blankBackgroundCorner(Transformation transform, float x, float y) {
        // One default-font space advances four pixels. Apply the actual client matrix first,
        // rather than assuming that the raw background is centered on the entity origin.
        var client = new Matrix4f().rotateY((float) Math.PI).scale(-.025f)
                .translate(1 - 4 / 2f, -9, 0);
        var point = client.transformPosition(new Vector3f(x, y, 0));
        return apply(transform, point.x, point.y, point.z);
    }

    @Test void backgroundMatchesAllFourLayoutCornersWithTheActualClientBlankQuad() {
        for (var rotation : new UiRotation[] {UiRotation.IDENTITY, UiRotation.degrees(35, 60, 47)}) {
            for (float width : new float[] {.34f, 2, 8}) {
                float height = .8f;
                var surface = MenuDisplayTransforms.surface(width, height, rotation);
                near(rotation.apply(new UiVector3(-width / 2, height / 2, 0)),
                        blankBackgroundCorner(surface, -1, -1));
                near(rotation.apply(new UiVector3(width / 2, height / 2, 0)),
                        blankBackgroundCorner(surface, 4, -1));
                near(rotation.apply(new UiVector3(-width / 2, -height / 2, 0)),
                        blankBackgroundCorner(surface, -1, 9));
                near(rotation.apply(new UiVector3(width / 2, -height / 2, 0)),
                        blankBackgroundCorner(surface, 4, 9));
            }
        }
    }

    @Test void blockOriginsRotateWithTheirContentsInsteadOfSlidingAwayFromTheirCenter() {
        var rotation = UiRotation.degrees(35, 60, 47);
        var block = MenuDisplayTransforms.visual(.6f, true, rotation);
        near(UiVector3.ZERO, apply(block, .5f, .5f, .5f));
        near(rotation.apply(new UiVector3(-.3, -.3, -.3)), apply(block, 0, 0, 0));
        var volume = MenuDisplayTransforms.volume(2, 1, 3, rotation);
        near(UiVector3.ZERO, apply(volume, .5f, .5f, .5f));
        near(rotation.apply(new UiVector3(-1, -.5, -1.5)), apply(volume, 0, 0, 0));
    }

    @Test void textAndItemsUseTheSameFullQuaternionWhileInvisibleVisualsCanStillHaveZeroScale() {
        var rotation = UiRotation.degrees(10, 90, 65);
        near(rotation.apply(new UiVector3(0, 1.5, 0)), apply(MenuDisplayTransforms.text(1.5f, rotation), 0, 1, 0));
        near(rotation.apply(new UiVector3(1.5, 0, 0)), apply(MenuDisplayTransforms.visual(1.5f, false, rotation), 1, 0, 0));
        assertVector(UiVector3.ZERO, apply(MenuDisplayTransforms.visual(0, true, rotation), 1, 1, 1));
    }
}
