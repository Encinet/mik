package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuInputViewTest {
    @Test void emptyRichPreviewsDoNotMislabelNonemptySourceDraftsAsUnset() {
        for (String raw : new String[]{"", "<red>"}) {
            var form = form(Component.text("Edit"), MenuInputForm.Field.text("value", Component.text("Value"), raw, 64)
                    .preview(_ -> Component.empty()));
            var draft = new MenuInputDraft(form);
            var scene = scene(form, draft, null);
            assertEquals(raw.isEmpty() ? "(empty)" : "", plain(entry(scene, "preview").label()));
            assertEquals(raw.isEmpty(), entry(scene, "clear") == null);
            assertEquals(raw, draft.value("value"));
        }
    }

    @Test void hiddenFieldsDoNotRunTheirPreviewRendererAndBrowsingKeepsTheirStoredDrafts() {
        var rendered = new java.util.ArrayList<String>();
        var builder = MenuInputForm.builder(Component.text("Edit"));
        for (String key : new String[]{"name","body","notes"}) builder.field(MenuInputForm.Field.text(key,Component.text(key),key,64)
                .preview(raw -> { rendered.add(key); return Component.text(raw); }));
        var form = builder.cancel(Component.text("Cancel")).submit(Component.text("Save"),(_,_) -> fail("Only render")).build();
        var draft = new MenuInputDraft(form);
        scene(form,draft,null);
        assertEquals(java.util.List.of("name"),rendered);
        draft.browse(2);
        var current = scene(form,draft,null);
        assertEquals(java.util.List.of("name","notes"),rendered);
        assertEquals(1,elements(current).values().stream().filter(node -> node.key().value().equals("preview")).count());
        assertEquals("name",draft.values().text("name"));
        assertEquals("body",draft.values().text("body"));
        assertEquals("notes",draft.values().text("notes"));
        assertFalse(draft.submitted());
    }

    @Test void browsingUsesTheDirectoryAndClearingTheVisibleFieldDoesNotAcquireEditingOrClearThePreviouslySelectedField() {
        var form = form(Component.text("Edit"), MenuInputForm.Field.text("name",Component.text("Name"),"Keep me",64),
                MenuInputForm.Field.text("body",Component.text("Description"),"Clear this",64));
        var draft = new MenuInputDraft(form);
        var edits = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<MenuScene> render = () -> MenuScene.screen("form").content(MenuInputView.content(
                MenuInputView.Presentation.INLINE,form,draft,new MenuInputView.Labels(Component.text("Empty"),Component.text("Clear"),null),
                index -> { edits.incrementAndGet(); draft.select(index); },draft::browse,draft::clear,
                () -> fail("Browsing must not submit"),() -> fail("Browsing must not cancel"))).build();
        var initial = render.get();
        entry(initial,"select-field:body").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(1,draft.viewed());
        assertEquals(0,draft.selected());
        var current = render.get();
        entry(current,"clear").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals("",draft.value("body"));
        assertEquals("Keep me",draft.value("name"));
        assertEquals(0,edits.get());
        assertFalse(draft.submitted());
    }

    @Test void aFieldCrossClearsOnlyItsOwnDraftAndThePopoverCanOwnCancellation() {
        var form=MenuInputForm.builder(Component.text("Edit"))
                .field(MenuInputForm.Field.text("name",Component.text("Name"),"Draft",64))
                .field(MenuInputForm.Field.text("description",Component.text("Description"),"Keep me",64))
                .cancel(Component.text("Cancel")).submit(Component.text("Save"),(_,_)->fail("Clear must not save")).build();
        var draft=new MenuInputDraft(form);
        var node=MenuInputView.content(MenuInputView.Presentation.INLINE,form,draft,
                new MenuInputView.Labels(Component.text("Empty"),Component.text("Clear field"),null),
                draft::select,draft::browse,draft::clear,()->fail("Clear must not submit"),()->fail("Clear must not cancel"),
                org.encinet.mik.shared.menu.MenuFormSession.Actions.SUBMIT);
        var scene=MenuScene.screen("draft").content(node).build();
        assertNull(entry(scene,"cancel"));
        assertNotNull(entry(scene,"confirm"));
        assertTrue(elements(scene).values().stream().anyMatch(element->element.key().value().equals("clear")
                && element.content() instanceof org.encinet.mik.shared.menu.ui.UiContent.Icon icon
                && icon.value()==org.encinet.mik.shared.menu.ui.UiIcons.CROSS));
        entry(scene,"clear").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals("",draft.value("name"));
        assertEquals("Keep me",draft.value("description"));
        assertFalse(draft.submitted());
    }

    @Test void inlineFormsHaveTheirOwnFieldLabelsAndKeepControlsInTheHostPlane() {
        var form = form(Component.text("Name"), MenuInputForm.Field.text("name", Component.text("Name"), "Draft", 64));
        var draft = new MenuInputDraft(form);
        var content = MenuInputView.content(MenuInputView.Presentation.INLINE, form, draft,
                new MenuInputView.Labels(Component.text("Empty"), Component.text("Clear"), null), draft::select, draft::browse,
                draft::clear, () -> { }, () -> { });
        var scene = MenuScene.screen("inline").content(content).build();
        assertNull(entry(scene, "title"));
        assertEquals(Component.text("Name"), entry(scene, "label").label());
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        assertDisjoint(frame);
        assertNull(entry(scene, "hint"));
        assertFalse(elements(scene).keySet().stream().anyMatch(path -> path.contains("/feedback/")),
                "ordinary editing must not reserve a feedback area");
        assertTrue(frame.panels().isEmpty(), "the host supplies the surface; an inline form must not introduce tilted subwindows");
        assertNotNull(entry(scene, "confirm"));
        assertNotNull(entry(scene, "cancel"));
    }

    @Test void anEmptySingleFieldDoesNotRepeatItsTitleOrOfferEmptyEditingActions() {
        var form = form(Component.text("Name", NamedTextColor.GOLD),
                MenuInputForm.Field.text("name", Component.text("Name", NamedTextColor.WHITE), "", 64));
        var scene = scene(form, new MenuInputDraft(form), null);
        var nodes = elements(scene).values();
        assertEquals(1, nodes.stream().filter(node -> plain(node.content().label()).equals("Name")).count());
        assertNull(entry(scene, "select"));
        assertNull(entry(scene, "clear"));
        assertNull(entry(scene, "mode"));
        assertTrue(entry(scene, "preview").interactive(), "the preview remains an input target, including after interruption");
        assertTrue(nodes.stream().noneMatch(node -> node.input().tooltip().isPresent()));
        assertNull(entry(scene, "hint"));
        assertTrue(entry(scene, "confirm").enabled());
        assertTrue(entry(scene, "cancel").enabled());
    }

    @Test void clickingFieldPreviewsSwitchesDraftsAndOnlyOffersApplicableTools() {
        var form = form(Component.text("Playlist"),
                MenuInputForm.Field.text("name", Component.text("Name"), "My songs", 64),
                MenuInputForm.Field.text("body", Component.text("Description"), "", 512).multiline(true));
        var draft = new MenuInputDraft(form);
        var scene = scene(form, draft, null);
        assertNotNull(entry(scene, "clear"));
        assertNull(entry(scene, "mode"));
        assertFalse(entry(scene, "label").interactive());
        entry(scene, "select-field:body").trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY);
        assertEquals(0, draft.selected(), "browsing a field does not acquire editing or change its action target");
        assertEquals(1, draft.viewed());
        scene = scene(form, draft, null);
        var body = scene.entries().entrySet().stream().filter(entry -> entry.getKey().endsWith("/field:body:control/preview"))
                .findFirst().orElseThrow().getValue();
        body.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(1, draft.selected());
        assertEquals("My songs", draft.value("name"));
        scene = scene(form, draft, null);
        assertNull(entry(scene, "clear"));
        assertNull(entry(scene, "mode"));
        draft.receive("First line\nSecond line");
        scene = scene(form, draft, null);
        assertNull(entry(scene, "mode"));
        assertEquals("First line\nSecond line", draft.values().text("body"));
        var frame = SceneGeometryTestSupport.measure(scene, 1);
        assertTrue(element(frame, "cancel").x() < element(frame, "confirm").x());
        assertTrue(element(frame, "clear").y() < element(frame, "confirm").y(), "clearing belongs to the selected field");
        entry(scene, "clear").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals("", draft.value("body"));
        assertEquals("My songs", draft.value("name"));
    }

    @Test void tooltipsOnlyExposePreviewContentThatWasActuallyShortened() {
        var full = Component.text("😀 Coloured preview\n".repeat(6), NamedTextColor.AQUA);
        var form = form(Component.text("Edit"), MenuInputForm.Field.text("value", Component.text("Value"), "raw", 64)
                .preview(_ -> full));
        var scene = scene(form, new MenuInputDraft(form), null);
        assertEquals(full, entry(scene, "preview").label());
        var preview = element(SceneGeometryTestSupport.measure(scene,1),"preview");
        assertEquals(full,preview.node().input().tooltip().orElseThrow().text());
        assertTrue(plain(preview.node().content().label()).endsWith("…"));

        var shortForm = form(Component.text("Edit"), MenuInputForm.Field.text("value", Component.text("Value"), "x".repeat(200), 512)
                .preview(_ -> Component.text("Short", NamedTextColor.GREEN)));
        var draft = new MenuInputDraft(shortForm);
        assertNull(entry(scene(shortForm, draft, null), "preview").tooltip());
        assertEquals("x".repeat(200), draft.values().text("value"), "only the rendered preview may be shortened");
    }

    @Test void problemsPreventSubmissionAndClickingTheSamePreviewCanEditItAgain() {
        var form = form(Component.text("Rename"), MenuInputForm.Field.text("name", Component.text("Name"), "Draft", 64));
        var draft = new MenuInputDraft(form);
        var problem = Component.text("Please correct this value.");
        var scene = scene(form, draft, problem);
        assertNull(entry(scene, "hint"));
        assertEquals(problem, entry(scene, "problem").label());
        assertFalse(entry(scene, "confirm").enabled());
        entry(scene, "preview").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals("Draft", draft.values().text("name"));
        draft.receive("Revised draft");
        scene = scene(form, draft, null);
        assertNull(entry(scene, "problem"));
        assertTrue(entry(scene, "confirm").enabled());
        entry(scene, "preview").trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals("Revised draft", draft.values().text("name"));
    }

    @Test void longMultilineDraftsAndTranslatedThreeFieldFormsFitWithoutHidingConfirmation() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            var builder = MenuInputForm.builder(Component.text(FluentTestSupport.text(bundle, Message.GOVERNANCE_MENU_PAUSE)))
                    .cancel(Component.text(FluentTestSupport.text(bundle, Message.CANCEL)))
                    .submit(Component.text(FluentTestSupport.text(bundle, Message.GOVERNANCE_MENU_CONFIRM)), (_, _) -> { });
            var fields = new Message[] {Message.GOVERNANCE_MENU_PAUSE_DURATION, Message.GOVERNANCE_MENU_PAUSE_APPEAL,
                    Message.GOVERNANCE_MENU_PAUSE_REASON};
            for (int index = 0; index < fields.length; index++) builder.field(MenuInputForm.Field.text("field-" + index,
                    Component.text(FluentTestSupport.text(bundle, fields[index])), "多语言 😀 text\n".repeat(100), 4096).multiline(true));
            var form = builder.build();
            var draft = new MenuInputDraft(form);
            for (double scale : new double[] {.7, 1, 1.4, 1.8}) {
                var labels = new MenuInputView.Labels(Component.text(FluentTestSupport.text(bundle, Message.MENU_INPUT_EMPTY)),
                        Component.text(FluentTestSupport.text(bundle, Message.MENU_INPUT_CLEAR)),
                        Component.text(FluentTestSupport.text(bundle, Message.MENU_INPUT_TOO_LONG, 5000, 4096)));
                var content = MenuInputView.content(MenuInputView.Presentation.WINDOW, form, draft, labels, _ -> { }, draft::browse, _ -> { }, () -> { }, () -> { });
                var frame = SceneGeometryTestSupport.measure(MenuScene.screen("input-test").content(content).build(), scale);
                assertDisjoint(frame);
                assertNotNull(element(frame, "confirm"));
                assertNotNull(element(frame, "cancel"));
                assertTrue(element(frame, "confirm").node().input().disabledReason().isPresent());
            }
        }
    }

    private MenuInputForm form(Component title, MenuInputForm.Field... fields) {
        var builder = MenuInputForm.builder(title).submit(Component.text("Apply"), (_, _) -> { })
                .cancel(Component.text("Cancel"));
        for (var field : fields) builder.field(field);
        return builder.build();
    }

    private MenuScene scene(MenuInputForm form, MenuInputDraft draft, Component problem) {
        var labels = new MenuInputView.Labels(Component.text("(empty)"),
                Component.text("Clear"), problem);
        return MenuScene.screen("input-test").content(MenuInputView.content(MenuInputView.Presentation.WINDOW, form, draft, labels,
                draft::select, draft::browse, draft::clear, () -> { }, () -> { })).build();
    }

    private String plain(Component text) { return PlainTextComponentSerializer.plainText().serialize(text); }
}
