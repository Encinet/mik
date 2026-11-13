package org.encinet.mik.shared.menu.runtime;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuWorldOverlayMaskTest {
    @Test void billboardTextMaskFollowsTheCameraInsteadOfItsUnchangingSpawnYaw() {
        var marker = label(new Location(WORLD,0,0,8)).billboard();
        var text = assertInstanceOf(FloatingMenuDecoration.Text.class,marker.content());
        var at = ((org.encinet.mik.shared.menu.FloatingMenuPlacement.World)marker.placement()).location();
        var mask = new MenuWorldOverlayMask(EYE,List.of(panel(0,0,2,2,1)));
        assertTrue(mask.overlaps(MenuWorldOverlayMask.textSurface(at,180,0,text)));
        for (float yaw : new float[]{0,90,180,270}) assertTrue(mask.overlapsText(at,yaw,30,text));
    }

    @Test void blockOutlinesYieldToMenuSurfacesAndReappearWhenTheMenuMovesAway() {
        var cube = new FloatingMenuDecoration.BlockOutline(org.bukkit.Material.JUKEBOX,.998f,0x70D7FF);
        var at = new Location(WORLD,0,0,8);
        assertTrue(new MenuWorldOverlayMask(EYE,List.of(panel(0,0,2,2,1))).overlapsOutline(at,cube));
        assertFalse(new MenuWorldOverlayMask(EYE,List.of(panel(3,0,2,2,1))).overlapsOutline(at,cube));
        assertFalse(new MenuWorldOverlayMask(EYE,List.of()).overlapsOutline(at,cube));
    }

    private static final World WORLD = world();
    private static final Location EYE = new Location(WORLD,0,0,0);

    @Test void worldLabelsKeepWallPenetrationWhileBothNearAndFarOverlaysYieldToTheInterface() {
        var label = label(new Location(WORLD,0,0,8));
        assertTrue(((FloatingMenuDecoration.Text)label.content()).seeThrough());
        var mask = new MenuWorldOverlayMask(EYE,List.of(panel(0,0,2,2,1)));
        assertFalse(mask.test(new Location(WORLD,0,0,8)));
        assertFalse(mask.test(new Location(WORLD,0,0,1)),"world objects closer than the panel must also leave its reading region clear");
        assertTrue(mask.test(new Location(WORLD,8,0,8)));
        // The World proxy rejects any attempt to read blocks or trace walls.
        assertTrue(new MenuWorldOverlayMask(EYE,List.of()).test(new Location(WORLD,0,0,8)));
    }

    @Test void separatePanelsDoNotMaskTheGapBetweenThem() {
        var mask = new MenuWorldOverlayMask(EYE,List.of(panel(-1,0,2,1,1),panel(1,0,2,1,1)));
        assertTrue(mask.test(new Location(WORLD,0,0,8)));
        assertFalse(mask.test(new Location(WORLD,4,0,8)));
        assertFalse(mask.test(new Location(WORLD,-4,0,8)));
    }

    @Test void theWholePaintedLabelIsCheckedWhenItsAnchorIsOutsideThePanel() {
        Location at = new Location(WORLD,1.4,-.1,2);
        var text = (FloatingMenuDecoration.Text)label(at).content();
        var mask = new MenuWorldOverlayMask(EYE,List.of(panel(0,0,2,2,1)));
        assertTrue(mask.test(at),"a center-only test misses the start of a long label");
        assertTrue(mask.overlaps(MenuWorldOverlayMask.textSurface(at,180,0,text)));
    }

    @Test void particleQuadsKeepClearanceAtPanelEdges() {
        var mask = new MenuWorldOverlayMask(EYE,List.of(panel(0,0,2,2,1)));
        assertFalse(mask.test(new Location(WORLD,1.05,0,2)));
        assertTrue(mask.test(new Location(WORLD,1.5,0,2)));
    }

    @Test void aFreshSnapshotRestoresMarkersAfterClosingOrMovingThePanel() {
        Location point = new Location(WORLD,0,0,8);
        assertFalse(new MenuWorldOverlayMask(EYE,List.of(panel(0,0,2,2,1))).test(point));
        assertTrue(new MenuWorldOverlayMask(EYE,List.of(panel(3,0,2,2,1))).test(point));
        assertTrue(new MenuWorldOverlayMask(EYE,List.of()).test(point));
    }

    @Test void tiltedPanelsAndVolumesRemainMaskedAfterSceneRotationTranslationAndScaling() {
        var panel = new MenuSurfaceAttachment.Surface(new UiVector3(0,0,2),UiRotation.degrees(202,-20,12),
                new FloatingMenuSize(2,1),1,.4);
        var overlay = panel(0,0,8,.5,.3);
        assertTrue(new MenuWorldOverlayMask(EYE,List.of(panel)).overlaps(overlay));
        for (double scale : new double[]{.3,1,2}) for (float yaw : new float[]{-80,0,55}) {
            var rotation = UiRotation.degrees(yaw,-15,0);
            var translation = new UiVector3(12,64,-7);
            var eye = new Location(WORLD,12,64,-7,yaw,15);
            var mask = new MenuWorldOverlayMask(eye,List.of(transformed(panel,rotation,translation,scale)));
            assertTrue(mask.overlaps(transformed(overlay,rotation,translation,scale)));
            assertFalse(mask.overlaps(transformed(panel(12,0,8,.5,.3),rotation,translation,scale)));
        }
    }

    @Test void panelsBehindTheEyeDoNotHideWorldMarkersAndOtherWorldEffectsAreRejected() {
        var mask = new MenuWorldOverlayMask(EYE,List.of(panel(0,0,-2,20,20)));
        assertTrue(mask.test(new Location(WORLD,0,0,8)));
        assertFalse(mask.test(new Location(world(),0,0,8)));
    }

    @Test void surfacesCrossingTheEyePlaneAreClippedBeforeProjection() {
        var crossing = new MenuSurfaceAttachment.Surface(new UiVector3(0,0,.1),UiRotation.degrees(225,15,10),
                new FloatingMenuSize(3,2),1,.5);
        var mask = assertDoesNotThrow(() -> new MenuWorldOverlayMask(EYE,List.of(crossing)));
        assertFalse(mask.test(new Location(WORLD,0,0,8)));
    }

    private static FloatingMenuDecoration label(Location at) {
        return FloatingMenuDecoration.worldText("marker",at,180,0,Component.text("A long device name\n↓"),
                0xD0181B22,3,1.4f,.6f,FloatingMenuDecoration.Alignment.CENTER);
    }

    private static MenuSurfaceAttachment.Surface panel(double x, double y, double z, double width, double height) {
        return new MenuSurfaceAttachment.Surface(new UiVector3(x,y,z),UiRotation.degrees(180,0,0),new FloatingMenuSize(width,height),1);
    }

    private static MenuSurfaceAttachment.Surface transformed(MenuSurfaceAttachment.Surface surface, UiRotation rotation,
                                                             UiVector3 translation, double scale) {
        return new MenuSurfaceAttachment.Surface(translation.add(rotation.apply(surface.center().multiply(scale))),
                rotation.compose(surface.rotation()),surface.size(),surface.scale()*scale,surface.depth());
    }

    private static World world() {
        UUID id = UUID.randomUUID();
        return (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},
                (_,method,_) -> switch (method.getName()) {
                    case "getUID" -> id;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
