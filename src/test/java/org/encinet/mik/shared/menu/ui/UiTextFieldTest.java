package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiTextFieldTest {
    @Test void nonemptySourceKeepsItsRichPreviewEvenWhenItHasNoText() {
        var head = Component.object(ObjectContents.playerHead(new java.util.UUID(0, 1)));
        for (Component value : java.util.List.of(head, Component.empty().append(head),
                Component.score("viewer", "points"), Component.empty(), Component.text("", NamedTextColor.WHITE), Component.text("  "))) {
            var scene = MenuScene.screen("field").content(Ui.textField("source").preview(raw -> {
                        assertEquals("source", raw);
                        return value;
                    })
                    .placeholder(Component.text("Empty")).build()).build();
            assertEquals(value, entry(scene, "preview").label());
            assertEquals(value, element(SceneGeometryTestSupport.measure(scene, 1), "preview").node().content().label());
        }
        var spaces = MenuScene.screen("field").content(Ui.textField("  ").placeholder(Component.text("Empty")).build()).build();
        assertEquals(Component.text("  "), entry(spaces, "preview").label());
    }

    @Test void multilinePreviewsRespectRenderedLineLimitsAndKeepTheFullTextAvailable() {
        String raw = "长文本 ".repeat(20);
        var full = Component.text(raw, NamedTextColor.AQUA);
        var scene = MenuScene.screen("multiline").viewport(new UiSize(160, 512))
                .content(Ui.textField(raw).preview(_ -> full).maxLines(4).build()).build();
        var frame = SceneGeometryTestSupport.measure(scene, 1.8);
        var preview = element(frame, "preview");
        assertTrue(SceneGeometryTestSupport.textLines(scene, 1.8, "preview") <= 4);
        assertNotEquals(full, preview.node().content().label());
        assertEquals(full, preview.node().input().tooltip().orElseThrow().text());
        assertEquals(full, entry(scene, "preview").label(), "Layout never replaces the declared source value");
    }

    @Test void fieldsWithoutVisibleToolsGiveThePreviewTheirWholeWidth() {
        for (String raw : java.util.List.of("", "Short")) for (boolean emptyTools : new boolean[] {false, true})
                for (double width : new double[] {160, 240, 480}) for (double scale : new double[] {.7, 1, 1.4, 1.8}) {
            var field = Ui.textField(raw).placeholder(Component.text("Search"));
            if (emptyTools) field.actions(Ui.row().build());
            var scene = MenuScene.screen("field").viewport(new UiSize(width, 512)).content(field.build()).build();
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            var preview = element(frame, "preview");
            assertEquals(0, preview.x(), 1e-6);
            assertEquals(frame.size().width(), preview.size().width(), 1e-6,
                    "Absent or empty tools must not reserve a button slot or gap");
            assertDisjoint(frame);
        }
    }

    @Test void localToolsUseTheAvailableWidthBeforeFallingBelowTheValue() {
        for (String label : java.util.List.of("Terminé", "Готово", "Concluir")) for (double scale : new double[] {1.6, 1.8}) {
            var value = Ui.textField("Short")
                    .actions(Ui.row().gap(UiLength.ui(6))
                            .child(Ui.button(Component.text("×")).key("clear").build())
                            .child(Ui.button(Component.text(label)).key("done").build()).build()).build();
            var scene = MenuScene.screen("field").viewport(new UiSize(480, 512)).content(value).build();
            var frame = SceneGeometryTestSupport.measure(scene, scale);
            assertDisjoint(frame);
            var preview = element(frame, "preview"); var done = element(frame, "done");
            assertEquals(preview.y() + preview.size().height() / 2, done.y() + done.size().height() / 2, 1e-6);
            assertTrue(frame.size().width() > 240, "an intact action group can expand the natural reading width");
            scene = MenuScene.screen("field").viewport(new UiSize(240, 512)).content(value).build();
            frame = SceneGeometryTestSupport.measure(scene, scale);
            assertDisjoint(frame);
            assertTrue(element(frame, "done").y() > element(frame, "preview").y(), "a genuinely narrow host still stacks");
        }
    }

    @Test void emptySourceUsesThePlaceholderWithoutRenderingAndItsLabelCannotCaptureInput() {
        var edits = new AtomicInteger();
        var field = Ui.textField("").preview(_ -> { throw new AssertionError("Empty source must not render a preview"); })
                .key("search").placeholder(Component.text("Empty"))
                .primary((_, _) -> edits.incrementAndGet());
        var scene = MenuScene.screen("field").content(Ui.form().field("search", Component.text("Search"), field.build()).build()).build();
        assertFalse(entry(scene, "label").interactive());
        var value = entry(scene, "preview");
        assertEquals(Component.text("Empty", NamedTextColor.AQUA), value.label());
        assertEquals(FloatingMenuDecoration.Alignment.LEFT, value.alignment());
        assertNull(value.tooltip());
        value.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(1, edits.get());
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        assertDisjoint(frame);
        assertTrue(element(frame, "preview").size().width() >= 160);
    }

    @Test void valueEditingAndAuxiliaryActionsHaveSeparateTargetsAndAvailability() {
        var edits = new AtomicInteger();
        var copies = new AtomicInteger();
        var full = Component.text("Visible prefix", NamedTextColor.GOLD);
        var field = Ui.textField("Visible prefix").preview(_ -> full).primary((_, _) -> edits.incrementAndGet())
                .actions(Ui.button(Component.text("Copy")).key("copy").primary((_, _) -> copies.incrementAndGet()).build());
        var scene = MenuScene.screen("field").content(field.build()).build();
        assertEquals(full, entry(scene, "preview").label());
        entry(scene, "preview").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(1, edits.get());
        assertEquals(0, copies.get());
        entry(scene, "copy").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(1, edits.get());
        assertEquals(1, copies.get());
        assertDisjoint(SceneGeometryTestSupport.measure(scene, 1));

        var reason = Component.text("Saving");
        scene = MenuScene.screen("field").content(field.editing(true).disabled(reason).build()).build();
        var value = entry(scene, "preview");
        assertTrue(value.selected());
        assertFalse(value.enabled());
        assertEquals(reason, value.disabledReason());
        assertEquals(UiPointerBehavior.INSPECT, value.pointerBehavior());
        assertTrue(entry(scene, "copy").enabled(), "The host can keep read-only tools usable while editing is disabled");
    }

    @Test void boundedPreviewsPreserveFormattedValuesAndExplicitHelp() {
        String raw = "很长的歌曲 😀\n第二行\n第三行";
        var full = Component.text(raw, NamedTextColor.AQUA);
        var field = Ui.textField(raw).preview(_ -> full).key("name").maxLines(1).editing(true);
        var scene = MenuScene.screen("field").content(field.build()).build();
        assertEquals(full, entry(scene, "preview").label());
        assertEquals(full, element(SceneGeometryTestSupport.measure(scene,1),"preview").node().input().tooltip().orElseThrow().text());
        assertTrue(entry(scene, "preview").selected());
        var help = Component.text("Only visible to you");
        scene = MenuScene.screen("field").content(field.tooltip(help).build()).build();
        assertEquals(help, entry(scene, "preview").tooltip().text());
        assertEquals(full, entry(scene, "preview").label());
    }

    @Test void typingAndClearingDoNotResizeTheFieldOrWrapSingleLineValues() {
        Double width = null;
        for (String raw : java.util.List.of("", "Short", "A long formatted song title ".repeat(4))) {
            var field = Ui.textField(raw).placeholder(Component.text("Empty"));
            if (!raw.isEmpty()) field.actions(Ui.button(Component.text("Clear")).key("clear").build());
            var scene = MenuScene.screen("field").content(field.build()).build();
            var frame = SceneGeometryTestSupport.measure(scene, 1);
            if (width == null) width = frame.size().width();
            assertEquals(width, frame.size().width(), 1e-6);
            assertEquals(1, SceneGeometryTestSupport.textLines(scene, 1, "preview"));
            assertDisjoint(frame);
            if (!raw.isEmpty()) assertTrue(element(frame, "clear").x() > element(frame, "preview").x());
        }
    }

    @Test void widthTruncationPreservesTheFullValueAndDoesNotReplaceOperationalHelp() {
        String raw = "A song with a sufficiently long name to overflow the preview";
        var full = Component.text(raw, NamedTextColor.GOLD);
        var field = Ui.textField(raw).preview(_ -> full);
        var scene = MenuScene.screen("field").content(field.build()).build();
        var preview = element(SceneGeometryTestSupport.measure(scene, 1.8), "preview");
        assertNotEquals(full, preview.node().content().label());
        assertEquals(full, preview.node().input().tooltip().orElseThrow().text());
        assertEquals(full, entry(scene, "preview").label());
        var help = Component.text("Use the wheel to change this value.");
        scene = MenuScene.screen("field").content(field.tooltip(help).build()).build();
        assertEquals(full.append(Component.newline()).append(help), element(SceneGeometryTestSupport.measure(scene, 1.8), "preview").node().input().tooltip().orElseThrow().text());
    }
}
