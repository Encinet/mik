package org.encinet.mik.shared.menu.runtime.input;

import org.encinet.mik.shared.menu.runtime.FloatingMenuService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuInputEditorTest {
    @Test void openingAndFocusingAFormShowsTheTextboxWithoutAcquiringChatOrOpeningADialog() {
        var h = new Harness();
        h.editor.focus("name");
        assertNotNull(entry(h.scene(),"preview"));
        assertFalse(entry(h.scene(),"preview").selected());
        verify(h.menus,never()).beginChatOnce(any(),any(),any());
        verify(h.menus,never()).editText(any(),any(),any(),anyString(),any(),any(),any(),any(),any());
        assertFalse(h.inputs.offerText(h.id,"ordinary chat"));
        h.editor.close();
    }

    @Test void theHostCanExplicitlyStartTheConfiguredFieldEditor() {
        var h = new Harness();
        h.editor.focus("name");
        assertFalse(h.inputs.offerText(h.id,"ordinary chat"));
        h.editor.edit("name");
        assertTrue(h.inputs.offerText(h.id,"Explicit edit")); h.inputs.tick();
        assertEquals(Component.text("Explicit edit"),entry(h.scene(),"preview").label());
        h.editor.close();
    }

    @Test void shortTextUsesOneChatMessageAndStaysADraftUntilTheFormIsConfirmed() {
        var h = new Harness();
        h.click("preview");
        assertTrue(entry(h.scene(),"preview").selected());
        assertNotNull(entry(h.scene(),"input-hint"));
        assertFalse(entry(h.scene(),"confirm").enabled());
        assertTrue(h.inputs.offerText(h.id,"  Piano 😀  "));
        assertFalse(h.inputs.offerText(h.id,"ordinary second message"));
        h.inputs.tick();
        assertEquals(Component.text("  Piano 😀  "),entry(h.scene(),"preview").label());
        assertFalse(entry(h.scene(),"preview").selected());
        assertTrue(h.saved.isEmpty());
        assertEquals(0,h.finished.get());
        verify(h.menus,never()).editText(any(),any(),any(),anyString(),any(),any(),any(),any(),any());
        h.click("confirm");
        assertEquals("  Piano 😀  ",h.saved.getFirst().text("name"));
        assertEquals(1,h.saved.size()); assertEquals(1,h.finished.get());
        assertFalse(h.inputs.offerText(h.id,"late text"));
    }

    @Test void cancellingSwitchingFieldsAndClosingDiscardQueuedChatWithoutSaving() {
        var h = new Harness();
        h.click("preview");
        h.inputs.offerText(h.id,"queued before cancel"); h.click("cancel-field-input"); h.inputs.tick();
        assertEquals(Component.text("Original"),entry(h.scene(),"preview").label());
        h.click("preview"); h.inputs.offerText(h.id,"queued before switching");
        h.editor.focus("notes"); h.inputs.tick(); h.editor.focus("name");
        assertEquals(Component.text("Original"),entry(h.scene(),"preview").label());
        h.click("preview"); h.inputs.offerText(h.id,"queued before close");
        h.editor.close(); h.inputs.tick();
        assertFalse(h.inputs.offerText(h.id,"ordinary chat after close"));
        assertTrue(h.saved.isEmpty()); assertEquals(0,h.finished.get());
    }

    @Test void invalidChatShowsTheFieldErrorAndCanBeCorrectedWithoutLeavingTheForm() {
        var h = new Harness();
        for (String raw : List.of("x".repeat(65),"first\nsecond")) {
            h.click("preview"); assertTrue(h.inputs.offerText(h.id,raw)); h.inputs.tick();
            assertNotNull(entry(h.scene(),"problem"));
            assertFalse(entry(h.scene(),"confirm").enabled());
            assertEquals(Component.text("Original"),entry(h.scene(),"preview").label());
        }
        h.click("preview"); h.inputs.offerText(h.id,"Corrected"); h.inputs.tick();
        assertNull(entry(h.scene(),"problem")); assertTrue(entry(h.scene(),"confirm").enabled());
        assertTrue(h.saved.isEmpty()); h.editor.close();
    }

    @Test void multilineEditingUsesTheNativeEditorOnlyAfterClickingItsTextbox() {
        var h = new Harness();
        h.editor.focus("notes");
        verify(h.menus,never()).editText(any(),any(),any(),anyString(),any(),any(),any(),any(),any());
        h.click("preview");
        assertNotNull(h.nativeAccepted);
        verify(h.menus,never()).beginChatOnce(any(),any(),any());
        h.nativeAccepted.accept("First\r\n\r\nLast\n");
        h.click("confirm");
        assertEquals("First\n\nLast\n",h.saved.getFirst().text("notes"));
        assertEquals("Original",h.saved.getFirst().text("name"));
    }

    @Test void uiInputPolicyIsIndependentOfTheFieldsLineFormat() {
        var h = new Harness(MenuInputForm.InputMethod.DIALOG,MenuInputForm.InputMethod.CHAT);
        h.click("preview");
        assertNotNull(h.nativeAccepted,"A single-line field can explicitly choose Dialog");
        h.nativeAccepted.accept("New name");
        h.editor.focus("notes"); h.click("preview");
        assertTrue(h.inputs.offerText(h.id,"First\nSecond")); h.inputs.tick();
        h.click("confirm");
        assertEquals("First\nSecond",h.saved.getFirst().text("notes"));
        assertEquals("New name",h.saved.getFirst().text("name"));
    }

    @Test void asynchronousFailureRetainsTheAcceptedDraftAndAllowsRetryWithoutReediting() {
        var attempts = new ArrayList<MenuInputForm.Completion>();
        var h = new Harness((_,values,done) -> { assertEquals("Retry name",values.text("name")); attempts.add(done); });
        h.click("preview"); h.inputs.offerText(h.id,"Retry name"); h.inputs.tick();
        h.click("confirm"); h.click("confirm");
        assertEquals(1,attempts.size()); assertEquals(0,h.finished.get());
        assertFalse(entry(h.scene(),"confirm").enabled()); assertFalse(entry(h.scene(),"preview").enabled());
        attempts.getFirst().failure(Component.text("Storage unavailable"));
        assertEquals(Component.text("Retry name"),entry(h.scene(),"preview").label());
        assertTrue(entry(h.scene(),"confirm").enabled()); assertNotNull(entry(h.scene(),"problem"));
        h.click("confirm"); assertEquals(2,attempts.size());
        attempts.getFirst().success(); assertEquals(0,h.finished.get());
        attempts.getLast().success(); attempts.getLast().success();
        assertEquals(1,h.finished.get());
    }

    @Test void closingWhileSavingPreventsLateCompletionFromReturningOrReopeningAForm() {
        var attempt = new java.util.concurrent.atomic.AtomicReference<MenuInputForm.Completion>();
        var h = new Harness((_,_,done) -> attempt.set(done));
        h.click("confirm"); h.editor.close(); attempt.get().success();
        assertEquals(0,h.finished.get()); assertFalse(h.inputs.offerText(h.id,"ordinary chat"));
    }

    private static final class Harness {
        final UUID id = UUID.randomUUID();
        final Player player = mock(Player.class);
        final FloatingMenuService menus = mock(FloatingMenuService.class);
        final MenuInputSessions inputs = new MenuInputSessions((_,_) -> {});
        final List<MenuInputForm.Values> saved = new ArrayList<>();
        final AtomicInteger finished = new AtomicInteger();
        final MenuInputEditor editor;
        Consumer<String> nativeAccepted;
        Harness() { this(MenuInputForm.InputMethod.CHAT,MenuInputForm.InputMethod.DIALOG); }
        Harness(MenuInputForm.InputMethod nameInput, MenuInputForm.InputMethod notesInput) { this(nameInput,notesInput,null); }
        Harness(MenuInputForm.AsyncSubmit save) { this(MenuInputForm.InputMethod.CHAT,MenuInputForm.InputMethod.DIALOG,save); }
        Harness(MenuInputForm.InputMethod nameInput, MenuInputForm.InputMethod notesInput, MenuInputForm.AsyncSubmit save) {
            var language = mock(LanguageService.class,call -> {
                if (call.getMethod().getReturnType() == Component.class) return Component.text("Input feedback");
                if (call.getMethod().getReturnType() == String.class) return "Input feedback";
                return RETURNS_DEFAULTS.answer(call);
            });
            doAnswer(call -> inputs.beginChatOnce(id,() -> true,call.getArgument(1),call.getArgument(2)))
                    .when(menus).beginChatOnce(eq(player),any(),any());
            doAnswer(call -> {
                nativeAccepted = call.getArgument(7);
                return mock(MenuDialogSession.class);
            }).when(menus).editText(eq(player),any(),any(),anyString(),any(),any(),any(),any(),any());
            doAnswer(call -> { call.getArgument(0,Runnable.class).run(); return null; }).when(menus).execute(any());
            var builder = MenuInputForm.builder(Component.text("Playlist")).layout(MenuInputForm.Layout.FIELD_DIRECTORY)
                    .field(MenuInputForm.Field.text("name",Component.text("Name"),"Original",64).inputMethod(nameInput))
                    .field(MenuInputForm.Field.text("notes",Component.text("Notes"),"",512).multiline(true).inputMethod(notesInput))
                    .cancel(Component.text("Cancel"));
            if (save == null) builder.submit(Component.text("Save"),(_,values) -> saved.add(values));
            else builder.submitAsync(Component.text("Save"),save);
            var form = builder.build();
            editor = new MenuInputEditor(menus,language,player,form);
            editor.inline(() -> {},finished::incrementAndGet);
        }
        MenuScene scene() { return MenuScene.screen("test-input").content(editor.content()).build(); }
        void click(String key) { entry(scene(),key).trigger(FloatingMenuInteraction.PRIMARY).execute(player,null,FloatingMenuInteraction.PRIMARY); }
    }
}
