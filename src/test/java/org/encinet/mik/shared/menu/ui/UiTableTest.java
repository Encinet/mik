package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.image.UiImage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.encinet.mik.shared.menu.ui.UiLength.ui;

class UiTableTest {
    private UiContainer table(List<String> rows) {
        var bitmap = UiImage.remote("https://example.org/cover.png", 64, 64);
        return Ui.<String>table().gaps(ui(8), ui(6))
                .column(new UiTable.Column<>(UiKey.named("art"), Ui.text(Component.text("art")).build(), UiTrack.fixed(ui(40)),
                        row -> Ui.image(bitmap, Component.text(row)).size(new UiSize(30, 30)).build()))
                .column(new UiTable.Column<>(UiKey.named("title"), Ui.text(Component.text("title")).build(), UiTrack.fraction(1),
                        row -> Ui.column().child(Ui.text(Component.text(row)).key("name").build())
                                .child(Ui.button(Component.text("play")).key("play").primary((_, _) -> { }).build()).build()))
                .rows(rows, UiKey::named).build();
    }
    private UiFrame measure(UiNode node) {
        return UiCompiler.compile(node, UiConstraints.bounded(200, 300), (_, limits) ->
                new UiCompiler.ElementMeasurement(new UiSize(Math.min(20, limits.maxWidth()), Math.min(10, limits.maxHeight())), 80));
    }

    @Test void arbitraryImageAndInteractiveSubtreesShareTracksAndStableRows() {
        var before = measure(table(List.of("alpha", "beta")));
        var after = measure(table(List.of("beta", "alpha")));
        assertEquals(200, before.size().width());
        var art = before.elements().stream().filter(element -> element.path().contains("alpha]:art")).findFirst().orElseThrow();
        var name = before.elements().stream().filter(element -> element.path().contains("alpha]:title/name")).findFirst().orElseThrow();
        var play = before.elements().stream().filter(element -> element.path().contains("alpha]:title/play")).findFirst().orElseThrow();
        assertEquals(48, name.x());
        assertEquals(art.y() + art.size().height() / 2, (name.y() + play.y() + play.size().height()) / 2);
        assertEquals(before.elements().stream().map(UiFrame.Element::path).collect(java.util.stream.Collectors.toSet()),
                after.elements().stream().map(UiFrame.Element::path).collect(java.util.stream.Collectors.toSet()));
        assertThrows(IllegalArgumentException.class, () -> table(List.of("alpha", "alpha")));
    }

    @Test void cellAlignmentCanUseTopCenterOrBottomWithoutChangingTracksOrRowHeights() {
        UiSize previous = null;
        for (var alignment : UiLayouts.GridAlignment.values()) {
            var table = Ui.<String>table().verticalAlignment(alignment)
                    .column(new UiTable.Column<>(UiKey.named("cover"), Ui.text(Component.empty()).build(), UiTrack.content(),
                            _ -> Ui.text(Component.text("cover")).key("cover").size(new UiSize(40, 40)).build()))
                    .column(new UiTable.Column<>(UiKey.named("name"), Ui.text(Component.empty()).build(), UiTrack.content(),
                            row -> Ui.text(Component.text(row)).key("name").size(new UiSize(80, 10)).build()))
                    .rows(List.of("song"), UiKey::named).build();
            var frame = measure(table);
            var cover = UiSceneTestSupport.element(frame, "cover");
            var name = UiSceneTestSupport.element(frame, "name");
            double offset = switch (alignment) { case START -> 0; case CENTER, STRETCH -> 15; case END -> 30; };
            assertEquals(cover.y() + offset, name.y(), 1e-8);
            if (previous != null) assertEquals(previous, frame.size());
            previous = frame.size();
            UiSceneTestSupport.assertDisjoint(frame);
        }
    }

    @Test void intrinsicControlsKeepTheirMeasuredWidthWhileTextColumnsShrink() {
        var table = Ui.<String>table().gaps(ui(8), ui(6))
                .column(new UiTable.Column<>(UiKey.named("text"), Ui.text(Component.empty()).build(), UiTrack.content(),
                        row -> Ui.text(Component.text(row)).key("flexible").build()))
                .column(new UiTable.Column<>(UiKey.named("choice"), Ui.text(Component.empty()).build(), UiTrack.intrinsic(),
                        _ -> Ui.button(Component.text("☐")).key("selection").build()))
                .rows(List.of("long title"), UiKey::named).build();
        var frame = UiCompiler.compile(table, UiConstraints.bounded(200, 300), (element, limits) -> {
            double width = switch (element.key().value()) {
                case "selection" -> 56;
                case "flexible" -> Math.min(240, limits.maxWidth());
                default -> 0;
            };
            if (width > limits.maxWidth()) throw new UiOverflowException("Control cannot shrink below its measured width");
            return new UiCompiler.ElementMeasurement(new UiSize(width, 10), 80);
        });
        var text = UiSceneTestSupport.element(frame, "flexible");
        var selection = UiSceneTestSupport.element(frame, "selection");
        assertEquals(136, text.size().width());
        assertEquals(56, selection.size().width());
        assertEquals(8, selection.x() - text.x() - text.size().width());
    }

    @Test void shrinkingRespectsTheActualMinimumOfEveryCellInsteadOfOverSqueezingShortColumns() {
        var table = constrainedContentTable();
        int[] measurements = {0};
        var frame = UiCompiler.compile(table, UiConstraints.bounded(200, 300), (element, limits) -> {
            measurements[0]++;
            return measureContent(element, limits);
        });
        for (String key : List.of("artist-a", "artist-b", "duration-a", "duration-b"))
            assertTrue(UiSceneTestSupport.element(frame, key).size().width() >= 40, key);
        assertTrue(UiSceneTestSupport.element(frame, "title-a").size().width() >= 10);
        assertEquals(200, frame.size().width(), 1e-5);
        UiSceneTestSupport.assertDisjoint(frame);
        assertTrue(measurements[0] < 512, "minimum discovery must stay bounded and reuse the compiler measurement cache");
    }

    @Test void tablesThatCannotFitTheirRealMinimumStillRequestAnotherLayout() {
        assertThrows(UiOverflowException.class, () -> UiCompiler.compile(constrainedContentTable(),
                UiConstraints.bounded(90, 300), this::measureContent));
    }

    private UiNode constrainedContentTable() {
        var table = Ui.<String>table().gaps(ui(6), ui(6));
        for (String key : List.of("title", "artist", "duration"))
            table.column(new UiTable.Column<>(UiKey.named(key), Ui.text(Component.empty()).build(), UiTrack.content(),
                    row -> Ui.text(Component.text(key)).key(key + "-" + row).build()));
        return table.rows(List.of("a", "b"), UiKey::named).build();
    }

    private UiCompiler.ElementMeasurement measureContent(UiElement element, UiConstraints limits) {
        String key = element.key().value();
        double natural = key.startsWith("title-") ? 300 : key.startsWith("artist-") || key.startsWith("duration-") ? 50 : 0;
        double minimum = key.startsWith("title-") ? 10 : natural > 0 ? key.endsWith("a") ? 30 : 40 : 0;
        if (limits.maxWidth() + 1e-9 < minimum) throw new UiOverflowException("Cell needs room for its smallest unit");
        return new UiCompiler.ElementMeasurement(new UiSize(Math.min(natural, limits.maxWidth()), 10), 80);
    }
}
