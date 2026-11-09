package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuElementStyle;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuElementState;
import org.encinet.mik.shared.menu.ui.UiRole;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.encinet.mik.shared.menu.FloatingMenuScale;
import org.encinet.mik.shared.menu.FloatingMenuPreferences;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.FloatingMenuSize;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuNodeGeometryTest {

    @Test
    void multilineItemLabelsCannotIntersectTheHoveredIconAtLargeTextSizes() {
        var text = FloatingMenuNodeSizing.measureText(Component.text("First\nSecond\nThird\nFourth"), UiRole.ACTION, true);
        for (double typography : new double[]{.7, 1, 1.4, 1.8}) {
            var placement = FloatingMenuNodeGeometry.placement(FloatingMenuElementStyle.ITEM,
                    text.size().height() * typography, 1, 0);
            double labelTop = placement.textUp() + text.renderedHeight() * typography / 2;
            double iconBottom = placement.visualUp() - .50 * 1.22 / 2;
            assertTrue(iconBottom - labelTop >= .08,
                    "The actual hovered item overlaps its label at text scale " + typography);
        }
    }

    @Test
    void iconLabelAndDepthOffsetsShrinkWithTheWholeScene() {
        FloatingMenuNodeGeometry.Placement full = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.ITEM, 0.42, 1.0, 0.02);
        FloatingMenuNodeGeometry.Placement reduced = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.ITEM, 0.42, 0.4, 0.02);

        assertEquals(full.visualUp() * 0.4, reduced.visualUp(), 0.0001);
        assertEquals(full.textUp() * 0.4, reduced.textUp(), 0.0001);
        assertEquals(full.textForward() * 0.4, reduced.textForward(), 0.0001);
        assertTrue(full.visualUp() > 0);
        assertTrue(full.textUp() < 0);
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
                Component.text("first\nsecond"), UiRole.ACTION, true);

        assertEquals(text.renderedHeight() * 0.5,
                FloatingMenuNodeGeometry.textAnchorDown(text.renderedHeight(), 1.0), 0.0001);
        assertEquals(text.renderedHeight() * 0.25,
                FloatingMenuNodeGeometry.textAnchorDown(text.renderedHeight(), 0.5), 0.0001);
    }

    @Test
    void visualFootprintIsSymmetricAndContainsTheLowerLabel() {
        FloatingMenuSize text = new FloatingMenuSize(0.9, 0.42);

        FloatingMenuSize footprint = FloatingMenuNodeGeometry.footprint(FloatingMenuElementStyle.ITEM, text, 0);

        assertTrue(footprint.width() >= text.width());
        assertTrue(footprint.height() * 0.5 >= 0.30 + text.height() * 0.5);
    }

    @Test
    void blockLabelsStayBelowEveryVisualStateOnTheReadingPlaneAtAllReadingScales() {
        for (String label : new String[]{"Stone", "First\nSecond\nThird\nFourth"}) {
            var text = FloatingMenuNodeSizing.measureText(
                    Component.text(label), UiRole.ACTION, true);
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
                                assertEquals(0.018 * spatialScale, placement.textForward(), 0.0001);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void captionsShareTheTextPlaneEvenWhenTheirPanelIsTiltedRolledAndScaled() {
        for (var rotation : new UiRotation[]{UiRotation.IDENTITY, UiRotation.degrees(-28, 22, 0), UiRotation.degrees(35, -60, 47)}) {
            var normal = rotation.apply(new UiVector3(0, 0, 1));
            for (double scale : new double[]{.35, 1, 1.4}) for (double height : new double[]{.42, .9, 1.8}) {
                var plain = FloatingMenuNodeGeometry.placement(FloatingMenuElementStyle.TEXT, height, scale, 0);
                for (var style : new FloatingMenuElementStyle[]{FloatingMenuElementStyle.ITEM, FloatingMenuElementStyle.BLOCK}) {
                    for (double idle : new double[]{-.15, 0, .15}) {
                        var caption = FloatingMenuNodeGeometry.placement(style, height, scale, idle);
                        // The client renders from a bottom anchor; both its top and bottom must
                        // remain on the reading plane after applying the complete panel rotation.
                        double anchorDown = FloatingMenuNodeGeometry.textAnchorDown(height, scale);
                        for (double y : new double[]{caption.textUp() - anchorDown, caption.textUp() + anchorDown}) {
                            var point = rotation.apply(new UiVector3(0, y, caption.textForward()));
                            double depth = point.right() * normal.right() + point.up() * normal.up() + point.forward() * normal.forward();
                            assertEquals(plain.textForward(), depth, 1e-6, style + " caption left its panel");
                        }
                    }
                }
            }
        }
    }

    @Test
    void visualFootprintsContainMultilineLabelsAndCustomIdleMotion() {
        for (double height : new double[]{0.42, 0.9, 1.8}) {
            var text = new FloatingMenuSize(1.2, height);
            for (double amplitude : new double[]{0, FloatingMenuAnimation.DEFAULT.idleAmplitude(), 0.15}) {
                for (var style : new FloatingMenuElementStyle[]{FloatingMenuElementStyle.ITEM, FloatingMenuElementStyle.BLOCK}) {
                    var footprint = FloatingMenuNodeGeometry.footprint(style, text, amplitude);
                    for (double idle : new double[]{-amplitude, amplitude}) {
                        var placement = FloatingMenuNodeGeometry.placement(style, height, 1, idle);
                        assertTrue(footprint.height() * 0.5 >= Math.abs(placement.textUp()) + height * 0.5 - 0.0001);
                        assertTrue(footprint.height() * 0.5 >= Math.abs(placement.visualUp())
                                + FloatingMenuNodeGeometry.visualScale(style, FloatingMenuElementState.HOVERED) * 0.5 - 0.0001);
                        assertTrue(FloatingMenuNodeGeometry.surfaceDepth(style) * 0.5 >= placement.textForward());
                        assertTrue(footprint.width() >= text.width());
                    }
                }
            }
        }
    }

    @Test
    void visualNodesReserveTheirIconAndCaptionDepth() {
        assertTrue(FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.ITEM) > .50 * 1.22);
        assertEquals(0.0, FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.TEXT));
        assertTrue(FloatingMenuNodeGeometry.surfaceDepth(FloatingMenuElementStyle.BLOCK) > 0.46);
    }
}
