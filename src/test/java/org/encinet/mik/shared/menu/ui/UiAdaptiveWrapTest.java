package org.encinet.mik.shared.menu.ui;

import java.util.Map;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.UiSceneContent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiAdaptiveWrapTest {
    @Test void naturalFlowStaysWithinItsRowBudget() {
        var frame = measure(80, UiLayouts.adaptiveWrap(2, ui(4), ui(4), grid()),
                Map.of("a", 30.0, "b", 30.0, "c", 30.0, "d", 30.0));

        assertEquals(new UiSize(64, 44), frame.size());
        assertEquals(0, UiSceneTestSupport.element(frame, "a").y());
        assertEquals(0, UiSceneTestSupport.element(frame, "b").y());
        assertEquals(24, UiSceneTestSupport.element(frame, "c").y());
        assertEquals(24, UiSceneTestSupport.element(frame, "d").y());
    }

    @Test void compactFallbackReplacesAnOverlongNaturalFlow() {
        var frame = measure(35, UiLayouts.adaptiveWrap(2, ui(4), ui(4), grid()),
                Map.of("a", 30.0, "b", 30.0, "c", 30.0, "d", 30.0));

        assertEquals(new UiSize(35, 44), frame.size());
        assertEquals(UiSceneTestSupport.element(frame, "a").y(), UiSceneTestSupport.element(frame, "b").y());
        assertEquals(UiSceneTestSupport.element(frame, "c").y(), UiSceneTestSupport.element(frame, "d").y());
        assertTrue(UiSceneTestSupport.element(frame, "c").y() > UiSceneTestSupport.element(frame, "a").y());
    }

    @Test void templatesApplyTheirTextWidthPolicyAndPassiveTextStaysReadable() {
        var grid = MenuScene.screen("grid-width").content(UiTemplates.actions("actions", 3));
        grid.control("short", Component.text("Short action")).region("actions");
        assertEquals(128, UiSceneContent.elements(grid.build().content()).values().iterator().next().textWidthPixels());

        var flow = MenuScene.screen("flow-width").content(UiTemplates.contentActions("actions", 3, 2));
        flow.control("long", Component.text("A translated action label that exceeds the default width"))
                .region("actions");
        assertEquals(FloatingMenuTextWidth.CONTENT.resolve(128),
                UiSceneContent.elements(flow.build().content()).values().iterator().next().textWidthPixels());

        var navigation = MenuScene.screen("navigation-width").content(UiTemplates.navigation("navigation"));
        navigation.navigation("back", Component.text("A long navigation label")).region("navigation");
        assertEquals(FloatingMenuTextWidth.CONTENT.resolve(128),
                UiSceneContent.elements(navigation.build().content()).values().iterator().next().textWidthPixels());

        assertEquals(FloatingMenuTextWidth.AUTO.resolve(true), Ui.button(Component.text("Button")).build().textWidthPixels());
        assertEquals(FloatingMenuTextWidth.AUTO.resolve(false), Ui.text(Component.text("Reading")).build().textWidthPixels());
    }

    @Test void contentActionsKeepExplicitTextWidthOverrides() {
        var menu = MenuScene.screen("content-actions-width")
                .content(UiTemplates.contentActions("actions", 3, 2));
        menu.control("long", Component.text("Explicit width")).region("actions")
                .textWidth(FloatingMenuTextWidth.WIDE);

        var scene = menu.build();
        assertEquals(FloatingMenuTextWidth.WIDE.resolve(128),
                UiSceneContent.elements(scene.content()).values().iterator().next().textWidthPixels());
    }

    @Test void contentWidthPolicySurvivesPortWrappersAndSizing() {
        var flow = UiTemplates.contentActions("actions", 3, 2);
        assertEquals(FloatingMenuTextWidth.CONTENT, flow.withSummaryLines(1).itemTextWidth());
        assertEquals(FloatingMenuTextWidth.CONTENT, ((UiPort) UiTemplates.slot("slot", flow)).itemTextWidth());

        var paged = UiTemplates.pagedActions("actions", 2, 0, _ -> {});
        assertEquals(FloatingMenuTextWidth.CONTENT, paged.content().itemTextWidth());
        assertEquals(FloatingMenuTextWidth.CONTENT, paged.withSizing(
                UiSizing.width(UiLength.ZERO, UiLength.ui(320))).content().itemTextWidth());
    }

    @Test void invalidFlowBudgetsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> UiLayouts.adaptiveWrap(0, ui(4), ui(4), grid()));
    }

    private static UiLayout grid() {
        return UiLayouts.adaptiveGrid(2, ui(4), ui(4), UiLayouts.GridAlignment.CENTER);
    }

    private static UiFrame measure(double width, UiLayout layout, Map<String, Double> naturalWidths) {
        var group = Ui.layout(layout).key("group");
        naturalWidths.keySet().stream().sorted().forEach(key -> group.child(Ui.text(Component.text(key)).key(key).build()));
        return UiCompiler.compile(group.build(), UiConstraints.bounded(width, 200), (element, constraints) ->
                new UiCompiler.ElementMeasurement(new UiSize(Math.min(naturalWidths.get(element.key().value()), constraints.maxWidth()),
                        Math.min(20, constraints.maxHeight())), element.textWidthPixels()));
    }

    private static UiLength ui(double value) { return UiLength.ui(value); }
}
