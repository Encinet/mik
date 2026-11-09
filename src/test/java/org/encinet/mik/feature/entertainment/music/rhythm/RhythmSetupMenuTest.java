package org.encinet.mik.feature.entertainment.music.rhythm;

import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.encinet.mik.shared.i18n.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RhythmSetupMenuTest {
    @Test void translatedDifficultyPagesKeepEveryOptionAndExplicitStartAcrossReadingExtremes() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            java.util.function.Function<RhythmDifficulty,Component> label = difficulty -> Component.text(FluentTestSupport.text(bundle,switch (difficulty) {
                case EASY -> Message.MUSIC_RHYTHM_DIFFICULTY_EASY; case NORMAL -> Message.MUSIC_RHYTHM_DIFFICULTY_NORMAL;
                case HARD -> Message.MUSIC_RHYTHM_DIFFICULTY_HARD; case EXPERT -> Message.MUSIC_RHYTHM_DIFFICULTY_EXPERT;
            })).append(Component.text("\n≤ 5.9/s · ±165 ms · ×1.15"));
            for (double font : new double[]{.7,1.8}) for (double scale : new double[]{.7,1.3}) for (int fov : new int[]{30,110}) {
                var page = new java.util.concurrent.atomic.AtomicInteger();
                var selected = new AtomicReference<>(RhythmDifficulty.NORMAL); var started = new AtomicReference<RhythmDifficulty>();
                var seen = new java.util.HashSet<RhythmDifficulty>(); int pages = 1;
                for (int index = 0; index < pages; index++) {
                    var displayed = selected.get();
                    var scene = RhythmSetupMenu.difficulty(Component.text("A long selected song and game mode ".repeat(8)),selected.get(),page.get(),page::set,
                            label,null,(_,value) -> selected.set(value),(_,value) -> started.set(value),
                            Component.text(FluentTestSupport.text(bundle,Message.MUSIC_RHYTHM_START)),
                            Component.text(FluentTestSupport.text(bundle,Message.MUSIC_BACK))).build();
                    var frame = SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale);
                    UiSceneTestSupport.assertDisjoint(frame);
                    var paging = frame.pagination().getFirst(); pages = paging.page().count(); assertEquals(index,paging.page().index());
                    assertNotNull(UiSceneTestSupport.element(frame,"start")); assertNotNull(UiSceneTestSupport.element(frame,"back"));
                    var published = scene.measured(frame);
                    for (var option : frame.elements()) if (option.node().key().value().startsWith("difficulty:")) {
                        var difficulty = RhythmDifficulty.valueOf(option.node().key().value().substring("difficulty:".length()));
                        assertTrue(seen.add(difficulty));
                        assertEquals(label.apply(difficulty),option.node().input().tooltip().orElseThrow().text());
                        published.entries().get(option.path()).trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                        assertEquals(difficulty,selected.get()); assertNull(started.get());
                    }
                    UiSceneTestSupport.entry(published,frame,"start").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
                    assertEquals(displayed,started.get(),"Start belongs to the choice displayed by this snapshot"); started.set(null);
                    if (paging.page().hasNext()) paging.selectPage().accept(paging.page().next().index());
                }
                assertEquals(java.util.Set.of(RhythmDifficulty.values()),seen);
            }
        }
    }

    @Test void choosingDifficultyDoesNotStartAndTheStartButtonUsesTheDisplayedChoice() {
        var selected = new AtomicReference<>(RhythmDifficulty.NORMAL);
        var started = new AtomicReference<RhythmDifficulty>();
        var first = menu(selected, started, null);
        UiSceneTestSupport.entry(first, "difficulty:HARD").trigger(FloatingMenuInteraction.PRIMARY)
                .execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(RhythmDifficulty.HARD, selected.get());
        assertNull(started.get());
        var updated = menu(selected, started, null);
        assertTrue(UiSceneTestSupport.entry(updated, "difficulty:HARD").selected());
        UiSceneTestSupport.entry(updated, "start").trigger(FloatingMenuInteraction.PRIMARY)
                .execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(RhythmDifficulty.HARD, started.get());
        var frame = SceneGeometryTestSupport.measure(updated, 1.8);
        UiSceneTestSupport.assertDisjoint(frame);
        assertTrue(UiSceneTestSupport.element(frame,"start").y() >= frame.readingRegion().y() + frame.readingRegion().size().height());
    }

    @Test void unavailablePlaybackExplainsWhyNeitherSelectionNorStartCanExecute() {
        var reason = Component.text("This jukebox has no playable chart");
        var menu = menu(new AtomicReference<>(RhythmDifficulty.NORMAL), new AtomicReference<>(), reason);
        for (var entry : menu.entries().values()) if (entry.id().contains("/difficulty/") || entry.id().endsWith("/start")) {
            assertFalse(entry.enabled());
            assertEquals(reason, entry.disabledReason());
        }
        assertTrue(UiSceneTestSupport.entry(menu, "back").enabled());
    }

    private static MenuScene menu(AtomicReference<RhythmDifficulty> choice, AtomicReference<RhythmDifficulty> started, Component reason) {
        return RhythmSetupMenu.difficulty(Component.text("A song and its selected game mode"),choice.get(),0,_ -> {},
                value -> Component.text(value.name()), reason, (_, value) -> choice.set(value),
                (_, value) -> started.set(value), Component.text("Start"), Component.text("Back")).build();
    }
}
