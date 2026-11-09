package org.encinet.mik.shared.menu.ui;

import org.encinet.mik.shared.menu.image.UiImage;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.menu.FloatingMenuAction;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuPage;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** Typed, local builders. Building a component never opens a view or registers a task. */
public final class Ui {
    private Ui() { }

    public static ElementBuilder text(Component text) { return new ElementBuilder(new UiContent.Text(text), UiRole.TEXT); }
    public static UiAlternative alternative(UiNode preferred, UiNode fallback) { return new UiAlternative(preferred, fallback); }
    /** A bounded reading preview whose expansion control appears only when the measured text is cut. */
    public static UiExcerpt excerpt(UiElement text, UiElement expansion) {
        return new UiExcerpt(UiKey.AUTO, text, expansion, UiLength.ui(6), UiStyle.PLAIN, UiSizing.CONTENT);
    }
    public static ElementBuilder heading(Component text) {
        return new ElementBuilder(new UiContent.Text(text), UiRole.HEADING).style(UiTheme.HEADING);
    }
    public static ElementBuilder image(UiImage source, Component alternative) {
        return new ElementBuilder(new UiContent.Image(source, alternative), UiRole.TEXT);
    }
    /** A live read-only indicator; its value does not participate in layout. */
    public static ElementBuilder progress(Component label, UiRange value) {
        return new ElementBuilder(new UiContent.Range(value, label), UiRole.TEXT);
    }
    /** One hit target with pointer preview, click-to-set and focused wheel adjustment. */
    public static ElementBuilder slider(Component label, UiRange value, UiRange.Change change) {
        Objects.requireNonNull(change, "change");
        var control = new ElementBuilder(new UiContent.Range(value, label), UiRole.ACTION).style(UiStyle.PLAIN)
                .primary((player, handle) -> handle.horizontalFraction().ifPresent(fraction -> change.apply(player, handle, value.at(fraction))));
        for (var direction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN))
            control.on(direction, (player, handle, input) -> {
                double current = value.current();
                double next = Math.clamp(current + (input == FloatingMenuInteraction.SCROLL_UP ? value.step() : -value.step()),
                        value.minimum(), value.maximum());
                if (current != next) change.apply(player, handle, next);
            });
        return control;
    }
    public static ElementBuilder button(Component label) { return new ElementBuilder(new UiContent.Text(label), UiRole.ACTION); }
    /** A normal button with a semantic label that does not take part in artwork measurement. */
    public static ElementBuilder button(UiIcon icon, Component label) {
        return new ElementBuilder(new UiContent.Icon(icon, label), UiRole.ACTION).tooltip(label);
    }
    /** Read-only artwork; callers may add useful status help without introducing a click action. */
    public static ElementBuilder icon(UiIcon icon, Component label) {
        return new ElementBuilder(new UiContent.Icon(icon, label), UiRole.STATUS).tooltip(label);
    }
    public static UiTextField textField(Component value) { return new UiTextField(value); }
    /** A slider with a compact value; the host can supply precise editing without owning a second range. */
    public static UiNumberField numberField(Component label, UiRange range, UiRange.Change change) {
        return new UiNumberField(label, range, change);
    }
    static ElementBuilder rangeValue(UiRange range) {
        return new ElementBuilder(new UiContent.RangeValue(range, range.text(range.current())), UiRole.ACTION);
    }
    public static UiForm form() { return new UiForm(); }
    public static ElementBuilder item(Material material, Component label) { return item(new ItemStack(material), label); }
    public static ElementBuilder item(ItemStack item, Component label) { return new ElementBuilder(new UiContent.Item(item, label, false), UiRole.ACTION); }
    public static ElementBuilder block(Material material, Component label) { return new ElementBuilder(new UiContent.Item(new ItemStack(material), label, true), UiRole.ACTION); }
    public static LinearBuilder row() { return new LinearBuilder(true); }
    public static LinearBuilder column() { return new LinearBuilder(false); }
    public static GridBuilder grid(int columns) { return new GridBuilder(columns); }
    public static <T> UiTable<T> table() { return new UiTable<>(); }
    public static PagedFlowBuilder pagedFlow(int rows) { return new PagedFlowBuilder(rows); }
    public static PagedListBuilder pagedList() { return new PagedListBuilder(); }
    public static DocumentBuilder document() { return new DocumentBuilder(); }
    public static WindowedListBuilder windowedList(int itemCount, UiLength rowHeight,
                                                    java.util.function.IntFunction<? extends UiNode> item) {
        if (rowHeight.value() <= 0) throw new IllegalArgumentException("A window row needs a positive height");
        return windowedList(itemCount, Ui.stack().size(new UiSize(0, rowHeight.value())).build(), item);
    }
    public static WindowedListBuilder windowedList(int itemCount, UiNode rowTemplate,
                                                    java.util.function.IntFunction<? extends UiNode> item) {
        return new WindowedListBuilder(itemCount, rowTemplate, item);
    }
    public static UiWindowedGrid windowedGrid(UiWindowedList items, int maximumColumns,
                                              UiLength minimumColumnWidth, UiLength columnGap) {
        return new UiWindowedGrid(items, maximumColumns, minimumColumnWidth, columnGap);
    }
    public static SurfaceBuilder surface(UiNode content) { return new SurfaceBuilder(content); }
    public static SpatialGroupBuilder group(UiNode content) { return new SpatialGroupBuilder(content); }
    public static ContainerBuilder stack() { return new ContainerBuilder(UiLayouts.stack()); }
    public static ContainerBuilder layout(UiLayout layout) { return new ContainerBuilder(layout); }
    public static UiPort port(String key, UiLayout layout) { return new UiPort(UiKey.named(key), layout, UiStyle.PLAIN, UiSizing.CONTENT); }

    public abstract static class Builder<B extends Builder<B>> {
        protected UiKey key = UiKey.AUTO;
        protected UiStyle style = UiStyle.PLAIN;
        protected UiSizing sizing = UiSizing.CONTENT;
        protected abstract B self();
        public B key(String key) { this.key = UiKey.named(key); return self(); }
        public B key(UiKey key) { this.key = Objects.requireNonNull(key, "key"); return self(); }
        public B style(UiStyle style) { this.style = Objects.requireNonNull(style, "style"); return self(); }
        public B size(UiSize size) { sizing = UiSizing.fixed(size); return self(); }
        public B sizing(UiSizing sizing) { this.sizing = Objects.requireNonNull(sizing, "sizing"); return self(); }
        public abstract UiNode build();
    }

    public static final class PagedListBuilder extends Builder<PagedListBuilder> {
        private UiNode header = Ui.column().build();
        private List<UiChild> items = List.of();
        private UiLength gap = UiLength.ui(8);
        private int page;
        private IntConsumer select = _ -> {};
        private Function<FloatingMenuPage,UiNode> pager;
        @Override protected PagedListBuilder self() { return this; }
        public PagedListBuilder header(UiNode value) { header = Objects.requireNonNull(value); return this; }
        public PagedListBuilder items(List<? extends UiNode> values) { return items(values, LinearSlot.CONTENT); }
        public PagedListBuilder items(List<? extends UiNode> values, LinearSlot slot) {
            Objects.requireNonNull(slot);
            items = values.stream().map(value -> UiChild.linear(value.key(), value, slot)).toList();
            return this;
        }
        public PagedListBuilder gap(UiLength value) { gap = Objects.requireNonNull(value); return this; }
        public PagedListBuilder page(int value, IntConsumer selected) { page = value; select = Objects.requireNonNull(selected); return this; }
        public PagedListBuilder pager(Function<FloatingMenuPage,UiNode> value) { pager = Objects.requireNonNull(value); return this; }
        @Override public UiPagedList build() {
            IntConsumer selected = select;
            return new UiPagedList(key,header,items,gap,page,pager == null ? value -> UiTemplates.pager(value,selected) : pager,selected,style,sizing);
        }
    }

    public static final class DocumentBuilder extends Builder<DocumentBuilder> {
        private UiNode header = Ui.column().build();
        private List<UiChild> blocks = List.of();
        private UiLength gap = UiLength.ui(8);
        private UiReadingPosition position = UiReadingPosition.START;
        private java.util.function.Consumer<UiReadingPosition> select;
        private UiPageControls pager = UiTemplates::pager;
        private boolean bodyReading;
        @Override protected DocumentBuilder self() { return this; }
        public DocumentBuilder header(UiNode value) { header = Objects.requireNonNull(value); return this; }
        public DocumentBuilder blocks(List<? extends UiNode> values) {
            blocks = values.stream().map(value -> UiChild.linear(value.key(), value, LinearSlot.stretch())).toList();
            return this;
        }
        public DocumentBuilder gap(UiLength value) { gap = Objects.requireNonNull(value); return this; }
        public DocumentBuilder pager(UiPageControls value) { pager = Objects.requireNonNull(value); return this; }
        /** The nonempty body owns reading space; the persistent header and contextual footer remain auxiliary. */
        public DocumentBuilder readingBody() { bodyReading = true; return this; }
        public DocumentBuilder position(UiReadingPosition value, java.util.function.Consumer<UiReadingPosition> changed) {
            position = Objects.requireNonNull(value); select = Objects.requireNonNull(changed); return this;
        }
        @Override public UiDocument build() {
            return new UiDocument(key, header, blocks, gap, position,
                    Objects.requireNonNull(select, "Document needs its owner's reading-position callback"), style, sizing, pager, bodyReading);
        }
    }

    public static final class WindowedListBuilder extends Builder<WindowedListBuilder> {
        private final int itemCount;
        private final UiNode rowTemplate;
        private final java.util.function.IntFunction<? extends UiNode> item;
        private UiNode header = Ui.column().build();
        private UiLength gap = UiLength.ui(8);
        private int page;
        private IntConsumer select = _ -> {};
        private Function<FloatingMenuPage, UiNode> pager;
        private java.util.Optional<UiWindowedList.Observation> observation = java.util.Optional.empty();
        private java.util.OptionalInt anchorIndex = java.util.OptionalInt.empty();
        private WindowedListBuilder(int itemCount, UiNode rowTemplate, java.util.function.IntFunction<? extends UiNode> item) {
            this.itemCount = itemCount; this.rowTemplate = rowTemplate; this.item = item;
        }
        @Override protected WindowedListBuilder self() { return this; }
        public WindowedListBuilder header(UiNode value) { header = Objects.requireNonNull(value); return this; }
        public WindowedListBuilder gap(UiLength value) { gap = Objects.requireNonNull(value); return this; }
        public WindowedListBuilder page(int value, IntConsumer selected) { page = value; select = Objects.requireNonNull(selected); anchorIndex = java.util.OptionalInt.empty(); return this; }
        public WindowedListBuilder containing(int index) { anchorIndex = java.util.OptionalInt.of(index); return this; }
        public WindowedListBuilder pager(Function<FloatingMenuPage, UiNode> value) { pager = Objects.requireNonNull(value); return this; }
        /** Called after a successful layout is published, never during measurement. */
        public WindowedListBuilder observeWindow(Object identity, java.util.function.Consumer<FloatingMenuPage> visible) {
            observation = java.util.Optional.of(new UiWindowedList.Observation(identity,visible)); return this;
        }
        @Override public UiWindowedList build() {
            IntConsumer selected = select;
            return new UiWindowedList(key, header, itemCount, rowTemplate, item, gap, page,
                    pager == null ? value -> UiTemplates.pager(value, selected) : pager, selected, style, sizing,anchorIndex,observation);
        }
    }

    public static final class PagedFlowBuilder extends Builder<PagedFlowBuilder> {
        private final int rows;
        private List<UiChild> items = List.of();
        private UiLength gap = UiLength.ui(12), rowGap = UiLength.ui(12);
        private int pageIndex;
        private Function<FloatingMenuPage, UiNode> pager;
        private IntConsumer selectPage = _ -> {};

        private PagedFlowBuilder(int rows) { this.rows = rows; }
        @Override protected PagedFlowBuilder self() { return this; }
        public PagedFlowBuilder items(List<? extends UiNode> values) {
            items = values.stream().map(UiChild::of).toList();
            return this;
        }
        public PagedFlowBuilder gaps(UiLength horizontal, UiLength vertical) { gap = horizontal; rowGap = vertical; return this; }
        public PagedFlowBuilder page(int index, IntConsumer select) { pageIndex = index; selectPage = Objects.requireNonNull(select); return this; }
        public PagedFlowBuilder pager(Function<FloatingMenuPage, UiNode> value) { pager = Objects.requireNonNull(value); return this; }
        @Override public UiPagedFlow build() {
            IntConsumer selected = selectPage;
            return new UiPagedFlow(key, items, rows, gap, rowGap, pageIndex,
                    pager == null ? value -> UiTemplates.pager(value, selected) : pager, selected, style, sizing);
        }
    }

    public static final class SpatialGroupBuilder extends Builder<SpatialGroupBuilder> {
        private final UiNode content;
        private UiTransform transform = UiTransform.IDENTITY;
        private UiPivot pivot = UiPivot.CENTER;
        private SpatialGroupBuilder(UiNode content) { this.content = Objects.requireNonNull(content); }
        @Override protected SpatialGroupBuilder self() { return this; }
        public SpatialGroupBuilder transform(UiTransform value) { transform = Objects.requireNonNull(value); return this; }
        public SpatialGroupBuilder pivot(UiPivot value) { pivot = Objects.requireNonNull(value); return this; }
        @Override public UiSpatialGroup build() { return new UiSpatialGroup(key, content, style, sizing, transform, pivot); }
    }

    public static final class ElementBuilder extends Builder<ElementBuilder> {
        private UiContent content;
        private UiRole role;
        private final EnumMap<FloatingMenuInteraction, FloatingMenuAction> actions = new EnumMap<>(FloatingMenuInteraction.class);
        private boolean selected;
        private Component disabledReason;
        private UiTooltip tooltip;
        private boolean keepAccessible;
        private int textWidth = 160;
        private FloatingMenuDecoration.Alignment alignment = FloatingMenuDecoration.Alignment.CENTER;

        private ElementBuilder(UiContent content, UiRole role) {
            this.content = content; this.role = role;
            if (role.supportsActions()) style = UiStyle.CONTROL;
        }
        @Override protected ElementBuilder self() { return this; }
        public ElementBuilder role(UiRole role) { this.role = Objects.requireNonNull(role, "role"); return this; }
        public ElementBuilder selected(boolean selected) {
            if (!role.supportsActions()) throw new IllegalStateException("Read-only content cannot be selected");
            this.selected = selected; role = UiRole.CHOICE; return this;
        }
        public ElementBuilder disabled(Component reason) { disabledReason = Objects.requireNonNull(reason, "reason"); return this; }
        public ElementBuilder tooltip(Component text) { return tooltip(UiTooltip.of(text)); }
        public ElementBuilder tooltip(UiTooltip tooltip) { this.tooltip = Objects.requireNonNull(tooltip); return this; }
        public ElementBuilder tint(UiColor color) {
            if (!(content instanceof UiContent.Icon icon)) throw new IllegalStateException("Tint requires icon content");
            content = new UiContent.Icon(icon.value(), icon.label(), color);
            return this;
        }
        /** Complete text is preferred; only an overflowing composition may use this measured summary. */
        public ElementBuilder summaryLines(int lines) {
            if (!(content instanceof UiContent.Text text)) throw new IllegalStateException("Text summaries need text content");
            content = new UiContent.Text(text.label(),lines,true,lines < Integer.MAX_VALUE);
            return this;
        }
        /** Fits text to one rendered line, shortening it at the measured width when necessary. */
        public ElementBuilder singleLine() {
            if (!(content instanceof UiContent.Text text)) throw new IllegalStateException("Single-line layout needs text content");
            content = new UiContent.Text(text.label(), 1);
            if (tooltip == null) tooltip = UiTooltip.of(text.label());
            return this;
        }
        /** Complete text establishes its natural minimum width; ancestors must allocate room for it. */
        public ElementBuilder noWrap() {
            if (!(content instanceof UiContent.Text text)) throw new IllegalStateException("No-wrap layout needs text content");
            content = new UiContent.Text(text.label(), Integer.MAX_VALUE, false);
            return this;
        }
        /** Limits rendered lines at the final width; full reading can be supplied by an excerpt. */
        public ElementBuilder maxLines(int lines) {
            if (!(content instanceof UiContent.Text text)) throw new IllegalStateException("Line limits need text content");
            content = new UiContent.Text(text.label(), lines);
            return this;
        }
        public ElementBuilder textWidth(int pixels) { if (pixels < 1) throw new IllegalArgumentException("Invalid text width"); textWidth = pixels; return this; }
        public ElementBuilder alignment(FloatingMenuDecoration.Alignment value) { alignment = Objects.requireNonNull(value); return this; }
        public ElementBuilder keepAccessible() { keepAccessible = true; return this; }
        public ElementBuilder on(FloatingMenuInteraction input, FloatingMenuAction action) {
            actions.put(Objects.requireNonNull(input, "input"), Objects.requireNonNull(action, "action")); return this;
        }
        public ElementBuilder primary(FloatingMenuCommand action) { return on(FloatingMenuInteraction.PRIMARY, (p, h, _) -> action.execute(p, h)); }
        public ElementBuilder secondary(FloatingMenuCommand action) { return on(FloatingMenuInteraction.SECONDARY, (p, h, _) -> action.execute(p, h)); }
        @Override public UiElement build() {
            boolean target = role.supportsActions();
            return new UiElement(key, content, role, new UiInput(target, actions, Optional.empty(), selected,
                    Optional.ofNullable(disabledReason), Optional.ofNullable(tooltip)), style, sizing, textWidth,
                    alignment, keepAccessible);
        }
    }

    public static final class SurfaceBuilder extends Builder<SurfaceBuilder> {
        private final UiNode content;
        private UiLength elevation = UiLength.ZERO;
        private UiSurfaceFacing facing = UiSurfaceFacing.INHERIT;
        private SurfaceBuilder(UiNode content) { this.content = Objects.requireNonNull(content); style = UiStyle.PANEL; }
        @Override protected SurfaceBuilder self() { return this; }
        public SurfaceBuilder elevation(UiLength elevation) { this.elevation = Objects.requireNonNull(elevation); return this; }
        public SurfaceBuilder facing(UiSurfaceFacing facing) { this.facing = Objects.requireNonNull(facing); return this; }
        @Override public UiSurface build() { return new UiSurface(key, content, style, sizing, elevation, facing); }
    }

    public static class ContainerBuilder extends Builder<ContainerBuilder> {
        protected UiLayout layout;
        protected final List<UiChild> children = new ArrayList<>();
        protected ContainerBuilder(UiLayout layout) { this.layout = Objects.requireNonNull(layout, "layout"); }
        @Override protected ContainerBuilder self() { return this; }
        protected UiKey childKey(UiNode child) { return child.key().equals(UiKey.AUTO) ? UiKey.named("$" + children.size()) : child.key(); }
        public ContainerBuilder child(UiNode child) { return child(child, LinearSlot.CONTENT); }
        public ContainerBuilder child(UiNode child, LinearSlot slot) {
            children.add(UiChild.linear(childKey(child), child, slot)); return this;
        }
        public ContainerBuilder child(UiKey key, UiNode child) { children.add(UiChild.linear(key, child, LinearSlot.CONTENT)); return this; }
        public ContainerBuilder child(UiKey key, UiNode child, LinearSlot slot) { children.add(UiChild.linear(key, child, slot)); return this; }
        public ContainerBuilder children(List<? extends UiNode> nodes) { nodes.forEach(this::child); return this; }
        @Override public UiContainer build() { return new UiContainer(key, layout, children, style, sizing); }
    }

    public static final class LinearBuilder extends Builder<LinearBuilder> {
        private final boolean horizontal;
        private UiLength gap = UiLength.ui(8);
        private final List<UiChild> children = new ArrayList<>();
        private LinearBuilder(boolean horizontal) { this.horizontal = horizontal; }
        @Override protected LinearBuilder self() { return this; }
        public LinearBuilder gap(UiLength gap) { this.gap = Objects.requireNonNull(gap, "gap"); return this; }
        public LinearBuilder child(UiNode node) { return child(node, LinearSlot.CONTENT); }
        public LinearBuilder child(UiNode node, LinearSlot slot) {
            UiKey key = node.key().equals(UiKey.AUTO) ? UiKey.named("$" + children.size()) : node.key();
            return child(key, node, slot);
        }
        public LinearBuilder child(UiKey key, UiNode node) { return child(key, node, LinearSlot.CONTENT); }
        public LinearBuilder child(UiKey key, UiNode node, LinearSlot slot) { children.add(UiChild.linear(key, node, slot)); return this; }
        public LinearBuilder children(List<? extends UiNode> nodes) { nodes.forEach(this::child); return this; }
        @Override public UiContainer build() { return new UiContainer(key, horizontal ? UiLayouts.row(gap) : UiLayouts.column(gap), children, style, sizing); }
    }

    public static final class GridBuilder extends Builder<GridBuilder> {
        private final int columns;
        private UiLength columnGap = UiLength.ui(8), rowGap = UiLength.ui(8);
        private final List<UiChild> children = new ArrayList<>();
        private boolean adaptive;
        private UiLayouts.GridAlignment horizontalAlignment = UiLayouts.GridAlignment.START;
        private GridBuilder(int columns) { if (columns < 1) throw new IllegalArgumentException("Invalid column count"); this.columns = columns; }
        @Override protected GridBuilder self() { return this; }
        public GridBuilder gap(UiLength value) { columnGap = rowGap = Objects.requireNonNull(value, "gap"); return this; }
        public GridBuilder gaps(UiLength horizontal, UiLength vertical) { columnGap = Objects.requireNonNull(horizontal); rowGap = Objects.requireNonNull(vertical); return this; }
        public GridBuilder adaptiveColumns() { adaptive = true; return this; }
        public GridBuilder horizontalAlignment(UiLayouts.GridAlignment value) {
            horizontalAlignment = Objects.requireNonNull(value, "horizontalAlignment"); return this;
        }
        public GridBuilder child(UiNode node) {
            UiKey key = node.key().equals(UiKey.AUTO) ? UiKey.named("$" + children.size()) : node.key();
            children.add(new UiChild(key, node, LinearSlot.CONTENT, Optional.empty())); return this;
        }
        public GridBuilder child(UiNode node, GridSlot slot) {
            UiKey key = node.key().equals(UiKey.AUTO) ? UiKey.named("$" + children.size()) : node.key();
            children.add(new UiChild(key, node, LinearSlot.CONTENT, Optional.of(slot))); return this;
        }
        @Override public UiContainer build() {
            if (adaptive && children.stream().anyMatch(child -> child.grid().isPresent()))
                throw new IllegalArgumentException("Adaptive grids require automatic cells; use layout alternatives for explicit spans");
            return new UiContainer(key, adaptive ? UiLayouts.adaptiveGrid(columns, columnGap, rowGap, horizontalAlignment)
                    : UiLayouts.grid(columns, columnGap, rowGap, horizontalAlignment), children, style, sizing);
        }
    }
}
