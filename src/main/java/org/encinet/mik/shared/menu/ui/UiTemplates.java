package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import java.util.List;

/** Structural recipes for pages assembled through named content ports. */
public final class UiTemplates {
    private UiTemplates() { }

    public static UiContainer menu(UiNode... sections) {
        return Ui.column().gap(UiLength.ui(12)).children(List.of(sections)).build();
    }

    /** Controlled expansion: hidden content is neither constructed, measured nor indexed for input. */
    public static UiContainer disclosure(UiKey key, UiNode summary, UiElement toggle, boolean expanded,
                                         java.util.function.Supplier<? extends UiNode> body) {
        java.util.Objects.requireNonNull(body);
        if (!toggle.role().supportsActions()) throw new IllegalArgumentException("A disclosure needs an action control");
        var group = Ui.column().key(key).gap(UiLength.ui(8))
                .child(UiKey.named("header"), actionRow(summary, toggle));
        if (expanded) group.child(UiKey.named("body"), java.util.Objects.requireNonNull(body.get()));
        return group.build();
    }

    /** Reading content on the left, related actions on the right; narrow hosts stack the two regions. */
    public static UiContainer actionRow(UiNode content, UiNode actions) {
        double minimum = Math.max(content == null ? 0 : content.sizing().minimum().width(),
                actions == null ? 0 : actions.sizing().minimum().width());
        var row = Ui.layout(new UiActionRowLayout()).key("action-row").sizing(UiSizing.atLeast(new UiSize(minimum, 0)));
        if (content != null) row.child(UiKey.named("content"), content, new LinearSlot(1, LinearSlot.Alignment.START));
        if (actions != null) row.child(UiKey.named("actions"), actions, new LinearSlot(0, LinearSlot.Alignment.END));
        return row.build();
    }

    /** A panel-centered title with trailing tools; narrow hosts keep the tools on a separate row. */
    public static UiContainer titleBar(UiNode title, UiNode actions) {
        var bar = Ui.layout(new UiCenteredBarLayout(UiKey.named("title"), UiKey.named("leading"), UiKey.named("actions")))
                .key("title-bar").child(UiKey.named("title"), title);
        if (actions != null) bar.child(UiKey.named("actions"), actions);
        return bar.build();
    }

    /** Center content on the whole bar, independently of optional and unequal edge groups. */
    public static UiContainer centeredBar(UiNode center, UiNode leading, UiNode trailing) {
        return centeredBar("centered-bar", center, leading, trailing);
    }

    private static UiContainer centeredBar(String key, UiNode center, UiNode leading, UiNode trailing) {
        var bar = Ui.layout(new UiCenteredBarLayout(UiKey.named("center"), UiKey.named("leading"), UiKey.named("trailing")))
                .key(key).child(UiKey.named("center"), center);
        if (leading != null) bar.child(UiKey.named("leading"), leading);
        if (trailing != null) bar.child(UiKey.named("trailing"), trailing);
        return bar.build();
    }

    /** One title row for light companions; its owner supplies the scoped dismissal command. */
    public static UiNode popoverHeader(Component title, UiNode actions, Component closeLabel,
                                       org.encinet.mik.shared.menu.FloatingMenuCommand close) {
        var tools = Ui.row().key("header-actions").gap(UiLength.ui(6));
        if (actions != null) tools.child(actions);
        tools.child(Ui.button(UiIcons.CROSS, closeLabel).key("close").primary(close).build());
        return titleBar(Ui.heading(title).key("title").summaryLines(1).build(), tools.build());
    }

    public static UiPort heading(String id) {
        return new UiPort(UiKey.named(id), UiLayouts.column(UiLength.ui(4)), UiTheme.HEADING,
                UiSizing.CONTENT, List.of(id), UiRole.HEADING);
    }

    public static UiPort title(String id) {
        return new UiPort(UiKey.named(id), UiLayouts.column(UiLength.ui(4)), UiTheme.TITLE,
                UiSizing.CONTENT, List.of(id), UiRole.HEADING);
    }

    /** A single aligned column whose controls share the widest item's allocated width. */
    public static UiPort list(String id) {
        return new UiPort(UiKey.named(id), UiLayouts.column(UiLength.ui(8)), UiStyle.PLAIN, UiSizing.CONTENT)
                .withItemSlot(LinearSlot.stretch());
    }

