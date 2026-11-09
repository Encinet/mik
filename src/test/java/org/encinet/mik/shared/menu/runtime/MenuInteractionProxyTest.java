package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.UiVector3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuInteractionProxyTest {
    @Test void distantControlsKeepTheirAngularFootprintWithinClientPickingReach() {
        var eye = new UiVector3(12,65.62,-3);
        for (var offset : java.util.List.of(new UiVector3(0,0,4),new UiVector3(4,2,3),new UiVector3(-5,-1,4))) {
            var size = new FloatingMenuSize(1.6,.8);
            var proxy = MenuInteractionProxy.project(eye,eye.add(offset),size);
            var center = proxy.base().add(new UiVector3(0,proxy.height() / 2,0));
            var relative = center.subtract(eye);
            double original = length(offset), projected = length(relative), factor = projected / original;
            assertEquals(2.5,projected,1e-8);
            assertEquals(offset.multiply(factor).right(),relative.right(),1e-8);
            assertEquals(offset.multiply(factor).up(),relative.up(),1e-8);
            assertEquals(offset.multiply(factor).forward(),relative.forward(),1e-8);
            assertEquals(size.width() / original,proxy.width() / projected,1e-8);
            assertEquals(size.height() / original,proxy.height() / projected,1e-8);
            assertEquals(proxy,MenuInteractionProxy.project(eye,eye.add(offset),size),"Unchanged frames need no metadata or teleport");
        }
    }

    @Test void nearbyControlsRemainAtTheirActualSurfaceAndEyeMovementReprojectsOnlyThePickingAid() {
        var eye = new UiVector3(0,65,0); var center = new UiVector3(0,65,2); var size = new FloatingMenuSize(1,.5);
        var nearby = MenuInteractionProxy.project(eye,center,size);
        assertEquals(new UiVector3(0,64.75,2),nearby.base()); assertEquals(1,nearby.width()); assertEquals(.5,nearby.height());
        var movedEye = new UiVector3(0,65,-2);
        var projected = MenuInteractionProxy.project(movedEye,center,size);
        assertEquals(2.5,length(projected.base().add(new UiVector3(0,projected.height() / 2,0)).subtract(movedEye)),1e-8);
        assertEquals(new UiVector3(0,65,2),center,"Client assistance never changes visual placement");
    }

    private static double length(UiVector3 point) { return Math.hypot(Math.hypot(point.right(),point.up()),point.forward()); }
}
