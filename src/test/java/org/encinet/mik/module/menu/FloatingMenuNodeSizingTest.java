package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuNodeSizingTest {

    @Test
    void explicitAndAutomaticLineBreaksIncreaseMeasuredHeight() {
        var shortText = FloatingMenuNodeSizing.measureText(
                Component.text("short"), FloatingMenuNodeRole.CONTROL, true);
        var explicitLines = FloatingMenuNodeSizing.measureText(
                Component.text("first\nsecond"), FloatingMenuNodeRole.CONTROL, true);
        var wrapped = FloatingMenuNodeSizing.measureText(
                Component.text("这是一段需要根据空间面板宽度自动换行的很长文本内容"),
                FloatingMenuNodeRole.NAVIGATION, true);

        assertEquals(1, shortText.lines());
        assertEquals(2, explicitLines.lines());
        assertTrue(explicitLines.size().height() > shortText.size().height());
        assertTrue(wrapped.lines() > 1);
    }

    @Test
    void informationCanUseWiderSurfacesThanNavigation() {
        Component text = Component.text("A moderately long piece of spatial information");
        var information = FloatingMenuNodeSizing.measureText(
                text, FloatingMenuNodeRole.INFORMATION, false);
        var navigation = FloatingMenuNodeSizing.measureText(
                text, FloatingMenuNodeRole.NAVIGATION, true);

        assertTrue(information.lineWidthPixels() > navigation.lineWidthPixels());
        assertTrue(navigation.lines() >= information.lines());
    }

    @Test
    void wideTextFlowReducesWrappingAndExpandsMeasuredHitGeometry() {
        Component text = Component.text(
                "AAAAAAAAAAAAAAAAAAAAAAAAA\nBBBBBBBBBBBBBBBBBBBBBBBBB");
        var automatic = FloatingMenuNodeSizing.measureText(
                text, FloatingMenuNodeRole.CONTROL, true);
        var wide = FloatingMenuNodeSizing.measureText(
                text, FloatingMenuNodeRole.CONTROL, true, FloatingMenuTextWidth.WIDE);

        assertEquals(168, wide.lineWidthPixels());
        assertTrue(wide.lines() < automatic.lines());
        assertTrue(wide.size().width() > automatic.size().width());
    }
}
