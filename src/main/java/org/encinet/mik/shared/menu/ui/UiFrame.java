package org.encinet.mik.shared.menu.ui;

import java.util.List;
import java.util.function.IntConsumer;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;

/** One authoritative local geometry snapshot, shared by rendering and input projection. */
public record UiFrame(UiSize size, List<Element> elements, List<Surface> surfaces, List<Panel> panels,
                      List<Group> groups, UiLayout.Position focus, ReadingAnchor readingAnchor,
                      List<Pagination> pagination, Region readingRegion) {
    public UiFrame { elements = List.copyOf(elements); surfaces = List.copyOf(surfaces); panels = List.copyOf(panels); groups = List.copyOf(groups); pagination = List.copyOf(pagination); }
    public record Pagination(String path, double x, double y, UiSize size, FloatingMenuPage page, IntConsumer selectPage,
                             java.util.Optional<UiReadingPosition> readingPosition, java.util.Optional<UiWindowedList.Observation> observation) {
        public Pagination(String path, double x, double y, UiSize size, FloatingMenuPage page, IntConsumer selectPage) {
            this(path, x, y, size, page, selectPage, java.util.Optional.empty(),java.util.Optional.empty());
        }
        public FloatingMenuAction action(FloatingMenuInteraction interaction) {
            if (!interaction.scroll()) return null;
            return (_, _, _) -> {
                int destination = interaction == FloatingMenuInteraction.SCROLL_UP ? page.previous().index() : page.next().index();
                if (destination != page.index()) selectPage.accept(destination);
            };
        }
    }
    public record Element(String path, UiElement node, double x, double y, UiSize size,
                          java.util.Optional<UiBaseline> baseline) {
        public Element { java.util.Objects.requireNonNull(baseline); }
        public Element(String path, UiElement node, double x, double y, UiSize size) {
            this(path, node, x, y, size, java.util.Optional.empty());
        }
    }
    public sealed interface Spatial permits Panel, Group { String path(); }
    public record Panel(String path, double x, double y, UiSize size, UiLength elevation, UiSurfaceFacing facing) implements Spatial { }
    public record Surface(String path, UiStyle style, double x, double y, UiSize size) { }
    public record Group(String path, double x, double y, UiSize size, UiTransform transform, UiPivot pivot) implements Spatial { }
    public record ReadingAnchor(String path, boolean declared) { }
    public record Region(String path, double x, double y, UiSize size) { }
}
