package org.encinet.mik.module.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.plot.PlotMenuLayouts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotPermissionTableMeasurementTest {
    private static final Map<Message, List<Message>> CATEGORIES = Map.of(
            Message.PLOT_PERMISSION_CATEGORY_BUILD, List.of(Message.PLOT_PERMISSION_PLACE,
                    Message.PLOT_PERMISSION_BREAK, Message.PLOT_PERMISSION_BUCKET, Message.PLOT_PERMISSION_IGNITE,
                    Message.PLOT_PERMISSION_MODIFY, Message.PLOT_PERMISSION_HARVEST),
            Message.PLOT_PERMISSION_CATEGORY_INTERACTION, List.of(Message.PLOT_PERMISSION_DOOR,
                    Message.PLOT_PERMISSION_SWITCH, Message.PLOT_PERMISSION_WORKSTATION, Message.PLOT_PERMISSION_SIGN),
            Message.PLOT_PERMISSION_CATEGORY_STORAGE, List.of(Message.PLOT_MENU_FLAG_CONTAINER,
                    Message.PLOT_PERMISSION_ENTITY_STORAGE, Message.PLOT_PERMISSION_ITEM_PICKUP,
                    Message.PLOT_PERMISSION_ITEM_DROP),
            Message.PLOT_PERMISSION_CATEGORY_ENTITY, List.of(Message.PLOT_MENU_FLAG_ENTITY, Message.PLOT_PERMISSION_TRADE,
                    Message.PLOT_PERMISSION_RIDE, Message.PLOT_PERMISSION_ANIMAL,
                    Message.PLOT_PERMISSION_ENTITY_DAMAGE, Message.PLOT_PERMISSION_DECORATION),
            Message.PLOT_PERMISSION_CATEGORY_MANAGEMENT, List.of(Message.PLOT_PERMISSION_MANAGE_SETTINGS,
                    Message.PLOT_PERMISSION_MANAGE_AREA, Message.PLOT_PERMISSION_MANAGE_MEMBERS,
                    Message.PLOT_PERMISSION_MANAGE_PERMISSIONS, Message.PLOT_PERMISSION_TRANSFER,
                    Message.PLOT_PERMISSION_DELETE));
    private static final List<Message> SOURCES = List.of(Message.PLOT_PERMISSION_SOURCE_LOCAL,
            Message.PLOT_PERMISSION_SOURCE_PARENT,
            Message.PLOT_PERMISSION_SOURCE_ROLE, Message.PLOT_PERMISSION_SOURCE_OWNER,
            Message.PLOT_PERMISSION_SOURCE_GROUP);

    @Test
    void allLocaleCategoriesAndTextScalesHaveSeparatedMeasuredTextAndClickAreas() throws IOException {
        int locales = 0;
        boolean wrappedLabel = false;
        try (var paths = Files.list(Path.of("src/main/resources/lang"))) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".ftl")).sorted().toList()) {
                Map<String, String> messages = messages(path);
                locales++;
                for (var category : CATEGORIES.entrySet()) {
                    for (boolean draft : List.of(false, true)) {
                        FloatingMenuDefinition definition = definition(messages, category.getKey(), category.getValue(), draft);
                        for (FloatingMenuTextScale scale : FloatingMenuTextScale.values()) {
                            List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
                            for (var entry : definition.entries().values()) {
                                assertEquals(FloatingMenuElementStyle.TEXT, entry.style());
                                var measurement = FloatingMenuNodeSizing.measure(entry, scale.factor());
                                if (entry.region().endsWith("-group-heading") && measurement.text().lines() > 1)
                                    wrappedLabel = true;
                                nodes.add(new FloatingMenuLayout.Node(entry.id(), entry.style(), entry.role(),
                                        entry.region(), measurement.footprint()));
                            }
                            assertSeparated(nodes, path.getFileName() + ":" + category.getKey() + ":" + scale + ":" + draft);
                        }
                    }
                }
            }
        }
        assertEquals(16, locales);
        assertTrue(wrappedLabel);
    }

    private static FloatingMenuDefinition definition(Map<String, String> messages, Message category,
                                                      List<Message> operations, boolean draft) {
        var builder = FloatingMenuDefinition.screen("plot-permissions-measured");
        builder.information("heading", Component.text(draft ? text(messages, Message.PLOT_MEMBER_DRAFT_TITLE)
                        .replace("{ $arg0 }", "A long player name 成员名称测试") : "A long plot name 地段名称测试"))
                .region("heading").textWidth(FloatingMenuTextWidth.WIDE);
        builder.control("subject", Component.text((draft ? text(messages, Message.PLOT_MEMBER_DRAFT_ROLE)
                        .replace("{ $arg0 }", text(messages, Message.PLOT_MENU_GROUP_ADMIN))
                        : text(messages, Message.PLOT_MENU_GROUP_NEWCOMER)) + " ▾"))
                .region("subject").textWidth(FloatingMenuTextWidth.COMPACT)
                .alignment(FloatingMenuDecoration.Alignment.LEFT).primary((viewer, handle) -> { });
        builder.information("section", Component.text(text(messages, category)))
                .region("section").textWidth(FloatingMenuTextWidth.WIDE)
                .alignment(FloatingMenuDecoration.Alignment.LEFT);
        for (Message next : CATEGORIES.keySet()) {
            builder.control(next.key(), Component.text((next == category ? "› " : "  ") + text(messages, next)))
                    .region("categories").textWidth(FloatingMenuTextWidth.COMPACT)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT).primary((viewer, handle) -> { });
        }
        builder.information("operation-heading", Component.text(text(messages, Message.PLOT_PERMISSION_OPERATION)))
                .region("operation-heading").spatialOnly();
        builder.information("result-heading", Component.text(text(messages, Message.PLOT_PERMISSION_RESULT)))
                .region("result-heading").spatialOnly();
        for (int index = 0; index < operations.size(); index++) {
            String region = "permission-" + index;
            builder.information(region + "-label", Component.text(text(messages, operations.get(index))))
                    .region(region + "-group-heading").textWidth(FloatingMenuTextWidth.COMPACT)
                    .alignment(FloatingMenuDecoration.Alignment.LEFT);
            Message state = switch (index % 3) {
                case 0 -> Message.PLOT_PERMISSION_DEFAULT_STATUS;
                case 1 -> Message.PLOT_ALLOWED;
                default -> Message.PLOT_DENIED;
            };
            String result = text(messages, state).replace("{ $arg0 }", text(messages, Message.PLOT_DENIED));
            String source = index % 2 == 0 ? text(messages, Message.PLOT_PERMISSION_SOURCE_PARENT)
                    + " · A long inherited plot name 母地段名称测试 → " + text(messages, Message.PLOT_MENU_GROUP_COLLABORATOR)
                    + " → " + text(messages, SOURCES.get(index % SOURCES.size()))
                    : text(messages, Message.PLOT_PERMISSION_PERSONAL_SOURCE);
            Component status = Component.text(result + "\n" + source);
            if (category == Message.PLOT_PERMISSION_CATEGORY_MANAGEMENT
                    && operations.get(index) == Message.PLOT_PERMISSION_MANAGE_PERMISSIONS) {
                builder.information(region, status).region(region).textWidth(FloatingMenuTextWidth.WIDE)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT);
            } else {
                builder.control(region, status).region(region).textWidth(FloatingMenuTextWidth.WIDE)
                        .alignment(FloatingMenuDecoration.Alignment.LEFT).primary((viewer, handle) -> { });
            }
        }
        if (draft) builder.information("hint", Component.text(text(messages, Message.PLOT_MEMBER_DRAFT_HINT)))
                .region("hint").textWidth(FloatingMenuTextWidth.EXPANDED);
        builder.navigation("reset", Component.text(text(messages, draft
                        ? Message.PLOT_MEMBER_DRAFT_RESET : Message.PLOT_PERMISSION_RESET)))
                .region("navigation").primary((viewer, handle) -> { });
        if (draft) builder.navigation("submit", Component.text(text(messages, Message.PLOT_MEMBER_DRAFT_SUBMIT)))
                .region("navigation").primary((viewer, handle) -> { });
        builder.back(Component.text(text(messages, Message.PLOT_BACK))).region("navigation");
        return builder.build();
    }

    private static Map<String, String> messages(Path path) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            int separator = line.indexOf(" = ");
            if (separator > 0) result.put(line.substring(0, separator), line.substring(separator + 3));
        }
        return result;
    }

    private static String text(Map<String, String> messages, Message key) {
        String value = messages.get(key.key());
        assertTrue(value != null && !value.isBlank(), key.key());
        return value;
    }

    private static void assertSeparated(List<FloatingMenuLayout.Node> nodes, String scenario) {
        FloatingMenuLayout layout = PlotMenuLayouts.permissionTable();
        List<FloatingMenuPose> poses = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++)
            poses.add(layout.pose(new FloatingMenuLayout.Context(index, nodes)));
        for (int first = 0; first < nodes.size(); first++) {
            var firstNode = nodes.get(first);
            var firstPose = poses.get(first);
            for (int second = first + 1; second < nodes.size(); second++) {
                var secondNode = nodes.get(second);
                var secondPose = poses.get(second);
                boolean overlapsHorizontal = Math.abs(firstPose.right() - secondPose.right())
                        < (firstNode.size().width() + secondNode.size().width()) / 2 - 1.0E-9;
                boolean overlapsVertical = Math.abs(firstPose.up() - secondPose.up())
                        < (firstNode.size().height() + secondNode.size().height()) / 2 - 1.0E-9;
                assertFalse(overlapsHorizontal && overlapsVertical,
                        scenario + ": " + firstNode.elementId() + " overlaps " + secondNode.elementId());
            }
        }
    }
}
