package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuNodeGeometryTest {

    @Test
    void iconLabelAndDepthOffsetsShrinkWithTheWholeScene() {
        FloatingMenuNodeGeometry.Placement full = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.ITEM, 1.0, 0.02);
        FloatingMenuNodeGeometry.Placement reduced = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.ITEM, 0.4, 0.02);

        assertEquals(full.visualUp() * 0.4, reduced.visualUp(), 0.0001);
        assertEquals(full.textUp() * 0.4, reduced.textUp(), 0.0001);
        assertEquals(full.textForward() * 0.4, reduced.textForward(), 0.0001);
    }

    @Test
    void textOnlyNodesDoNotReceiveAnImaginaryIconGap() {
        FloatingMenuNodeGeometry.Placement placement = FloatingMenuNodeGeometry.placement(
                FloatingMenuElementStyle.TEXT, 0.55, 0.04);

        assertEquals(0.0, placement.visualUp());
        assertEquals(0.0, placement.textUp());
        assertEquals(0.018 * 0.55, placement.textForward(), 0.0001);
    }

    @Test
    void invalidScaleCannotLeakIntoEntityCoordinates() {
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuNodeGeometry.placement(
                        FloatingMenuElementStyle.ITEM, 0.0, 0.0));
    }

    @Test
    void bottomAnchoredClientTextIsMovedBackToItsMeasuredCenter() {
        FloatingMenuNodeSizing.TextLayout text = FloatingMenuNodeSizing.measureText(
                Component.text("first\nsecond"), FloatingMenuNodeRole.CONTROL, true);

        assertEquals(text.renderedHeight() * 0.5,
                FloatingMenuNodeGeometry.textAnchorDown(text, 1.0), 0.0001);
        assertEquals(text.renderedHeight() * 0.25,
                FloatingMenuNodeGeometry.textAnchorDown(text, 0.5), 0.0001);
    }

    @Test
    void visualFootprintIsSymmetricAndContainsTheLowerLabel() {
        FloatingMenuSize text = new FloatingMenuSize(0.9, 0.42);

        FloatingMenuSize footprint = FloatingMenuNodeGeometry.visualFootprint(text, 0.58);

        assertEquals(0.9, footprint.width(), 0.0001);
        assertTrue(footprint.height() * 0.5 >= 0.30 + text.height() * 0.5);
    }
}
