package org.encinet.mik.feature.world.heads;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.feature.world.heads.catalog.HeadCatalog;
import org.encinet.mik.feature.world.heads.catalog.HeadEntry;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.FloatingMenuDecoration.Alignment;
import org.encinet.mik.shared.menu.FloatingMenuHandle;
import org.encinet.mik.shared.menu.FloatingMenuPage;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.MenuWindowPolicy;
import org.encinet.mik.shared.menu.ui.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Presentation of the directory, filters and item details using the shared spatial components. */
final class HeadScenes {
    enum Section { CATALOG, PLAYERS, FAVORITES, LOCAL }

    record Directory(Section section, boolean hasLocal, UiNode query, List<HeadEntry> entries, int position,
                     Message notice, boolean retry, boolean stale, int filterCount, Set<String> favorites) { }
    record DirectoryActions(Consumer<Section> section, IntConsumer read, FloatingMenuCommand filters, Runnable retry,
                            HeadAction detail, HeadAction favorite) { }
    record FilterView(UiNode query, HeadCatalog.Facet facet, List<HeadCatalog.Label> labels, int position,
                      Set<String> selected, int count, HeadCatalog.Sort sort, boolean descending) { }
    record FilterActions(Consumer<HeadCatalog.Facet> facet, IntConsumer read, Consumer<String> choose,
                         Runnable sort, Runnable direction, Runnable clear) { }
    record Detail(HeadEntry entry, HeadCatalog catalog, String title, int size, boolean saved,
                  Message problem, Message favoriteProblem) { }
    @FunctionalInterface interface HeadAction { void apply(FloatingMenuHandle handle, HeadEntry entry); }

    private static final UiLength GAP = UiLength.ui(8);
    private static final UiLength CARD_GAP = UiLength.ui(7);
    private static final int GALLERY_COLUMNS = 8;
    private static final double GALLERY_VIEWPORT_ASPECT = 3.0 / 2;
    private static final UiStyle GALLERY_HEADER = UiTheme.TOOLBAR.withPadding(UiInsets.all(UiLength.ui(6)));
    private static final UiStyle COMPACT_GALLERY_PAGER = UiTheme.TOOLBAR.withPadding(new UiInsets(3, 7, 3, 7));
    private static final UiStyle GALLERY_PAGER = COMPACT_GALLERY_PAGER.withTextScale(1.22);
    private static final UiStyle GALLERY_TILE = UiStyle.CONTROL.withTextScale(1.08);
    private static final UiSizing FILTER_LIST_SIZE = new UiSizing(UiSize.ZERO, new UiSize(680, 350));
    private static final UiLength DETAIL_MODEL = UiLength.ui(104);
    private static final UiLength COMPACT_DETAIL_MODEL = UiLength.ui(64);
    private static final UiStyle PREVIEW_SURFACE = UiStyle.PANEL.withBackground(UiColor.themed(UiColorRole.INPUT));
    private record CardSize(UiLength width, UiLength model) { }
    private static final CardSize GALLERY_CARD = new CardSize(UiLength.ui(124), UiLength.ui(50));
    private static final CardSize COMPACT_CARD = new CardSize(UiLength.ui(108), UiLength.ui(20));
    private static final CardSize TEXT_CARD = new CardSize(GALLERY_CARD.width(), UiLength.ZERO);

    private final LanguageService languages;
    private final Player player;

    HeadScenes(LanguageService languages, Player player) {
        this.languages = languages;
        this.player = player;
    }

