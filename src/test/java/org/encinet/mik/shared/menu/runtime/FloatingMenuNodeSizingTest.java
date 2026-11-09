package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiRole;
import org.encinet.mik.shared.menu.FloatingMenuScale;
import org.encinet.mik.shared.menu.FloatingMenuPreferences;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;

class FloatingMenuNodeSizingTest {

    @Test
    void defaultFontAdvancesIncludeThePreviouslyUnderestimatedLatinGlyphs() {
        // Advances from the 26.3 default bitmap font: r=6, f=5, l=3, `=3, ~=7.
        var text = FloatingMenuNodeSizing.measureText(Component.text("rfl`~"), false, 100);
        assertEquals(24, text.renderedWidthPixels());
    }

    @Test
    void inheritedBoldAndExplicitResetsParticipateInWidthAndWrapping() {
        var inherited = Component.text("A").decorate(TextDecoration.BOLD)
                .append(Component.text("B"))
                .append(Component.text("C").decoration(TextDecoration.BOLD, false));
        assertEquals(20, FloatingMenuNodeSizing.measureText(inherited, false, 100).renderedWidthPixels());
        assertEquals(1, FloatingMenuNodeSizing.measureText(Component.text("AAAAAAA"), false, 42).lines());
        assertEquals(2, FloatingMenuNodeSizing.measureText(
                Component.text("AAAAAAA").decorate(TextDecoration.BOLD), false, 42).lines());
    }

    @Test
    void wrappingUsesWordBoundariesAndKeepsTheWholeClientLineWidthInsideTheSurface() {
        var text = FloatingMenuNodeSizing.measureText(Component.text("AA AA AA"), false, 24);
        // The client keeps each word intact: hard-wrapping characters incorrectly predicts two lines.
        assertEquals(3, text.lines());
        assertEquals(24, text.renderedWidthPixels());
        assertEquals(24 * .025 * FloatingMenuNodeSizing.TEXT_SCALE, text.contentWidth(), 1e-6);
        assertTrue(text.size().width() > text.contentWidth());
        assertEquals(1, FloatingMenuNodeSizing.measureText(Component.text("AA "), false, 12).lines());
        assertEquals(2, FloatingMenuNodeSizing.measureText(Component.text("AA\n"), false, 12).lines());
        assertEquals(2, FloatingMenuNodeSizing.measureText(Component.text("中文"), false, 1).lines());
        assertEquals(9, FloatingMenuNodeSizing.measureText(Component.text("中文"), false, 1).renderedWidthPixels());
    }

    @Test
    void explicitAndAutomaticLineBreaksIncreaseMeasuredHeight() {
        var shortText = FloatingMenuNodeSizing.measureText(
                Component.text("short"), UiRole.ACTION, true);
        var explicitLines = FloatingMenuNodeSizing.measureText(
                Component.text("first\nsecond"), UiRole.ACTION, true);
        var wrapped = FloatingMenuNodeSizing.measureText(
                Component.text("这是一段需要根据空间面板宽度自动换行的很长文本内容"),
                UiRole.NAVIGATION, true);

        assertEquals(1, shortText.lines());
        assertEquals(2, explicitLines.lines());
        assertTrue(explicitLines.size().height() > shortText.size().height());
        assertTrue(wrapped.lines() > 1);
    }

    @Test
    void informationCanUseWiderSurfacesThanNavigation() {
        Component text = Component.text("A moderately long piece of spatial information");
        var information = FloatingMenuNodeSizing.measureText(
                text, UiRole.TEXT, false);
        var navigation = FloatingMenuNodeSizing.measureText(
                text, UiRole.NAVIGATION, true);

        assertTrue(information.lineWidthPixels() > navigation.lineWidthPixels());
        assertTrue(navigation.lines() >= information.lines());
    }

    @Test
    void wideTextFlowReducesWrappingAndExpandsMeasuredHitGeometry() {
        Component text = Component.text(
                "AAAAAAAAAAAAAAAAAAAAAAAAA\nBBBBBBBBBBBBBBBBBBBBBBBBB");
        var automatic = FloatingMenuNodeSizing.measureText(
                text, UiRole.ACTION, true);
        var wide = FloatingMenuNodeSizing.measureText(
                text, UiRole.ACTION, true, FloatingMenuTextWidth.WIDE);

        assertEquals(168, wide.lineWidthPixels());
        assertTrue(wide.lines() < automatic.lines());
        assertTrue(wide.size().width() > automatic.size().width());
    }

    @Test
    void readingScaleExpandsTextAndItsInteractiveFootprintTogether() {
        MenuScene.Builder builder = MenuScene.builder();
        builder.control("readable", Component.text("Readable text"));
        MenuScene.Entry entry = entry(builder.build(), "readable");

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
        var builder = MenuScene.builder();
        builder.control("text", Component.text("Independent text\nSecond line"));
        var entry = entry(builder.build(), "text");
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
