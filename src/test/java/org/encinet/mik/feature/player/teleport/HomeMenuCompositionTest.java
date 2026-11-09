package org.encinet.mik.feature.player.teleport;

import java.util.List;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.runtime.MenuQueryViewTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class HomeMenuCompositionTest {
    @Test void aShortHomeListIsNotWidenedByTheSearchFieldsPreferredWidth() {
        var query = MenuQueryViewTestSupport.content("", MenuQueryViewTestSupport.Phase.IDLE,
                Component.text("Search homes"), Component.text("Type in chat"), Component.text("Clear"), Component.text("Cancel"));
        var list = HomeMenuComposition.homes(List.of("家", "Base"), "家", _ -> {}, UiReadingPosition.START, _ -> {});
        for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{360, 640, 1280}) {
            var scene = MenuScene.screen("homes").viewport(new UiSize(width, 1024))
                    .content(HomeMenuComposition.content(query, list, Ui.text(Component.empty()).key("empty").build())).build();
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var browser = panel(frame, "browser");
            var padding = frame.surfaces().stream().filter(surface -> surface.path().equals(browser.path()))
                    .findFirst().orElseThrow().style().padding();
            var naturalList = SceneGeometryTestSupport.measure(MenuScene.screen("list").content(list).build(), scale);
            assertEquals(Math.max(naturalList.size().width(), query.sizing().minimum().width()),
                    browser.size().width() - padding.horizontal(), 1e-6,
                    "the list owns its width; the search field may contribute only its usable minimum");
            MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale);
            assertDisjoint(frame);
        }
    }

    @Test void everyHomeRemainsReachableAcrossFovTextAndInterfacePreferencesWithoutChangingTheSelection() {
        var names = java.util.stream.IntStream.range(0,20).mapToObj(index -> "庭院 Garden " + index).toList();
        for (var scale : FloatingMenuScale.values()) for (double typography : new double[]{.7,1,1.8}) for (int degrees : new int[]{30,70,110}) {
            var position = new java.util.concurrent.atomic.AtomicReference<>(UiReadingPosition.START);
            var selected = new java.util.concurrent.atomic.AtomicReference<>(names.getFirst());
            var seen = new java.util.LinkedHashSet<String>();
            int count = 1;
            for (int page = 0; page < count; page++) {
                var snapshot = position.get();
                var menu = MenuScene.screen("homes").viewport(HomeMenuComposition.VIEWPORT)
                        .content(HomeMenuComposition.content(Ui.textField(Component.empty()).placeholder(Component.text("Search homes")).build(),
                                HomeMenuComposition.homes(names, selected.get(), selected::set, snapshot, position::set), Ui.text(Component.empty()).key("empty").build()));
                menu.information("summary", Component.text("Homes: 20 / 20")).region("header");
                var scene = menu.build();
                var frame = SceneGeometryTestSupport.readableFrame(scene, typography, new FloatingMenuFieldOfView(degrees), scale.factor());
                var paging = frame.pagination().getFirst();
                count = paging.page().count();
                assertEquals(page, paging.page().index());
                assertSame(snapshot, position.get());
                assertEquals(names.getFirst(), selected.get(), "paging never chooses a different teleport destination");
                for (var row : homeRows(frame)) assertTrue(seen.add(row.node().key().value().substring("home:".length())));
                assertDisjoint(frame);
                if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
            }
            assertEquals(names, List.copyOf(seen), scale + " / " + typography + " @ " + degrees);
        }
    }

    @Test void emptyAndFilteredResultsOwnANonemptyReadingRegionAndKeepTheirWholeExplanationAndExitReachable() throws Exception {
        for (var locale : Language.values()) for (double typography : new double[]{.7,1.8})
            for (var scale : List.of(FloatingMenuScale.MINIMUM, FloatingMenuScale.MAXIMUM)) for (int fov : new int[]{30,70,110})
                for (boolean savedHomes : new boolean[]{false,true}) {
                    var text = text(locale);
                    var explanation = savedHomes ? text.apply(Message.HOME_SEARCH_EMPTY, new Object[0])
                            : text.apply(Message.HOME_EMPTY_TITLE, new Object[0]).append(Component.newline())
                            .append(text.apply(Message.HOME_EMPTY_DESCRIPTION, new Object[0]));
                    String original = PlainTextComponentSerializer.plainText().serialize(explanation);
                    String key = savedHomes ? "no-matches" : "empty";
                    var position = new java.util.concurrent.atomic.AtomicReference<>(UiReadingPosition.START);
                    int count = 1;
                    for (int page = 0; page < count; page++) {
                        var menu = MenuScene.screen("homes").viewport(HomeMenuComposition.VIEWPORT)
                                .content(HomeMenuComposition.content(Ui.textField(Component.empty()).placeholder(Component.text("Search")).build(),
                                        HomeMenuComposition.homes(List.of(), null, _ -> {}, position.get(), position::set),
                                        Ui.text(explanation).key(key).build()));
                        menu.information("summary", text.apply(Message.HOME_SUMMARY_COUNT, new Object[]{savedHomes ? 20 : 0,20})).region("header");
                        menu.close(text.apply(Message.CLOSE, new Object[0])).region("global-actions");
                        var frame = SceneGeometryTestSupport.readableFrame(menu.build(), typography, new FloatingMenuFieldOfView(fov), scale.factor());
                        assertTrue(frame.readingRegion().size().width() > 0 && frame.readingRegion().size().height() > 0);
                        var paging = frame.pagination().getFirst();
                        count = paging.page().count();
                        assertEquals(page, paging.page().index());
                        int from = paging.readingPosition().orElseThrow().offset();
                        int to = original.codePointCount(0, original.length());
                        if (paging.page().hasNext()) {
                            paging.selectPage().accept(paging.page().next().index());
                            to = position.get().offset();
                        }
                        assertEquals(original.substring(original.offsetByCodePoints(0,from), original.offsetByCodePoints(0,to)).stripTrailing(),
                                PlainTextComponentSerializer.plainText().serialize(element(frame,key).node().content().label()));
                        assertNotNull(element(frame,"close"));
                        assertDisjoint(frame);
                    }
                }
    }

    @Test void emptyAndShortListsDoNotReserveUnusedVerticalSpace() throws Exception {
        for (var language : List.of(Language.ZH_CN, Language.EN_US, Language.RU_RU)) {
            for (var scale : new double[]{.7, 1, 1.8}) for (int count : new int[]{0, 1, 4}) {
                var scene = scene(text(language), new FloatingMenuPage(0, count, 4), false,
                        "", MenuQueryViewTestSupport.Phase.IDLE, count);
                var frame = SceneGeometryTestSupport.measure(scene, scale);
                var browser = panel(frame, "browser");
                var surface = frame.surfaces().stream().filter(value -> value.path().equals(browser.path())).findFirst().orElseThrow();
                var children = frame.elements().stream().filter(value -> value.path().startsWith(browser.path() + "/")).toList();
                double top = children.stream().mapToDouble(UiFrame.Element::y).min().orElseThrow();
                double bottom = children.stream().mapToDouble(value -> value.y() + value.size().height()).max().orElseThrow();
                assertEquals(bottom - top + surface.style().padding().vertical(), browser.size().height(), 1e-6,
                        language + " scale=" + scale + " homes=" + count + ": the browser must hug its actual rows");
            }
        }
    }

    @Test void namesPreferFullTextAndSelectTheOriginalDestinationAfterReflowOrTruncation() {
        var names = List.of("基地", "庭院 Garden overlooking the mountains — 我的家园与建筑工坊", "工坊/北侧 😀");
        var selected = new java.util.concurrent.atomic.AtomicReference<String>();
        var scene = MenuScene.screen("home-list").viewport(new UiSize(240, 1024))
                .content(UiTemplates.window("browser", HomeMenuComposition.homes(names, names.get(1), selected::set, UiReadingPosition.START, _ -> {}))).build();
        for (double scale : new double[]{.7, 1, 1.8}) {
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var rows = homeRows(frame);
            assertEquals(names.size(), rows.size());
            assertDisjoint(frame);
            SceneGeometryTestSupport.assertVisualContentsFit(scene, scale);
            for (int index = 0; index < rows.size(); index++) {
                String name = names.get(index);
                String key = rows.get(index).node().key().value();
                assertEquals(index == 1, entry(scene, key).selected());
                if (!rows.get(index).node().content().label().equals(Component.text(name, net.kyori.adventure.text.format.NamedTextColor.YELLOW)))
                    assertEquals(name, PlainTextComponentSerializer.plainText().serialize(rows.get(index).node().input().tooltip().orElseThrow().text()));
                entry(scene, key).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                assertEquals(name, selected.get(), "truncation and encoded keys must never change the home being selected");
            }
        }
    }

    @Test void shortNamesUseTheBrowserRowInsteadOfLeavingTinyButtonsInTheMiddle() {
        var query = Ui.textField(Component.empty()).placeholder(Component.text("Search homes")).build();
        var scene = MenuScene.screen("homes").viewport(HomeMenuComposition.VIEWPORT)
                .content(HomeMenuComposition.content(query, HomeMenuComposition.homes(List.of("家", "Base"), "家", _ -> {}, UiReadingPosition.START, _ -> {}), Ui.text(Component.empty()).key("empty").build())).build();
        for (double scale : new double[]{.7, 1, 1.8}) {
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var browser = panel(frame, "browser");
            var padding = frame.surfaces().stream().filter(surface -> surface.path().equals(browser.path()))
                    .findFirst().orElseThrow().style().padding();
            for (var row : homeRows(frame)) {
                assertEquals(browser.x() + padding.start(), row.x(), 1e-6);
                assertEquals(browser.size().width() - padding.horizontal(), row.size().width(), 1e-6);
            }
            assertDisjoint(frame);
        }
    }

    @Test void helpAndConfirmationAreCompanionsOfTheSameHomeScreen() throws Exception {
        for (var language : Language.values()) for (var scale : FloatingMenuTextScale.values()) {
            var translate = text(language);
            var body = HomeMenuComposition.help(List.of(Message.HOME_USAGE_SETHOME,Message.HOME_USAGE_HOME,Message.HOME_USAGE_DELHOME,Message.HOME_USAGE_NAME_RULE).stream()
                            .map(message -> translate.apply(message,new Object[0])).toList(),0,_ -> {});
            var host = scene(translate,new FloatingMenuPage(0,4,4),false).toBuilder()
                    .popover(MenuPopover.attached(UiKey.item("help"), Component.text("Details"), body, Component.text("Close"))).build();
            assertDisjoint(SceneGeometryTestSupport.popoverFrame(host,scale.factor(),FloatingMenuFieldOfView.DEFAULT));
        }
        var applied = new java.util.concurrent.atomic.AtomicInteger();
        var order = new java.util.ArrayList<String>();
        var content = HomeMenuComposition.confirmation(Component.text("Delete?"),Component.text("Delete"),
                (_,_) -> { order.add("apply"); applied.incrementAndGet(); });
        var scene = MenuScene.screen("confirm").content(content).build();
        var handle = (FloatingMenuHandle) java.lang.reflect.Proxy.newProxyInstance(FloatingMenuHandle.class.getClassLoader(),new Class<?>[]{FloatingMenuHandle.class},
                (_,method,_) -> { assertEquals("closePopover",method.getName()); order.add("close"); return null; });
        assertNull(entry(scene,"cancel"), "the popover header owns equivalent cancellation");
        entry(scene,"confirm").triggers().get(FloatingMenuInteraction.PRIMARY).execute(null,handle,FloatingMenuInteraction.PRIMARY);
        assertEquals(List.of("close","apply"),order,"the source may disappear during apply without navigating away from Home");
    }

    @Test void theBrowserReservesOnlyItsContentWidthAndWindowPadding() throws Exception {
        for (var page : List.of(new FloatingMenuPage(0, 0, 4), new FloatingMenuPage(0, 1, 4))) {
            var scene = scene(text(Language.ZH_CN), page, false);
            var frame = SceneGeometryTestSupport.measure(scene, 1);
            var browser = panel(frame, "browser");
            var controls = frame.elements().stream().filter(element -> element.path().startsWith(browser.path() + "/")).toList();
            double left = controls.stream().mapToDouble(UiFrame.Element::x).min().orElseThrow();
            double right = Math.max(controls.stream().mapToDouble(element -> element.x() + element.size().width()).max().orElseThrow(),
                    frame.surfaces().stream().filter(surface -> surface.path().startsWith(browser.path() + "/"))
                            .mapToDouble(surface -> surface.x() + surface.size().width()).max().orElse(0));
            var style = frame.surfaces().stream().filter(surface -> surface.path().equals(browser.path())).findFirst().orElseThrow().style();
            assertEquals(right - left + style.padding().horizontal(), browser.size().width(), 1e-6,
                    "search and grid spans must not introduce an artificial pane width");
        }
    }

    @Test void narrowerHostsReflowTheSameHomeControlsAndKeepTheListAsTheReadingFocus() throws Exception {
        for (var language : List.of(Language.ZH_CN, Language.EN_US, Language.RU_RU)) {
            var text = text(language);
            for (double width : new double[]{240, 360, 520, 960}) for (double scale : new double[]{1, 1.8}) {
                var scene = scene(text, new FloatingMenuPage(0, 4, 4), false, "Garden",
                        MenuQueryViewTestSupport.Phase.EDITING, 4).toBuilder().viewport(new UiSize(width, 2048)).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale),
                        language + " width=" + width + " scale=" + scale);
                assertTrue(frame.size().width() <= width + 1e-6);
                assertDisjoint(frame);
                MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale);
                SceneGeometryTestSupport.assertVisualContentsFit(scene, scale);
                var browser = panel(frame, "browser"); var detail = panel(frame, "detail");
                assertTrue(detail.x() >= browser.x() + browser.size().width()
                        || detail.y() >= browser.y() + browser.size().height());
                assertEquals(frame.readingRegion().x() + frame.readingRegion().size().width() / 2, frame.focus().x(), 1e-6);
                assertEquals(frame.readingRegion().y() + frame.readingRegion().size().height() / 2, frame.focus().y(), 1e-6);
                assertNotNull(entry(scene, "cancel"));
                assertNotNull(entry(scene, "action:teleport"));
                assertNotNull(entry(scene, "create"));
            }
        }
    }

    @Test void theListOwnsTheReadingFocusWithDetailsAndToolsOnItsRight() throws Exception {
        var scene = scene(text(Language.ZH_CN), new FloatingMenuPage(2, 20, 4), false);
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        var browser = panel(frame, "browser"); var detail = panel(frame, "detail"); var tools = panel(frame, "home-tools");
        assertDisjoint(frame);
        assertTrue(detail.x() >= browser.x() + browser.size().width());
        assertTrue(tools.x() >= browser.x() + browser.size().width());
        assertTrue(tools.y() >= detail.y() + detail.size().height());
        assertEquals(detail.x(), tools.x(), 1e-6);
        assertEquals(detail.size().width(), tools.size().width(), 1e-6);
        assertEquals(frame.readingRegion().x() + frame.readingRegion().size().width() / 2, frame.focus().x(), 1e-6);
        assertEquals(frame.readingRegion().y() + frame.readingRegion().size().height() / 2, frame.focus().y(), 1e-6);
        var rows = homeRows(frame);
        assertRotation(UiRotation.IDENTITY, SceneGeometryTestSupport.pose(scene, 1, rows.getFirst().node().key().value()).rotation());
        assertEquals(UiSurfaceFacing.VERTICAL, tools.facing());
        assertTrue(tools.elevation().value() > 0);
        var toolPose = SceneGeometryTestSupport.pose(scene, 1, "create");
        assertTrue(Math.abs(toolPose.yawDegrees()) + Math.abs(toolPose.pitchDegrees()) > 0);
        assertEquals(0, Math.min(Math.abs(toolPose.yawDegrees()), Math.abs(toolPose.pitchDegrees())), 1e-8);
        for (String key : List.of("page", "next")) {
            var pager = element(frame, key);
            assertTrue(pager.x() >= browser.x() && pager.x() + pager.size().width() <= browser.x() + browser.size().width() + 1e-6);
            assertTrue(pager.y() > rows.getLast().y());
        }
    }

    @Test void emptyPartialAndFullListsWithLongNamesFitEveryLanguageAndTextSize() throws Exception {
        for (var language : Language.values()) {
            var text = text(language);
            for (var scale : FloatingMenuTextScale.values()) {
                for (var page : List.of(new FloatingMenuPage(0, 0, 4), new FloatingMenuPage(0, 1, 4),
                        new FloatingMenuPage(0, 4, 4), new FloatingMenuPage(2, 20, 4), new FloatingMenuPage(4, 20, 4))) {
                    var scene = scene(text, page, false);
                    var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()), language + " @ " + scale + " " + page);
                    assertDisjoint(frame);
                    MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale.factor());
                    SceneGeometryTestSupport.assertVisualContentsFit(scene, scale.factor());
                    var browser = panel(frame, "browser"); var tools = panel(frame, "home-tools");
                    assertTrue(tools.x() >= browser.x() + browser.size().width());
                    if (page.itemCount() > 0) {
                        assertEquals(entry(scene,"active-home").label(),element(frame,"active-home").node().content().label(),
                                "The destination card must publish the complete name, world, coordinates and distance");
                        assertTrue(element(frame, "create").y() > element(frame, "active-home").y());
                        assertEquals(frame.readingRegion().x() + frame.readingRegion().size().width() / 2, frame.focus().x(), 1e-6);
                    } else {
                        assertNull(entry(scene, "active-home"));
                        assertNull(entry(scene, "action:teleport"));
                    }
                }
            }
        }
    }

    @Test void anUnavailableDestinationKeepsItsDetailsAndToolsInTheSameRegions() throws Exception {
        var scene = scene(text(Language.EN_US), new FloatingMenuPage(0, 1, 4), true);
        var frame = SceneGeometryTestSupport.measure(scene, 1.8);
        assertDisjoint(frame);
        assertTrue(panel(frame, "home-tools").y() >= panel(frame, "detail").y() + panel(frame, "detail").size().height());
        assertNotNull(entry(scene, "action:update"));
        assertNotNull(entry(scene, "action:delete"));
    }

    @Test void filteredEmptyAndInlineSearchStatesFitEveryLanguageAndTextSize() throws Exception {
        for (var language : Language.values()) {
            var text = text(language);
            for (var scale : FloatingMenuTextScale.values()) for (int scenario = 0; scenario < 4; scenario++) {
                boolean editing = scenario == 2;
                var page = new FloatingMenuPage(0, scenario == 0 ? 4 : 0, 4);
                String query = scenario == 0 ? "Garden" : "找不到的名称 😀 Long query ".repeat(4);
                var phase = editing ? MenuQueryViewTestSupport.Phase.EDITING : scenario == 3
                        ? MenuQueryViewTestSupport.Phase.SUBMITTED : MenuQueryViewTestSupport.Phase.IDLE;
                var scene = scene(text, page, false, query, phase, 20);
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()), language + " @ " + scale);
                assertDisjoint(frame);
                MenuQueryViewTestSupport.assertCompactCentered(scene, frame, scale.factor());
                SceneGeometryTestSupport.assertVisualContentsFit(scene, scale.factor());
                var browser = panel(frame, "browser");
                assertTrue(panel(frame, "home-tools").x() >= browser.x() + browser.size().width());
                if (page.itemCount() == 0) {
                    assertNotNull(entry(scene, "no-matches"));
                    assertNull(entry(scene, "empty"), "no matches must not claim the player has no homes");
                    assertNull(entry(scene, "active-home"));
                    assertNull(entry(scene, "action:teleport"));
                }
                if (editing) {
                    assertNotNull(entry(scene, "cancel"));
                    assertEquals(0, SceneGeometryTestSupport.pose(scene, scale.factor(), "cancel").yawDegrees(), 1e-8);
                    assertEquals(0, SceneGeometryTestSupport.pose(scene, scale.factor(), "cancel").pitchDegrees(), 1e-8);
                    var preview = element(frame, "preview"); var cancel = element(frame, "cancel");
                    if (cancel.y() >= preview.y() + preview.size().height() - 1e-6) {
                        assertEquals(preview.x() + preview.size().width(), cancel.x() + cancel.size().width(), 1e-6,
                                "a stacked query keeps its trailing actions right-aligned with the field");
                    } else {
                        assertEquals(preview.y() + preview.size().height() / 2, cancel.y() + cancel.size().height() / 2, 1e-6,
                                language + " @ " + scale + " browser=" + browser.size());
                    }
                }
                assertEquals(!editing, entry(scene, "clear") != null);
                assertNull(entry(scene, "confirm"));
                if (!editing) assertNull(entry(scene, "cancel"));
                if (scenario == 3) assertFalse(entry(scene, "preview").selected());
                assertNull(entry(scene, "label"));
                assertEquals(1, SceneGeometryTestSupport.textLines(scene, scale.factor(), "preview"));
            }
        }
    }

    private static UiFrame.Panel panel(UiFrame frame, String key) {
        return frame.panels().stream().filter(panel -> panel.path().endsWith("/" + key)).findFirst().orElseThrow();
    }

    private static List<UiFrame.Element> homeRows(UiFrame frame) {
        return frame.elements().stream().filter(element -> element.node().key().value().startsWith("home:")).toList();
    }

    private static BiFunction<Message, Object[], Component> text(Language language) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        return (message, args) -> message == Message.MENU_QUERY_HINT_RICH
                ? FluentTestSupport.rich(bundle, message, RichArg.component("chatKey", Component.keybind("key.chat")))
                : Component.text(FluentTestSupport.text(bundle, message, args));
    }

    private static MenuScene scene(BiFunction<Message, Object[], Component> text, FloatingMenuPage page, boolean invalid) {
        return scene(text, page, invalid, "", MenuQueryViewTestSupport.Phase.IDLE, page.itemCount());
    }

    private static MenuScene scene(BiFunction<Message, Object[], Component> text, FloatingMenuPage page, boolean invalid,
                                   String query, MenuQueryViewTestSupport.Phase phase, int totalHomes) {
        var search = MenuQueryViewTestSupport.content(query, phase,
                text.apply(Message.HOME_SEARCH_PLACEHOLDER, new Object[0]), text.apply(Message.MENU_QUERY_HINT_RICH, new Object[0]),
                text.apply(Message.MENU_INPUT_CLEAR, new Object[0]), text.apply(Message.MENU_QUERY_CANCEL, new Object[0]));
        var names = java.util.stream.IntStream.range(0, page.itemCount())
                .mapToObj(index -> "庭院 Garden overlooking the mountains — 我的家园与建筑工坊 " + index).toList();
        var empty = totalHomes == 0 ? text.apply(Message.HOME_EMPTY_TITLE, new Object[0]).append(Component.newline())
                .append(text.apply(Message.HOME_EMPTY_DESCRIPTION, new Object[0])) : text.apply(Message.HOME_SEARCH_EMPTY, new Object[0]);
        var menu = MenuScene.screen("homes").viewport(HomeMenuComposition.VIEWPORT).content(HomeMenuComposition.content(search,
                HomeMenuComposition.homes(names, names.isEmpty() ? null : names.getFirst(), _ -> {}, UiReadingPosition.START, _ -> {}),
                Ui.text(empty).key(totalHomes == 0 ? "empty" : "no-matches").build()));
        menu.information("summary", text.apply(Message.HOME_MENU_TITLE, new Object[0]).append(Component.newline())
                .append(text.apply(Message.HOME_SUMMARY_COUNT, new Object[]{totalHomes, 20}))).region("header");
        if (page.itemCount() > 0) {
            Component name = Component.text("庭院 Garden overlooking the mountains — 我的家园与建筑工坊");
            Component world = text.apply(Message.HOME_WORLD, new Object[]{"world_with_a_long_name"});
            Component distance = text.apply(Message.HOME_DISTANCE, new Object[]{
                    PlainTextComponentSerializer.plainText().serialize(text.apply(Message.HOME_DISTANCE_BLOCKS, new Object[]{12345678}))});
            Component details = invalid ? name.append(Component.newline()).append(text.apply(Message.HOME_LOCATION_INVALID, new Object[0]))
                    : name.append(Component.newline()).append(world).append(Component.newline())
                    .append(text.apply(Message.HOME_LOCATION, new Object[]{-29999999, 319, 29999999})).append(Component.newline()).append(distance);
            menu.information("active-home", details).region("detail").textWidth(FloatingMenuTextWidth.WIDE);
            for (var action : List.of(Message.HOME_ACTION_TELEPORT, Message.HOME_ACTION_UPDATE, Message.HOME_ACTION_DELETE)) {
                String id = switch (action) {
                    case HOME_ACTION_TELEPORT -> "teleport"; case HOME_ACTION_UPDATE -> "update";
                    default -> "delete";
                };
                menu.control("action:" + id, text.apply(action, new Object[0])).region("actions").primary((_, _) -> { });
            }
        }
        menu.control("create", text.apply(Message.HOME_ACTION_CREATE, new Object[0])).region("global-actions").primary((_, _) -> { });
        menu.control("help", text.apply(Message.HOME_USAGE_BOOK, new Object[0])).region("global-actions").primary((_, _) -> { });
        menu.close(text.apply(Message.CLOSE, new Object[0])).region("global-actions");
        return menu.build();
    }
}