    MenuScene.Builder directory(Directory model, DirectoryActions actions) {
        var sections = new ArrayList<UiNode>();
        for (Section section : Section.values()) {
            if (section != Section.LOCAL || model.hasLocal()) {
                sections.add(Ui.button(label(Message.HEADS_SECTION, key(section))).singleLine().key(key(section))
                        .selected(section == model.section()).primary((_, _) -> actions.section().accept(section)).build());
            }
        }
        var title = Ui.heading(label(Message.HEADS_TITLE).decorate(TextDecoration.BOLD)).key("title")
                .style(UiTheme.TITLE.withTextScale(1.42)).singleLine().alignment(Alignment.LEFT).build();
        var back = Ui.button(UiIcons.CHEVRON_LEFT, label(Message.BACK)).key("back").role(UiRole.NAVIGATION)
                .sizing(UiSizing.atLeast(new UiSize(38, 26))).primary((_, handle) -> handle.back()).build();
        var identity = Ui.row().key("directory-title").gap(GAP).child(back).child(title).build();
        var navigation = UiTemplates.actionRow(identity, wrap("sections").children(sections).build());
        UiNode tools = null;
        if (model.section() == Section.CATALOG || model.section() == Section.LOCAL) {
            tools = Ui.button(label(Message.HEADS_FILTERS, model.filterCount())).key("filters").singleLine()
                    .selected(model.filterCount() > 0).primary(actions.filters()).build();
        }
        var search = UiTemplates.actionRow(model.query(), tools);
        var controls = directoryHeader(navigation, search, model.stale());
        var gap = UiLength.ui(12);
        UiNode content;
        if (model.entries().isEmpty()) {
            var body = Ui.surface(directoryStatus(model, actions)).key("directory-empty").style(UiTheme.WINDOW).build();
            content = headerBody(controls, body, gap);
        } else {
            var compactCards = gallery(model, actions, COMPACT_CARD);
            var regular = headerBody(controls, Ui.alternative(gallery(model, actions, GALLERY_CARD).atLeastRows(2), compactCards), gap);
            var compactNavigation = Ui.layout(UiLayouts.grid(sections.size() + 1, GAP, UiLength.ZERO))
                    .key("sections").child(back).children(sections).build();
            var compactControls = directoryHeader(compactNavigation, search, model.stale());
            var compact = headerBody(compactControls, Ui.alternative(compactCards, gallery(model, actions, TEXT_CARD)), gap);
            content = Ui.alternative(regular, compact);
        }
        return MenuScene.screen("head-library", label(Message.HEADS_TITLE)).withoutTitle()
                .animation(FloatingMenuAnimation.PLANAR).windowPolicy(MenuWindowPolicy.FIXED)
                .viewportAspect(GALLERY_VIEWPORT_ASPECT).content(content);
    }

    private UiNode directoryHeader(UiNode navigation, UiNode search, boolean stale) {
        var header = Ui.column().key("directory-controls").gap(UiLength.ui(5))
                .child(UiKey.named("navigation"), navigation, LinearSlot.stretch())
                .child(UiKey.named("search"), search, LinearSlot.stretch());
        if (stale) header.child(status(Message.HEADS_CACHED, NamedTextColor.YELLOW));
        return Ui.surface(header.build()).key("directory-header").style(GALLERY_HEADER).elevation(UiLength.ui(48))
                .sizing(UiSizing.width(UiLength.ZERO, UiLength.ui(620)))
                .facing(UiSurfaceFacing.FORWARD).build().stretchingContent();
    }

    private UiNode directoryStatus(Directory model, DirectoryActions actions) {
        var panel = Ui.column().key("directory-status").gap(GAP)
                .child(status(model.notice() == null ? Message.MENU_SEARCH_EMPTY : model.notice(), NamedTextColor.WHITE));
        if (model.retry()) panel.child(Ui.button(label(Message.MENU_SEARCH_RETRY)).key("retry")
                .primary((_, _) -> actions.retry().run()).build());
        return panel.build();
    }

    private UiWindowedGrid gallery(Directory model, DirectoryActions actions, CardSize size) {
        UiElement template = card(size, Component.text("M"), null).build();
        var pagerStyle = size.equals(GALLERY_CARD) ? GALLERY_PAGER : COMPACT_GALLERY_PAGER;
        int columns = Math.min(GALLERY_COLUMNS, model.entries().size());
        double width = columns * size.width().value() + (columns - 1) * CARD_GAP.value();
        var items = Ui.windowedList(model.entries().size(), template, index -> {
            HeadEntry entry = model.entries().get(index);
            Component name = Component.text(entry.name());
            boolean saved = model.favorites().contains(entry.id());
            Component caption = saved ? Component.text("★ ", NamedTextColor.GOLD).append(name) : name;
            var tooltip = new UiTooltip(label(Message.HEADS_DETAIL, "id", entry.id()).append(Component.newline())
                    .append(label(Message.HEADS_FAVORITE, saved ? "shortcut-remove" : "shortcut-add").color(NamedTextColor.YELLOW)), 180, 10)
                    .withFullText(caption);
            return card(size, caption, entry).key(UiKey.item(entry.id())).selected(saved).tooltip(tooltip)
                    .primary((_, handle) -> actions.detail().apply(handle, entry))
                    .secondary((_, handle) -> actions.favorite().apply(handle, entry)).build();
        }).key("heads").containing(model.position()).gap(CARD_GAP).sizing(UiSizing.width(UiLength.ZERO, UiLength.ui(width)))
                .pager(page -> UiTemplates.pager(page, pageSelection(page, actions.read()), pagerStyle));
        if (model.notice() != null) items.header(model.retry() ? directoryStatus(model, actions)
                : status(model.notice(), NamedTextColor.YELLOW));
        return Ui.windowedGrid(items.build(), GALLERY_COLUMNS, size.width(), CARD_GAP).centered(true)
                .folded(UiColor.themed(UiColorRole.SIDEBAR).withAlpha(0.95));
    }

