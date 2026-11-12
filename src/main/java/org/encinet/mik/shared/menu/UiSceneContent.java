package org.encinet.mik.shared.menu;

import org.encinet.mik.shared.menu.ui.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Projects logical components into a render index without making entity ids part of their identity. */
public final class UiSceneContent {
    public static final double WORLD_PER_UI = 0.02;
    private UiSceneContent() { }

    public static UiNode resolve(UiNode root, Map<String, MenuScene.Entry> declarations) {
        UiTree.check(root);
        Set<String> used = new HashSet<>();
        UiNode resolved = resolve(root, declarations, used);
        UiTree.check(resolved);
        if (used.size() != declarations.size()) {
            Set<String> missing = new java.util.LinkedHashSet<>(declarations.keySet());
            missing.removeAll(used);
            throw new IllegalArgumentException("Content has no owning component: " + missing);
        }
        return resolved;
    }

    private static UiNode resolve(UiNode node, Map<String, MenuScene.Entry> declarations, Set<String> used) {
        if (node instanceof UiAlternative alternative) {
            var preferredOwners = new HashSet<>(used);
            var fallbackOwners = new HashSet<>(used);
            UiNode preferred = resolve(alternative.preferred(),declarations,preferredOwners);
            UiNode fallback = resolve(alternative.fallback(),declarations,fallbackOwners);
            if (!preferredOwners.equals(fallbackOwners)) throw new IllegalArgumentException("Alternative representations must own the same declarations");
            used.addAll(preferredOwners);
            return Ui.alternative(preferred,fallback);
        }
        if (node instanceof UiDocumentPort document) {
            var content = (UiContainer) resolve(document.content(),declarations,used);
            return new UiDocument(content.key(),Ui.column().build(),content.children(),UiLength.ui(8),document.position(),
                    document.selectPosition(),content.style(),content.sizing(),UiTemplates::pager,document.bodyReading());
        }
        if (node instanceof UiPagedLayoutPort paged) {
            var content = (UiContainer) resolve(paged.content(), declarations, used);
            var body = new UiContainer(content.key(), content.layout(), pageChildren(content, paged.compactModels()), content.style(), content.sizing());
            return new UiPagedLayout(body, UiLength.ui(10), paged.pageIndex(),
                    page -> UiTemplates.pager(page, paged.selectPage()), paged.selectPage());
        }
        if (node instanceof UiPagedPort flow) {
            var content = (UiContainer) resolve(flow.content(), declarations, used);
            var children = pageChildren(content, flow.compactModels());
            return new UiPagedFlow(content.key(), children, flow.rows(), UiLength.ui(10), UiLength.ui(8),
                    flow.pageIndex(), page -> UiTemplates.pager(page, flow.selectPage()), flow.selectPage(), content.style(), content.sizing());
        }
        if (node instanceof UiPort port) {
            List<UiChild> children = new ArrayList<>();
            for (MenuScene.Entry entry : declarations.values()) {
                if (!port.sources().contains("*") && !port.sources().contains(entry.region())) continue;
                if (!used.add(entry.id())) throw new IllegalArgumentException("Content has multiple owners: " + entry.id());
                UiElement element = element(entry, port.itemTextWidth());
                if (element.content() instanceof UiContent.Text && port.summaryLines() < Integer.MAX_VALUE)
                    element = element.withSummaryLines(port.summaryLines());
                if (element.role() == UiRole.TEXT && port.textRole() == UiRole.HEADING) element = element.withRole(UiRole.HEADING);
                children.add(UiChild.linear(element.key(), element, port.itemSlot()));
            }
            return new UiContainer(port.key(), port.layout(), children, port.style(), port.sizing());
        }
        if (node instanceof UiPagedTable table) {
            return new UiPagedTable((UiContainer) resolve(table.table(), declarations, used), table.rowKeys(),
                    table.pageIndex(), table.selectPage());
        }
        if (node instanceof UiWindowedTable table) {
            var source = table.template();
            int headers = table.children().size();
            var cells = new ArrayList<UiChild>(source.children());
            for (int index = 0; index < headers; index++) {
                var cell = cells.get(index);
                cells.set(index,new UiChild(cell.scope(),resolve(cell.node(),declarations,used),cell.linear(),cell.grid()));
            }
            var template = new UiContainer(source.key(),source.layout(),cells,source.style(),source.sizing());
            return new UiWindowedTable(template,table.itemCount(),table.row(),table.pageIndex(),table.selectPage(),table.anchorIndex(),table.pager(),table.visibleColumns());
        }
        if (node instanceof UiPagedList list) {
            return new UiPagedList(list.key(),resolve(list.header(),declarations,used),list.items().stream()
                    .map(child -> new UiChild(child.scope(),resolve(child.node(),declarations,used),child.linear(),child.grid())).toList(),
                    list.gap(),list.pageIndex(),list.pager(),list.selectPage(),list.style(),list.sizing());
        }
        if (node instanceof UiDocument document) {
            return new UiDocument(document.key(), resolve(document.header(), declarations, used), document.blocks().stream()
                    .map(child -> new UiChild(child.scope(), resolve(child.node(), declarations, used), child.linear(), child.grid())).toList(),
                    document.gap(), document.position(), document.selectPosition(), document.style(), document.sizing(), document.pager(), document.bodyReading());
        }
        if (node instanceof UiWindowedGrid grid) {
            return new UiWindowedGrid((UiWindowedList) resolve(grid.items(),declarations,used),
                    grid.maximumColumns(),grid.minimumColumnWidth(),grid.columnGap(),grid.centered());
        }
        if (node instanceof UiWindowedList list) {
            // Deferred rows own typed content, never legacy declaration ports.
            return new UiWindowedList(list.key(), resolve(list.header(), declarations, used), list.itemCount(),
                    list.rowTemplate(), list.item(), list.gap(), list.pageIndex(), list.pager(), list.selectPage(), list.style(), list.sizing(),list.anchorIndex(),list.observation());
        }
        if (node instanceof UiPagedLayout paged) {
            return new UiPagedLayout((UiContainer) resolve(paged.content(), declarations, used), paged.gap(),
                    paged.pageIndex(), paged.pager(), paged.selectPage());
        }
        if (node instanceof UiPagedFlow flow) {
            return new UiPagedFlow(flow.key(), flow.items().stream()
                    .map(child -> new UiChild(child.scope(), resolve(child.node(), declarations, used), child.linear(), child.grid())).toList(),
                    flow.rows(), flow.gap(), flow.rowGap(), flow.pageIndex(), flow.pager(), flow.selectPage(), flow.style(), flow.sizing());
        }
        if (node instanceof UiSurface surface) {
            return new UiSurface(surface.key(), resolve(surface.content(), declarations, used), surface.style(), surface.sizing(), surface.elevation(), surface.facing(), surface.stretchContent());
        }
        if (node instanceof UiSpatialGroup group) {
            return new UiSpatialGroup(group.key(), resolve(group.content(), declarations, used), group.style(), group.sizing(), group.transform(), group.pivot());
        }
        if (node instanceof UiPager pager) {
            return new UiPager((UiContainer) resolve(pager.controls(), declarations, used), pager.page(), pager.selectPage());
        }
        if (node instanceof UiContainer container) {
            return new UiContainer(container.key(), container.layout(), container.children().stream()
                    .map(child -> new UiChild(child.scope(), resolve(child.node(), declarations, used), child.linear(), child.grid()))
                    .toList(),
                    container.style(), container.sizing());
        }
        return node;
    }

