package org.encinet.mik.feature.world.vehicle;

import java.util.List;
import java.util.UUID;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleMenuTest {
    @Test void selectedVehicleControlsKeepTheirExactIdentityAndRequestedAccess() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            var instance = instance(h); configure(h, instance);
            var menu = new VehicleMenu(h.plugin, h.language, h.module); menu.open(h.player);
            h.click("vehicle:" + instance.id);
            assertTrue(h.scene.entries().values().stream().noneMatch(entry -> entry.id().endsWith("/fuel")));
            h.click("maintenance");
            h.click("fuel"); verify(h.module).service(h.player, instance.id, true);
            h.click("repair"); verify(h.module).service(h.player, instance.id, false);
            var action = UiSceneTestSupport.entry(h.scene, "access").trigger(FloatingMenuInteraction.PRIMARY);
            instance.publicAccess = true;
            action.execute(h.player, null, FloatingMenuInteraction.PRIMARY);
            verify(h.module).setAccess(h.player, instance.id, true);
            menu.close();
        }
    }
    @Test void staleCallbacksAndReplacedInstancesCannotServiceAnotherVehicle() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            var instance = instance(h); configure(h, instance);
            var menu = new VehicleMenu(h.plugin, h.language, h.module); menu.open(h.player); h.click("vehicle:" + instance.id); h.click("maintenance");
            var action = UiSceneTestSupport.entry(h.scene, "fuel").trigger(FloatingMenuInteraction.PRIMARY);
            menu.open(h.player); action.execute(h.player, null, FloatingMenuInteraction.PRIMARY);
            verify(h.module, never()).service(any(), any(), anyBoolean());
            h.click("vehicle:" + instance.id); h.click("maintenance");
            doThrow(new IllegalArgumentException("Vehicle changed")).when(h.module).requireSame(instance);
            h.click("fuel"); verify(h.module, never()).service(any(), any(), anyBoolean());
            menu.close();
        }
    }
    @Test void vehiclePagesRemainReadableAndUnsafeServiceButtonsExplainTheirState() throws Exception {
        for (var language : Language.values()) try (var h = new VehicleEditorHarness(language)) {
            var instance = instance(h); configure(h, instance); instance.body.engineRunning = true;
            var menu = new VehicleMenu(h.plugin, h.language, h.module); menu.open(h.player); check(h);
            h.click("vehicle:" + instance.id); check(h);
            h.click("details"); check(h); h.click("back");
            h.click("maintenance"); check(h);
            assertFalse(UiSceneTestSupport.entry(h.scene, "fuel").enabled());
            assertNotNull(UiSceneTestSupport.entry(h.scene, "fuel").disabledReason());
            h.click("driving"); check(h);
            assertNotNull(UiSceneTestSupport.entry(h.scene, "drive"));
            menu.close();
        }
    }
    private static void check(VehicleEditorHarness h) {
        for (int fov : new int[]{30, 110}) {
            var frame = SceneGeometryTestSupport.readableFrame(h.scene, 1.8, new FloatingMenuFieldOfView(fov), 1);
            UiSceneTestSupport.assertDisjoint(frame);
            assertTrue(frame.readingRegion().size().height() > 0);
        }
    }
    private static VehicleInstance instance(VehicleEditorHarness h) {
        return new VehicleInstance(UUID.randomUUID(), UUID.randomUUID(), h.player.getUniqueId(), VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO));
    }
    private static void configure(VehicleEditorHarness h, VehicleInstance instance) {
        when(h.module.vehicles(h.player)).thenReturn(List.of(instance));
        when(h.module.menuTarget(h.player, instance.id)).thenReturn(instance);
        when(h.module.nearby(h.player, instance)).thenReturn(true);
        when(h.module.manages(h.player, instance)).thenReturn(true);
    }
}
