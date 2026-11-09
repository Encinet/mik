package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;

import java.util.UUID;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;
import static org.junit.jupiter.api.Assertions.*;

/** Feature geometry tests render the production query control, including its editing actions. */
public final class MenuQueryViewTestSupport {
    private MenuQueryViewTestSupport() { }
    public enum Phase { IDLE, EDITING, SUBMITTED }

    public static UiNode content(String value, Phase phase, Component placeholder, Component prompt,
                                 Component clear, Component cancel) {
        var inputs = new MenuInputSessions((_, _) -> { });
        UUID id = UUID.randomUUID();
        try (var editor = new MenuQueryEditor(placeholder, 128,
                new MenuQueryEditor.Labels(prompt, clear, cancel, Component.text("Single line"),
                        _ -> Component.text("Too long")),
                (text, ended) -> inputs.beginChatOnce(id, () -> true, text, ended), _ -> { }, () -> { }, () -> { })) {
            if (phase != Phase.IDLE) {
                var scene = MenuScene.screen("query").content(editor.content(value)).build();
                entry(scene, "preview").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                if (phase == Phase.SUBMITTED) { inputs.offerText(id, value); inputs.tick(); }
            }
            return editor.content(value);
        }
    }

    /** The query stays centered at its natural width or the host's smaller inner width. */
    public static void assertCompactCentered(MenuScene scene, UiFrame frame, double typography) {
        var query = new java.util.concurrent.atomic.AtomicReference<UiNode>();
        var path = new java.util.concurrent.atomic.AtomicReference<String>();
        UiTree.visit(scene.content(), (current, node) -> {
            if (node.key().value().equals("query-input")) { query.set(node); path.set(current); }
        });
        assertNotNull(query.get());
        var owner = frame.panels().stream().filter(panel -> path.get().startsWith(panel.path() + "/"))
                .max(java.util.Comparator.comparingInt(panel -> panel.path().length())).orElseThrow();
        var style = frame.surfaces().stream().filter(surface -> surface.path().equals(owner.path()))
                .findFirst().orElseThrow().style();
        double availableWidth = owner.size().width() - style.padding().horizontal();
        var natural = SceneGeometryTestSupport.measure(MenuScene.screen("query-natural")
                .viewport(new UiSize(availableWidth, scene.viewport().height())).content(query.get()).build(), typography);
        var field = frame.surfaces().stream().filter(surface -> surface.path().startsWith(path.get() + "/")
                && surface.path().endsWith("/query")).findFirst().orElseThrow();
        double left = field.x(), right = field.x() + field.size().width();
        assertEquals(natural.size().width(), right - left, 1e-6, "query width must remain independent of its results");
        assertEquals(owner.x() + owner.size().width() / 2, (left + right) / 2, 1e-6,
                "center the entire query and its actions, rather than only the value preview");
    }
}