    private static List<UiChild> pageChildren(UiContainer content, boolean compactModels) {
        return content.children().stream().map(child -> {
            if (!compactModels || !(child.node() instanceof UiElement element) || !(element.content() instanceof UiContent.Item)) return child;
            UiElement compact = new UiElement(element.key(), new UiContent.Text(element.content().label(), 2), element.role(), element.input(),
                    element.style(), element.sizing(), element.textWidthPixels(), element.alignment(), element.keepAccessible());
            return new UiChild(child.scope(), Ui.alternative(element, compact), child.linear(), child.grid());
        }).toList();
    }

    private static UiElement element(MenuScene.Entry entry, FloatingMenuTextWidth inheritedTextWidth) {
        UiContent content = entry.item() == null ? new UiContent.Text(entry.label())
                : new UiContent.Item(entry.item(), entry.label(), entry.style() == FloatingMenuElementStyle.BLOCK);
        UiRole role = entry.role();
        FloatingMenuTextWidth textWidth = entry.textWidth() == FloatingMenuTextWidth.AUTO
                ? inheritedTextWidth : entry.textWidth();
        return new UiElement(UiKey.item(entry.id()), content, role,
                new UiInput(entry.interactive(), entry.triggers(), Optional.ofNullable(entry.focusAction()), entry.selected(),
                        Optional.ofNullable(entry.disabledReason()), Optional.ofNullable(entry.tooltip())),
                entry.interactive() ? UiStyle.CONTROL : UiStyle.PLAIN, UiSizing.CONTENT,
                textWidth.resolve(entry.interactive() ? 128 : 160), entry.alignment(), entry.keepAccessible());
    }

    public static Map<String, UiElement> elements(UiNode root) {
        Map<String, UiElement> result = new LinkedHashMap<>();
        UiTree.visit(root, (path, node) -> {
            if (node instanceof UiElement element) result.put(path, element);
            else if (node instanceof UiPort || node instanceof UiPagedPort || node instanceof UiPagedLayoutPort || node instanceof UiDocumentPort) throw new IllegalArgumentException("Unresolved UI content port at " + path);
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    public static Map<String, MenuScene.Entry> entries(UiNode root) {
        Map<String, MenuScene.Entry> result = new LinkedHashMap<>();
        elements(root).forEach((path, element) -> {
            if (!(element.content() instanceof UiContent.Image)) result.put(path, entry(path, element));
        });
        return result;
    }

    public static MenuScene.Entry entry(String path, UiElement element) {
        UiInput input = element.input();
        FloatingMenuElementStyle style = FloatingMenuElementStyle.TEXT;
        org.bukkit.inventory.ItemStack item = null;
        if (element.content() instanceof UiContent.Item visual) {
            item = visual.stack();
            style = visual.block() ? FloatingMenuElementStyle.BLOCK : FloatingMenuElementStyle.ITEM;
        }
        return new MenuScene.Entry(path, "components", item, element.content().label(), element.role(), style,
                FloatingMenuTextWidth.AUTO, input.actions(), input.focus().orElse(null), input.interactive(),
                element.alignment(), input.selected(), input.enabled(), element.keepAccessible(),
                input.disabledReason().orElse(null), input.tooltip().orElse(null));
    }
}
