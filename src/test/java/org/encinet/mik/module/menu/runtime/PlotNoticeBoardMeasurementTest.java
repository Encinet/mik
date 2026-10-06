package org.encinet.mik.module.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.plot.PlotMenuLayouts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotNoticeBoardMeasurementTest {
    @Test
    void readingPageAndNavigationRemainSeparatedAtEveryTextScale() {
        for (String body : List.of("地".repeat(240), "🌿".repeat(240),
                "A long garden announcement ".repeat(8),
                "地".repeat(232) + "\n".repeat(7))) {
            var builder = FloatingMenuDefinition.screen("notice-measurement")
                    .layout(PlotMenuLayouts.noticeBoard());
            builder.information("heading", Component.text("地段公告板 · Garden announcement board"))
                    .region("heading");
            builder.information("body", Component.text(body)).region("body")
                    .textWidth(FloatingMenuTextWidth.WIDE);
            builder.navigation("previous", Component.text("上一页 Previous page")).region("pagination");
            builder.navigation("next", Component.text("下一页 Next page")).region("pagination");
            builder.back(Component.text("返回 Back")).region("navigation");
            var definition = builder.build();
            for (FloatingMenuTextScale scale : FloatingMenuTextScale.values()) {
                List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
                for (var entry : definition.entries().values()) {
                    var measurement = FloatingMenuNodeSizing.measure(entry, scale.factor());
                    assertTrue(Double.isFinite(measurement.footprint().height()));
                    nodes.add(new FloatingMenuLayout.Node(entry.id(), entry.style(), entry.role(),
                            entry.region(), measurement.footprint()));
                }
                assertSeparated(nodes, scale);
            }
        }
    }

    private static void assertSeparated(List<FloatingMenuLayout.Node> nodes, FloatingMenuTextScale scale) {
        FloatingMenuLayout layout = PlotMenuLayouts.noticeBoard();
        List<FloatingMenuPose> poses = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++)
            poses.add(layout.pose(new FloatingMenuLayout.Context(index, nodes)));
        for (int first = 0; first < nodes.size(); first++) {
            for (int second = first + 1; second < nodes.size(); second++) {
                var firstNode = nodes.get(first);
                var secondNode = nodes.get(second);
                var firstPose = poses.get(first);
                var secondPose = poses.get(second);
                boolean horizontalOverlap = Math.abs(firstPose.right() - secondPose.right())
                        < (firstNode.size().width() + secondNode.size().width()) / 2 - 1.0E-9;
                boolean verticalOverlap = Math.abs(firstPose.up() - secondPose.up())
                        < (firstNode.size().height() + secondNode.size().height()) / 2 - 1.0E-9;
                assertFalse(horizontalOverlap && verticalOverlap,
                        scale + ": " + firstNode.elementId() + " overlaps " + secondNode.elementId());
            }
        }
    }
}
