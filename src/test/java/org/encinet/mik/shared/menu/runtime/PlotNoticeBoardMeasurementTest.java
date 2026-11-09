package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.feature.world.plot.PlotMenuLayouts;
import org.encinet.mik.feature.world.plot.PlotNoticeBoard;
import org.encinet.mik.feature.world.plot.PlotNoticeTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

class PlotNoticeBoardMeasurementTest {
    @Test
    void readingPageAndNavigationRemainSeparatedAtEveryTextScale() {
        for (String body : List.of("地".repeat(240), "🌿".repeat(240),
                "A long garden announcement ".repeat(8),
                "地".repeat(232) + "\n".repeat(7))) {
            var notice = new PlotNoticeBoard(java.util.UUID.randomUUID(), "Garden announcement board", java.util.UUID.randomUUID(), "Owner", body, "");
            var builder = MenuScene.screen("notice-measurement")
                    .content(PlotMenuLayouts.noticeBoard(PlotNoticeTestSupport.reading(notice, org.encinet.mik.shared.menu.ui.UiReadingPosition.START, _ -> {})));
            builder.back(Component.text("返回 Back")).region("navigation");
            var definition = builder.build();
            for (FloatingMenuTextScale scale : FloatingMenuTextScale.values()) {
                var measured = SceneGeometryTestSupport.measure(definition, scale.factor());
                org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertDisjoint(measured);
            }
        }
    }

}
