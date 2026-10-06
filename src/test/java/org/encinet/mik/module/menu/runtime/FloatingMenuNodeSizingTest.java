package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuScale;
import org.encinet.mik.module.menu.FloatingMenuPreferences;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;

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

    @Test
    void readingScaleExpandsTextAndItsInteractiveFootprintTogether() {
        FloatingMenuDefinition.Builder builder = FloatingMenuDefinition.builder();
        builder.control("readable", Component.text("Readable text"));
        FloatingMenuDefinition.Entry entry = builder.build().entries().get("readable");

        var small = FloatingMenuNodeSizing.measure(entry,
                new FloatingMenuPreferences(FloatingMenuScale.NORMAL, FloatingMenuTextScale.SMALL).typographyFactor());
        var normal = FloatingMenuNodeSizing.measure(entry,
                FloatingMenuPreferences.DEFAULT.typographyFactor());
        var large = FloatingMenuNodeSizing.measure(entry,
                new FloatingMenuPreferences(FloatingMenuScale.NORMAL, FloatingMenuTextScale.MAXIMUM).typographyFactor());

        assertTrue(small.footprint().width() < normal.footprint().width());
        assertTrue(large.footprint().width() > normal.footprint().width());
        assertTrue(small.footprint().height() < normal.footprint().height());
        assertTrue(large.footprint().height() > normal.footprint().height());
    }

    @Test
    void textAndItsClickAreaShareTheAdditionalTextMultiplierAcrossEveryMenuSize() {
        var builder = FloatingMenuDefinition.builder();
        builder.control("text", Component.text("Independent text\nSecond line"));
        var entry = builder.build().entries().get("text");
        var normal = FloatingMenuNodeSizing.measure(entry);
        for (FloatingMenuScale layout : FloatingMenuScale.values()) {
            for (FloatingMenuTextScale text : FloatingMenuTextScale.values()) {
                var preferences = new FloatingMenuPreferences(layout, text);
                var measured = FloatingMenuNodeSizing.measure(entry, preferences.typographyFactor());
                assertEquals(normal.footprint().width() * text.factor(),
                        measured.footprint().width(), 0.0001);
                assertEquals(normal.footprint().height() * text.factor(),
                        measured.footprint().height(), 0.0001);
                assertEquals(normal.text().renderedHeight() * text.factor(),
                        measured.text().renderedHeight() * preferences.typographyFactor(), 0.0001);
            }
        }
    }
}