    private static Ui.ElementBuilder card(CardSize size, Component caption, HeadEntry entry) {
        if (size.model().value() == 0) return Ui.button(caption).style(GALLERY_TILE).singleLine()
                .sizing(UiSizing.width(UiLength.ZERO, size.width()));
        ItemStack item = entry == null ? new ItemStack(Material.PLAYER_HEAD) : HeadItems.preview(entry);
        return Ui.item(item, caption).style(GALLERY_TILE).modelSize(size.model()).singleLine()
                .sizing(UiSizing.width(size.width(), size.width()));
    }

    MenuScene filters(FilterView model, FilterActions actions) {
        Component title = label(Message.HEADS_FILTERS, model.count());
        var tabs = wrap("facet-tabs");
        for (var facet : HeadCatalog.Facet.values()) {
            tabs.child(Ui.button(label(Message.HEADS_FACET, key(facet))).key(key(facet)).singleLine()
                    .selected(model.facet() == facet).primary((_, _) -> actions.facet().accept(facet)).build());
        }
        var ordering = wrap("ordering").child(Ui.button(label(Message.HEADS_SORT, key(model.sort())))
                .key("sort").singleLine().primary((_, _) -> actions.sort().run()).build());
        if (model.sort() != HeadCatalog.Sort.RELEVANCE) {
            ordering.child(Ui.button(label(Message.HEADS_DIRECTION, model.descending() ? "descending" : "ascending"))
                    .key("direction").singleLine().primary((_, _) -> actions.direction().run()).build());
        }
        if (model.count() > 0) {
            ordering.child(Ui.button(label(Message.HEADS_CLEAR_FILTERS)).key("clear-filters").singleLine()
                    .primary((_, _) -> actions.clear().run()).build());
        }
        var header = Ui.column().key("filter-header").gap(GAP)
                .child(Ui.heading(title).key("filter-title").singleLine().build())
                .child(tabs.build()).child(ordering.build())
                .child(UiTemplates.actionRow(model.query(), null), LinearSlot.stretch()).build();
        var list = Ui.windowedList(model.labels().size(), Ui.button(Component.text("M")).singleLine().build(), index -> {
            HeadCatalog.Label group = model.labels().get(index);
            Component name = Component.text(group.name());
            var button = Ui.button(name).key(UiKey.item(group.id())).singleLine().selected(model.selected().contains(group.id()))
                    .primary((_, _) -> actions.choose().accept(group.id()));
            if (group.description() != null && !group.description().isBlank())
                button.tooltip(name.append(Component.newline()).append(Component.text(group.description())));
            return button.build();
        }).key("facets").containing(model.position()).sizing(FILTER_LIST_SIZE)
                .pager(page -> UiTemplates.pager(page, pageSelection(page, actions.read())));
        if (model.labels().isEmpty()) list.header(status(Message.MENU_SEARCH_EMPTY, NamedTextColor.WHITE));
        UiNode body = Ui.windowedGrid(list.build(), 3, UiLength.ui(150), GAP);
        return page("head-filters", title, headerBody(header, body, GAP)).build();
    }

    MenuScene detail(Detail view, IntConsumer amount, FloatingMenuCommand give, FloatingMenuCommand favorite) {
        Component title = Component.text(view.title());
        UiKey overview = UiKey.named("overview");
        UiLayout layout = view.entry() == null ? UiLayouts.column(GAP) : UiLayouts.contentColumn(overview, GAP);
        var body = Ui.layout(layout).key("head-detail").sizing(UiSizing.width(UiLength.ui(290), UiLength.ui(700)))
                .child(Ui.heading(title).key("head-name").singleLine().build());
        UiNode favoriteButton = favoriteButton(view, favorite);
        if (view.entry() != null) {
            body.child(Ui.alternative(overview(view, false), overview(view, true)), LinearSlot.stretch());
            var take = Ui.button(label(Message.HEADS_TAKE, view.size()).decorate(TextDecoration.BOLD)).key("take")
                    .style(UiStyle.CONTROL.withTextScale(1.12)).singleLine()
                    .sizing(UiSizing.atLeast(new UiSize(140, 34))).primary(give).build();
            body.child(quantity(view.size(), amount), LinearSlot.stretch())
                    .child(UiTemplates.actionRow(take, favoriteButton), LinearSlot.stretch());
        } else {
            if (view.problem() != null) body.child(status(view.problem(), NamedTextColor.YELLOW));
            if (favoriteButton != null) body.child(favoriteButton);
        }
        return page("head-detail", title, body.build()).build();
    }

