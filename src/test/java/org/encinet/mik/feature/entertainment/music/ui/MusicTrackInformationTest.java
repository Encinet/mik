package org.encinet.mik.feature.entertainment.music.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicTrackInformationTest {
    @Test void detailsAndImportInformationUseOpaqueIdsAndKeepTheirIdentitiesAfterReordering() {
        var tracks = List.of(
                MusicWorkspaceStateTest.track("专辑"),
                MusicWorkspaceStateTest.track("另一张专辑"),
                MusicWorkspaceStateTest.track("100% melody"),
                MusicWorkspaceStateTest.track("Song for 42"),
                MusicWorkspaceStateTest.track("album ".repeat(30) + "song"));
        for (String role : List.of("track:", "preview:")) {
            var scene = scene(role, tracks);
            var reordered = scene(role, tracks.reversed());
            assertEquals(tracks.size(), scene.entries().size());
            assertEquals(scene.entries().keySet(), reordered.entries().keySet());
            for (var track : tracks) {
                var node = entry(scene, UiKey.item(role + track.id()).value());
                assertNotNull(node, track.id());
                assertEquals(Component.text(track.details().title()), node.label());
            }
            assertDisjoint(SceneGeometryTestSupport.measure(scene, 1));
        }
    }

    private MenuScene scene(String role, List<MusicTrack> tracks) {
        var information = Ui.column();
        for (var track : tracks) information.child(MusicWorkspace.info(role + track.id(),
                Component.text(track.details().title())).withSummaryLines(2));
        return MenuScene.screen("track-information").content(information.build()).build();
    }
}
