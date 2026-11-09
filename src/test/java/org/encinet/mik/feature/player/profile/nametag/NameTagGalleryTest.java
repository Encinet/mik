package org.encinet.mik.feature.player.profile.nametag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.Ui;
import org.encinet.mik.shared.menu.ui.UiFrame;
import org.encinet.mik.shared.menu.ui.UiSize;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertDisjoint;
import static org.junit.jupiter.api.Assertions.*;

class NameTagGalleryTest {
    @Test void stylesPackByWidthAndEveryFullPageHasThreeRows() throws IOException {
        var rows = new ArrayList<NameTagComposition.Row>();
        for (int i = 0; i < 60; i++) rows.add(new NameTagComposition.Row(new UUID(0, i), Component.text(i % 3 == 0 ? "Longer style " + i : "S" + i)));
        for (double scale : List.of(.7, 1.0, 1.4, 1.8)) {
            int narrow = styles(SceneGeometryTestSupport.measure(gallery(Language.DEFAULT, rows, 0, 160), scale)).size();
            int wide = styles(SceneGeometryTestSupport.measure(gallery(Language.DEFAULT, rows, 0, 640), scale)).size();
            assertTrue(wide > narrow, "A wider host must admit more styles");
            var seen = new HashSet<String>();
            int pages = SceneGeometryTestSupport.measure(gallery(Language.DEFAULT, rows, 0, 640), scale).pagination().getFirst().page().count();
            for (int page = 0; page < pages; page++) {
                var frame = SceneGeometryTestSupport.measure(gallery(Language.DEFAULT, rows, page, 640), scale);
                var styles = styles(frame);
                if (page + 1 < pages) assertEquals(3, styles.stream().map(UiFrame.Element::y).distinct().count());
                assertTrue(styles.stream().map(UiFrame.Element::y).distinct().count() <= 3);
                for (var style : styles) assertTrue(seen.add(style.path()), "No duplicate style across pages");
                assertDisjoint(frame);
            }
            assertEquals(rows.size(), seen.size(), "Every style remains reachable");
        }
        var shortStyles = rows.stream().map(row -> new NameTagComposition.Row(row.id(), Component.text("A"))).toList();
        var frame = SceneGeometryTestSupport.measure(gallery(Language.DEFAULT, shortStyles, 0, 640), 1);
        assertTrue(styles(frame).stream().filter(e -> e.y() == styles(frame).getFirst().y()).count() > 2);
    }

    @Test void galleryWidthIsBoundedWhileStylesExpandOrExposeTheirFullSource() throws IOException {
        var rows = new ArrayList<NameTagComposition.Row>();
        for (int index = 0; index < 7; index++) rows.add(new NameTagComposition.Row(new UUID(0, index),
                Component.text("宽名字😀 ".repeat(40) + "\nHidden").decorate(TextDecoration.BOLD)));
        for (Language language : Language.values()) for (double scale : List.of(.7, 1.0, 1.4, 1.8))
                for (double width : List.of(160.0, 320.0, 640.0, 960.0)) {
            var scene = gallery(language, rows, 0, width);
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var copyHint = Component.text(FluentTestSupport.text(FluentTestSupport.bundle(language), Message.NAMETAG_COPY_HINT));
            assertTrue(styles(frame).size() >= 1 && styles(frame).size() <= 3);
            assertEquals(styles(frame).size(), styles(frame).stream().map(UiFrame.Element::y).distinct().count());
            for (var card : styles(frame)) {
                assertTrue(card.x() + card.size().width() <= Math.min(480, width) + 1e-6,
                        "The entire style list must stay within its allocated width");
                if (width >= 640) assertTrue(card.size().width() > 240,
                        "An individual style must be able to use the list's available width");
                var effect = rows.getFirst().effect();
                org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertFullTextAvailable(effect,card);
                assertEquals(card.node().content().label().equals(effect) ? copyHint : effect.append(Component.newline()).append(copyHint),
                        card.node().input().tooltip().orElseThrow().text());
            }
            assertDisjoint(frame);
        }
    }

    private List<UiFrame.Element> styles(UiFrame frame) {
        return frame.elements().stream().filter(e -> e.node().key().value().equals("copy-style")).toList();
    }

    private MenuScene gallery(Language language, List<NameTagComposition.Row> rows, int index, double width) throws IOException {
        var bundle = FluentTestSupport.bundle(language);
        return MenuScene.screen("gallery").viewport(new UiSize(width, 2048)).content(NameTagComposition.gallery(rows,
                Component.text(FluentTestSupport.text(bundle, Message.NAMETAG_COPY_HINT)),
                _ -> (_, _) -> { }, index, _ -> Ui.column().build(), _ -> { })).build();
    }
}