    private MenuScene.Builder page(String id, Component title, UiNode body) {
        return MenuScene.screen(id, title).withoutTitle().animation(FloatingMenuAnimation.PLANAR)
                .windowPolicy(MenuWindowPolicy.FIXED)
                .content(UiTemplates.withBack(UiTemplates.window(id, body), label(Message.BACK)));
    }

    private UiNode overview(Detail view, boolean compact) {
        var entry = view.entry();
        int factLines = compact ? 1 : 2;
        var preview = Ui.surface(Ui.item(HeadItems.preview(entry), Component.empty()).key("preview")
                        .role(UiRole.TEXT).style(UiStyle.PLAIN).modelSize(compact ? COMPACT_DETAIL_MODEL : DETAIL_MODEL).build())
                .key("preview-surface").style(PREVIEW_SURFACE).build();
        var factsLayout = compact ? UiLayouts.adaptiveGrid(2, GAP, UiLength.ui(4)) : UiLayouts.column(UiLength.ui(4));
        var facts = Ui.layout(factsLayout).key("facts")
                .sizing(UiSizing.width(UiLength.ui(142), UiLength.ui(400)))
                .child(fact("id", entry.id(), factLines), LinearSlot.fit(LinearSlot.Alignment.START));
        if (view.catalog() != null) {
            if (entry.category() != null) facts.child(fact("category", view.catalog().label(HeadCatalog.Facet.CATEGORY, entry.category()), factLines),
                    LinearSlot.fit(LinearSlot.Alignment.START));
            addFact(facts, "tag", entry.tags(), view.catalog(), HeadCatalog.Facet.TAG, factLines);
            addFact(facts, "collection", entry.collections(), view.catalog(), HeadCatalog.Facet.COLLECTION, factLines);
        }
        return Ui.layout(UiLayouts.firstFit(UiLayouts.contentRow(UiKey.named("facts"), UiLength.ui(16)),
                        UiLayouts.column(GAP)))
                .key("overview").child(preview).child(facts.build()).build();
    }

    private UiNode quantity(int current, IntConsumer change) {
        Component title = label(Message.HEADS_AMOUNT);
        var presets = wrap("presets");
        for (int value : new int[]{1, 16, HeadItems.MAX_AMOUNT}) {
            presets.child(Ui.button(Component.text(value)).key(UiKey.item(value)).selected(current == value)
                    .primary((_, _) -> change.accept(value)).build());
        }
        var field = Ui.numberField(title, UiRange.fixed(1, HeadItems.MAX_AMOUNT, current),
                (_, _, value) -> change.accept((int) Math.round(value))).key("amount").build();
        return Ui.form().key("quantity").field("quantity", title, UiTemplates.actionRow(field, presets.build())).build();
    }

    private UiNode favoriteButton(Detail view, FloatingMenuCommand favorite) {
        if (view.entry() == null && !view.saved()) return null;
        var button = Ui.button(label(Message.HEADS_FAVORITE, view.saved() ? "remove" : "add")).key("favorite")
                .singleLine().selected(view.saved()).primary(favorite);
        if (view.favoriteProblem() != null) button.disabled(label(view.favoriteProblem()));
        return button.build();
    }

    Component label(Message message, Object... args) { return Component.text(languages.t(player, message, args)); }

    private UiNode fact(String kind, String text, int lines) {
        Component label = label(Message.HEADS_DETAIL, kind, text);
        return Ui.text(label).key(kind).maxLines(lines).alignment(Alignment.LEFT).tooltip(label).build();
    }

    private void addFact(Ui.ContainerBuilder body, String kind, Set<String> values, HeadCatalog catalog, HeadCatalog.Facet facet, int lines) {
        if (values.isEmpty()) return;
        String text = String.join(", ", values.stream().map(id -> catalog.label(facet, id))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList());
        body.child(fact(kind, text, lines), LinearSlot.fit(LinearSlot.Alignment.START));
    }

    private UiNode status(Message key, NamedTextColor color) {
        return Ui.text(label(key).color(color)).key("notice").maxLines(2).build();
    }

    private static UiNode headerBody(UiNode header, UiNode body, UiLength gap) {
        return UiTemplates.focus(Ui.layout(UiLayouts.headerBody(gap)).key("reading")
                .child(header.key(), header).child(body.key(), body).build(), body.key());
    }

    private static IntConsumer pageSelection(FloatingMenuPage page, IntConsumer read) {
        return target -> read.accept(new FloatingMenuPage(target, page.itemCount(), page.pageSize()).fromIndex());
    }

    private static Ui.ContainerBuilder wrap(String key) { return Ui.layout(UiLayouts.wrap(GAP, GAP)).key(key); }
    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
}
