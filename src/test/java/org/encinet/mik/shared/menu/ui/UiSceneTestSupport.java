package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.UiSceneContent;

import java.util.Map;

/** Shared scene assertions avoid making feature tests depend on flattened entity paths. */
public final class UiSceneTestSupport {
    private UiSceneTestSupport() { }

    public static void assertVector(UiVector3 expected, UiVector3 actual) {
        assertVector(expected, actual, 1e-8);
    }
    public static void assertVector(UiVector3 expected, UiVector3 actual, double tolerance) {
        org.junit.jupiter.api.Assertions.assertEquals(expected.right(), actual.right(), tolerance);
        org.junit.jupiter.api.Assertions.assertEquals(expected.up(), actual.up(), tolerance);
        org.junit.jupiter.api.Assertions.assertEquals(expected.forward(), actual.forward(), tolerance);
    }

    public static void assertRotation(UiRotation expected, UiRotation actual) {
        for (var axis : java.util.List.of(new UiVector3(1, 0, 0), new UiVector3(0, 1, 0), new UiVector3(0, 0, 1)))
            assertVector(expected.apply(axis), actual.apply(axis));
    }

    public static MenuScene fixture(UiNode content, String... ports) {
        return fixtureBuilder(content, ports).build();
    }

    public static MenuScene.Builder fixtureBuilder(UiNode content, String... ports) {
        var scene = MenuScene.screen("fixture").content(content);
        for (String port : ports) scene.information(port, Component.text(port)).region(port);
        return scene;
    }

    public static MenuScene.Entry entry(MenuScene scene, String localKey) {
        String path = elements(scene).entrySet().stream()
                .filter(element -> element.getValue().key().value().equals(localKey))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        return path == null ? null : scene.entries().get(path);
    }

    /** Dynamic pager content is indexed from the published frame rather than the declaration tree. */
    public static MenuScene.Entry entry(MenuScene scene, UiFrame frame, String localKey) {
        return frame.elements().stream().filter(element -> element.node().key().value().equals(localKey))
                .findFirst().map(element -> scene.entries().getOrDefault(element.path(),UiSceneContent.entry(element.path(),element.node()))).orElse(null);
    }

    public static Map<String, UiElement> elements(MenuScene scene) {
        var result = new java.util.LinkedHashMap<>(UiSceneContent.elements(scene.content()));
        if (scene.popover() != null) UiSceneContent.elements(scene.popover().contentTree())
                .forEach((path, element) -> result.put(org.encinet.mik.shared.menu.MenuPopover.scoped(path), element));
        return Map.copyOf(result);
    }

    public static UiFrame frame(MenuScene scene) {
        return UiCompiler.compile(scene.content(), UiConstraints.bounded(scene.viewport().width(), scene.viewport().height()),
                (node, constraints) -> new UiCompiler.ElementMeasurement(
                        new UiSize(Math.min(100, constraints.maxWidth()), 30), node.textWidthPixels()));
    }

    public static UiFrame.Element element(UiFrame frame, String localKey) {
        return frame.elements().stream().filter(element -> element.node().key().value().equals(localKey)).findFirst().orElseThrow();
    }

    /** A measured summary must keep its complete source before any supplemental help. */
    public static void assertFullTextAvailable(Component source, UiFrame.Element rendered) {
        if (source.equals(rendered.node().content().label())) return;
        var help = rendered.node().input().tooltip().orElseThrow(() ->
                new AssertionError("Truncated content has no full-text help: " + rendered.path()));
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        org.junit.jupiter.api.Assertions.assertTrue(plain.serialize(help.text()).startsWith(plain.serialize(source)),rendered.path());
    }

    public static UiFrame.Surface owningSurface(UiFrame frame, UiFrame.Element element) {
        return frame.surfaces().stream().filter(surface -> element.path().startsWith(surface.path()+"/"))
                .max(java.util.Comparator.comparingInt(surface -> surface.path().length())).orElseThrow();
    }

