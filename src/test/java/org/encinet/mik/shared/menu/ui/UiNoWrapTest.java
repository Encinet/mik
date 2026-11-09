package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiNoWrapTest {
    @Test void fullTextEstablishesItsNaturalWidthRegardlessOfTheNormalWrapWidth() {
        var text = Component.text("左后方 · 12345678 格 · 高出 319 格").decorate(TextDecoration.BOLD);
        for (double scale : new double[]{.7, 1, 1.8}) {
            var reference = MenuScene.screen("reference").viewport(new UiSize(2048, 512))
                    .content(Ui.column().style(UiStyle.PLAIN.withTextScale(1.25))
                            .child(Ui.text(text).textWidth(4096).build()).build()).build();
            var scene = MenuScene.screen("intrinsic").viewport(new UiSize(2048, 512))
                    .content(Ui.column().style(UiStyle.PLAIN.withTextScale(1.25))
                            .child(Ui.text(text).key("value").textWidth(16).noWrap().build()).build()).build();
            var expected = SceneGeometryTestSupport.naturalFrame(reference, scale);
            var frame = SceneGeometryTestSupport.naturalFrame(scene, scale);
            assertEquals(expected.size().width(), frame.size().width(), 1e-6);
            assertEquals(expected.size().height(), frame.size().height(), 1e-6);
            assertEquals(text, element(frame, "value").node().content().label());
            assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, scale, "value"));
            assertNull(entry(scene, "value").tooltip(), "a complete reading needs no duplicate full-text tooltip");
        }
    }

    @Test void aHorizontalRowGrowsForCompleteReadingsAndTheirActions() {
        var row = Ui.row().gap(UiLength.ui(8))
                .child(Ui.text(Component.text("Community creative world · 12345678 blocks")).key("position").noWrap().build())
                .child(Ui.button(Component.text("Teleport beside")).key("visit").noWrap().build()).build();
        var scene = MenuScene.screen("location").viewport(new UiSize(2048, 512)).content(row).build();
        var frame = SceneGeometryTestSupport.naturalFrame(scene, 1.8);
        assertDisjoint(frame);
        var position = element(frame, "position"); var visit = element(frame, "visit");
        assertEquals(position.y()+position.size().height()/2, visit.y()+visit.size().height()/2, 1e-6);
        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, 1.8, "position"));
        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, 1.8, "visit"));
        assertTrue(frame.size().width() > 240, "natural sizing must not impose a short-field width");
        var constrained = scene.toBuilder().viewport(new UiSize(240, 512)).build();
        assertThrows(UiOverflowException.class, () -> SceneGeometryTestSupport.naturalFrame(constrained, 1.8),
                "a real hard limit requests parent reallocation instead of silently wrapping or truncating");
    }

    @Test void intrinsicTableColumnsReserveTheFullReadingBeforeFlexibleColumnsShrink() {
        var position = Component.text("Back right · 12345678 blocks · 319 blocks above");
        var flexible = Ui.text(Component.text("A long descriptive track title ".repeat(8))).key("song").textWidth(4096).build();
        var table = Ui.<String>table().gaps(UiLength.ui(8), UiLength.ui(6))
                .column(new UiTable.Column<>(UiKey.named("song"), Ui.text(Component.empty()).build(), UiTrack.content(), _ -> flexible))
                .column(new UiTable.Column<>(UiKey.named("location"), Ui.text(Component.empty()).build(), UiTrack.intrinsic(),
                        _ -> Ui.text(position).key("position").noWrap().build()))
                .rows(java.util.List.of("device"), UiKey::named).build();
        var scene = MenuScene.screen("table").viewport(new UiSize(960, 2048)).content(table).build();
        var frame = SceneGeometryTestSupport.naturalFrame(scene, 1.8);
        assertDisjoint(frame);
        assertEquals(position, element(frame, "position").node().content().label());
        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(scene, 1.8, "position"));
        assertTrue(element(frame, "position").size().width() > 220);
        assertTrue(element(frame, "song").size().width() < element(frame, "position").size().width());
    }

    @Test void explicitNewlinesRemainIntactAndEllipsisControlsKeepTheirExistingBehavior() {
        var scene = MenuScene.screen("explicit").content(Ui.text(Component.text("First line\nSecond line")).key("value").noWrap().build()).build();
        assertEquals(2, SceneGeometryTestSupport.naturalTextLines(scene, 1, "value"));
        var clipped = MenuScene.screen("preview").viewport(new UiSize(100, 512))
                .content(Ui.text(Component.text("A sufficiently long preview title")).key("value").singleLine().build()).build();
        assertNotEquals(entry(clipped, "value").label(), element(SceneGeometryTestSupport.naturalFrame(clipped, 1), "value").node().content().label());
        assertEquals(1, SceneGeometryTestSupport.naturalTextLines(clipped, 1, "value"));
    }
}
