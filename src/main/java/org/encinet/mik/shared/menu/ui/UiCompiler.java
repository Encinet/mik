package org.encinet.mik.shared.menu.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.ToDoubleFunction;

/** A bounded, pure tree compiler. All state and content reads precede this pass. */
public final class UiCompiler {
    public static final int MAX_MEASUREMENTS = 16384;
    private static final int MAX_PAGE_LAYOUT_PASSES = 8;

    @FunctionalInterface
    public interface ElementMeasurer {
        ElementMeasurement measure(UiElement element, UiConstraints constraints);
        /** Intrinsic, non-shrinkable content after inherited typography has been resolved. */
        default UiSize minimum(UiElement element) { return UiSize.ZERO; }
        /** Size and fitted label stay unchanged when only maxHeight decreases but still fits the measured size. */
        default boolean heightIndependent(UiElement element) { return false; }
        /** Optional fast paragraph pagination. Ends count source code points, including consumed page separators. */
        default Optional<List<Integer>> documentPages(UiElement element, UiConstraints constraints) { return Optional.empty(); }
    }

    public record ElementMeasurement(UiSize size, int textWidthPixels, Optional<Component> label, Optional<UiBaseline> baseline) {
        public ElementMeasurement {
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(baseline, "baseline");
            if (baseline.isPresent() && baseline.orElseThrow().last() > size.height() + 1e-6)
                throw new IllegalArgumentException("Text baseline exceeds the measured component height");
            if (textWidthPixels < 1) throw new IllegalArgumentException("Invalid measured text width");
        }
        public ElementMeasurement(UiSize size, int textWidthPixels, Optional<Component> label) {
            this(size, textWidthPixels, label, Optional.empty());
        }
        public ElementMeasurement(UiSize size, int textWidthPixels) { this(size, textWidthPixels, Optional.empty()); }
        public ElementMeasurement withBaseline(UiBaseline value) { return new ElementMeasurement(size, textWidthPixels, label, Optional.of(value)); }
    }

    private final ElementMeasurer measurer;
    private final String readingPath;
    private final Map<MeasureKey, Measured> cache = new HashMap<>();
    private record Page(FloatingMenuPage page, IntConsumer selectPage, Optional<UiReadingPosition> readingPosition,
                        Optional<UiWindowedList.Observation> observation) {
        Page(FloatingMenuPage page, IntConsumer selectPage, Optional<UiReadingPosition> readingPosition) {
            this(page,selectPage,readingPosition,Optional.empty());
        }
        Page(FloatingMenuPage page, IntConsumer selectPage) { this(page, selectPage, Optional.empty()); }
    }
    private record PageLayout(UiPagePacking.Packed body, FloatingMenuPage page, UiNode pager) { }
    private record WindowPage(FloatingMenuPage page, UiNode pager) { }
    private record TableColumns(double[] widths, double windowWidth) { }
    private final Map<Measured, Page> pagination = new java.util.IdentityHashMap<>();
    private int measurements;
    private enum TextPass { COMPLETE, ADAPTIVE, SUMMARY }
    private TextPass textPass = TextPass.COMPLETE;
    private boolean measuredAdaptiveText;

    private UiCompiler(ElementMeasurer measurer, String readingPath) {
        this.measurer = Objects.requireNonNull(measurer, "measurer");
        this.readingPath = Objects.requireNonNull(readingPath);
    }

    public static UiFrame compile(UiNode root, UiConstraints constraints, ElementMeasurer measurer) {
        UiTree.check(root);
        UiCompiler compiler = new UiCompiler(measurer, UiFocusLayout.readingPath(root));
        Measured measured = compiler.measureRoot(Objects.requireNonNull(root, "root"),constraints);
        List<UiFrame.Element> elements = new ArrayList<>();
        List<UiFrame.Surface> surfaces = new ArrayList<>();
        List<UiFrame.Panel> panels = new ArrayList<>();
        List<UiFrame.Group> groups = new ArrayList<>();
        List<UiFrame.Pagination> pages = new ArrayList<>();
        compiler.flatten(measured, "root", 0, 0, 1, elements, surfaces, panels, groups, pages);
        return new UiFrame(measured.size, elements, surfaces, panels, groups, measured.focus, measured.readingAnchor, pages,
                compiler.readingRegion(measured, "root", 0, 0).orElseGet(() -> new UiFrame.Region("root", 0, 0, measured.size)));
    }

    private Measured measureRoot(UiNode root, UiConstraints constraints) {
        try { return measure(root,"root",constraints,0,1); }
        catch (UiOverflowException overflow) {
            if (!measuredAdaptiveText) throw overflow;
            // Reuse complete regions. Only overflowing subtrees may give up their full text;
            // an auxiliary card must not compact a sibling that already fits.
            textPass = TextPass.ADAPTIVE;
            return measure(root,"root",constraints,0,1);
        }
    }

    private Measured measureSummary(UiNode node, String path, UiConstraints parent, int depth, double textScale) {
        TextPass previous = textPass;
        textPass = TextPass.SUMMARY;
        try { return measure(node,path,parent,depth,textScale); }
        finally { textPass = previous; }
    }