    /** Equal rectangular controls share both edge positions and regular row/column spacing. */
    public static void assertUniformGrid(java.util.List<UiFrame.Element> elements) {
        org.junit.jupiter.api.Assertions.assertFalse(elements.isEmpty());
        var size = elements.getFirst().size();
        for (var element : elements) {
            org.junit.jupiter.api.Assertions.assertEquals(size.width(), element.size().width(), 1e-6, element.path());
            org.junit.jupiter.api.Assertions.assertEquals(size.height(), element.size().height(), 1e-6, element.path());
        }
        assertRegularTracks(elements.stream().mapToDouble(UiFrame.Element::x).distinct().sorted().toArray());
        assertRegularTracks(elements.stream().mapToDouble(UiFrame.Element::y).distinct().sorted().toArray());
    }

    private static void assertRegularTracks(double[] positions) {
        if (positions.length < 3) return;
        double step = positions[1] - positions[0];
        for (int index = 2; index < positions.length; index++)
            org.junit.jupiter.api.Assertions.assertEquals(step, positions[index] - positions[index - 1], 1e-6);
    }

    /** A reading list keeps every visible item on its own row, even in a wide host. */
    public static void assertColumn(java.util.List<UiFrame.Element> elements) {
        org.junit.jupiter.api.Assertions.assertFalse(elements.isEmpty());
        double bottom = Double.NEGATIVE_INFINITY;
        for (var element : elements) {
            org.junit.jupiter.api.Assertions.assertTrue(element.y() >= bottom - 1e-6,element.path());
            bottom = element.y() + element.size().height();
        }
    }

    public static void assertDisjoint(UiFrame frame) {
        for (int i = 0; i < frame.elements().size(); i++) for (int j = i + 1; j < frame.elements().size(); j++) {
            UiFrame.Element a = frame.elements().get(i), b = frame.elements().get(j);
            boolean overlap = a.x() < b.x() + b.size().width() - 1e-6 && b.x() < a.x() + a.size().width() - 1e-6
                    && a.y() < b.y() + b.size().height() - 1e-6 && b.y() < a.y() + a.size().height() - 1e-6;
            if (overlap) throw new AssertionError("Overlapping UI: " + a.path() + " and " + b.path());
        }
    }

    /** Deferred rows recreate action objects; compare geometry, content and interaction shape instead. */
    public static void assertSameLayout(UiFrame expected, UiFrame actual, String detail) {
        org.junit.jupiter.api.Assertions.assertEquals(expected.size(),actual.size(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.surfaces(),actual.surfaces(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.panels(),actual.panels(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.groups(),actual.groups(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.focus(),actual.focus(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.readingAnchor(),actual.readingAnchor(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.readingRegion(),actual.readingRegion(),detail);
        org.junit.jupiter.api.Assertions.assertEquals(expected.elements().size(),actual.elements().size(),detail);
        for (int index = 0; index < expected.elements().size(); index++) {
            var left = expected.elements().get(index); var right = actual.elements().get(index);
            org.junit.jupiter.api.Assertions.assertEquals(left.node().input().actions().keySet(),right.node().input().actions().keySet(),detail + " " + left.path());
            org.junit.jupiter.api.Assertions.assertEquals(left.node().input().focus().isPresent(),right.node().input().focus().isPresent(),detail + " " + left.path());
            org.junit.jupiter.api.Assertions.assertEquals(withoutCallbacks(left),withoutCallbacks(right),detail + " " + left.path());
        }
        org.junit.jupiter.api.Assertions.assertEquals(expected.pagination().stream().map(UiSceneTestSupport::pageShape).toList(),
                actual.pagination().stream().map(UiSceneTestSupport::pageShape).toList(),detail);
    }

    private static java.util.List<?> pageShape(UiFrame.Pagination page) {
        return java.util.List.of(page.path(),page.x(),page.y(),page.size(),page.page(),page.readingPosition());
    }
    private static UiFrame.Element withoutCallbacks(UiFrame.Element element) {
        var node = element.node(); var input = node.input();
        var description = new UiInput(input.target(),Map.of(),java.util.Optional.empty(),input.selected(),input.disabledReason(),input.tooltip());
        var value = new UiElement(node.key(),node.content(),node.role(),description,node.style(),node.sizing(),node.textWidthPixels(),node.alignment(),node.keepAccessible());
        return new UiFrame.Element(element.path(),value,element.x(),element.y(),element.size(),element.baseline());
    }
}
