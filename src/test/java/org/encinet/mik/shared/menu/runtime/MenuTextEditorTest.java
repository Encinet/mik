package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class MenuTextEditorTest {
    record Dialog(String value, Component problem, Consumer<String> accepted, Runnable cancelled) { }

    private static final class Host implements MenuTextEditor.Host {
        final List<Dialog> dialogs = new ArrayList<>();
        int dismissals;
        @Override public Runnable show(String value, Component problem, Consumer<String> accepted, Runnable cancelled) {
            dialogs.add(new Dialog(value, problem, accepted, cancelled));
            return () -> { dismissals++; cancelled.run(); };
        }
        Dialog current() { return dialogs.getLast(); }
    }

    private MenuInputForm form(boolean multiline, String initial, int maximum) {
        return MenuInputForm.builder(Component.text("Edit"))
                .field(MenuInputForm.Field.text("body", Component.text("Body"), initial, maximum).multiline(multiline))
                .field(MenuInputForm.Field.text("other", Component.text("Other"), "Keep me", 64))
                .cancel(Component.text("Cancel")).submit(Component.text("Save"), (_, _) -> fail("Field editing cannot save the form"))
                .build();
    }

    private MenuTextEditor editor(MenuInputForm form, MenuInputDraft draft, Host host, Runnable cancelled) {
        return new MenuTextEditor(draft.value("body"), host,
                raw -> MenuInputDraft.validate(form.fields().getFirst(), raw) == null ? null : Component.text("Invalid text"),
                draft::receive, cancelled);
    }

    @Test void theCompleteSourceIsEditableAndReopeningUsesTheLatestDraftWithoutSavingTheForm() {
        String initial = " <gradient:red:blue>原文😀</gradient>\n".repeat(10) + "\n";
        var form = form(true, initial, 2048);
        var draft = new MenuInputDraft(form);
        var host = new Host();
        var first = editor(form, draft, host, () -> fail("Not cancelled"));
        first.open();
        first.open();
        assertEquals(1, host.dialogs.size());
        assertEquals(initial, host.current().value(), "Dialog receives raw text, never its abbreviated or rendered preview");
        String revised = " first\n\nlast \n";
        host.current().accepted().accept(revised);
        assertEquals(revised, draft.value("body"));
        assertEquals("Keep me", draft.value("other"));
        assertFalse(draft.submitted());
        editor(form, draft, host, () -> fail("Not cancelled")).open();
        assertEquals(revised, host.current().value());
        host.current().accepted().accept("");
        assertEquals("", draft.value("body"), "Completing an empty field clears that field's draft");
        assertTrue(draft.submit());
        assertFalse(draft.submit());
    }

    @Test void cancellingOrClosingDiscardsOnlyThisEditAndLateResponsesCannotChangeTheDraft() {
        for (boolean cancel : new boolean[] {true, false}) {
            var form = form(true, "Original\n", 256);
            var draft = new MenuInputDraft(form);
            var host = new Host();
            var cancelled = new AtomicInteger();
            var editor = editor(form, draft, host, cancelled::incrementAndGet);
            editor.open();
            Dialog stale = host.current();
            if (cancel) stale.cancelled().run();
            else editor.close();
            editor.close();
            stale.accepted().accept("Late text");
            stale.cancelled().run();
            assertEquals("Original\n", draft.value("body"));
            assertEquals(cancel ? 1 : 0, cancelled.get());
            assertEquals(1, host.dismissals);
        }
    }

    @Test void invalidInputReopensWithTheCandidateAndErrorsWhileKeepingTheAcceptedDraftIntact() {
        var form = form(false, "Original", 8);
        var draft = new MenuInputDraft(form);
        var host = new Host();
        var editor = editor(form, draft, host, () -> { });
        editor.open();
        Dialog first = host.current();
        first.accepted().accept("Too long 😀 text");
        assertEquals("Too long 😀 text", host.current().value());
        assertNotNull(host.current().problem());
        assertEquals("Original", draft.value("body"));
        first.accepted().accept("Stale");
        first.cancelled().run();
        assertEquals(2, host.dialogs.size());
        host.current().accepted().accept("a\nb");
        assertEquals("a\nb", host.current().value());
        assertNotNull(host.current().problem());
        host.current().accepted().accept("😀".repeat(8));
        assertEquals("😀".repeat(8), draft.value("body"), "Length validation counts Unicode code points");
        host.current().accepted().accept("Replay");
        assertEquals("😀".repeat(8), draft.value("body"));
    }

    @Test void multilineEditingNormalizesLineEndingsAndPreservesWhitespaceAndEmptyLines() {
        var form = form(true, "", 64);
        var draft = new MenuInputDraft(form);
        var host = new Host();
        editor(form, draft, host, () -> fail("Not cancelled")).open();
        host.current().accepted().accept(" first\r\n\r\nlast \r");
        assertEquals(" first\n\nlast \n", draft.value("body"));
    }

    @Test void cancellingAfterAValidationErrorRestoresThePreviouslyAcceptedDraft() {
        var form = form(true, "Good", 8);
        var draft = new MenuInputDraft(form);
        var host = new Host();
        var cancelled = new AtomicInteger();
        editor(form, draft, host, cancelled::incrementAndGet).open();
        host.current().accepted().accept("Invalid candidate");
        host.current().cancelled().run();
        assertEquals("Good", draft.value("body"));
        assertEquals(1, cancelled.get());
    }
}
