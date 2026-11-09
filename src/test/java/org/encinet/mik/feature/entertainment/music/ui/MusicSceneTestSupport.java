package org.encinet.mik.feature.entertainment.music.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;

/** Task fixtures use the production host and the same complete-page viewport. */
final class MusicSceneTestSupport {
    private MusicSceneTestSupport() { }

    static MenuScene auxiliary(UiNode content, int width) {
        return MenuScene.screen("music-auxiliary-fixture").withoutTitle().viewport(new UiSize(width,2048))
                .content(MusicWorkspaceComposition.content(Ui.column().build(),library(),
                        MusicWorkspaceComposition.task(new java.util.UUID(44,55),content),Ui.column().build(),Ui.column().build())).build();
    }

    static UiNode transport(UiNode content) {
        return MusicWorkspaceComposition.content(Ui.column().build(),library(),null,content,Ui.column().build());
    }

    private static UiNode library() { return Ui.text(Component.text("Songs")).key("library-reading").build(); }
}
