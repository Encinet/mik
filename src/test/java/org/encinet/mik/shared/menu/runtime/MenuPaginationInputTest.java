package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;

import static org.junit.jupiter.api.Assertions.*;

class MenuPaginationInputTest {
    @Test void anOrdinaryPagerDeclaresWheelPagingForItsWholeCollection() {
        var selected = new ArrayList<Integer>();
        var body = collection("library", new FloatingMenuPage(0, 12, 4), selected);
        var geometry = measure(body);
        assertEquals(1, geometry.frame().pagination().size());
        geometry.definition().triggers().get(FloatingMenuInteraction.SCROLL_UP).execute(null, null, FloatingMenuInteraction.SCROLL_UP);
        assertTrue(selected.isEmpty(), "The first page consumes upward scrolling without changing the page");
        geometry.definition().triggers().get(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1), selected);
    }

    @Test void independentPagedCollectionsCanShareOneScene() {
        var left = new ArrayList<Integer>();
        var right = new ArrayList<Integer>();
        var geometry = measure(Ui.row().gap(UiLength.ui(16))
                .child(collection("library", new FloatingMenuPage(0, 12, 4), left))
                .child(collection("queue", new FloatingMenuPage(2, 12, 4), right)).build());
        assertEquals(2, geometry.frame().pagination().size());
        assertNull(geometry.definition().triggers().get(FloatingMenuInteraction.SCROLL_DOWN),
                "Multiple lists must not choose a scene-wide paging target");
        for (var region : geometry.pagination()) {
            var target = geometry.frame().elements().stream().filter(element -> element.node().key().value().equals("select")
                    && element.path().startsWith(region.binding().path() + "/")).findFirst().orElseThrow();
            for (var direction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN)) {
                var action = MenuPaginationInput.action(geometry.pagination(), target.path(), "", direction,
                        _ -> fail("An element already identifies its owning collection"));
                assertNotNull(action);
                action.execute(null, null, direction);
            }
        }
        assertEquals(List.of(1), left, "The first page cannot scroll upward or fall through to the queue");
        assertEquals(List.of(1), right, "The last page cannot scroll downward or fall through to the library");
    }

    @Test void blankSpaceUsesTheSameFoldedPlaneAndBoundsAsTheCollection() {
        var selected = new ArrayList<Integer>();
        var geometry = measure(Ui.row().gap(UiLength.ui(16))
                .child(collection("library", new FloatingMenuPage(0, 12, 4), selected))
                .child(collection("queue", new FloatingMenuPage(0, 12, 4), new ArrayList<>())).build());
        var region = geometry.pagination().getFirst();
        var pose = region.pose();
        var origin = new Vector(0, 0, -20);
        var center = new Vector(pose.right(), pose.up(), -pose.forward());
        var up = FloatingMenuSurfaceGeometry.panelUp((float) pose.yawDegrees(), (float) pose.pitchDegrees(), (float) pose.rollDegrees());
        var right = up.clone().crossProduct(FloatingMenuSurfaceGeometry.facing((float) pose.yawDegrees(), (float) pose.pitchDegrees())).normalize();
        var point = center.clone().add(right.multiply(region.size().width() * pose.scale() * .4))
                .add(up.multiply(region.size().height() * pose.scale() * .4));
        var ray = point.subtract(origin).normalize();
        var action = MenuPaginationInput.action(geometry.pagination(), null, "", FloatingMenuInteraction.SCROLL_DOWN,
                candidate -> intersect(origin, ray, candidate));
        assertNotNull(action, "Reading text and gaps do not require a clickable row");
        action.execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1), selected);
        assertNull(MenuPaginationInput.action(geometry.pagination(), null, "", FloatingMenuInteraction.SCROLL_DOWN,
                _ -> Optional.empty()), "A missed collection must not pick a different list");
    }

    @Test void automaticListsAndTheirPagerShareOneBindingAndSeveralListsCanCoexist() {
        var lists = Ui.row().gap(UiLength.ui(16));
        for (String key : List.of("left", "right")) {
            var items = new ArrayList<UiNode>();
            for (int index = 0; index < 40; index++) items.add(Ui.text(Component.text("Song " + index)).key("song:" + index).build());
            lists.child(Ui.pagedList().key(key).items(items).page(0, _ -> { })
                    .sizing(new UiSizing(UiSize.ZERO, new UiSize(240, 160))).build());
        }
        var geometry = measure(lists.build());
        assertEquals(2, geometry.frame().pagination().size(), "A generated footer must not register a second binding for the same list");
        assertTrue(geometry.frame().pagination().stream().allMatch(binding -> binding.page().count() > 1));
    }

    @Test void aHiddenSinglePagePagerStillOwnsInputWithoutAddingSpace() {
        var body = Ui.column().gap(UiLength.ui(12)).child(Ui.text(Component.text("Only song")).key("song").build());
        var original = measure(body.build());
        var selected = new ArrayList<Integer>();
        body.child(UiTemplates.pager(new FloatingMenuPage(0, 1, 4), selected::add));
        var paged = measure(body.build());
        assertEquals(original.frame().size(), paged.frame().size());
        assertEquals(original.frame().elements(), paged.frame().elements());
        assertEquals(1, paged.pagination().size());
        for (var direction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN))
            paged.definition().triggers().get(direction).execute(null, null, direction);
        assertTrue(selected.isEmpty());
    }

    @Test void anEmptySiblingDoesNotRemoveTheVisibleCollectionsWheelTarget() {
        var selected = new ArrayList<Integer>();
        var empty = Ui.column().key("empty").child(UiTemplates.pager(new FloatingMenuPage(0, 0, 4),
                _ -> fail("Empty collections cannot change page"))).build();
        var geometry = measure(Ui.row().child(empty).child(collection("visible", new FloatingMenuPage(0, 9, 3), selected)).build());
        assertEquals(2, geometry.frame().pagination().size());
        assertEquals(1, geometry.pagination().size(), "A zero-area collection needs no hit surface");
        var action = MenuPaginationInput.action(geometry.pagination(), element(geometry.frame(), "select").path(), "",
                FloatingMenuInteraction.SCROLL_DOWN, _ -> Optional.empty());
        assertNotNull(action);
        action.execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1), selected);
    }

    @Test void nestedListsStopAtTheirOwnBoundaryWithoutBubblingToTheOuterList() {
        var inner = new ArrayList<Integer>();
        var outer = new ArrayList<Integer>();
        var content = Ui.column().key("outer")
                .child(collection("inner", new FloatingMenuPage(0, 9, 3), inner))
                .child(UiTemplates.pager(new FloatingMenuPage(1, 9, 3), outer::add)).build();
        var geometry = measure(content);
        assertEquals(2, geometry.pagination().size());
        String target = element(geometry.frame(), "select").path();
        for (var direction : List.of(FloatingMenuInteraction.SCROLL_UP, FloatingMenuInteraction.SCROLL_DOWN)) {
            var action = MenuPaginationInput.action(geometry.pagination(), target, "", direction,
                    _ -> fail("The inner element determines its input scope"));
            action.execute(null, null, direction);
        }
        assertTrue(outer.isEmpty());
        assertEquals(List.of(1), inner);
    }

    @Test void popoverCollectionsResolveScopedTargetsWithoutChangingParentPages() {
        var parent = new ArrayList<Integer>();
        var selected = new ArrayList<Integer>();
        var content = Ui.column().key("choices").child(Ui.button(Component.text("Choice")).key("choice").build())
                .child(UiTemplates.pager(new FloatingMenuPage(0, 9, 3), selected::add)).build();
        var popup = MenuPopover.attached(UiKey.named("select"), Component.text("Choices"), content, Component.text("Close"));
        var source = MenuScene.screen("parent").content(collection("parent", new FloatingMenuPage(1, 9, 3), parent))
                .popover(popup).build();
        var geometry = SceneGeometry.measure(source, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var layer = geometry.popover().content();
        String target = MenuPopover.scoped(element(layer.frame(), "choice").path());
        var action = MenuPaginationInput.action(layer.pagination(), target, MenuPopover.scoped(""), FloatingMenuInteraction.SCROLL_DOWN,
                _ -> fail("The scoped target belongs to the popup collection"));
        action.execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
        assertEquals(List.of(1), selected);
        assertTrue(parent.isEmpty());
    }

    private Optional<FloatingMenuSurfaceGeometry.Hit> intersect(Vector origin, Vector ray, MenuPaginationInput.Region region) {
        var pose = region.pose();
        return FloatingMenuSurfaceGeometry.intersect(origin, ray, new Vector(pose.right(), pose.up(), -pose.forward()),
                (float) pose.yawDegrees(), (float) pose.pitchDegrees(), (float) pose.rollDegrees(),
                region.size().width() * pose.scale(), region.size().height() * pose.scale(), 0);
    }

    private UiNode collection(String key, FloatingMenuPage page, List<Integer> selected) {
        return UiTemplates.window(key, Ui.column().key("collection")
                .child(Ui.text(Component.text("Read-only song information")).key("song").build())
                .child(Ui.button(Component.text("Select song")).key("select").primary((_,_) -> { }).build())
                .child(UiTemplates.pager(page, Component.text("Previous"), Component.text((page.index() + 1) + " / " + page.count()),
                        Component.text("Next"), selected::add)).build());
    }

    private SceneGeometry measure(UiNode content) {
        return SceneGeometry.measure(MenuScene.screen("paging").viewport(new UiSize(1280, 2048)).content(content).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1);
    }
}
