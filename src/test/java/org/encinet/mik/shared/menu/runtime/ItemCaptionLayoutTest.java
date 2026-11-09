package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.encinet.mik.shared.menu.FloatingMenuEasing;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertDisjoint;
import static org.junit.jupiter.api.Assertions.*;

class ItemCaptionLayoutTest {
    private static final List<String> CAPTIONS = List.of("Icon", "First\nSecond\nThird\nFourth",
            "需要自动换行的立体图标名称与内容说明", "A longer description beside another control", "🎵 Music\n<source>", "i");

    @Test void iconsMultilineCaptionsAndNeighboringTextFitAcrossResponsiveGridsAndSpatialTransforms() {
        for (double typography : new double[]{.7, 1, 1.4, 1.8}) for (double width : new double[]{480, 640, 960}) {
            var grid = Ui.layout(UiLayouts.adaptiveGrid(3, UiLength.ui(10), UiLength.ui(12))).key("icons");
            for (int i = 0; i < CAPTIONS.size(); i++) grid.child(Ui.item(item(), Component.text(CAPTIONS.get(i)))
                    .key("icon:" + i).textWidth(96).style(UiStyle.CONTROL.withTextScale(i == 2 ? 1.4 : 1)).build());
            UiNode content = Ui.surface(Ui.column().gap(UiLength.ui(8))
                    .child(Ui.heading(Component.text("Heading above the icons")).key("heading").build())
                    .child(grid.build())
                    .child(Ui.text(Component.text("Information below the icons")).key("information").build()).build())
                    .key("panel").style(UiStyle.PANEL).build();
            var scene = MenuScene.screen("icon-grid").viewport(new UiSize(width, 2048))
                    .animation(new FloatingMenuAnimation(6, 4, 3, .12, .045, .06, .15, .08,
                            FloatingMenuEasing.CUBIC_OUT, FloatingMenuEasing.CUBIC_OUT))
                    .content(Ui.group(content).transform(UiTransform.IDENTITY.rotated(25, 15, 32).scaled(1.4)).build()).build();
            var frame = assertDoesNotThrow(() -> naturalFrame(scene, typography), "viewport=" + width + " typography=" + typography);
            assertDisjoint(frame);
            assertNaturalVisualContentsFit(scene, typography);
        }
    }

    @Test void tableCellsReserveTheCompleteItemAndCaptionStackBesidePlainText() {
        for (double typography : new double[]{.7, 1, 1.4, 1.8}) {
            var table = Ui.<String>table().key(UiKey.named("mixed-table"))
                    .column(new UiTable.Column<>(UiKey.named("preview"), Ui.heading(Component.text("Preview")).build(), UiTrack.content(),
                            label -> Ui.item(item(), Component.text(label)).key("icon").textWidth(80).build()))
                    .column(new UiTable.Column<>(UiKey.named("description"), Ui.heading(Component.text("Details")).build(), UiTrack.fraction(1),
                            label -> Ui.text(Component.text("Text beside the icon: " + label)).key("text").textWidth(160).build()))
                    .rows(CAPTIONS.subList(0, 4), UiKey::item).build();
            var scene = MenuScene.screen("icon-table").content(Ui.surface(table).style(UiStyle.PANEL).build()).build();
            var frame = assertDoesNotThrow(() -> naturalFrame(scene, typography));
            assertDisjoint(frame);
            assertNaturalVisualContentsFit(scene, typography);
        }
    }
}
