package org.encinet.mik.feature.world.plot;

import java.util.List;
import java.util.function.BiFunction;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;
import org.encinet.mik.shared.menu.ui.*;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotMenuPresentationTest {
    private static final String LONG_NAME = "庭院与地下铁路连接中心 A long project name";

    @Test void directoryAndBoardListsFitEveryLanguageAndTextSize() throws Exception {
        for (var language : Language.values()) {
            var text = text(language);
            for (var scale : FloatingMenuTextScale.values()) for (int count : new int[]{0, 6, 8}) {
                boolean directory = count != 8;
                var cards = java.util.stream.IntStream.range(0,count).mapToObj(index -> new PlotCardList.Card(
                        UiKey.named("plot:" + index),card(text,LONG_NAME + index),(_,_) -> {})).toList();
                var list = PlotCardList.content(cards,text.apply(Message.PLOT_DIRECTORY_EMPTY,new Object[0]),0,_ -> {});
                var builder = PlotMenuLayouts.screen("plots", FloatingMenuAppearance.DEFAULT,
                        directory ? PlotMenuLayouts.directory(list) : PlotMenuLayouts.browser(list));
                builder.information("heading", text.apply(Message.PLOT_TITLE, new Object[0])).region("heading");
                if (directory) {
                    builder.navigation("here", Component.text(LONG_NAME)).region("here");
                    for (var filter : PlotDirectory.Filter.values()) builder.control("filter:" + filter,
                            text.apply(filter.label, new Object[]{1000})).region("filters").selected(filter == PlotDirectory.Filter.OWNED);
                    builder.control("search", text.apply(Message.PLOT_DIRECTORY_SEARCH_VALUE, new Object[]{LONG_NAME})).region("search");
                    builder.control("clear", text.apply(Message.PLOT_DIRECTORY_CLEAR_SEARCH, new Object[0])).region("search");
                }
                for (var message : List.of(Message.PLOT_MENU_CREATE, Message.PLOT_BOARD_MENU, Message.PLOT_MENU_MANAGE))
                    builder.navigation(message.name(), text.apply(message, new Object[0])).region("actions");
                builder.back(text.apply(Message.BACK, new Object[0])).region("navigation");
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(builder.build(), scale.factor()), language + " " + scale + " " + count);
                assertDisjoint(frame);
                assertEquals(2, frame.panels().size());
                if (count == 6) assertTrue(element(frame, "here").y() < element(frame, "plot:0").y());
                if (count > 1) {
                    var page = frame.pagination().getFirst().page();
                    assertTrue(page.pageSize() > 0);
                    assertEquals(page.toIndex() - page.fromIndex(),frame.elements().stream()
                            .filter(value -> value.node().key().value().startsWith("plot:")).count());
                }
            }
        }
    }

    @Test void workspacesSettingsAndMemberPermissionsFitEveryLanguageAndTextSize() throws Exception {
        for (var language : Language.values()) {
            var text = text(language);
            for (var scale : FloatingMenuTextScale.values()) for (int mode = 0; mode < 4; mode++) {
                var builder = PlotMenuLayouts.screen("detail", FloatingMenuAppearance.DEFAULT,
                        mode < 2 ? PlotMenuLayouts.workspace(UiReadingPosition.START,_ -> {}) : mode == 2 ? PlotMenuLayouts.settings(true,0,_ -> {}) : PlotMenuLayouts.members(PlotCardList.content(java.util.stream.IntStream.range(0,6)
                                .mapToObj(index -> new PlotCardList.Card(UiKey.named("member:" + index),Component.text("Long_Player_Name_" + index),(_,_) -> {})).toList(),Component.text("Members"),0,_ -> {})));
                builder.information("heading", Component.text(LONG_NAME)).region("heading");
                if (mode < 2) {
                    builder.navigation("parent", text.apply(Message.PLOT_MENU_SUBPLOT_PARENT, new Object[]{LONG_NAME})).region("context");
                    builder.information("summary", card(text, LONG_NAME).append(Component.newline())
                            .append(text.apply(Message.PLOT_WORKSPACE_ROLE, new Object[]{"Long_Player_Name"}))).region("summary");
                    builder.control("notice", text.apply(Message.PLOT_NOTICE_READ, new Object[0]).append(Component.newline())
                            .append(Component.text("活动与建造公告\nA notice with several lines\n请保留沿河通道"))).region("notice");
                    for (var task : mode == 0 ? List.of(PlotWorkspace.Task.values()) : List.of(PlotWorkspace.Task.DESTINATIONS, PlotWorkspace.Task.SUBPLOTS))
                        builder.navigation("task:" + task, text.apply(task.label, new Object[0]).append(Component.newline())
                                .append(text.apply(task.hint, new Object[0]))).region("tasks");
                } else {
                    builder.information("context", Component.text(LONG_NAME)).region("context");
                    if (mode == 2) {
                        for (var label : List.of(Message.PLOT_MENU_RENAME, Message.PLOT_NOTICE_EDIT, Message.PLOT_MENU_ATMOSPHERE))
                            builder.control(label.name(), text.apply(label, new Object[0])).region("settings");
                        builder.information("ownership", text.apply(Message.PLOT_WORKSPACE_OWNERSHIP, new Object[0])).region("ownership-heading");
                        builder.information("owner", text.apply(Message.PLOT_MENU_OWNER, new Object[]{"Long_Player_Name"})).region("owner");
                        for (var label : List.of(Message.PLOT_MENU_TRANSFER, Message.PLOT_MENU_DELETE)) builder.control(label.name(), text.apply(label, new Object[0])).region("ownership");
                        builder.information("id", text.apply(Message.PLOT_WORKSPACE_ID, new Object[]{"12345678-1234-1234-1234-123456789012"})).region("identifier");
                    } else {
                        builder.information("members-heading", text.apply(Message.PLOT_MENU_MEMBERS, new Object[]{1000})).region("members-heading");
                        builder.information("groups-heading", text.apply(Message.PLOT_WORKSPACE_GROUPS, new Object[0])).region("groups-heading");
                        for (var label : List.of(Message.PLOT_ROLE_OWNER, Message.PLOT_ROLE_ADMIN, Message.PLOT_MENU_GROUP_COLLABORATOR,
                                Message.PLOT_MENU_GROUP_NEWCOMER, Message.PLOT_MENU_GROUP_MEMBER)) builder.navigation(label.name(), text.apply(label, new Object[0])).region("groups");
                        builder.control("invite", text.apply(Message.PLOT_MENU_ADD_MEMBER, new Object[0])).region("actions");
                    }
                }
                builder.back(text.apply(Message.BACK, new Object[0])).region("navigation");
                assertDisjoint(assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(builder.build(), scale.factor()), language + " " + scale + " " + mode));
            }
        }
    }

    @Test void selectionMapAndRectangleInspectorFitAllLanguagesAndTextSizes() throws Exception {
        for (var language : Language.values()) {
            var text = text(language);
            for (var scale : FloatingMenuTextScale.values()) for (int mode = 0; mode < 3; mode++) {
                var builder = PlotMenuLayouts.screen("editor", FloatingMenuAppearance.DEFAULT, PlotEditorComposition.create(PlotEditorMapPanel.create(PlotMapTestSupport.scene(mode), text,
                                (_, _) -> { }),regionList(mode))).viewport(PlotEditorComposition.VIEWPORT);
                builder.information("heading", Component.text(LONG_NAME)).region("heading");
                builder.information("selection", text.apply(Message.PLOT_SELECTION_SIZE, new Object[]{120000, 384, 240000})
                        .append(Component.newline()).append(Component.text("-29999999, -64, -29999999 → 29999999, 319, 29999999")))
                        .region("selection").textWidth(FloatingMenuTextWidth.WIDE);
                builder.information("status", text.apply(Message.PLOT_MAP_UNKNOWN, new Object[]{1000})).region("hint");
                if (mode != 1) {
                    builder.control("clear", text.apply(Message.PLOT_SELECTION_CLEAR, new Object[0])).region("points");
                }
                if (mode == 0) {
                    builder.control("fit", text.apply(Message.PLOT_SELECTION_FIT, new Object[0])).region("points");
                    builder.navigation("regions", text.apply(Message.PLOT_REGION_OPEN, new Object[]{128})).region("points");
                    for (var label : List.of(Message.PLOT_SELECTION_ADD, Message.PLOT_SELECTION_SUBTRACT))
                        builder.control(label.name(), text.apply(label, new Object[0])).region("composition");
                } else {
                    builder.information("regions-heading", text.apply(mode == 1 ? Message.PLOT_REGION_OPEN : Message.PLOT_REGION_ADJUST,
                            mode == 1 ? new Object[]{128} : new Object[0])).region("region-heading");
                    if (mode == 1) {
                    }
                    builder.information("selected-region", Component.text("#3 · 4096×384×4096\n-29999999, -64, -29999999\n→ -29995904, 319, -29995904"))
                            .region("region-selection").textWidth(FloatingMenuTextWidth.WIDE);
                    for (var label : mode == 1 ? List.of(Message.PLOT_REGION_ADJUST, Message.PLOT_REGION_DELETE) : List.of(Message.PLOT_REGION_APPLY))
                        builder.control(label.name(), text.apply(label, new Object[0])).region("region-actions");
                }
                for (var label : List.of(Message.PLOT_MAP_ZOOM_IN, Message.PLOT_MAP_ZOOM_OUT, Message.PLOT_MAP_FIT, Message.PLOT_MAP_NEAR))
                    builder.control(label.name(), text.apply(label, new Object[0])).region("view");
                builder.control("map-layer", text.apply(Message.PLOT_MAP_LAYER, new Object[]{-64, -61})).region("view");
                for (var label : List.of(Message.PLOT_MAP_NORTH, Message.PLOT_MAP_WEST, Message.PLOT_MAP_EAST, Message.PLOT_MAP_SOUTH))
                    builder.control(label.name(), text.apply(label, new Object[0])).region("pan");
                for (var label : List.of(Message.PLOT_MAP_CORNER_A, Message.PLOT_MAP_CORNER_B))
                    builder.control(label.name(), text.apply(label, new Object[0]).append(Component.newline())
                            .append(Component.text("-29999999, -64, -29999999"))).region("corners");
                builder.information("legend", text.apply(Message.PLOT_MAP_LEGEND, new Object[0])).region("legend");
                if (mode != 2) builder.control("save", text.apply(Message.PLOT_AREA_REPLACE_SAVE, new Object[0])).region("commit");
                builder.control("undo", text.apply(Message.PLOT_SELECTION_UNDO, new Object[0])).region("history");
                builder.control("redo", text.apply(Message.PLOT_SELECTION_REDO, new Object[0])).region("history");
                builder.navigation("back", text.apply(mode == 2 ? Message.PLOT_REGION_CANCEL : Message.BACK, new Object[0])).region("navigation");
                var scene = builder.build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, scale.factor()), language + " " + scale + " mode=" + mode);
                assertDisjoint(frame);
                assertTrue(element(frame, "map-layer").y() >= element(frame, "open-native-map").y() + element(frame, "open-native-map").size().height());
                assertTrue(element(frame, "back").y() > element(frame, "map-layer").y());
                assertTrue(scene.entries().values().stream().allMatch(entry -> entry.style() == FloatingMenuElementStyle.TEXT));
            }
        }
    }

    private static BiFunction<Message, Object[], Component> text(Language language) throws Exception {
        var bundle = FluentTestSupport.bundle(language);
        return (message, arguments) -> Component.text(FluentTestSupport.text(bundle, message, arguments));
    }

    private static Component card(BiFunction<Message, Object[], Component> text, String name) {
        return Component.text(name).append(Component.newline()).append(Component.text("#1234abcd · "))
                .append(text.apply(Message.PLOT_ITEM_SUMMARY, new Object[]{120000, 128}));
    }
    private static UiNode regionList(int mode) {
        var regions = java.util.stream.IntStream.range(0,128).mapToObj(index -> new PlotSelectionRegions.Region(
                new PlotSelection.Bounds(index*100,64,0,index*100+7,67,7))).toList();
        var snapshot = new PlotEditorMap.Regions(regions,regions.get(2),3,mode!=0,mode==2,0);
        return PlotRegionList.content(snapshot,index -> PlotRegionList.row(regions.get(index),
                Component.text("#" + (index+1) + " · 4096×384×4096"),index==2,(_,_) -> {}),_ -> {});
    }

}
