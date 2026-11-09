package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuIconRendererTest {
    @Test void labelsFramesAndTypographyNeverResizeAnIconButtonOrLeakTheLabelIntoItsPaint() {
        for (double textScale : new double[]{.7,1,1.8}) {
            UiFrame original = null;
            for (String name : List.of("Pause","关闭此浮窗","Eine sehr lange Beschreibung ".repeat(5)))
                for (var icon : List.of(UiIcons.PLAY,UiIcons.PAUSE,UiIcons.LOADING)) {
                var scene = MenuScene.screen("icons").content(Ui.button(icon,Component.text(name)).key("action").primary((_,_)->{}).build()).build();
                var geometry = SceneGeometry.measure(scene,textScale,FloatingMenuFieldOfView.DEFAULT,1);
                var frame = geometry.frame();
                if (original == null) original = frame;
                assertEquals(original.size(),frame.size());
                assertEquals(original.elements().getFirst().size(),frame.elements().getFirst().size());
                assertEquals(icon.partCount()*FloatingMenuDecoration.IconPart.Layer.values().length,geometry.generatedDecorations().size());
                var entry = geometry.definition().entries().values().iterator().next();
                assertEquals(Component.text(name),MenuTooltipContent.resolve(entry,Component.empty(),_->false).text());
                assertEquals(Component.text(name),entry.label());
                assertEquals(1,geometry.measurements().values().iterator().next().text().lines());
            }
        }
    }

    @Test void inlineIconsFollowTheirRowsTypeSizeAndStayCenteredWithText() {
        Double ratio = null;
        for (double typography : new double[]{.7,1,1.4,1.8}) for (var style : List.of(UiStyle.PLAIN,
                UiStyle.PLAIN.withTextScale(.85), UiTheme.HEADING, UiTheme.TITLE)) {
            var row = Ui.row().style(style)
                    .child(Ui.icon(UiIcons.ACTIVITY_BARS,Component.text("Playing")).key("status").build())
                    .child(Ui.text(Component.text("音乐 Music")).key("text").singleLine().build())
                    .child(Ui.button(UiIcons.VOLUME,Component.text("Volume")).key("volume").build())
                    .child(Ui.button(UiIcons.CROSS,Component.text("Clear")).key("clear").build()).build();
            var geometry = SceneGeometry.measure(MenuScene.screen("inline").content(row).build(),typography,FloatingMenuFieldOfView.DEFAULT,1);
            assertDisjoint(geometry.frame());
            var icon = geometry.frame().elements().stream().filter(element -> element.path().endsWith("status")).findFirst().orElseThrow();
            var text = geometry.frame().elements().stream().filter(element -> element.path().endsWith("text")).findFirst().orElseThrow();
            var clear = geometry.frame().elements().stream().filter(element -> element.path().endsWith("clear")).findFirst().orElseThrow();
            double current = icon.size().height()/text.size().height();
            if (ratio == null) ratio = current;
            assertEquals(ratio,current,1e-6);
            assertTrue(current > .9 && current < 1);
            assertEquals(icon.y()+icon.size().height()/2,text.y()+text.size().height()/2,1e-6);
            assertEquals(icon.y()+icon.size().height()/2,clear.y()+clear.size().height()/2,1e-6);
            var status = geometry.icons().values().stream().filter(part -> part.source().endsWith("status")).findFirst().orElseThrow();
            var action = geometry.icons().values().stream().filter(part -> part.source().endsWith("clear")).findFirst().orElseThrow();
            var volume = geometry.icons().values().stream().filter(part -> part.source().endsWith("volume")).findFirst().orElseThrow();
            assertEquals(icon.size().height()*UiSceneContent.WORLD_PER_UI,status.size(),1e-6);
            assertEquals(status.size(),action.size(),1e-6);
            assertEquals(status.size(),volume.size(),1e-6);
            assertTrue(clear.size().height() > icon.size().height());
            if (typography == 1 && style == UiStyle.PLAIN) assertEquals(new UiSize(21,21),clear.size());
        }
    }

    @Test void iconAndTextButtonsShareOneControlHeightAtEveryInheritedTextSize() {
        for (double typography : new double[] {.7,1,1.4,1.8}) for (var style : List.of(UiStyle.PLAIN,
                UiStyle.PLAIN.withTextScale(.85), UiTheme.HEADING, UiTheme.TITLE)) {
            var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(8))).style(style)
                    .child(Ui.button(Component.text("Identity")).key("identity").build())
                    .child(Ui.button(UiIcons.REFRESH, Component.text("Refresh")).key("refresh").build())
                    .child(Ui.button(UiIcons.CROSS, Component.text("Close")).key("close").disabled(Component.text("Busy")).build())
                    .build();
            var scene = MenuScene.screen("mixed-tools").content(tools).build();
            var frame = SceneGeometryTestSupport.measure(scene, typography);
            assertDisjoint(frame);
            var text = element(frame, "identity");
            for (String key : List.of("refresh", "close")) {
                var icon = element(frame, key);
                assertEquals(text.size().height(), icon.size().height(), 1e-6);
                assertEquals(icon.size().height(), icon.size().width(), 1e-6);
                assertEquals(text.y()+text.size().height()/2, icon.y()+icon.size().height()/2, 1e-6);
            }
        }
    }

    @Test void tiltedIconsUseTheirControlsPlaneAndEveryPartRemainsWithinItsStableCanvas() {
        var node = Ui.group(Ui.row().child(Ui.button(UiIcons.CROSS,Component.text("Close")).key("close").build())
                .child(Ui.icon(UiIcons.ACTIVITY_BARS,Component.text("Playing")).key("status").build()).build())
                .transform(new UiTransform(new UiVector3(3,4,2),UiRotation.degrees(20,12,9),1.4)).build();
        var scene = MenuScene.screen("tilt").content(node).build();
        var geometry = SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1);
        assertDisjoint(geometry.frame());
        assertFalse(entry(scene,"status").interactive());
        for (var decoration : geometry.generatedDecorations().values()) {
            var part = (FloatingMenuDecoration.IconPart) decoration.content();
            var pose = ((FloatingMenuPlacement.Local)decoration.placement()).pose();
            assertEquals(geometry.poses().get(part.source()),pose);
            assertNotEquals(UiRotation.IDENTITY,pose.rotation());
            for (int tick = 0; tick < 32; tick++) {
                var paint = MenuIconRenderer.paint(part,tick,false,true,UiPalette.DEFAULT);
                double radians = Math.toRadians(paint.roll());
                double half = (Math.abs(Math.cos(radians))*paint.width()+Math.abs(Math.sin(radians))*paint.height())/2;
                assertTrue(Math.abs(paint.right())+half <= part.size()/2+1e-6);
                assertEquals(paint.color(),MenuIconRenderer.paint(part,tick,false,false,UiPalette.DEFAULT).color());
                assertTrue(MenuIconRenderer.paint(part,tick,false,false,UiPalette.DEFAULT).alpha() < paint.alpha());
            }
        }
    }

    @Test void foregroundLoadingOwnsTheOnlyLoopAndIconPartsCountAgainstTheSceneEntityBudget() {
        var activity = part(UiIcons.ACTIVITY_BARS,0,FloatingMenuDecoration.IconPart.Layer.CORE);
        var loading = part(UiIcons.LOADING,0,FloatingMenuDecoration.IconPart.Layer.CORE);
        assertEquals(MenuPopover.scoped("refresh"),MenuIconRenderer.animationOwner(Map.of("player",activity,MenuPopover.scoped("refresh"),loading)));
        assertEquals("refresh",MenuIconRenderer.animationOwner(Map.of("player",activity,"refresh",loading)));
        var row = Ui.row();
        for (int i=0;i<250;i++) row.child(Ui.icon(UiIcons.LOADING,Component.text("Waiting")).key("busy:"+i).build());
        var scene = MenuScene.screen("budget").viewport(new UiSize(4096,4096)).content(
                Ui.layout(UiLayouts.wrap(UiLength.ZERO,UiLength.ZERO)).children(row.build().children().stream().map(UiChild::node).toList()).build()).build();
        assertThrows(UiOverflowException.class,()->SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1));
    }

    @Test void smoothCurvesHaveContinuousCoresInsteadOfPolygonCornersOrPinholes() {
        assertArcCovered(UiIcons.REFRESH,12,12,8,32,328);
        assertArcCovered(UiIcons.UNDO,12,12,8,-148,148);
        assertArcCovered(UiIcons.SEARCH,10.5,10.5,6,0,360);
        assertArcCovered(UiIcons.VOLUME,11,12,8,-33,33);
    }

    @Test void everyNativeLayerStaysInTheCanvasAndSoftEdgesSitBehindAllCores() throws Exception {
        var touching = UiIcon.of(UiIcon.Part.rectangle(0,0,24,24));
        var icons = new ArrayList<UiIcon>();
        icons.add(touching);
        for (var field : UiIcons.class.getFields()) if (field.getType() == UiIcon.class) icons.add((UiIcon)field.get(null));
        for (var icon : icons) for (int tick=0; tick<64; tick+=2) {
            double frontEdge = 0, backCore = 1;
            for (int index=0; index<icon.partCount(); index++) {
                var edge = MenuIconRenderer.paint(part(icon,index,FloatingMenuDecoration.IconPart.Layer.FEATHER),tick,false,true,UiPalette.DEFAULT);
                var core = MenuIconRenderer.paint(part(icon,index,FloatingMenuDecoration.IconPart.Layer.CORE),tick,false,true,UiPalette.DEFAULT);
                assertTrue(edge.width() >= core.width() && edge.height() >= core.height());
                assertEquals(core.alpha()*.35,edge.alpha(),1e-8);
                frontEdge = Math.max(frontEdge,edge.forward());
                backCore = Math.min(backCore,core.forward());
                for (var paint : List.of(edge,core)) {
                    double angle = Math.toRadians(paint.roll()), cosine = Math.abs(Math.cos(angle)), sine = Math.abs(Math.sin(angle));
                    assertTrue(Math.abs(paint.right())+(cosine*paint.width()+sine*paint.height())/2 <= UiIcon.CANVAS/2d+1e-6);
                    assertTrue(Math.abs(paint.up())+(sine*paint.width()+cosine*paint.height())/2 <= UiIcon.CANVAS/2d+1e-6);
                }
            }
            assertTrue(frontEdge < backCore);
        }
    }

    @Test void diagonalStrokesRetainACrispCoreAndALowerContrastEdge() {
        var icon = UiIcon.of(UiIcon.Part.line(5,5,19,19));
        var edge = MenuIconRenderer.paint(part(icon,0,FloatingMenuDecoration.IconPart.Layer.FEATHER),0,true,true,UiPalette.DEFAULT);
        var core = MenuIconRenderer.paint(part(icon,0,FloatingMenuDecoration.IconPart.Layer.CORE),0,true,true,UiPalette.DEFAULT);
        assertTrue(contains(core,0,0));
        assertFalse(contains(core,Math.sqrt(.5),Math.sqrt(.5)));
        assertTrue(contains(edge,Math.sqrt(.5),Math.sqrt(.5)));
        assertEquals(1,core.alpha());
        assertEquals(.35,edge.alpha());
    }

    private static void assertArcCovered(UiIcon icon, double x, double y, double radius, double start, double end) {
        var cores = java.util.stream.IntStream.range(0,icon.partCount())
                .mapToObj(index -> MenuIconRenderer.paint(part(icon,index,FloatingMenuDecoration.IconPart.Layer.CORE),0,true,true,UiPalette.DEFAULT)).toList();
        for (double angle=start; angle<=end; angle+=.5) for (double distance : new double[]{radius-.5,radius,radius+.5}) {
            double radians = Math.toRadians(angle), right = x+Math.cos(radians)*distance-12, up = 12-y-Math.sin(radians)*distance;
            assertTrue(cores.stream().anyMatch(paint -> contains(paint,right,up)),"Missing curve coverage at "+angle+"°, radius "+distance);
        }
    }

    private static boolean contains(MenuIconRenderer.Paint paint, double right, double up) {
        double angle = Math.toRadians(-paint.roll()), dx = right-paint.right(), dy = up-paint.up();
        return Math.abs(dx*Math.cos(angle)-dy*Math.sin(angle)) <= paint.width()/2+1e-6
                && Math.abs(dx*Math.sin(angle)+dy*Math.cos(angle)) <= paint.height()/2+1e-6;
    }

    private static FloatingMenuDecoration.IconPart part(UiIcon icon, int index, FloatingMenuDecoration.IconPart.Layer layer) {
        return new FloatingMenuDecoration.IconPart("icon",icon,index,layer,UiIcon.CANVAS,UiColor.themed(UiColorRole.TEXT),1);
    }
}
