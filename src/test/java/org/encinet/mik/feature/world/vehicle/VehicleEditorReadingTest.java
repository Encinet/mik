package org.encinet.mik.feature.world.vehicle;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorReadingTest {
    @Test void directoriesKeepTheirSourceAnchorAndEveryObjectsActionAcrossCapacityChanges() {
        for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
            var source = new ArrayList<VehicleEditorList.Item>();
            var chosen = new AtomicInteger(-1);
            for (int index = 0; index < 256; index++) {
                int id = index;
                source.add(new VehicleEditorList.Item(UiKey.named("model:" + id), Component.text("Model " + id + " · " + "long name ".repeat(20)),
                        (_, _) -> chosen.set(id)));
            }
            var anchor = new AtomicInteger(); var seen = new HashSet<Integer>();
            var route = new Library(0);
            UiFrame first = null;
            for (int pass = 0; pass < 256; pass++) {
                var reading = VehicleEditorList.content(source, Component.text("Empty"), anchor.get(), anchor::set);
                var scene = scene(VehicleEditorView.open(route), reading, VehicleEditorComposition.Primary.ITEMS, _ -> { }, _ -> { });
                var frame = measure(scene, font, fov, scale); assertDisjoint(frame);
                if (first == null) first = frame;
                var page = frame.pagination().stream().filter(value -> value.path().endsWith("/items")).findFirst().orElseThrow();
                for (var item : frame.elements()) if (item.node().key().value().startsWith("model:")) {
                    int id = Integer.parseInt(item.node().key().value().substring(6));
                    assertTrue(seen.add(id));
                    item.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                    assertEquals(id, chosen.get()); assertTrue(item.node().input().tooltip().isPresent());
                }
                if (!page.page().hasNext()) break;
                page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                assertEquals(page.page().next().fromIndex(), anchor.get());
            }
            assertEquals(256, seen.size());
            var reading = VehicleEditorList.content(source, Component.text("Empty"), 173, _ -> { });
            source.clear(); // Layout only reads its immutable snapshot.
            var frame = measure(scene(VehicleEditorView.open(route), reading, VehicleEditorComposition.Primary.ITEMS, _ -> { }, _ -> { }),
                    font, fov, scale);
            assertNotNull(element(frame, "model:173"));
            var page = frame.pagination().stream().filter(value -> value.path().endsWith("/items")).findFirst().orElseThrow();
            assertTrue(page.page().fromIndex() <= 173 && page.page().toIndex() > 173);
            assertEquals(first.readingRegion().size(), frame.readingRegion().size());
        }
    }

    @Test void everyAxisPairStaysTogetherAndPagingNeverAdjustsTheModel() throws Exception {
        var route = new Position(new ComponentRef(Section.PARTS, 1), false);
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                var current = new AtomicReference<>(VehicleEditorView.open(route)); var edits = new AtomicInteger(); var seen = new HashSet<Integer>();
                for (int pass = 0; pass < 3; pass++) {
                    var view = current.get(); var axes = new ArrayList<UiElement>();
                    for (int axis = 0; axis < 3; axis++) for (int direction : new int[]{-1, 1})
                        axes.add(Ui.button(Component.text("XYZ".charAt(axis) + (direction < 0 ? " − 0.25" : " + 0.25")))
                                .key("axis:" + axis + ":" + direction).singleLine().primary((_, _) -> edits.incrementAndGet()).build());
                    var reading = VehicleEditorComposition.axes(axes, List.of(Component.text("X · 0"), Component.text("Y · 1"), Component.text("Z · 2")), view, current::set);
                    var scene = scene(view, reading, VehicleEditorComposition.Primary.ITEMS, current::set, menu -> {
                        VehicleEditorComposition.tools(menu, route, message -> label(bundle, message), (_, _) -> { });
                        for (var message : List.of(Message.VEHICLE_MODEL_HERE, Message.VEHICLE_MODEL_LOOK, Message.VEHICLE_MODEL_SNAP_ON,
                                Message.VEHICLE_MODEL_STEP, Message.VEHICLE_MODEL_MANUAL)) menu.control(message.key(), label(bundle, message)).region("actions");
                    });
                    var frame = measure(scene, font, fov, scale); assertDisjoint(frame); assertEquals(0, edits.get());
                    for (int axis = 0; axis < 3; axis++) {
                        int selected = axis;
                        if (frame.elements().stream().noneMatch(item -> item.node().key().value().equals("axis:" + selected + ":-1"))) continue;
                        assertTrue(seen.add(axis));
                        var minus = element(frame, "axis:" + axis + ":-1"); var plus = element(frame, "axis:" + axis + ":1");
                        var value = element(frame, "value:" + axis);
                        assertEquals(minus.y(), plus.y(), 1e-6); assertTrue(minus.x() < plus.x());
                        assertTrue(minus.x() < value.x() && value.x() < plus.x());
                        assertTrue(element(frame, "object-tool:position").y() < minus.y());
                    }
                    assertNotNull(element(frame, "back")); assertNotNull(element(frame, "dismiss"));
                    var page = frame.pagination().stream().filter(value -> value.path().endsWith("/items")).findFirst().orElseThrow();
                    if (!page.page().hasNext()) break;
                    page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                }
                assertEquals(Set.of(0, 1, 2), seen); assertEquals(0, edits.get());
            }
        }
    }

    @Test void actionOnlyEmptyAndUnavailableViewsHaveANonemptyReadingRegionInEveryLanguage() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                for (var route : List.of(new Model("sedan", 117), Workspace.INSTANCE, Utilities.INSTANCE)) {
                    var current = new AtomicReference<>(VehicleEditorView.open(route)); var seen = new HashSet<String>();
                    var messages = route == Utilities.INSTANCE
                            ? List.of(Message.VEHICLE_MODEL_ANCHOR, Message.VEHICLE_MODEL_PICK, Message.VEHICLE_MODEL_CENTER_HERE,
                                    Message.VEHICLE_MODEL_CENTER_LOOK, Message.VEHICLE_MODEL_CHECK, Message.VEHICLE_MODEL_SNAP_ON)
                            : List.of(Message.VEHICLE_MODEL_SAVE, Message.VEHICLE_MODEL_SAVE_CONTINUE, Message.VEHICLE_MODEL_PREVIEW, Message.VEHICLE_MODEL_DISCARD);
                    for (int pass = 0; pass < messages.size(); pass++) {
                        var scene = scene(current.get(), null, VehicleEditorComposition.Primary.ACTIONS, current::set, menu -> {
                            for (var message : messages) menu.control(message.key(), label(bundle, message)).region("actions");
                        });
                        var frame = measure(scene, font, fov, scale); assertDisjoint(frame); assertReading(frame);
                        for (var item : frame.elements()) if (item.path().startsWith(frame.readingRegion().path() + "/items/"))
                            assertTrue(seen.add(item.node().key().value()));
                        assertNotNull(element(frame, "back")); assertNotNull(element(frame, "dismiss"));
                        var page = frame.pagination().stream().filter(value -> value.path().equals(frame.readingRegion().path())).findFirst().orElseThrow();
                        if (!page.page().hasNext()) break;
                        page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                    }
                    assertEquals(messages.size(), seen.size());
                }
                var route = new Library(0);
                var empty = VehicleEditorList.content(List.of(), label(bundle, Message.MENU_SEARCH_EMPTY), 0, _ -> { });
                var frame = measure(scene(VehicleEditorView.open(route), empty, VehicleEditorComposition.Primary.ITEMS, _ -> { }, _ -> { }), font, fov, scale);
                assertReading(frame); assertNotNull(element(frame, "empty"));
                var missing = scene(VehicleEditorView.open(new Inspector(new ComponentRef(Section.SEATS, 5))), null,
                        VehicleEditorComposition.Primary.SUMMARY, _ -> { }, _ -> { });
                assertReading(measure(missing, font, fov, scale));
            }
        }
    }

    @Test void theSameControlOnAnotherObjectHasADifferentInputIdentity() {
        var paths = new HashSet<String>();
        for (int index : new int[]{0, 1}) for (var section : Section.values()) {
            var route = new Inspector(new ComponentRef(section, index));
            var scene = scene(VehicleEditorView.open(route), null, VehicleEditorComposition.Primary.ITEMS, _ -> { },
                    menu -> menu.control("remove", Component.text("Remove")).region("items"));
            assertTrue(paths.add(entry(scene, "remove").id()));
        }
    }

    @Test void everyInspectorOperationRemainsReachableAlongsideItsObjectTools() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (var section : Section.values()) for (double font : new double[]{.7, 1.8}) for (int fov : new int[]{30, 110}) {
                var route = new Inspector(new ComponentRef(section, 1));
                var current = new AtomicReference<>(VehicleEditorView.open(route)); var seen = new HashSet<String>();
                var operations = switch (section) {
                    case PARTS -> List.of(Message.VEHICLE_MODEL_ANIMATION, Message.VEHICLE_MODEL_BLOCK, Message.VEHICLE_MODEL_ITEM,
                            Message.VEHICLE_MODEL_TEXT, Message.VEHICLE_MODEL_OPTIONS, Message.VEHICLE_MODEL_REMOVE);
                    case SEATS -> List.of(Message.VEHICLE_MODEL_DRIVER, Message.VEHICLE_MODEL_REMOVE);
                    case COLLIDERS, SUPPORTS -> List.of(Message.VEHICLE_MODEL_REMOVE);
                };
                for (int pass = 0; pass < operations.size(); pass++) {
                    var scene = scene(current.get(), null, VehicleEditorComposition.Primary.ITEMS, current::set, menu -> {
                        VehicleEditorComposition.tools(menu, route, message -> label(bundle, message), (_, _) -> { });
                        for (var message : operations) menu.control(message.key(), label(bundle, message)).region("items");
                        menu.control("copy", label(bundle, Message.VEHICLE_MODEL_COPY)).region("actions");
                    });
                    var frame = measure(scene, font, fov, 1.3); assertReading(frame);
                    for (var item : frame.elements()) if (item.path().startsWith(frame.readingRegion().path() + "/items/"))
                        assertTrue(seen.add(item.node().key().value()));
                    assertNotNull(element(frame, "copy")); assertNotNull(element(frame, "object-tool:inspector"));
                    var page = frame.pagination().stream().filter(value -> value.path().equals(frame.readingRegion().path())).findFirst().orElseThrow();
                    if (!page.page().hasNext()) break;
                    page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                }
                assertEquals(operations.size(), seen.size(), language + " · " + section);
            }
        }
    }

    private static MenuScene scene(VehicleEditorView view, UiNode reading, VehicleEditorComposition.Primary primary,
                                   Consumer<VehicleEditorView> read, Consumer<MenuScene.Builder> populate) {
        var menu = VehicleModelEditor.createMenu(Component.text("Vehicle editor"), view.route());
        menu.information("summary", Component.text("Object · local coordinates\nFull description")).region("summary");
        populate.accept(menu);
        menu.navigation("back", Component.text("Back")).region("navigation");
        menu.content(VehicleEditorComposition.create(view, reading, primary, read));
        return VehicleModelEditor.finishMenu(menu, Component.text("Close"));
    }

    private static UiFrame measure(MenuScene scene, double font, int fov, double scale) {
        return SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
    }

    private static void assertReading(UiFrame frame) {
        assertTrue(frame.readingRegion().size().width() > 0); assertTrue(frame.readingRegion().size().height() > 0);
        assertTrue(frame.elements().stream().anyMatch(item -> item.path().startsWith(frame.readingRegion().path() + "/")));
        assertDisjoint(frame);
    }

    private static Component label(net.quickwrite.fluent4j.container.FluentBundle bundle, Message message) {
        return Component.text(FluentTestSupport.text(bundle, message));
    }
}