    public static UiPort information(String id) { return Ui.port(id, UiLayouts.column(UiLength.ui(6))); }
    public static UiPort actions(String id, int columns) {
        return Ui.port(id, UiLayouts.adaptiveGrid(columns, UiLength.ui(10), UiLength.ui(8), UiLayouts.GridAlignment.CENTER));
    }
    public static UiPagedPort pagedActions(String id, int rows, int page, java.util.function.IntConsumer select) {
        return new UiPagedPort(Ui.port(id, UiLayouts.wrap(UiLength.ui(10), UiLength.ui(8))), rows, page, select);
    }
    public static UiPagedLayoutPort pagedActions(String id, UiLayout layout, int page, java.util.function.IntConsumer select) {
        return new UiPagedLayoutPort(Ui.port(id, layout), page, select, false);
    }
    public static UiPort navigation(String id) { return Ui.port(id, UiLayouts.wrap(UiLength.ui(10), UiLength.ui(8))); }
    /** Owns the conventional nodes declared by MenuScene.Builder.pagination, including absent edge buttons. */
    public static UiNode pagination(String id) {
        return Ui.port(id, new UiCenteredBarLayout(UiKey.item("page"), UiKey.item("page:previous"), UiKey.item("page:next")));
    }
    public static UiNode actions(int columns) {
        return all(UiLayouts.adaptiveGrid(columns, UiLength.ui(10), UiLength.ui(8), UiLayouts.GridAlignment.CENTER));
    }
    public static UiNode adaptiveGrid(int columns, UiLength horizontalGap, UiLength verticalGap) {
        return all(UiLayouts.adaptiveGrid(columns, horizontalGap, verticalGap));
    }
    public static UiNode adaptiveRow(UiLength spacing) { return all(UiLayouts.row(spacing)); }
    public static UiNode canvas(UiSize viewport, java.util.Map<String, UiLayout.Position> centers) {
        java.util.Map<UiKey, UiLayout.Position> positions = new java.util.LinkedHashMap<>();
        centers.forEach((key, value) -> positions.put(UiKey.named(key), value));
        return all(UiLayouts.canvas(viewport, positions));
    }
    public static UiNode adaptiveColumn(UiLength spacing) { return all(UiLayouts.column(spacing)); }
    public static UiPort cards(String id, int columns, int maximumRows) {
        if (columns < 1 || maximumRows < 1) throw new IllegalArgumentException("Invalid card capacity");
        return new UiPort(UiKey.named(id), (scope, constraints) -> {
            if (scope.count() > (long) columns * maximumRows) throw new UiOverflowException("Card page exceeds its capacity");
            return UiLayouts.adaptiveGrid(columns, UiLength.ui(10), UiLength.ui(8)).layout(scope, constraints);
        }, UiStyle.PLAIN, UiSizing.CONTENT);
    }
    public static UiNode balancedCards(String id, int maximumColumns, int maximumRows) {
        return new UiPort(UiKey.named(id), (scope, constraints) -> {
            if (maximumColumns < 1 || maximumRows < 1) throw new IllegalArgumentException("Invalid card capacity");
            int columns = Math.clamp((scope.count() + maximumRows - 1) / maximumRows, 1, maximumColumns);
            return UiLayouts.adaptiveGrid(columns, UiLength.ui(10), UiLength.ui(8)).layout(scope, constraints);
        }, UiStyle.PLAIN, UiSizing.CONTENT);
    }
    public static UiNode slot(String id, UiNode template) {
        if (!(template instanceof UiPort port)) throw new IllegalArgumentException("A named port needs an independent child layout");
        return new UiPort(UiKey.named(id), port.layout(), port.style(), port.sizing(), List.of(id), port.textRole(), port.itemSlot(),port.summaryLines());
    }

    public static UiSurface panel(String id, UiNode content, String... sources) {
        UiTree.check(content);
        UiNode child = content;
        if (content instanceof UiPort port && port.sources().contains("*") && sources.length > 0) {
            child = new UiPort(UiKey.named("body"), port.layout(), UiStyle.PLAIN, UiSizing.CONTENT,
                    List.of(sources), port.textRole(), port.itemSlot(),port.summaryLines());
        } else if (sources.length > 0) {
            java.util.Set<String> declared = new java.util.LinkedHashSet<>();
            sources(content, declared);
            if (!declared.equals(new java.util.LinkedHashSet<>(List.of(sources))))
                throw new IllegalArgumentException("Panel source list does not match its content: " + id);
        }
        return Ui.surface(child).key(id).facing(UiSurfaceFacing.VIEWER).build();
    }
    private static void sources(UiNode node, java.util.Set<String> result) {
        UiTree.visit(node, (_, child) -> {
            if (child instanceof UiPort port) result.addAll(port.sources());
            else if (child instanceof UiPagedPort port) result.addAll(port.content().sources());
            else if (child instanceof UiPagedLayoutPort port) result.addAll(port.content().sources());
            else if (child instanceof UiDocumentPort port) result.addAll(port.content().sources());
            else if (child instanceof UiAlternative alternative) {
                var preferred = new java.util.LinkedHashSet<String>();
                var fallback = new java.util.LinkedHashSet<String>();
                sources(alternative.preferred(),preferred); sources(alternative.fallback(),fallback);
                if (!preferred.equals(fallback)) throw new IllegalArgumentException("Panel alternatives need the same content sources");
                result.addAll(preferred);
            }
        });
    }

