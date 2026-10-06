package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuAnimation;
import org.encinet.mik.module.menu.FloatingMenuElementState;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuSize;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuNodeGeometryTest {

    @Test
    void iconLabelAndDepthOffsetsShrinkWithTheWholeScene() {
        FloatingMenuNodeGeometry.Placement full = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.ITEM, 0.42, 1.0, 0.02);
        FloatingMenuNodeGeometry.Placement reduced = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.ITEM, 0.42, 0.4, 0.02);

        assertEquals(full.visualUp() * 0.4, reduced.visualUp(), 0.0001);
        assertEquals(full.textUp() * 0.4, reduced.textUp(), 0.0001);
        assertEquals(full.textForward() * 0.4, reduced.textForward(), 0.0001);
        assertEquals(0.08, full.visualUp(), 0.0001);
        assertEquals(-0.30, full.textUp(), 0.0001);
        assertEquals(0.018, full.textForward(), 0.0001);
    }

    @Test
    void textOnlyNodesDoNotReceiveAnImaginaryIconGap() {
        FloatingMenuNodeGeometry.Placement placement = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.TEXT, 0.42, 0.55, 0.04);

        assertEquals(0.0, placement.visualUp());
        assertEquals(0.0, placement.textUp());
        assertEquals(0.018 * 0.55, placement.textForward(), 0.0001);
    }

    @Test
    void invalidScaleCannotLeakIntoEntityCoordinates() {
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuNodeGeometry.placement(
                        FloatingMenuElementStyle.ITEM, 0.42, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuNodeGeometry.placement(
                        FloatingMenuElementStyle.BLOCK, Double.NaN, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuNodeGeometry.placement(
                        FloatingMenuElementStyle.BLOCK, -1.0, 1.0, 0.0));
    }

    @Test
    void bottomAnchoredClientTextIsMovedBackToItsMeasuredCenter() {
        FloatingMenuNodeSizing.TextLayout text = FloatingMenuNodeSizing.measureText(
                Component.text("first\nsecond"), FloatingMenuNodeRole.CONTROL, true);

        assertEquals(text.renderedHeight() * 0.5,
                FloatingMenuNodeGeometry.textAnchorDown(text.renderedHeight(), 1.0), 0.0001);
        assertEquals(text.renderedHeight() * 0.25,
                FloatingMenuNodeGeometry.textAnchorDown(text.renderedHeight(), 0.5), 0.0001);
    }

    @Test
    void visualFootprintIsSymmetricAndContainsTheLowerLabel() {
        FloatingMenuSize text = new FloatingMenuSize(0.9, 0.42);

        FloatingMenuSize footprint = FloatingMenuNodeGeometry.visualFootprint(text, 0.58);

        assertEquals(0.9, footprint.width(), 0.0001);
        assertTrue(footprint.height() * 0.5 >= 0.30 + text.height() * 0.5);
    }

    @Test
    void blockLabelsStayBelowAndInFrontOfEveryVisualStateAtAllReadingScales() {
        for (String label : new String[]{"Stone", "First\nSecond\nThird\nFourth"}) {
            var text = FloatingMenuNodeSizing.measureText(
                    Component.text(label), FloatingMenuNodeRole.BLOCK, true);
            for (FloatingMenuScale layoutScale : FloatingMenuScale.values()) {
                for (FloatingMenuTextScale textScale : FloatingMenuTextScale.values()) {
                    double typographyScale = new FloatingMenuPreferences(layoutScale, textScale).typographyFactor();
                    double height = text.size().height() * typographyScale;
                    double renderedHeight = text.renderedHeight() * typographyScale;
                    for (double spatialScale : new double[]{0.35, 1.0, 1.4}) {
                        for (double idle : new double[]{-0.018, 0.0, 0.018}) {
                            var placement = FloatingMenuNodeGeometry.placement(
                                    FloatingMenuElementStyle.BLOCK, height, spatialScale, idle);
                            double labelTop = placement.textUp()
                                    + FloatingMenuNodeGeometry.textAnchorDown(renderedHeight, spatialScale);
                            for (FloatingMenuElementState state : FloatingMenuElementState.values()) {
                                double stateScale = switch (state) {
                                    case NORMAL -> 1.0;
                                    case SELECTED -> 1.10;
                                    case HOVERED -> 1.22;
                                    case PRESSED -> 0.88;
                                    case DISABLED -> 0.92;
                                };
                                double halfCube = 0.32 * stateScale * spatialScale * 0.5;
                                assertTrue(placement.visualUp() - halfCube - labelTop
                                        >= 0.08 * spatialScale);
                                assertTrue(placement.textForward() > halfCube);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void blockFootprintContainsMultilineLabelsAndCustomIdleMotion() {
        for (double height : new double[]{0.42, 0.9, 1.8}) {
            var text = new FloatingMenuSize(1.2, height);
            for (double amplitude : new double[]{0, FloatingMenuAnimation.DEFAULT.idleAmplitude(), 0.15}) {
                var footprint = FloatingMenuNodeGeometry.blockFootprint(text, amplitude);
                for (double idle : new double[]{-amplitude, amplitude}) {
                    var placement = FloatingMenuNodeGeometry.placement(
                            FloatingMenuElementStyle.BLOCK, height, 1, idle);
                    assertTrue(footprint.height() * 0.5
                            >= Math.abs(placement.textUp()) + height * 0.5 - 0.0001);
                    assertTrue(footprint.height() * 0.5
                            >= Math.abs(placement.visualUp()) + 0.46 * 0.5 - 0.0001);
                    assertTrue(FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.BLOCK) * 0.5
                            >= placement.textForward());
                    assertTrue(footprint.width() >= text.width());
                }
            }
        }
    }

    @Test
    void onlyBlockNodesReserveExtraSceneDepth() {
        assertEquals(0.0, FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.ITEM));
        assertEquals(0.0, FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.TEXT));
        assertTrue(FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.BLOCK) > 0.46);
    }
}