    private Measured measure(UiNode node, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        if (depth > UiTree.MAX_DEPTH) throw new IllegalArgumentException("UI tree is too deep at " + path);
        MeasureKey key = new MeasureKey(node, path, parent, inheritedTextScale,textPass);
        Measured cached = cache.get(key);
        if (cached == null && textPass == TextPass.ADAPTIVE)
            cached = cache.get(new MeasureKey(node,path,parent,inheritedTextScale,TextPass.COMPLETE));
        if (cached != null) return cached;
        if (++measurements > MAX_MEASUREMENTS) throw new IllegalArgumentException("UI measurement budget exceeded at " + path);
        try {
            double textScale = inheritedTextScale * node.style().textScale();
            if (textScale < .25 || textScale > 4) throw new IllegalArgumentException("Inherited text scale exceeds .25–4");
            UiConstraints limits = node.sizing().within(parent);
            Measured result;
            if (node instanceof UiAlternative alternative) {
                UiTree.check(alternative.preferred());
                UiTree.check(alternative.fallback());
                try { result = measure(alternative.preferred(), path, parent, depth, inheritedTextScale); }
                catch (UiOverflowException overflow) { result = measure(alternative.fallback(), path, parent, depth, inheritedTextScale); }
            } else if (node instanceof UiExcerpt excerpt) {
                result = measureExcerpt(excerpt, path, parent, depth, inheritedTextScale);
            } else if (node instanceof UiPagedTable table) {
                result = measureTable(table, path, parent, depth, inheritedTextScale);
            } else if (node instanceof UiWindowedTable table) {
                result = measureWindowedTable(table,path,parent,depth,inheritedTextScale);
            } else if (node instanceof UiPagedList list) {
                result = measureList(list, path, parent, depth, inheritedTextScale);
            } else if (node instanceof UiWindowedList list) {
                result = measureWindow(list, path, parent, depth, inheritedTextScale,1,0);
            } else if (node instanceof UiWindowedGrid grid) {
                double width = grid.sizing().within(parent).inset(grid.style().padding()).maxWidth();
                int columns = Math.max(1,Math.min(grid.maximumColumns(),(int) Math.floor(
                        (width + grid.columnGap().value()) / (grid.minimumColumnWidth().value() + grid.columnGap().value()))));
                columns = Math.min(columns,Math.max(1,grid.items().itemCount()));
                result = measureWindow(grid.items(),path,parent,depth,inheritedTextScale,columns,grid.columnGap().value());
            } else if (node instanceof UiDocument document) {
                result = measureDocument(document, path, parent, depth, inheritedTextScale);
            } else if (node instanceof UiPagedLayout paged) {
                result = measurePagedLayout(paged, path, parent, depth, inheritedTextScale);
            } else if (node instanceof UiPagedFlow flow) {
                result = measureFlow(flow, path, parent, depth, inheritedTextScale);
            } else if (node instanceof UiElement element) {
                if (element.content() instanceof UiContent.Text text && text.fullFirst()) measuredAdaptiveText = true;
                UiElement styled = element.textRepresentation(textPass == TextPass.SUMMARY).withStyle(element.style().withTextScale(textScale));
                ElementMeasurement natural = measurer.measure(styled, limits);
                if (natural.size().width() > limits.maxWidth() + 1e-6 || natural.size().height() > limits.maxHeight() + 1e-6)
                    throw new UiOverflowException("Measured content exceeds its constraints");
                UiSize size = limits.constrain(natural.size());
                UiElement fitted = styled.withTextWidth(natural.textWidthPixels());
                if (natural.label().isPresent()) fitted = fitted.withText(natural.label().orElseThrow());
                result = new Measured(fitted,
                        size, List.of(), List.of(), new UiLayout.Position(size.width() / 2, size.height() / 2),
                        new UiFrame.ReadingAnchor(path, false), natural.baseline().map(value ->
                                value.shifted((size.height() - natural.size().height()) / 2)), false);
            } else if (node instanceof UiBranch container) {
                UiInsets declaredPadding = node.style().padding();
                Map<Integer, Measured> children = new HashMap<>();
                UiLayout.MeasureScope scope = measurementScope(container.children(), children, path, depth, textScale);
                UiLayout.Result arranged = container.layout().layout(scope, limits.inset(declaredPadding));
                if (arranged.positions().size() != scope.count() || children.size() != scope.count()) {
                    throw new IllegalArgumentException("Layout must measure and arrange every child");
                }
                List<Measured> ordered = new ArrayList<>();
                for (var orientationTarget : arranged.surfaceFacings().keySet())
                    if (java.util.stream.IntStream.range(0,scope.count()).noneMatch(index -> scope.key(index).equals(orientationTarget)))
                        throw new IllegalArgumentException("Unknown surface orientation target: " + orientationTarget);
                for (int i = 0; i < scope.count(); i++) {
                    Measured child = children.get(i);
                    var facing = arranged.surfaceFacings().get(scope.key(i));
                    if (facing != null && child.node instanceof UiSurface surface
                            && surface.facing() != UiSurfaceFacing.INHERIT && surface.facing() != UiSurfaceFacing.FORWARD)
                        child = new Measured(surface.withFacing(facing),child.size,child.children,child.positions,
                                child.focus,child.readingAnchor,child.baseline,child.declaredFocus);
                    ordered.add(child);
                }
                // Empty layout slots remain addressable without their padding creating a phantom panel.
                UiInsets padding = arranged.size().equals(UiSize.ZERO) && node.sizing().minimum().equals(UiSize.ZERO)
                        ? UiInsets.NONE : declaredPadding;
                if (padding.horizontal() > limits.maxWidth() || padding.vertical() > limits.maxHeight())
                    throw new UiOverflowException("Padding exceeds the viewport");
                UiSize requested = new UiSize(arranged.size().width() + padding.horizontal(), arranged.size().height() + padding.vertical());
                if (requested.width() > limits.maxWidth() + 1e-6 || requested.height() > limits.maxHeight() + 1e-6)
                    throw new UiOverflowException("Arranged content exceeds the viewport");
                for (int i = 0; i < ordered.size(); i++) {
                    UiLayout.Position position = arranged.positions().get(i);
                    UiSize child = ordered.get(i).size;
                    if (position.x() < -1e-6 || position.y() < -1e-6
                            || position.x() + child.width() > arranged.size().width() + 1e-6
                            || position.y() + child.height() > arranged.size().height() + 1e-6)
                        throw new UiOverflowException("Child exceeds arranged bounds: " + scope.key(i));
                }
                UiSize size = limits.constrain(requested);
                List<UiLayout.Position> positions = arranged.positions().stream()
                        .map(position -> new UiLayout.Position(position.x() + padding.start(), position.y() + padding.top())).toList();
                UiLayout.Position focus = arranged.focus().map(value -> new UiLayout.Position(
                        value.x() + padding.start(), value.y() + padding.top()))
                        .orElseGet(() -> new UiLayout.Position(size.width() / 2, size.height() / 2));
                var readingAnchor = new UiFrame.ReadingAnchor(path, node instanceof UiSurface);
                boolean declaredFocus = arranged.focusTarget().isPresent() || arranged.focus().isPresent();
                if (arranged.focusTarget().isPresent()) {
                    UiKey target = arranged.focusTarget().orElseThrow();
                    int index = java.util.stream.IntStream.range(0, scope.count()).filter(i -> scope.key(i).equals(target))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing focus child: " + target));
                    UiLayout.Position position = positions.get(index), child = ordered.get(index).focus;
                    focus = new UiLayout.Position(position.x() + child.x(), position.y() + child.y());
                    readingAnchor = new UiFrame.ReadingAnchor(path + "/" + target.value(), true);
                } else if (!declaredFocus && ordered.size() == 1 && ordered.getFirst().declaredFocus) {
                    var position = positions.getFirst();
                    var child = ordered.getFirst().focus;
                    focus = new UiLayout.Position(position.x() + child.x(), position.y() + child.y());
                    readingAnchor = new UiFrame.ReadingAnchor(path, true);
                    declaredFocus = true;
                }
                double firstBaseline = Double.POSITIVE_INFINITY, lastBaseline = Double.NEGATIVE_INFINITY;
                for (int index = 0; index < ordered.size(); index++) if (ordered.get(index).baseline.isPresent()) {
                    var baseline = ordered.get(index).baseline.orElseThrow();
                    firstBaseline = Math.min(firstBaseline, positions.get(index).y() + baseline.first());
                    lastBaseline = Math.max(lastBaseline, positions.get(index).y() + baseline.last());
                }
                Optional<UiBaseline> baseline = Double.isFinite(firstBaseline)
                        ? Optional.of(new UiBaseline(firstBaseline, lastBaseline)) : Optional.empty();
                result = new Measured(node, size, ordered, positions, focus, readingAnchor, baseline, declaredFocus);
            } else {
                throw new IllegalArgumentException("Unresolved content port");
            }
            cache.put(key, result); return result;
        } catch (IllegalArgumentException error) {
            if (error instanceof UiOverflowException) {
                if (textPass == TextPass.ADAPTIVE && (!(node instanceof UiElement element)
                        || element.content() instanceof UiContent.Text text && text.fullFirst())) {
                    Measured compact = measureSummary(node,path,parent,depth,inheritedTextScale);
                    cache.put(key,compact);
                    return compact;
                }
                throw new UiOverflowException("UI does not fit at " + path + ": " + error.getMessage(), error);
            }
            throw new IllegalArgumentException("Invalid UI at " + path + ": " + error.getMessage(), error);
        }
    }