    public static UiSurface window(String id, UiNode content) {
        return Ui.surface(content).key(id).style(UiTheme.WINDOW).facing(UiSurfaceFacing.VIEWER).build();
    }

    public static UiSurface sidebar(String id, UiNode content) {
        return Ui.surface(content).key(id).style(UiTheme.SIDEBAR).facing(UiSurfaceFacing.HORIZONTAL).build();
    }

    public static UiSurface toolbar(String id, UiNode content) {
        return Ui.surface(content).key(id).style(UiTheme.TOOLBAR).facing(UiSurfaceFacing.VERTICAL)
                .elevation(UiLength.ui(4)).build();
    }

    /** Content and its controls remain separate planes; the reading focus stays in the content. */
    public static UiNode sheet(UiNode content, UiNode tools) {
        return focus(Ui.layout(UiLayouts.contentColumn(UiKey.named("body"), UiLength.ui(12))).key("sheet")
                .child(UiKey.named("body"), content)
                .child(UiKey.named("tools"), tools).build(), UiKey.named("body"));
    }

    /** Adds the standard return action when otherwise embeddable content is opened on its own. */
    public static UiNode withBack(UiNode content, Component label) {
        return sheet(content, toolbar("navigation", Ui.button(label).key("back").role(UiRole.NAVIGATION)
                .primary((_, handle) -> handle.back()).build()));
    }

    /** Compact collection navigation; a collection that fits has no redundant controls. */
    public static UiNode pager(org.encinet.mik.shared.menu.FloatingMenuPage page, java.util.function.IntConsumer select) {
        if (page.count() <= 1) return new UiPager(Ui.column().key("pagination").build(), page, select);
        return pager(page, Ui.button(UiIcons.CHEVRON_LEFT, Component.translatable("spectatorMenu.previous_page")),
                Component.text((page.index() + 1) + " / " + page.count()),
                Ui.button(UiIcons.CHEVRON_RIGHT, Component.translatable("spectatorMenu.next_page")), select);
    }

    /** Localized text controls and a page summary use the same centered geometry as the compact icon pager. */
    public static UiNode pager(org.encinet.mik.shared.menu.FloatingMenuPage page, Component previous,
                               Component summary, Component next, java.util.function.IntConsumer select) {
        return pager(page, Ui.button(previous), summary, Ui.button(next), select);
    }

    private static UiNode pager(org.encinet.mik.shared.menu.FloatingMenuPage page, Ui.ElementBuilder previous,
                                Component summary, Ui.ElementBuilder next, java.util.function.IntConsumer select) {
        java.util.Objects.requireNonNull(select);
        UiNode before = page.hasPrevious() ? previous.key("previous")
                .primary((_, _) -> select.accept(page.previous().index())).build() : null;
        UiNode after = page.hasNext() ? next.key("next")
                .primary((_, _) -> select.accept(page.next().index())).build() : null;
        var controls = Ui.layout(new UiCenteredBarLayout(UiKey.named("center"), UiKey.named("leading"), UiKey.named("trailing"), true))
                .key("pagination").child(UiKey.named("center"), Ui.text(summary).key("page").summaryLines(1).tooltip(summary).build());
        if (before != null) controls.child(UiKey.named("leading"), before);
        if (after != null) controls.child(UiKey.named("trailing"), after);
        return new UiPager(controls.build(), page, select);
    }

    /** Two-column workspace, falling back to a vertical arrangement without losing either region. */
    public static UiNode split(UiNode sidebar, UiNode content) {
        return split(sidebar,content,UiLayouts.Axis.HORIZONTAL);
    }

    /** Prefer a reading direction, falling back only when the complete composition does not fit. */
    public static UiNode split(UiNode sidebar, UiNode content, UiLayouts.Axis preferred) {
        return focus(Ui.layout(new UiSplitLayout(preferred)).key("split")
                .child(UiKey.named("sidebar"),sidebar,new LinearSlot(0,LinearSlot.Alignment.START))
                .child(UiKey.named("main"),content,new LinearSlot(0,LinearSlot.Alignment.START)).build(),UiKey.named("main"));
    }

    public static UiNode workspace(UiNode navigation, UiNode content, UiNode tools) {
        return workspace(navigation, sheet(window("content", content), toolbar("actions", tools)));
    }

