package org.encinet.mik.feature.world.vehicle;

import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorWorkflowLayoutTest {
    @Test void actualEditorPagesStayReadableInEveryLanguageAtNarrowFovAndLargeText() throws Exception {
        for (var language : Language.values()) try (var h = new VehicleEditorHarness(language)) {
            h.edit(); check(h, language, "overview");
            h.click("front"); check(h, language, "front"); h.click("back");
            h.click("section:parameters"); check(h, language, "parameter groups");
            h.click("body"); check(h, language, "body parameters");
            h.click("center-of-mass"); check(h, language, "center of mass");
            h.click("section:SEATS"); check(h, language, "seat list");
            h.click("component:0"); check(h, language, "seat inspector");
            h.click("object-tool:exit"); check(h, language, "seat exit");
            h.click("section:COLLIDERS"); h.click("component:0"); h.click("object-tool:SIZE"); check(h, language, "collision size");
            h.click("section:PARTS"); h.click("component:0"); h.click("object-tool:ROTATE"); check(h, language, "part rotation");
            h.click("object-tool:SCALE"); check(h, language, "part scale");
            h.click("object-tool:inspector"); h.click("option"); check(h, language, "display properties");
            h.click("property:BILLBOARD"); check(h, language, "billboard choices");
        }
    }

    private static void check(VehicleEditorHarness h, Language language, String page) {
        for (double font : new double[]{.7, 1.8}) for (int fov : new int[]{30, 110}) {
            var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.scene, font, new FloatingMenuFieldOfView(fov), 1),
                    language + " / " + page + " / " + font + " / " + fov);
            UiSceneTestSupport.assertDisjoint(frame);
            assertTrue(frame.readingRegion().size().width() > 0);
            assertTrue(frame.readingRegion().size().height() > 0);
            assertNotNull(UiSceneTestSupport.element(frame, "dismiss"));
        }
    }
}
