package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.object.ObjectContents;
import java.util.UUID;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.MenuPopover;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiLength.ui;
import static org.junit.jupiter.api.Assertions.*;

class MenuTypographyTest {
    @Test void singleLineTextFitsItsMeasuredWidthAndPublishesTheSameLabelForRendering() {
        Component full = Component.text("粗体中文😀 ".repeat(30) + "\nsecond line", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
        var scene = MenuScene.screen("single-line").content(Ui.button(full).singleLine().key("style")
                .textWidth(1024).sizing(UiSizing.width(ui(0), ui(200))).build()).build();
        for (double scale : new double[] {.7, 1, 1.4, 1.8}) {
            var geometry = SceneGeometry.measure(scene, scale, FloatingMenuFieldOfView.DEFAULT, 1);
            var element = geometry.frame().elements().getFirst();
            Component visible = element.node().content().label();
            var measurement = geometry.measurements().get(element.path());
            assertEquals(1, measurement.text().lines());
            assertTrue(element.size().width() <= 200 + 1e-6);
            assertEquals(visible, geometry.labels().get(element.path()));
            assertEquals(full, element.node().input().tooltip().orElseThrow().text());
            String plain = PlainTextComponentSerializer.plainText().serialize(visible);
            assertTrue(plain.endsWith("…"));
            assertFalse(plain.contains("\n"));
            assertEquals(NamedTextColor.AQUA, visible.children().getFirst().color());
            assertEquals(TextDecoration.State.TRUE, visible.children().getFirst().decoration(TextDecoration.BOLD));
        }
    }

    @Test void singleLineTextRetainsShortLabelsAndStopsAtExplicitNewlines() {
        Component shortLabel = Component.text("Short");
        assertSame(shortLabel, UiTextMetrics.fitSingleLine(shortLabel, 100));
        assertEquals("Short…", PlainTextComponentSerializer.plainText().serialize(
                UiTextMetrics.fitSingleLine(shortLabel.append(Component.newline()).append(Component.text("Hidden")), 100)));
        assertEquals("…", PlainTextComponentSerializer.plainText().serialize(UiTextMetrics.fitSingleLine(Component.text("Wide"), 9)));
    }

    @Test void flatPlayerHeadsMeasureAsOneClientGlyphAndPreserveChildTextAndInheritedBold() {
        var head = Component.object(ObjectContents.playerHead(UUID.randomUUID()));
        assertEquals(new UiTextMetrics.Extent(8, 1), UiTextMetrics.measure(head, 160));
        assertEquals(new UiTextMetrics.Extent(8 + 4 + 6, 1), UiTextMetrics.measure(head.append(Component.text(" A")), 160));
        assertEquals(new UiTextMetrics.Extent(9, 1), UiTextMetrics.measure(head.decorate(TextDecoration.BOLD), 160));
        assertEquals(new UiTextMetrics.Extent(8, 2), UiTextMetrics.measure(head.append(Component.newline()).append(head), 8));
        var scene = MenuScene.screen("head").content(Ui.text(head).key("avatar").textWidth(8).style(UiStyle.PLAIN.withTextScale(3)).build()).build();
        var geometry = SceneGeometry.measure(scene, 1.8, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(8, geometry.measurements().values().iterator().next().text().renderedWidthPixels());
    }
    @Test void typographyIsInheritedOnceAndUsedByTextMeasurement() {
        var content = Ui.column().style(UiStyle.PLAIN.withTextScale(1.25))
                .child(Ui.text(Component.text("Same label")).key("normal").build())
                .child(Ui.text(Component.text("Same label")).key("large")
                        .style(UiStyle.PLAIN.withTextScale(1.4)).build()).build();
        var geometry = SceneGeometry.measure(MenuScene.screen("type").content(content).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1);
        var normal = geometry.frame().elements().getFirst();
        var large = geometry.frame().elements().getLast();
        assertEquals(1.25, normal.node().style().textScale());
        assertEquals(1.75, large.node().style().textScale());
        assertEquals(1.4, large.size().width() / normal.size().width(), 1e-6);
        assertEquals(1.4, large.size().height() / normal.size().height(), 1e-6);
        assertEquals(large.size().width() * .02, geometry.measurements().get(large.path()).footprint().width(), 1e-6);
        assertEquals(1.4, ((UiElement) content.children().getLast().node()).style().textScale());
    }

    @Test void namedHeadingPortsAndDirectHeadingsBothHaveMeasuredHierarchy() {
        var builder = MenuScene.screen("headings").content(UiTemplates.menu(
                UiTemplates.title("title"), UiTemplates.heading("section"), UiTemplates.information("body")));
        for (String port : new String[] {"title", "section", "body"})
            builder.information(port, Component.text("Label")).region(port);
        var frame = SceneGeometryTestSupport.measure(builder.build(), 1);
        assertTrue(frame.elements().get(0).size().width() > frame.elements().get(1).size().width());
        assertTrue(frame.elements().get(1).size().width() > frame.elements().get(2).size().width());
        var direct = SceneGeometryTestSupport.measure(MenuScene.screen("direct").content(
                Ui.heading(Component.text("Label")).build()).build(), 1);
        assertEquals(frame.elements().get(1).size(), direct.elements().getFirst().size());
    }

    @Test void cumulativeTypographyIsBoundedBeforeItCanProduceInvalidGeometry() {
        var content = Ui.column().style(UiStyle.PLAIN.withTextScale(3))
                .child(Ui.text(Component.text("Too large")).style(UiStyle.PLAIN.withTextScale(2)).build()).build();
        var error = assertThrows(IllegalArgumentException.class,
                () -> SceneGeometryTestSupport.measure(MenuScene.screen("invalid").content(content).build(), 1));
        assertTrue(error.getMessage().contains("root"));
    }

    @Test void stretchingAColumnToItsMeasuredWidthDoesNotWrapAnExactlyFittingGlyph() {
        var builder = MenuScene.screen("aligned").content(UiTemplates.list("choices"));
        builder.control("storage", Component.text("  보관함과 아이템")).region("choices");
        builder.control("build", Component.text("  건설 및 변경")).region("choices");
        var geometry = SceneGeometry.measure(builder.build(), .7, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(1, geometry.measurements().values().stream().mapToInt(m -> m.text().lines()).max().orElseThrow());
        assertEquals(geometry.frame().elements().getFirst().size().width(), geometry.frame().elements().getLast().size().width());
    }

    @Test void controlSurfacesAreIncludedInThePreAllocationEntityBudget() {
        var controls = Ui.grid(20).gap(ui(0));
        var inspections = Ui.grid(20).gap(ui(0));
        var text = Ui.grid(20).gap(ui(0));
        for (int i = 0; i < 690; i++) {
            controls.child(Ui.button(Component.text("X")).key("item" + i).primary((_,_) -> {}).build());
            inspections.child(Ui.button(Component.text("X")).key("item" + i).tooltip(Component.text("Description")).build());
            text.child(Ui.text(Component.text("X")).key("item" + i).build());
        }
        var scene = MenuScene.screen("many-controls").viewport(new UiSize(2048,2048)).content(controls.build()).build();
        var error = assertThrows(UiOverflowException.class,
                () -> SceneGeometryTestSupport.naturalGeometry(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1));
        assertTrue(error.getMessage().contains("2048-entity"));
        assertDoesNotThrow(() -> SceneGeometryTestSupport.naturalGeometry(MenuScene.screen("reading").viewport(new UiSize(2048,2048)).content(text.build()).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1));
        assertDoesNotThrow(() -> SceneGeometryTestSupport.naturalGeometry(MenuScene.screen("help").viewport(new UiSize(2048,2048)).content(inspections.build()).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1));
    }

    @Test void readOnlyHelpUsesItsActualEntityBudgetForTheParentAndCompanionTogether() {
        var facts = Ui.grid(25).gap(ui(0));
        for (int i = 0; i < 690; i++) facts.child(Ui.text(Component.text("X")).key("fact"+i)
                .style(UiStyle.PLAIN.withTextScale(.5)).tooltip(Component.text("Read-only detail")).build());
        var scene = MenuScene.screen("reading").content(Ui.button(Component.text("Details")).key("source").primary((_,_) -> {}).build())
                .popover(MenuPopover.attached(UiKey.named("source"),Component.text("Details"),facts.build(),Component.text("Close"))).build();
        var geometry = assertDoesNotThrow(() -> SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1));
        assertNotNull(geometry.popover());
        assertEquals(690,geometry.popover().content().frame().elements().stream().filter(element -> element.node().key().value().startsWith("fact")).count());
        assertTrue(geometry.popover().content().definition().entries().values().stream()
                .filter(entry -> entry.id().contains("fact")).allMatch(entry -> entry.pointerBehavior() == UiPointerBehavior.INSPECT));
    }
}
