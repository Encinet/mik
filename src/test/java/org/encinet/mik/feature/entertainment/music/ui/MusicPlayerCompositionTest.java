package org.encinet.mik.feature.entertainment.music.ui;

import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicPlayerCompositionTest {
    @Test void translatedPlayerGroupsFitWithoutOverlappingAtLargeTextAndNarrowWidths() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (double typography : new double[]{.7, 1, 1.3, 1.8}) for (int width : new int[]{360, 600, 1280}) {
                var scene = MenuScene.screen("player").withoutTitle().viewport(new UiSize(width, 2048))
                        .content(MusicSceneTestSupport.transport(player(label,false,() -> 83000))).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene, typography), language + " / " + typography + " / " + width);
                assertDisjoint(frame);
                assertTrue(frame.size().width() <= width + 1e-6);
                var previous = element(frame, "player-previous");
                var primary = element(frame, "player-primary");
                var next = element(frame, "player-next");
                assertEquals(previous.y(), primary.y(), 1e-6);
                assertEquals(primary.y(), next.y(), 1e-6);
                assertEquals(previous.size().width(), primary.size().width(), 1e-6);
                assertEquals(primary.size().width(), next.size().width(), 1e-6);
                assertEquals((previous.x() + next.x()) / 2, primary.x(), 1e-6);
            }
        }
    }

    @Test void pauseAndLiveTimeChangesLeaveThePrimaryActionAndTimelineInPlace() {
        double[] time = {59000};
        Function<String, Component> label = Component::text;
        var playing = MenuScene.screen("player").content(MusicSceneTestSupport.transport(player(label,false,() -> time[0]))).build();
        var before = SceneGeometryTestSupport.measure(playing, 1.8);
        time[0] = 3599000;
        var after = SceneGeometryTestSupport.measure(playing, 1.8);
        var paused = SceneGeometryTestSupport.measure(MenuScene.screen("player").content(MusicSceneTestSupport.transport(player(label,true,() -> time[0]))).build(),1.8);
        for (var key : new String[]{"player-primary", "timeline", "volume", "queue", "more"}) {
            var original = element(before, key);
            for (var frame : new UiFrame[]{after, paused}) {
                var current = element(frame, key);
                assertEquals(original.x(), current.x(), 1e-6, key);
                assertEquals(original.y(), current.y(), 1e-6, key);
                assertEquals(original.size(), current.size(), key);
            }
        }
    }

    @Test void thePlayerUsesTheWorkspaceConstraintsAlongsideItsCollectionAndAuxiliaryPanel() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            Function<String, Component> label = key -> Component.text(FluentTestSupport.text(bundle, Message.MUSIC_SPACE_LABEL, key));
            for (double typography : new double[]{.7, 1, 1.8}) {
                var navigation = MusicLibraryScenes.navigation(MusicWorkspaceState.Source.CATALOG, label, _ -> (_,_) -> {});
                var browser = Ui.text(Component.text("Songs" )).key("songs").build();
                var auxiliary = Ui.text(label.apply("device-queue")).key("queue-heading").build();
                var tools = Ui.button(label.apply("close")).key("close").build();
                var content = MusicWorkspaceComposition.content(navigation, browser, auxiliary, player(label, false, () -> 83000), tools);
                var scene = MenuScene.screen("music").withoutTitle().viewport(MusicWorkspaceComposition.VIEWPORT).content(content).build();
                var frame = SceneGeometryTestSupport.measure(scene, typography);
                assertDisjoint(frame);
                assertTrue(frame.size().width() <= MusicWorkspaceComposition.VIEWPORT.width() + 1e-6);
                assertTrue(element(frame, "timeline").y() > element(frame, "queue-heading").y());
            }
        }
    }

    private static UiNode player(Function<String, Component> label, boolean paused, java.util.function.DoubleSupplier time) {
        var context = MusicPlayerComposition.context(Ui.button(Component.text("Room · Sync group / 16 devices"))
                .key("target").singleLine().textWidth(160).build(), Ui.button(label.apply("repeat-all")).key("mode").singleLine().textWidth(120).build());
        var keys = MusicPlayerComposition.playback(
                MusicPlayerComposition.key("previous", UiIcons.PREVIOUS, label.apply("previous"), (_,_) -> {}, null),
                MusicPlayerComposition.key("primary", paused ? UiIcons.PLAY : UiIcons.PAUSE, label.apply(paused ? "resume" : "pause"), (_,_) -> {}, null),
                MusicPlayerComposition.key("next", UiIcons.NEXT, label.apply("skip-next"), (_,_) -> {}, null));
        var range = UiRange.live(0, 3723000, time).format(value -> Component.text(MusicTime.format((long) value))).withLabels();
        var media = Ui.column().key("media").gap(UiLength.ui(8))
                .child(MusicPlayerComposition.song(Ui.icon(paused ? UiIcons.PAUSE : UiIcons.ACTIVITY_BARS, label.apply(paused ? "paused" : "playing")).key("status").build(), Ui.text(Component.text("很长的歌曲名称 A very long song title for a narrow player"))
                                .key("title").singleLine().textWidth(160).build(),
                        Ui.button(UiIcons.ADD, label.apply("add-playlist")).key("save").build()))
                .child(Ui.text(Component.text("A long artist name 歌手名字")).key("artist").singleLine().textWidth(160).build())
                .child(Ui.slider(label.apply("seek"), range, (_,_,_) -> {}).key("timeline").build()).build();
        var actions = MusicPlayerComposition.actions(Ui.button(UiIcons.VOLUME, label.apply("volume-control")).key("volume").build(),
                Ui.button(UiIcons.QUEUE, label.apply("device-queue")).key("queue").build(), Ui.button(UiIcons.MORE, label.apply("more")).key("more").build());
        return MusicPlayerComposition.content(context, keys, media, actions, null);
    }
}
