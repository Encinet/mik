package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiFormTest {
    @Test void labelsShareATrackAndTheFeedbackSharesARowWithSubmission() {
        var scene = scene(form(), 500);
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        var name = field(frame, "name", "preview");
        var description = field(frame, "description", "preview");
        assertEquals(name.x(), description.x(), 1e-6);
        assertEquals(field(frame, "name", "label").y(), owningSurface(frame, name).y(), 1e-6);
        assertEquals(field(frame, "description", "label").y(), owningSurface(frame, description).y(), 1e-6);
        var hint = element(frame, "hint");
        var cancel = element(frame, "cancel");
        var confirm = element(frame, "confirm");
        assertTrue(hint.x() + hint.size().width() <= cancel.x());
        assertTrue(hint.y() < confirm.y() + confirm.size().height() && confirm.y() < hint.y() + hint.size().height());
        assertTrue(cancel.x() < confirm.x());
        assertEquals(frame.size().width(), confirm.x() + confirm.size().width(), 1e-6);
        assertDisjoint(frame);
    }

    @Test void narrowHostsStackWholeRegionsAndKeepTheActionsTogetherOnTheRight() {
        var scene = scene(form(), 180);
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        for (String key : new String[] {"name", "description"}) {
            var label = field(frame, key, "label");
            var control = field(frame, key, "preview");
            assertTrue(control.y() >= label.y() + label.size().height());
            assertEquals(label.x(), control.x(), 1e-6);
        }
        var hint = element(frame, "hint");
        var cancel = element(frame, "cancel");
        var confirm = element(frame, "confirm");
        assertTrue(cancel.y() >= hint.y() + hint.size().height());
        assertEquals(cancel.y(), confirm.y(), 1e-6);
        assertEquals(frame.size().width(), confirm.x() + confirm.size().width(), 1e-6);
        assertTrue(frame.size().width() <= 180);
        assertDisjoint(frame);
    }

    @Test void reflowKeepsFieldIdentityAndMixedControlsRemainUsable() {
        var calls = new java.util.ArrayList<String>();
        var content = Ui.form()
                .field("name", Component.text("Name"), Ui.textField(Component.text("Draft"))
                        .primary((_, _) -> calls.add("edit")).build())
                .field("visibility", Component.text("Visibility"), Ui.button(Component.text("Public")).key("public")
                        .primary((_, _) -> calls.add("toggle")).build()).build();
        var wide = scene(content, 500);
        var narrow = scene(content, 180);
        assertEquals(wide.entries().keySet(), narrow.entries().keySet());
        var frame = SceneGeometryTestSupport.measure(narrow, 1.8);
        assertDisjoint(frame);
        for (String action : new String[] {"preview", "public"}) entry(narrow, action)
                .trigger(org.encinet.mik.shared.menu.FloatingMenuInteraction.PRIMARY)
                .execute(null, null, org.encinet.mik.shared.menu.FloatingMenuInteraction.PRIMARY);
        assertEquals(java.util.List.of("edit", "toggle"), calls);
        assertFalse(entry(narrow, "label").interactive());
    }

    @Test void feedbackAndActionsCanBeIndependentlyOmittedWithoutLeavingAnEmptyRegion() {
        var actions = Ui.button(Component.text("Save")).key("save").build();
        var actionsOnly = UiTemplates.actionRow(null, actions);
        var frame = SceneGeometryTestSupport.measure(scene(actionsOnly, 300), 1);
        assertEquals(1, frame.elements().size());
        var stretched = Ui.column().sizing(UiSizing.width(UiLength.ui(300), UiLength.ui(300)))
                .child(actionsOnly, LinearSlot.stretch()).build();
        frame = SceneGeometryTestSupport.measure(scene(stretched, 300), 1);
        assertEquals(300, element(frame, "save").x() + element(frame, "save").size().width(), 1e-6);
        var feedbackOnly = UiTemplates.actionRow(Ui.text(Component.text("Input stopped")).key("hint").build(), null);
        assertDisjoint(SceneGeometryTestSupport.measure(scene(feedbackOnly, 300), 1));
    }

    @Test void nestedControlMinimaCanTriggerStackingWithoutChangingTheDeclaredTree() {
        var control = Ui.column().child(Ui.textField(Component.text("Draft")).sizing(UiSizing.atLeast(new UiSize(250, 0))).build()).build();
        var content = Ui.form().field("name", Component.text("Playlist name"), control).build();
        var frame = SceneGeometryTestSupport.measure(scene(content, 300), 1);
        var label = element(frame, "label");
        var input = element(frame, "preview");
        assertTrue(input.y() >= label.y() + label.size().height());
        assertTrue(owningSurface(frame, input).size().width() >= 250);
        assertDisjoint(frame);
        var row = UiTemplates.actionRow(control, Ui.button(Component.text("Save")).key("save").build());
        frame = SceneGeometryTestSupport.measure(scene(row, 280), 1);
        assertTrue(element(frame, "save").y() >= element(frame, "preview").y() + element(frame, "preview").size().height());
        assertDisjoint(frame);
    }

    @Test void aMeasuredFormFitsItsPublishedHeightAfterItsFeedbackBecomesWider() {
        var feedback = Ui.layout(UiLayouts.preferredWidth(UiLength.ui(160), UiLayouts.column(UiLength.ZERO)))
                .child(Ui.text(Component.text("Press T to type in the chat box")).key("hint").build()).build();
        var content = Ui.form()
                .field("name", Component.text("Playlist name"), Ui.textField(Component.text("Draft")).build())
                .field("body", Component.text("Description"), Ui.textField(Component.text("First\nSecond"))
                        .maxLines(4).actions(Ui.button(Component.text("Clear")).build()).build())
                .feedback(feedback).actions(Ui.button(Component.text("Save")).key("save").build()).build();
        for (double typography : new double[] {.7, 1, 1.4, 1.8}) {
            var frame = SceneGeometryTestSupport.measure(scene(content, 640), typography);
            var exact = MenuScene.screen("exact").viewport(new UiSize(640, frame.size().height())).content(content).build();
            var fitted = SceneGeometryTestSupport.measure(exact, typography);
            assertEquals(frame.size(), fitted.size());
            assertDisjoint(fitted);
        }
    }

    private UiNode form() {
        return Ui.form().field("name", Component.text("Playlist name"), Ui.textField(Component.text("Songs")).build())
                .field("description", Component.text("Description"), Ui.textField(Component.text("A description")).maxLines(4).build())
                .feedback(Ui.text(Component.text("Press T to type")).key("hint").build())
                .actions(Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(6)))
                        .child(Ui.button(Component.text("Cancel")).key("cancel").build())
                        .child(Ui.button(Component.text("Save")).key("confirm").build()).build()).build();
    }

    private MenuScene scene(UiNode content, double width) {
        return MenuScene.screen("form").viewport(new UiSize(width, 2048)).content(content).build();
    }

    private UiFrame.Element field(UiFrame frame, String key, String element) {
        return frame.elements().stream().filter(value -> value.path().contains("/field:" + key + ":")
                && value.node().key().value().equals(element)).findFirst().orElseThrow();
    }
}
