package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.MenuQueryViewTestSupport;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiLinearConstraintsTest {
    @Test void containedSearchUsesTheReadingColumnsWidthRegardlessOfDeclarationOrder() {
        for (var scale : FloatingMenuTextScale.values()) for (boolean searchFirst : new boolean[]{false, true}) {
            var body = Ui.text(Component.text("Home destinations and nearby places")).textWidth(4096).key("body").build();
            var query = query();
            var column = Ui.column().gap(UiLength.ui(8));
            if (searchFirst) column.child(query, LinearSlot.fit());
            column.child(body);
            if (!searchFirst) column.child(query, LinearSlot.fit());
            var bodyFrame = measure(body, 600, scale.factor());
            var frame = measure(column.build(), 600, scale.factor());
            double expected = Math.max(bodyFrame.size().width(), query.sizing().minimum().width());
            assertEquals(expected, frame.size().width(), 1e-6, scale.toString());
            var controls = frame.elements().stream().filter(element -> element.path().contains("/query-input/")).toList();
            double left = controls.stream().mapToDouble(UiFrame.Element::x).min().orElseThrow();
            double right = controls.stream().mapToDouble(element -> element.x() + element.size().width()).max().orElseThrow();
            assertTrue(right - left <= expected + 1e-6);
            assertEquals(frame.size().width() / 2, (left + right) / 2, 1e-6);
            assertDisjoint(frame);
        }
    }

    @Test void aContainedMessageWrapsInsideTheControlWithoutWideningItsParent() {
        var field = Ui.textField(Component.text("Value")).key("field").build();
        var hint = Ui.text(Component.text("A long explanation which must wrap within the existing field and must never make its parent wider."))
                .textWidth(4096).key("hint").build();
        for (var scale : FloatingMenuTextScale.values()) {
            var before = measure(field, 600, scale.factor());
            var after = measure(Ui.column().gap(UiLength.ui(4)).child(field).child(hint, LinearSlot.fit()).build(), 600, scale.factor());
            assertEquals(before.size().width(), after.size().width(), 1e-6);
            assertTrue(after.size().height() > before.size().height());
            assertTrue(element(after, "hint").size().width() <= before.size().width() + 1e-6);
            assertDisjoint(after);
        }
    }

    @Test void aLoneContainedControlRetainsItsNaturalWidthAndCanShrinkInANarrowHost() {
        var query = query();
        var column = Ui.column().child(query, LinearSlot.fit()).build();
        var natural = measure(query, 600, 1);
        assertEquals(natural.size(), measure(column, 600, 1).size());
        var narrow = measure(column, 180, 1.8);
        assertTrue(narrow.size().width() <= 180);
        assertDisjoint(narrow);
    }

    @Test void aGrowingBodyStillDeterminesTheColumnsCrossAxis() {
        var body = Ui.text(Component.text("Content")).key("body").build();
        var query = query();
        var content = Ui.column().sizing(UiSizing.atLeast(new UiSize(0, 400)))
                .child(body, LinearSlot.grow(1)).child(query, LinearSlot.fit()).build();
        var frame = measure(content, 600, 1);
        assertEquals(query.sizing().minimum().width(), frame.size().width(), 1e-6);
        assertTrue(frame.size().height() >= 400);
        assertDisjoint(frame);
    }

    @Test void availableCrossAxisSizingAlsoAlignsRowsAndNeverDiscardsHardMinima() {
        var body = Ui.text(Component.text("Body")).key("body").size(new UiSize(80, 80)).build();
        var frame = measure(Ui.row().gap(UiLength.ui(8)).child(body).child(query(), LinearSlot.fit()).build(), 600, 1);
        var preview = element(frame, "preview");
        assertEquals(80, frame.size().height(), 1e-6);
        assertEquals(40, preview.y() + preview.size().height() / 2, 1e-6);
        var impossible = Ui.column().child(body).child(Ui.text(Component.text("Required"))
                .sizing(UiSizing.atLeast(new UiSize(400, 0))).build(), LinearSlot.fit()).build();
        assertThrows(UiOverflowException.class, () -> measure(impossible, 300, 1));
    }

    @Test void crossAxisPlacementIsIndependentOfStretchAndHonorsChildMaxima() {
        for (boolean horizontal : new boolean[]{false, true}) for (var alignment : LinearSlot.Alignment.values()) {
            var anchor = Ui.text(Component.empty()).size(horizontal ? new UiSize(20, 80) : new UiSize(80, 20)).build();
            var capped = Ui.text(Component.empty()).key("capped").size(new UiSize(20, 20)).build();
            var content = (horizontal ? Ui.row() : Ui.column()).child(anchor)
                    .child(capped, new LinearSlot(0, alignment, LinearSlot.CrossSizing.STRETCH)).build();
            var frame = measure(content, 600, 1);
            var placed = element(frame, "capped");
            double expected = switch (alignment) { case START -> 0; case CENTER -> 30; case END -> 60; };
            assertEquals(expected, horizontal ? placed.y() : placed.x(), 1e-6);
            assertEquals(new UiSize(20, 20), placed.size());
            assertDisjoint(frame);
        }
    }

    private static UiNode query() {
        return MenuQueryViewTestSupport.content("A long search query with many characters", MenuQueryViewTestSupport.Phase.IDLE,
                Component.text("Home name"), Component.text("Type in chat"), Component.text("Clear"), Component.text("Cancel"));
    }

    private static UiFrame measure(UiNode content, double width, double scale) {
        return SceneGeometryTestSupport.naturalFrame(MenuScene.screen("constraints").viewport(new UiSize(width, 600)).content(content).build(), scale);
    }
}
