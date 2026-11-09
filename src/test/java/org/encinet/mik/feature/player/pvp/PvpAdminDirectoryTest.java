package org.encinet.mik.feature.player.pvp;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class PvpAdminDirectoryTest {
    @Test void everyPlayerAndBothActionsRemainReachableAcrossLanguagesAndReadingBounds() throws Exception {
        for (var locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            Component title = Component.text(FluentTestSupport.text(bundle, Message.PVP_ADMIN_MENU_TITLE));
            Component empty = Component.text(FluentTestSupport.text(bundle, Message.PVP_PLAYER_NOT_FOUND_MM));
            Component back = Component.text(FluentTestSupport.text(bundle, Message.BACK));
            var rows = new ArrayList<PvpAdminDirectory.Row>();
            for (int index = 0; index < 5; index++) {
                String name = "Player_" + index + "_庭院";
                Component label = Component.text(name + "\n" + FluentTestSupport.text(bundle, Message.PVP_STATE_LABEL)
                        + ": " + FluentTestSupport.text(bundle, index % 2 == 0 ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)
                        + "\n" + FluentTestSupport.text(bundle, Message.PVP_COMBAT_TAG_VALUE, 123));
                rows.add(row(index, label, Component.text(FluentTestSupport.text(bundle, Message.PVP_TARGET_MENU_TITLE, name))));
            }
            for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
                var position = new AtomicReference<>(PvpAdminDirectory.Position.START);
                var toggled = new ArrayList<UUID>(); var opened = new ArrayList<UUID>(); var seen = new HashSet<UUID>();
                var actions = new PvpAdminDirectory.Actions(index -> position.set(PvpAdminDirectory.Position.at(rows, index)),
                        (_, id) -> toggled.add(id), (_, id) -> opened.add(id));
                for (int pass = 0; pass < rows.size(); pass++) {
                    var scene = PvpAdminDirectory.compose(title, empty, back, rows, position.get(), actions);
                    var frame = measure(scene, font, fov, scale);
                    assertDisjoint(frame); assertNotNull(element(frame, "back"));
                    assertTrue(frame.readingRegion().path().endsWith("/players"));
                    assertTrue(toggled.isEmpty()); assertTrue(opened.isEmpty());
                    var published = frame.elements().stream().filter(item -> item.node().key().value().startsWith("player:")).toList();
                    assertFalse(published.isEmpty());
                    for (var item : published) {
                        UUID id = UUID.fromString(item.node().key().value().substring(7));
                        assertTrue(seen.add(id), locale + " repeated a player between pages");
                        var source = rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
                        assertEquals(source.label(), item.node().input().tooltip().orElseThrow().text());
                        var target = entry(scene, frame, "player:" + id);
                        target.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                        target.trigger(FloatingMenuInteraction.SECONDARY).execute(null, null, FloatingMenuInteraction.SECONDARY);
                        entry(scene, frame, "settings:" + id).trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                        assertEquals(List.of(id), toggled); assertEquals(List.of(id, id), opened);
                        toggled.clear(); opened.clear();
                    }
                    var page = frame.pagination().getFirst();
                    if (!page.page().hasNext()) break;
                    page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
                    assertEquals(page.page().toIndex(), position.get().index());
                }
                assertEquals(rows.size(), seen.size(), locale + " · " + font + " · " + fov + " · " + scale);
            }
        }
    }

    @Test void largeDirectoriesKeepUuidActionsAndPathsAfterReorderAndFreezeTheirSource() {
        for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
            var rows = new ArrayList<PvpAdminDirectory.Row>();
            for (int index = 0; index < 300; index++) rows.add(row(index, Component.text("Player_" + index + "\nPVP: On\nCombat: 123 seconds"), Component.text("Player settings")));
            var position = new AtomicReference<>(PvpAdminDirectory.Position.START); var selected = new AtomicReference<UUID>();
            var actions = new PvpAdminDirectory.Actions(index -> position.set(PvpAdminDirectory.Position.at(rows, index)),
                    (_, id) -> selected.set(id), (_, id) -> selected.set(id));
            var seen = new HashSet<UUID>(); String firstPath = null;
            for (int pass = 0; pass < rows.size(); pass++) {
                var scene = scene(rows, position.get(), actions);
                var frame = measure(scene, font, fov, scale);
                assertDisjoint(frame);
                for (var item : frame.elements()) if (item.node().key().value().startsWith("player:")) {
                    UUID id = UUID.fromString(item.node().key().value().substring(7));
                    assertTrue(seen.add(id));
                    item.node().input().actions().get(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                    assertEquals(id, selected.get());
                    if (id.equals(rows.getFirst().id())) firstPath = item.path();
                }
                var page = frame.pagination().getFirst();
                if (!page.page().hasNext()) break;
                page.action(FloatingMenuInteraction.SCROLL_DOWN).execute(null, null, FloatingMenuInteraction.SCROLL_DOWN);
            }
            assertEquals(300, seen.size());
            var first = rows.getFirst();
            var bookmark = PvpAdminDirectory.Position.at(rows, 0);
            Collections.reverse(rows);
            assertEquals(299, bookmark.resolve(rows));
            var snapshot = scene(rows, bookmark, actions);
            rows.clear();
            var frame = measure(snapshot, font, fov, scale);
            assertEquals(firstPath, element(frame, "player:" + first.id()).path());
            assertEquals(300, frame.pagination().getFirst().page().itemCount());
        }
    }

    @Test void firstWindowDoesNotCopyEverySnapshotHeadOrExecuteActions() {
        var copies = new HashMap<Integer, AtomicInteger>(); var rows = new ArrayList<PvpAdminDirectory.Row>();
        for (int index = 0; index < 300; index++) {
            var counter = new AtomicInteger(); copies.put(index, counter);
            rows.add(new PvpAdminDirectory.Row(new UUID(0, index), new Head(counter), Component.text("Player_" + index), Component.text("Settings")));
        }
        var executions = new AtomicInteger();
        var actions = new PvpAdminDirectory.Actions(_ -> executions.incrementAndGet(), (_, _) -> executions.incrementAndGet(), (_, _) -> executions.incrementAndGet());
        var scene = scene(rows, PvpAdminDirectory.Position.START, actions);
        copies.values().forEach(value -> value.set(0));
        var frame = measure(scene, .7, 110, .7);
        long created = copies.values().stream().filter(value -> value.get() > 0).count();
        assertTrue(created > 0, "Exercise the actual head representation");
        assertTrue(created <= UiWindowedList.MAX_VISIBLE_ITEMS);
        assertTrue(created < rows.size());
        assertEquals(0, copies.get(299).get()); assertEquals(0, executions.get());
        assertTrue(frame.elements().size() < rows.size());
    }

    @Test void bookmarksUseStablePlayersThenClampToANeighborIfTheyLeave() {
        var rows = new ArrayList<>(List.of(row(0, Component.empty(), Component.empty()), row(1, Component.empty(), Component.empty()), row(2, Component.empty(), Component.empty())));
        var bookmark = PvpAdminDirectory.Position.at(rows, 1);
        Collections.swap(rows, 0, 1);
        assertEquals(0, bookmark.resolve(rows));
        rows.removeFirst();
        assertEquals(1, bookmark.resolve(rows));
        rows.clear(); assertEquals(0, bookmark.resolve(rows));
        assertEquals(PvpAdminDirectory.Position.START, PvpAdminDirectory.Position.at(rows, Integer.MAX_VALUE));
    }

    @Test void emptyDirectoryHasAReadingRegionAndAnExitAtEveryReadingBoundary() {
        var actions = new PvpAdminDirectory.Actions(_ -> fail(), (_, _) -> fail(), (_, _) -> fail());
        for (double font : new double[]{.7, 1.8}) for (double scale : new double[]{.7, 1.3}) for (int fov : new int[]{30, 110}) {
            var frame = measure(scene(List.of(), PvpAdminDirectory.Position.START, actions), font, fov, scale);
            assertTrue(frame.readingRegion().size().width() > 0); assertTrue(frame.readingRegion().size().height() > 0);
            assertNotNull(element(frame, "empty")); assertNotNull(element(frame, "back")); assertDisjoint(frame);
        }
    }

    private static PvpAdminDirectory.Row row(int index, Component label, Component settings) {
        return new PvpAdminDirectory.Row(new UUID(0, index), new Head(new AtomicInteger()), label, settings);
    }
    private static MenuScene scene(List<PvpAdminDirectory.Row> rows, PvpAdminDirectory.Position position, PvpAdminDirectory.Actions actions) {
        return PvpAdminDirectory.compose(Component.text("PVP Admin"), Component.text("No online players"), Component.text("Back"), rows, position, actions);
    }
    private static UiFrame measure(MenuScene scene, double font, int fov, double scale) {
        return SceneGeometryTestSupport.readableFrame(scene, font, new FloatingMenuFieldOfView(fov), scale);
    }
    /** Item identity and copy work without bootstrapping Paper registries. Geometry uses the real menu measurer. */
    private static final class Head extends ItemStack {
        private final AtomicInteger copies;
        Head(AtomicInteger copies) { super(); this.copies = copies; }
        @Override public Material getType() { return Material.PLAYER_HEAD; }
        @Override public Head clone() { copies.incrementAndGet(); return this; }
    }
}