    private Measured measureExcerpt(UiExcerpt excerpt, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        UiConstraints available = excerpt.sizing().within(parent).inset(excerpt.style().padding());
        Measured text = measure(excerpt.text(), path + "/text",
                UiConstraints.bounded(available.maxWidth(), available.maxHeight()), depth + 1,
                inheritedTextScale * excerpt.style().textScale());
        boolean truncated = !((UiElement) text.node).content().label().equals(excerpt.text().content().label());
        UiLayout layout = truncated ? (scope, limits) -> {
            // The full-reading action keeps its natural height; only the preview gives up lines.
            UiSize action = scope.measure(1, UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()));
            double remaining = limits.maxHeight() - action.height() - excerpt.gap().value();
            if (remaining <= 0) throw new UiOverflowException("Excerpt needs room for text and its expansion action");
            UiSize body = scope.measure(0, new UiConstraints(limits.minWidth(), limits.maxWidth(), 0, remaining));
            UiSize size = limits.constrain(new UiSize(Math.max(body.width(), action.width()),
                    body.height() + excerpt.gap().value() + action.height()));
            return new UiLayout.Result(size, List.of(new UiLayout.Position((size.width() - body.width()) / 2, 0),
                    new UiLayout.Position((size.width() - action.width()) / 2, body.height() + excerpt.gap().value())));
        } : UiLayouts.column(UiLength.ZERO);
        List<UiChild> children = truncated ? excerpt.children() : List.of(excerpt.children().getFirst());
        UiNode realized = new UiContainer(excerpt.key(), layout, children, excerpt.style(), excerpt.sizing());
        return measure(realized, path, parent, depth, inheritedTextScale);
    }

    private UiLayout.MeasureScope measurementScope(List<UiChild> declared, Map<Integer, Measured> children,
                                                    String path, int depth, double textScale) {
        return new UiLayout.MeasureScope() {
            @Override public int count() { return declared.size(); }
            @Override public UiKey key(int index) { return declared.get(index).scope(); }
            @Override public UiLayout.Position measuredFocus(int index) {
                Measured measured = children.get(index);
                if (measured == null) throw new IllegalStateException("Child has not been measured");
                return measured.focus;
            }
            @Override public Optional<UiBaseline> measuredBaseline(int index) {
                Measured measured = children.get(index);
                if (measured == null) throw new IllegalStateException("Child has not been measured");
                return measured.baseline;
            }
            @Override public UiSize minimum(int index) {
                UiNode child = declared.get(index).node();
                UiSize declared = child.sizing().minimum();
                if (!(child instanceof UiElement element)) return declared;
                UiSize intrinsic = measurer.minimum(element.withStyle(element.style().withTextScale(textScale*element.style().textScale())));
                return new UiSize(Math.max(declared.width(),intrinsic.width()), Math.max(declared.height(),intrinsic.height()));
            }
            @Override public UiSize maximum(int index) {
                return declared.get(index).node().sizing().maximum();
            }
            @Override public UiSize measuredSize(int index) {
                Measured measured = children.get(index);
                if (measured == null) throw new IllegalStateException("Child has not been measured");
                return measured.size;
            }
            @Override public UiSize measure(int index, UiConstraints constraints) {
                UiChild child = declared.get(index);
                Measured value = UiCompiler.this.measure(child.node(), path + "/" + child.scope().value(), constraints, depth + 1, textScale);
                children.put(index, value); return value.size;
            }
            @Override public LinearSlot linearSlot(int index) { return declared.get(index).linear(); }
            @Override public GridSlot gridSlot(int index, int columns) {
                return declared.get(index).grid().orElseGet(() -> GridSlot.cell(index / columns, index % columns));
            }
        };
    }

    private Measured measureTable(UiPagedTable source, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        var table = source.table();
        var tracks = (UiTrackGridLayout) table.layout();
        var limits = table.sizing().within(parent).inset(table.style().padding());
        double width = limits.maxWidth();
        double scale = inheritedTextScale * table.style().textScale();
        var scope = measurementScope(table.children(), new HashMap<>(), path + "/columns", depth + 1, scale);
        double[] widths = tracks.columnWidths(scope, UiConstraints.bounded(width, limits.maxHeight()));
        var rowLayout = tracks.fixedColumns(widths);
        int columns = tracks.columns();
        var header = new UiContainer(UiKey.named("header"), rowLayout, tableRow(table.children(), 0, columns), UiStyle.PLAIN, UiSizing.CONTENT);
        var rows = new ArrayList<UiChild>();
        for (int index = 0; index < source.rowKeys().size(); index++) {
            var key = source.rowKeys().get(index);
            var row = new UiContainer(key, rowLayout, tableRow(table.children(), index + 1, columns), UiStyle.PLAIN, UiSizing.CONTENT);
            rows.add(UiChild.of(row));
        }
        var pages = new UiPagedList(UiKey.named("pages"), Ui.column().build(), rows, tracks.rowGap(), source.pageIndex(),
                page -> UiTemplates.pager(page, source.selectPage()), source.selectPage(), UiStyle.PLAIN, UiSizing.CONTENT);
        var realized = UiTemplates.focus(Ui.layout(UiLayouts.headerBody(tracks.rowGap())).key(source.key()).style(table.style()).sizing(table.sizing())
                .child(UiKey.named("header"), header, LinearSlot.stretch())
                .child(UiKey.named("pages"), pages, LinearSlot.stretch()).build(), UiKey.named("pages"));
        return measure(realized, path, parent, depth, inheritedTextScale);
    }

    private static List<UiChild> tableRow(List<UiChild> cells, int row, int columns) {
        var result = new ArrayList<UiChild>(columns);
        for (int column = 0; column < columns; column++) {
            UiChild cell = cells.get(row * columns + column);
            result.add(UiChild.grid(cell.scope(), cell.node(), GridSlot.cell(0, column)));
        }
        return List.copyOf(result);
    }

    private Measured measureWindowedTable(UiWindowedTable source, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        var table = source.template();
        var tracks = (UiTrackGridLayout) table.layout();
        var limits = table.sizing().within(parent).inset(table.style().padding());
        double width = limits.maxWidth();
        var rows = new HashMap<Integer,UiContainer>();
        java.util.function.IntFunction<UiContainer> rowAt = index -> rows.computeIfAbsent(index, value -> {
            var row = Objects.requireNonNull(source.row().apply(value),"table row");
            if (row.children().size() != tracks.columns()) throw new IllegalArgumentException("Windowed table rows need one cell per column");
            UiTree.check(row);
            return row;
        });
        double scale = inheritedTextScale * table.style().textScale();
        TableColumns fitted;
        if (source.visibleColumns()) fitted = visibleTableColumns(source,rowAt,path,limits,depth,scale);
        else {
            var scope = measurementScope(table.children(),new HashMap<>(),path + "/$column-template",depth + 1,scale);
            var widths = tracks.columnWidths(scope,UiConstraints.bounded(width,limits.maxHeight()));
            fitted = new TableColumns(widths,tracks.width(widths));
        }
        var layout = tracks.fixedColumns(fitted.widths());
        int columns = tracks.columns();
        var header = new UiContainer(UiKey.named("header"),layout,tableRow(table.children(),0,columns),UiStyle.PLAIN,UiSizing.CONTENT);
        var rowSizing = source.visibleColumns() ? UiSizing.width(UiLength.ui(fitted.windowWidth()),UiLength.ui(fitted.windowWidth())) : UiSizing.CONTENT;
        var prototype = new UiContainer(UiKey.named("row-template"),layout,tableRow(table.children(),1,columns),UiStyle.PLAIN,rowSizing);
        var pages = Ui.windowedList(source.itemCount(),prototype,index -> {
            var row = rowAt.apply(index);
            var fill = row.sizing();
            if (source.visibleColumns()) {
                var allocation = fill.within(new UiConstraints(fitted.windowWidth(),fitted.windowWidth(),0,UiSizing.CONTENT.maximum().height()));
                fill = new UiSizing(new UiSize(allocation.minWidth(),allocation.minHeight()),new UiSize(allocation.maxWidth(),allocation.maxHeight()));
            }
            return new UiContainer(row.key(),layout,tableRow(row.children(),0,columns),row.style(),fill);
        }).key("pages").sizing(UiSizing.width(UiLength.ZERO,UiLength.ui(fitted.windowWidth())))
                .gap(tracks.rowGap()).page(source.pageIndex(),source.selectPage())
                .pager(page -> source.pager().build(page,source.selectPage()));
        source.anchorIndex().ifPresent(pages::containing);
        var realized = UiTemplates.focus(Ui.layout(UiLayouts.headerBody(tracks.rowGap())).key(source.key()).style(table.style()).sizing(table.sizing())
                .child(UiKey.named("header"),header,LinearSlot.stretch())
                .child(UiKey.named("pages"),pages.build(),LinearSlot.stretch()).build(),UiKey.named("pages"));
        return measure(realized,path,parent,depth,inheritedTextScale);
    }

    /** Discover a bounded window before fitting its columns; off-screen content never dictates width. */
    private TableColumns visibleTableColumns(UiWindowedTable source, java.util.function.IntFunction<UiContainer> rowAt,
                                         String path, UiConstraints limits, int depth, double scale) {
        var table = source.template();
        var tracks = (UiTrackGridLayout) table.layout();
        int columns = tracks.columns();
        var bounds = UiConstraints.bounded(limits.maxWidth(),limits.maxHeight());
        var template = measurementScope(table.children(),new HashMap<>(),path + "/$row-template",depth + 1,scale);
        double headerHeight = 0, rowHeight = 0;
        // The template supplies height, not a width floor. Single-line cells can be measured
        // independently before the current window supplies the shared column demands.
        for (int index = 0; index < columns; index++) {
            headerHeight = Math.max(headerHeight,template.measure(index,bounds).height());
            rowHeight = Math.max(rowHeight,template.measure(columns + index,bounds).height());
        }
        if (source.itemCount() > 0 && rowHeight <= 0) throw new IllegalArgumentException("A window row template needs a positive height");
        var page = tableWindowPage(source,path,limits.maxWidth(),limits.maxHeight() - headerHeight - (headerHeight > 0 ? tracks.rowGap().value() : 0),
                rowHeight,depth,scale).page();
        for (int pass = 0; pass < MAX_PAGE_LAYOUT_PASSES; pass++) {
            var cells = new ArrayList<>(table.children().subList(0,columns));
            for (int index = page.fromIndex(); index < page.toIndex(); index++) {
                var row = rowAt.apply(index);
                for (int column = 0; column < columns; column++) {
                    var cell = row.children().get(column);
                    cells.add(UiChild.grid(cell.scope(),cell.node(),GridSlot.cell(index - page.fromIndex() + 1,column)));
                }
            }
            var scope = measurementScope(cells,new HashMap<>(),path + "/$visible-columns",depth + 1,scale);
            var widths = tracks.columnWidths(scope,bounds);
            var layout = tracks.fixedColumns(widths);
            var header = new UiContainer(UiKey.named("header-template"),layout,tableRow(table.children(),0,columns),UiStyle.PLAIN,UiSizing.CONTENT);
            var prototype = new UiContainer(UiKey.named("row-template"),layout,tableRow(table.children(),1,columns),UiStyle.PLAIN,UiSizing.CONTENT);
            headerHeight = measure(header,path + "/$header-template",bounds,depth + 1,scale).size.height();
            rowHeight = measure(prototype,path + "/$row-template",bounds,depth + 1,scale).size.height();
            double windowWidth = Math.max(limits.minWidth(),Math.max(tracks.width(widths),maximumFooterSpace(page,
                    candidate -> source.pager().build(candidate,source.selectPage()),
                    pager -> measure(pager,path + "/pages/pager",bounds,depth + 2,scale).size.width())));
            var next = tableWindowPage(source,path,windowWidth,limits.maxHeight() - headerHeight - (headerHeight > 0 ? tracks.rowGap().value() : 0),
                    rowHeight,depth,scale).page();
            if (next.equals(page)) return new TableColumns(widths,windowWidth);
            page = next;
        }
        throw new UiOverflowException("Visible table columns and pagination did not stabilize");
    }

    private WindowPage tableWindowPage(UiWindowedTable source, String path, double width, double available,
                                        double rowHeight, int depth, double scale) {
        double gap = ((UiTrackGridLayout) source.template().layout()).rowGap().value();
        return windowPage(available,gap,source.itemCount() > 0,
                page -> source.pager().build(page,source.selectPage()),
                height -> UiWindowPacking.page(source.pageIndex(),source.itemCount(),rowHeight,height,gap,source.anchorIndex(),1),
                pager -> measure(pager,path + "/pages/pager",UiConstraints.bounded(width,Math.max(0,available)),depth + 2,scale).size.height());
    }

    private Measured measureList(UiPagedList list, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        var limits = list.sizing().within(parent).inset(list.style().padding());
        double scale = inheritedTextScale * list.style().textScale(), gap = list.gap().value();
        var header = measure(list.header(),path+"/header",UiConstraints.bounded(limits.maxWidth(),limits.maxHeight()),depth+1,scale);
        double headerGap = header.size.height() == 0 ? 0 : gap;
        double available = limits.maxHeight() - header.size.height() - (list.items().isEmpty() ? 0 : headerGap);
        if (available < 0) throw new UiOverflowException("Paged list header exceeds the viewport");
        var items = pageItems(list.items(), path, limits.maxWidth(), available, depth, scale);
        var pages = pageLayout(list.pageIndex(), list.pager(), available, gap,
                height -> UiPagePacking.column(items.apply(height), height, gap),
                pager -> {
                    double height = measure(pager, path+"/pager", UiConstraints.bounded(limits.maxWidth(), available), depth+1, scale).size.height();
                    // With no items, a visible footer is the only region that needs a separator below the header.
                    return height == 0 ? 0 : height + (list.items().isEmpty() ? headerGap : 0);
                });
        var range = pages.body().pages().get(pages.page().index());
        var realized = Ui.column().key(list.key()).style(list.style()).sizing(list.sizing()).gap(list.gap());
        if (header.size.height()>0) realized.child(UiKey.named("header"),list.header(),LinearSlot.stretch());
        realized.child(UiKey.named("items"),new UiContainer(UiKey.named("items"),pages.body().layout(),
                list.items().subList(range.from(),range.to()),UiStyle.PLAIN,pageBodySizing(pages.body().size())));
        realized.child(UiKey.named("pager"),pages.pager());
        var result = measure(realized.build(),path,parent,depth,inheritedTextScale);
        pagination.put(result,new Page(pages.page(),list.selectPage()));
        return result;
    }

    private Measured measureDocument(UiDocument document, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        var limits = document.sizing().within(parent).inset(document.style().padding());
        double scale = inheritedTextScale * document.style().textScale(), gap = document.gap().value();
        var header = measure(document.header(), path + "/header", UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()), depth + 1, scale);
        double available = limits.maxHeight() - header.size.height();
        if (available < 0) throw new UiOverflowException("Document header exceeds the viewport");
        double width = limits.maxWidth();
        var sources = new UiDocumentPages(document, new UiDocumentPages.Measurer() {
            public UiSize block(UiNode node, UiKey scope, double height) {
                return measure(node, path + "/items/" + scope.value(), UiConstraints.bounded(width, height), depth + 2, scale).size;
            }
            public List<Integer> textPages(UiElement node, UiDocumentText text, double height) {
                var constraints = node.sizing().within(UiConstraints.bounded(width, height));
                var styled = node.withStyle(node.style().withTextScale(node.style().textScale() * scale));
                return measurer.documentPages(styled, constraints).orElseGet(() -> exactTextPages(styled, text, constraints));
            }
        });
        var body = sources.layout(available);
        if (header.size.height() > 0 && body.body().size().height() > 0) {
            available -= gap;
            body = sources.layout(available);
        }
        if (document.bodyReading() && body.body().size().height() == 0)
            throw new UiOverflowException("Body-focused reading needs nonempty content");
        final double footerAvailable = available;
        var page = body.page(document.position());
        double reserved = 0;
        for (int pass = 0; pass < MAX_PAGE_LAYOUT_PASSES; pass++) {
            double footer = maximumFooterSpace(page, candidate -> document.pager().build(candidate, _ -> {}),
                    prototype -> measure(prototype, path + "/pager", UiConstraints.bounded(limits.maxWidth(), footerAvailable), depth + 1, scale).size.height());
            reserved = Math.max(reserved, footer == 0 ? 0 : footer + (body.body().size().height() == 0 && header.size.height() > 0 ? gap : 0));
            double remaining = available - reserved - (reserved > 0 && body.body().size().height() > 0 ? gap : 0);
            var next = sources.layout(remaining);
            var actual = next.page(document.position());
            if (actual.equals(page)) {
                var completed = next;
                IntConsumer select = index -> document.selectPosition().accept(completed.position(Math.clamp(index, 0, actual.count() - 1)));
                var realized = Ui.column().key(document.key()).style(document.style()).sizing(document.sizing()).gap(document.gap());
                if (header.size.height() > 0) realized.child(UiKey.named("header"), document.header(), LinearSlot.stretch());
                realized.child(new UiContainer(UiKey.named("items"), UiLayouts.column(document.gap()), completed.children(actual.index()),
                        UiStyle.PLAIN, pageBodySizing(completed.body().size())),
                        completed.body().size().equals(UiSize.ZERO) ? LinearSlot.CONTENT : LinearSlot.stretch());
                var controls = Objects.requireNonNull(document.pager().build(actual, select), "document pager");
                realized.child(UiKey.named("pager"), controls, footer > 0 ? LinearSlot.stretch() : LinearSlot.CONTENT);
                UiNode content = document.bodyReading() ? UiTemplates.focus(realized.build(), UiKey.named("items")) : realized.build();
                var result = measure(content, path, parent, depth, inheritedTextScale);
                pagination.put(result, controls instanceof UiPager navigation
                        ? new Page(navigation.page(), navigation.selectPage(), Optional.of(completed.position(actual.index())))
                        : new Page(actual, select, Optional.of(completed.position(actual.index()))));
                return result;
            }
            body = next; page = actual;
        }
        throw new UiOverflowException("Document pagination did not settle within its measurement budget");
    }

    private List<Integer> exactTextPages(UiElement node, UiDocumentText text, UiConstraints constraints) {
        var boundaries = text.graphemeEnds();
        var pages = new ArrayList<Integer>();
        int from = 0, first = 0;
        while (from < text.length()) {
            int low = first, high = first;
            while (high < boundaries.size() && fitsText(node.withText(text.slice(from, boundaries.get(high))), constraints)) {
                low = high + 1;
                high = Math.min(boundaries.size(), first + Math.max(1, (high - first + 1) * 2));
            }
            int left = low, right = high;
            while (left < right) {
                int middle = (left + right) >>> 1;
                if (fitsText(node.withText(text.slice(from, boundaries.get(middle))), constraints)) left = middle + 1;
                else right = middle;
            }
            int end = left == first ? text.skipWhitespace(from) : boundaries.get(left - 1);
            if (end <= from) throw new UiOverflowException("Document paragraph needs one complete readable grapheme");
            pages.add(end); from = end;
            while (first < boundaries.size() && boundaries.get(first) <= from) first++;
        }
        return List.copyOf(pages);
    }

    private boolean fitsText(UiElement node, UiConstraints constraints) {
        if (++measurements > MAX_MEASUREMENTS) throw new IllegalArgumentException("UI measurement budget exceeded");
        try {
            var size = measurer.measure(node, constraints).size();
            return size.width() <= constraints.maxWidth() + 1e-6 && size.height() <= constraints.maxHeight() + 1e-6;
        } catch (UiOverflowException overflow) { return false; }
    }

    private Measured measureWindow(UiWindowedList list, String path, UiConstraints parent, int depth, double inheritedTextScale,
                                   int columns, double columnGap) {
        UiConstraints limits = list.sizing().within(parent).inset(list.style().padding());
        double scale = inheritedTextScale * list.style().textScale(), gap = list.gap().value();
        var header = measure(list.header(), path + "/header", UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()), depth + 1, scale);
        double available = limits.maxHeight() - header.size.height();
        if (header.size.height() > 0 && list.itemCount() > 0) available -= gap;
        UiTree.check(list.rowTemplate());
        double columnWidth = Math.max(0,(limits.maxWidth() - (columns - 1) * columnGap) / columns);
        double rowHeight = list.itemCount() == 0 ? 1 : measure(list.rowTemplate(), path + "/$row-template",
                UiConstraints.bounded(columnWidth, Math.max(0, available)), depth + 1, scale).size.height();
        if (rowHeight <= 0) throw new IllegalArgumentException("A window row template needs a positive height");
        final double footerAvailable = available;
        var packed = windowPage(available,gap,list.itemCount() > 0 || header.size.height() > 0,list.pager(),
                height -> UiWindowPacking.page(list.pageIndex(),list.itemCount(),rowHeight,height,gap,list.anchorIndex(),columns),
                prototype -> measure(prototype,path + "/pager",UiConstraints.bounded(limits.maxWidth(),Math.max(0,footerAvailable)),depth + 1,scale).size.height());
        var page = packed.page();
        var pager = packed.pager();
        var rows = new ArrayList<UiChild>();
        for (int index = page.fromIndex(); index < page.toIndex(); index++) {
            UiNode item = Objects.requireNonNull(list.item().apply(index), "window item");
            if (item.key().equals(UiKey.AUTO)) throw new IllegalArgumentException("Window rows need stable domain keys");
            UiTree.check(item);
            UiNode row = Ui.stack().key(item.key()).sizing(new UiSizing(new UiSize(0, rowHeight),
                    new UiSize(UiSizing.CONTENT.maximum().width(), rowHeight)))
                    .child(UiKey.named("content"), item).build();
            rows.add(columns == 1 ? UiChild.of(row)
                    : UiChild.grid(row,new GridSlot((index - page.fromIndex()) / columns,(index - page.fromIndex()) % columns,1,1)));
        }
        // Reserve a full page even on its last, short window. No off-screen rows are constructed.
        double bodyHeight = UiWindowPacking.height((Math.min(list.itemCount(), page.pageSize()) + columns - 1) / columns, rowHeight, gap);
        UiLayout bodyLayout = UiLayouts.column(list.gap());
        if (columns > 1) {
            var grid = new UiGridLayout(columns,UiLength.ui(columnGap),list.gap(),UiLayouts.GridAlignment.START,UiLayouts.GridAlignment.START);
            var widths = new double[columns]; java.util.Arrays.fill(widths,columnWidth);
            bodyLayout = (scope,constraints) -> grid.arrange(scope,constraints,grid.cells(scope),widths);
        }
        UiNode body = new UiContainer(UiKey.named("items"), bodyLayout, rows, UiStyle.PLAIN,
                new UiSizing(new UiSize(page.count() > 1 ? limits.maxWidth() : 0, bodyHeight),
                        new UiSize(UiSizing.CONTENT.maximum().width(), bodyHeight)));
        var realized = Ui.column().key(list.key()).style(list.style()).sizing(list.sizing()).gap(list.gap());
        if (header.size.height() > 0) realized.child(UiKey.named("header"), list.header(), LinearSlot.stretch());
        realized.child(body).child(UiKey.named("pager"), Objects.requireNonNull(pager));
        UiTree.check(realized.build());
        var result = measure(realized.build(), path, parent, depth, inheritedTextScale);
        pagination.put(result, pager instanceof UiPager navigation
                ? new Page(navigation.page(),navigation.selectPage(),Optional.empty(),list.observation())
                : new Page(page,list.selectPage(),Optional.empty(),list.observation()));
        return result;
    }

    /** One footer reservation rule for template-sized and content-sized reading windows. */
    private WindowPage windowPage(double available, double gap, boolean separateFooter,
                                   Function<FloatingMenuPage,UiNode> pager, DoubleFunction<FloatingMenuPage> pack,
                                   ToDoubleFunction<UiNode> footerHeight) {
        var page = pack.apply(available);
        double reserved = 0;
        for (int pass = 0; pass < MAX_PAGE_LAYOUT_PASSES; pass++) {
            var controls = Objects.requireNonNull(pager.apply(page),"window pager");
            UiTree.check(controls);
            double height = maximumFooterSpace(page,pager,footerHeight);
            reserved = Math.max(reserved,height == 0 ? 0 : height + (separateFooter ? gap : 0));
            var next = pack.apply(available - reserved);
            if (next.equals(page)) return new WindowPage(page,controls);
            page = next;
        }
        throw new UiOverflowException("Window pagination did not settle within its measurement budget");
    }

    private Measured measureFlow(UiPagedFlow flow, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        UiConstraints limits = flow.sizing().within(parent).inset(flow.style().padding());
        var measuredItems = pageItems(flow.items(), path, limits.maxWidth(), limits.maxHeight(), depth,
                inheritedTextScale * flow.style().textScale());
        var pages = pageLayout(flow.pageIndex(), flow.pager(), limits.maxHeight(), flow.rowGap().value(),
                height -> UiPagePacking.flow(measuredItems.apply(height), new UiSize(limits.maxWidth(), height),
                        flow.rows(), flow.gap().value(), flow.rowGap().value()),
                pager -> measure(pager, path+"/pager", UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()), depth+1,
                        inheritedTextScale * flow.style().textScale()).size.height());
        return realizePage(flow.key(), flow.items(), flow.style(), flow.sizing(), flow.rowGap(), flow.selectPage(),
                pages, path, parent, depth, inheritedTextScale);
    }

    private Measured measurePagedLayout(UiPagedLayout paged, String path, UiConstraints parent, int depth, double inheritedTextScale) {
        var content = paged.content();
        UiConstraints limits = paged.sizing().within(parent).inset(paged.style().padding());
        double textScale = inheritedTextScale * paged.style().textScale();
        var pages = pageLayout(paged.pageIndex(), paged.pager(), limits.maxHeight(), paged.gap().value(),
                height -> UiPagePacking.layout(content.children().size(), content.layout(), (from, to) -> {
                    var body = new UiContainer(UiKey.named("items"), content.layout(), content.children().subList(from, to),
                            UiStyle.PLAIN, UiSizing.CONTENT);
                    return measure(body, path + "/items", UiConstraints.bounded(limits.maxWidth(), height), depth + 1, textScale).size;
                }),
                pager -> measure(pager, path + "/pager", UiConstraints.bounded(limits.maxWidth(), limits.maxHeight()),
                        depth + 1, textScale).size.height());
        return realizePage(paged.key(), content.children(), paged.style(), paged.sizing(), paged.gap(), paged.selectPage(),
                pages, path, parent, depth, inheritedTextScale);
    }

    private Measured realizePage(UiKey key, List<UiChild> children, UiStyle style, UiSizing sizing, UiLength gap,
                                 java.util.function.IntConsumer select, PageLayout pages, String path,
                                 UiConstraints parent, int depth, double inheritedTextScale) {
        var range = pages.body().pages().get(pages.page().index());
        // Reserve the largest measured body so pagination keeps neighboring content stable.
        UiNode items = new UiContainer(UiKey.named("items"), pages.body().layout(),
                children.subList(range.from(), range.to()), UiStyle.PLAIN, pageBodySizing(pages.body().size()));
        UiNode realized = Ui.column().key(key).style(style).sizing(sizing).gap(gap)
                .child(items).child(UiKey.named("pager"), pages.pager()).build();
        Measured result = measure(realized, path, parent, depth, inheritedTextScale);
        pagination.put(result, new Page(pages.page(), select));
        return result;
    }

    private DoubleFunction<List<UiSize>> pageItems(List<UiChild> items, String path, double width, double initialHeight, int depth, double textScale) {
        List<UiSize> natural = items.stream().map(child -> measure(child.node(), path + "/items/" + child.scope().value(),
                UiConstraints.bounded(width, initialHeight), depth + 2, textScale).size).toList();
        return height -> {
            var sizes = new ArrayList<UiSize>(items.size());
            for (int index = 0; index < items.size(); index++) {
                UiChild child = items.get(index);
                UiSize size = natural.get(index);
                boolean reuse = child.node() instanceof UiElement element && measurer.heightIndependent(element)
                        && size.height() <= height;
                sizes.add(reuse ? size : measure(child.node(), path + "/items/" + child.scope().value(),
                        UiConstraints.bounded(width, height), depth + 2, textScale).size);
            }
            return sizes;
        };
    }

    private static UiSizing pageBodySizing(UiSize size) {
        return new UiSizing(size, new UiSize(UiSizing.CONTENT.maximum().width(), size.height()));
    }

    /** One bounded footer-and-body measurement rule for both collection layouts. */
    private PageLayout pageLayout(int index, Function<FloatingMenuPage, UiNode> pager, double available, double gap,
                                  DoubleFunction<UiPagePacking.Packed> pack, ToDoubleFunction<UiNode> footerSpace) {
        var body = pack.apply(available);
        var page = new FloatingMenuPage(index, body.pages().size(), 1);
        double reserved = 0;
        for (int pass = 0; pass < MAX_PAGE_LAYOUT_PASSES; pass++) {
            var footer = pager.apply(page);
            reserved = Math.max(reserved, maximumFooterSpace(page, pager, footerSpace));
            double remaining = available - reserved - (reserved > 0 && body.size().height() > 0 ? gap : 0);
            if (remaining < -1e-6) throw new UiOverflowException("Pagination leaves no room for its content");
            var next = reserved == 0 ? body : pack.apply(Math.max(0, remaining));
            var actual = new FloatingMenuPage(index, next.pages().size(), 1);
            if (actual.equals(page)) return new PageLayout(next, page, footer);
            body = next;
            page = actual;
        }
        throw new UiOverflowException("Pagination did not settle within its measurement budget");
    }

    /** First, interior and last controls have one capacity even when edge buttons or final actions differ. */
    private static double maximumFooterSpace(FloatingMenuPage page, Function<FloatingMenuPage, UiNode> pager,
                                              ToDoubleFunction<UiNode> measure) {
        double maximum = 0;
        var indices = new java.util.LinkedHashSet<Integer>();
        indices.add(0); indices.add(page.count() / 2); indices.add(page.count() - 1); indices.add(page.index());
        for (int index : indices) maximum = Math.max(maximum, measure.applyAsDouble(java.util.Objects.requireNonNull(
                pager.apply(new FloatingMenuPage(index, page.itemCount(), page.pageSize())), "pager")));
        return maximum;
    }

    private void flatten(Measured measured, String path, double x, double y, double inheritedAlpha,
                         List<UiFrame.Element> elements, List<UiFrame.Surface> surfaces, List<UiFrame.Panel> panels, List<UiFrame.Group> groups,
                         List<UiFrame.Pagination> pages) {
        Page ownedPage = pagination.get(measured);
        if (ownedPage == null) {
            for (var child : measured.children) if (child.node instanceof UiPager pager) {
                if (ownedPage != null) throw new IllegalArgumentException("Each paged collection needs its own container: " + path);
                ownedPage = new Page(pager.page(), pager.selectPage());
            }
            if (path.equals("root") && measured.node instanceof UiPager pager)
                ownedPage = new Page(pager.page(), pager.selectPage());
        }
        if (ownedPage != null) pages.add(new UiFrame.Pagination(path, x, y, measured.size, ownedPage.page(), ownedPage.selectPage(), ownedPage.readingPosition(),ownedPage.observation()));
        if (measured.node instanceof UiElement element) {
            elements.add(new UiFrame.Element(path, element.withStyle(element.style().withContentAlpha(
                    inheritedAlpha * element.style().contentAlpha())), x, y, measured.size, measured.baseline));
        } else if (measured.node instanceof UiBranch container) {
            boolean visible = measured.size.width() > 0 && measured.size.height() > 0;
            if (visible && container instanceof UiSurface surface) panels.add(new UiFrame.Panel(path, x, y, measured.size, surface.elevation(), surface.facing()));
            if (container instanceof UiSpatialGroup group) groups.add(new UiFrame.Group(path, x, y, measured.size, group.transform(), group.pivot()));
            if (visible && container.style().background().isPresent()) surfaces.add(new UiFrame.Surface(path,
                    container.style(), x, y, measured.size));
            for (int i = 0; i < measured.children.size(); i++) {
                UiLayout.Position position = measured.positions.get(i);
                flatten(measured.children.get(i), path + "/" + container.children().get(i).scope().value(),
                        x + position.x(), y + position.y(), inheritedAlpha * container.style().contentAlpha(), elements, surfaces, panels, groups, pages);
            }
        }
    }

    private Optional<UiFrame.Region> readingRegion(Measured measured, String path, double x, double y) {
        if (path.equals(readingPath)) return Optional.of(new UiFrame.Region(path, x, y, measured.size));
        if (!(measured.node instanceof UiBranch branch)) return Optional.empty();
        for (int index = 0; index < measured.children.size(); index++) {
            var position = measured.positions.get(index);
            String child = path + "/" + branch.children().get(index).scope().value();
            if (!readingPath.startsWith(child + "/") && !readingPath.equals(child)) continue;
            return readingRegion(measured.children.get(index), child, x + position.x(), y + position.y());
        }
        return Optional.empty();
    }

    /** Responsive branches can reuse a child at the same path under a different inherited typography. */
    private record MeasureKey(UiNode node, String path, UiConstraints constraints, double inheritedTextScale, TextPass textPass) {
        @Override public boolean equals(Object other) {
            return other instanceof MeasureKey key && node == key.node && path.equals(key.path) && constraints.equals(key.constraints)
                    && Double.compare(inheritedTextScale, key.inheritedTextScale) == 0 && textPass == key.textPass;
        }
        @Override public int hashCode() { return Objects.hash(System.identityHashCode(node), path, constraints, inheritedTextScale,textPass); }
    }
    private record Measured(UiNode node, UiSize size, List<Measured> children, List<UiLayout.Position> positions,
                            UiLayout.Position focus, UiFrame.ReadingAnchor readingAnchor, Optional<UiBaseline> baseline,
                            boolean declaredFocus) { }
}
