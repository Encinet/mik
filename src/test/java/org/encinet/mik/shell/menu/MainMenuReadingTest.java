package org.encinet.mik.shell.menu;

import java.util.HashSet;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainMenuReadingTest {
    @Test void defaultLauncherHasACompleteHumanScaleViewportAboveRealGround() {
        var left = UiTemplates.sidebar("left",Ui.text(Component.text("Community and announcements")).singleLine().build());
        var right = UiTemplates.sidebar("right",Ui.text(Component.text("Current plot and online friends")).singleLine().build());
        var builder = MenuScene.screen("main").stableAnchor()
                .content(MainMenuComposition.content(left,right,0,_ -> {}));
        builder.information("heading",Component.text("Menu")).region("heading");
        for (int index = 0; index < 6; index++) builder.item("entry:" + index,SceneGeometryTestSupport.item(),Component.text("Destination " + index))
                .region("destinations").keepAccessible().primary((_,_) -> {});
        builder.control("quick",Component.text("Quick action")).region("quick"); builder.close(Component.text("Close")).region("footer");
        var scene = builder.build();
        var result = SceneGeometryTestSupport.viewingFrame(scene,1,FloatingMenuFieldOfView.DEFAULT,1);
        assertEquals(2.8,result.distance(),1e-6);
        assertEquals(.25,result.scale(),1e-6, "The larger default survives placement above real ground");
        assertTrue(result.worldSize().width() <= 6.4, result.worldSize().toString());
        assertTrue(result.worldSize().height() <= 3.4, result.worldSize().toString());
        assertTrue(result.worldBottom() > 64, "The complete scene remains above the floor");
        UiSceneTestSupport.assertDisjoint(result.frame());
        assertTrue(result.frame().elements().stream().anyMatch(item -> item.node().key().value().equals("close")));
    }
    @Test void everyLauncherEntryRemainsReachableAcrossReadingPreferences() {
        for (var size : FloatingMenuScale.values()) for (int fov : new int[]{30,70,110})
            for (double typography : new double[]{.7,1,1.8}) {
                Set<String> seen = new HashSet<>();
                int count = 1;
                for (int page = 0; page < count; page++) {
                    var left = Ui.column().key("left").build();
                    var right = Ui.column().key("right").build();
                    var builder = MenuScene.screen("main").viewport(MenuScene.DEFAULT_VIEWPORT)
                            .content(MainMenuComposition.content(left, right, page, _ -> {}));
                    builder.information("heading",Component.text("Menu")).region("heading");
                    for (int index = 0; index < 6; index++) builder.item("entry:"+index, SceneGeometryTestSupport.item(),
                            Component.text("Destination " + index)).region("destinations").keepAccessible().primary((_,_)->{});
                    builder.control("quick", Component.text("Quick action")).region("quick");
                    builder.close(Component.text("Close")).region("footer");
                    var frame = SceneGeometryTestSupport.readableFrame(builder.build(), typography, new FloatingMenuFieldOfView(fov), size.factor());
                    UiSceneTestSupport.assertDisjoint(frame);
                    UiSceneTestSupport.assertUniformGrid(frame.elements().stream()
                            .filter(element -> element.node().key().value().startsWith("entry:")).toList());
                    assertEquals(frame.readingRegion().x() + frame.readingRegion().size().width() / 2, frame.focus().x(), 1e-6);
                    assertEquals(frame.readingRegion().y() + frame.readingRegion().size().height() / 2, frame.focus().y(), 1e-6);
                    count = frame.pagination().getFirst().page().count();
                    assertTrue(count <= 6);
                    assertTrue(frame.elements().stream().anyMatch(element -> element.node().key().value().equals("close")));
                    for (var element : frame.elements()) if (element.node().key().value().startsWith("entry:"))
                        assertTrue(seen.add(element.node().key().value()));
                }
                assertEquals(6, seen.size(), size+" / "+fov+" @ "+typography);
            }
    }
    @Test void launcherBalancesMeasuredEntryBoundsWithoutForcingSquareCells() {
        var builder = MenuScene.screen("main").viewport(MenuScene.DEFAULT_VIEWPORT)
                .content(MainMenuComposition.content(Ui.column().key("left").build(), Ui.column().key("right").build(), 0, _ -> {}));
        builder.information("heading", Component.text("Menu")).region("heading");
        for (int index = 0; index < 6; index++) builder.item("entry:" + index, SceneGeometryTestSupport.item(), Component.text("Entry " + index))
                .region("destinations").keepAccessible().primary((_, _) -> {});
        builder.close(Component.text("Close")).region("footer");
        var frame = SceneGeometryTestSupport.naturalFrame(builder.build(), 1);
        var entries = frame.elements().stream().filter(element -> element.node().key().value().startsWith("entry:")).toList();
        assertEquals(6, entries.size());
        UiSceneTestSupport.assertUniformGrid(entries);
        assertEquals(3, entries.stream().map(UiFrame.Element::x).distinct().count());
        assertEquals(2, entries.stream().map(UiFrame.Element::y).distinct().count());
        assertNotEquals(entries.getFirst().size().width(), entries.getFirst().size().height(),
                "The model and label keep their natural aspect ratio");
        assertEquals(1, frame.pagination().getFirst().page().count());
    }

    @Test void translatedEntriesKeepAllActionsAtDefaultAndLargeText() throws Exception {
        Message[] labels = {Message.MAIN_HOME, Message.PLOT_TITLE, Message.PLOT_DESTINATIONS,
                Message.GOVERNANCE_MENU_TITLE, Message.MAIN_MUSIC, Message.MAIN_ANNOUNCEMENTS};
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (double typography : new double[]{1, 1.8}) {
                var seen = new HashSet<String>();
                int pages = 1;
                for (int page = 0; page < pages; page++) {
                    var builder = MenuScene.screen("main").viewport(MenuScene.DEFAULT_VIEWPORT)
                            .content(MainMenuComposition.content(Ui.column().key("left").build(), Ui.column().key("right").build(), page, _ -> {}));
                    builder.information("heading", Component.text(FluentTestSupport.text(bundle, Message.MAIN_MENU_TITLE))).region("heading");
                    for (int index = 0; index < labels.length; index++) builder.item("entry:" + index, SceneGeometryTestSupport.item(),
                            Component.text(FluentTestSupport.text(bundle, labels[index]))).region("destinations").keepAccessible().primary((_, _) -> {});
                    builder.close(Component.text(FluentTestSupport.text(bundle, Message.CLOSE))).region("footer");
                    var frame = SceneGeometryTestSupport.readableFrame(builder.build(), typography, FloatingMenuFieldOfView.DEFAULT, 1);
                    pages = frame.pagination().getFirst().page().count();
                    UiSceneTestSupport.assertDisjoint(frame);
                    UiSceneTestSupport.assertUniformGrid(frame.elements().stream()
                            .filter(element -> element.node().key().value().startsWith("entry:")).toList());
                    for (var entry : frame.elements()) if (entry.node().key().value().startsWith("entry:")) {
                        assertTrue(seen.add(entry.node().key().value()), language + " @ " + typography);
                        assertTrue(entry.node().input().actions().containsKey(FloatingMenuInteraction.PRIMARY));
                    }
                }
                assertEquals(6, seen.size(), language + " @ " + typography);
            }
        }
    }

}
