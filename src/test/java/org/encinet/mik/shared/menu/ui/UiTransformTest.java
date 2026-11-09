package org.encinet.mik.shared.menu.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

class UiTransformTest {
    @Test void parentRotationMovesChildOffsetsAndTheWholeCompositionHasAnInverse() {
        var parent = UiTransform.IDENTITY.rotated(0, 0, 90);
        var child = UiTransform.IDENTITY.translated(4, 0, 0);
        assertVector(new UiVector3(0, 4, 0), parent.compose(child).apply(UiVector3.ZERO));
        assertVector(new UiVector3(4, 0, 0), child.compose(parent).apply(UiVector3.ZERO));
        parent = UiTransform.IDENTITY.translated(10, -3, 4).rotated(35, 15, 10).scaled(2);
        child = UiTransform.IDENTITY.translated(-2, 6, 3).rotated(-20, 17, -25).scaled(.75).around(new UiVector3(3, 2, 1));
        var point = new UiVector3(2, -7, 5);
        assertVector(parent.apply(child.apply(point)), parent.compose(child).apply(point));
        assertVector(point, parent.compose(child).inverse().apply(parent.compose(child).apply(point)));
    }

    @Test void pivotRotationAndScaleKeepTheChosenPivotFixed() {
        var pivot = new UiVector3(5, 0, 0);
        var transform = UiTransform.IDENTITY.rotated(0, 0, 90).scaled(2).around(pivot);
        assertVector(pivot, transform.apply(pivot));
        assertVector(new UiVector3(5, 2, 0), transform.apply(new UiVector3(6, 0, 0)));
    }

    @Test void eulerBoundaryConversionPreservesTheActualBasisIncludingAtBothVerticalPoles() {
        for (double yaw : new double[]{0, 30, 179, -170, 400})
            for (double pitch : new double[]{-90, -89.999, 0, 73, 90})
                for (double roll : new double[]{-180, -50, 0, 120, 360}) {
                    var rotation = UiRotation.degrees(yaw, pitch, roll);
                    var angles = rotation.angles();
                    assertRotation(rotation, UiRotation.degrees(angles.yaw(), angles.pitch(), angles.roll()));
                }
        assertEquals(0, UiRotation.IDENTITY.angles().yaw());
    }

    @Test void rotationInterpolationCrossesThePoleAndTheAngleSeamWithoutFlippingThroughIdentity() {
        var pole = UiRotation.degrees(0, 89, 0).interpolate(UiRotation.degrees(0, 91, 0), .5);
        assertVector(new UiVector3(0, -1, 0), pole.apply(new UiVector3(0, 0, 1)));
        var seam = UiRotation.degrees(179, 0, 0).interpolate(UiRotation.degrees(-179, 0, 0), .5);
        assertVector(new UiVector3(0, 0, -1), seam.apply(new UiVector3(0, 0, 1)));
    }

    @Test void invalidAndNonInvertibleDeclarationsFailBeforeGeometryIsPublished() {
        assertThrows(IllegalArgumentException.class, () -> UiTransform.IDENTITY.scaled(0));
        assertThrows(IllegalArgumentException.class, () -> UiTransform.IDENTITY.scaled(-1));
        assertThrows(IllegalArgumentException.class, () -> new UiRotation(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new UiVector3(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> UiRotation.IDENTITY.interpolate(UiRotation.IDENTITY, 2));
    }
}