    /** A stable reading region with a bounded, content-sized category rail and feature-owned content. */
    public static UiNode workspace(UiNode navigation, UiNode content) {
        UiNode rail = sidebar("navigation", navigation)
                .withSizing(UiSizing.width(UiLength.ZERO, UiLength.ui(240)));
        UiContainer row = Ui.layout(UiLayouts.contentRow(UiKey.named("main"),UiLength.ui(16))).key("workspace")
                .child(UiKey.named("sidebar"), rail, new LinearSlot(0, LinearSlot.Alignment.START))
                .child(UiKey.named("main"), content, new LinearSlot(0, LinearSlot.Alignment.START)).build();
        return focus(row, UiKey.named("main"));
    }

    public static UiNode horizontalPanels(UiLength spacing, UiNode... panels) {
        return Ui.row().gap(spacing).children(List.of(panels)).build();
    }
    public static UiNode topAlignedPanels(UiLength spacing, UiNode... panels) {
        var row = Ui.row().gap(spacing);
        for (UiNode panel : panels) row.child(panel, new LinearSlot(0, LinearSlot.Alignment.START));
        return row.build();
    }
    public static UiNode verticalPanels(UiLength spacing, UiNode... panels) {
        return Ui.column().gap(spacing).children(List.of(panels)).build();
    }
    public static UiNode panoramicPanels(String centerPanel, UiLength spacing, UiNode... panels) {
        if (java.util.Arrays.stream(panels).noneMatch(panel -> panel.key().value().equals(centerPanel)))
            throw new IllegalArgumentException("Missing central panel: " + centerPanel);
        UiContainer row = new UiContainer(UiKey.AUTO, UiLayouts.row(spacing), java.util.Arrays.stream(panels)
                .map(panel -> UiChild.linear(panel.key(), panel, new LinearSlot(0, LinearSlot.Alignment.CENTER))).toList(),
                UiStyle.PLAIN, UiSizing.CONTENT);
        return focus(row, UiKey.named(centerPanel));
    }

    /** Persistent sections are measured before the designated reading content. */
    public static UiContainer reading(UiKey content, UiNode... sections) {
        return focus(Ui.layout(UiLayouts.contentColumn(content,UiLength.ui(12)))
                .children(List.of(sections)).build(),content);
    }

    /** Applies the reading-column policy after a feature has chosen its content region. */
    public static UiContainer reading(UiContainer sections, UiKey content, UiLength gap) {
        return focus(new UiContainer(sections.key(),UiLayouts.contentColumn(content,gap),
                sections.children(),sections.style(),sections.sizing()),content);
    }

    /** The focus of a nested viewport propagates through its parent without positional assumptions. */
    public static UiContainer focus(UiContainer container, UiKey target) {
        if (container.children().stream().noneMatch(child -> child.scope().equals(target)))
            throw new IllegalArgumentException("Missing reading region: " + target);
        return new UiContainer(container.key(), new UiFocusLayout(container.layout(), target),
                container.children(), container.style(), container.sizing());
    }

    /** An explicit model viewport surrounded by header, inspector, controls and footer. */
    public static UiNode workbench(UiSize modelSize, UiNode inspector, UiNode header, UiNode controls, UiNode footer) {
        return workbench(Ui.stack().key("model-space").size(modelSize).build(), inspector, header, controls, footer);
    }

    /** The model viewport can own a declared component subtree or reserve space for a feature's world preview. */
    public static UiNode workbench(UiNode viewport, UiNode inspector, UiNode header, UiNode controls, UiNode footer) {
        UiLayout layout = (scope, constraints) -> {
            try { return UiLayouts.grid(3, UiLength.ui(16), UiLength.ui(12)).layout(scope, constraints); }
            catch (UiOverflowException error) { return UiLayouts.column(UiLength.ui(12)).layout(scope, constraints); }
        };
        // Parent-specific grid parameters belong to the parent, while all cells remain ordinary component trees.
        java.util.List<UiChild> children = java.util.List.of(
                UiChild.grid(UiKey.named("header"), header, GridSlot.cell(0, 0).withColumnSpan(3)),
                UiChild.grid(UiKey.named("inspector"), inspector, GridSlot.cell(1, 0)),
                UiChild.grid(UiKey.named("model-space"), viewport, GridSlot.cell(1, 1)),
                UiChild.grid(UiKey.named("controls"), controls, GridSlot.cell(1, 2)),
                UiChild.grid(UiKey.named("footer"), footer, GridSlot.cell(2, 0).withColumnSpan(3)));
        return focus(new UiContainer(UiKey.named("workbench"), layout, children, UiStyle.PLAIN, UiSizing.CONTENT), UiKey.named("model-space"));
    }
    private static UiNode all(UiLayout layout) {
        return new UiPort(UiKey.named("content"), layout, UiStyle.PLAIN, UiSizing.CONTENT, List.of("*"), UiRole.TEXT);
    }
}
