package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuInputDraftTest {
    @Test void browsingAndClearingAnExplicitFieldDoNotChangeTheActiveInputOrAnotherRejectedDraft() {
        var draft = new MenuInputDraft(form());
        draft.receive("too-long-input");
        var rejection = draft.problem();
        draft.browse(1);
        assertEquals(0, draft.selected());
        assertEquals(1, draft.viewed());
        assertEquals("original", draft.value("name"));
        draft.clear(1);
        assertEquals("", draft.value("body"));
        assertEquals("original", draft.value("name"));
        assertSame(rejection, draft.problem());
        assertFalse(draft.submitted());
        draft.clear(0);
        assertNull(draft.problem());
        draft.select(1);
        assertEquals(draft.selected(), draft.viewed());
        assertTrue(draft.submit());
        draft.browse(0);
        assertEquals(1, draft.viewed());
    }

    private MenuInputForm form() {
        return MenuInputForm.builder(Component.text("Edit"))
                .field(MenuInputForm.Field.text("name", Component.text("Name"), "original", 8))
                .field(MenuInputForm.Field.text("body", Component.text("Body"), "old", 32).multiline(true))
                .cancel(Component.text("Cancel")).submit(Component.text("Apply"), (_, _) -> fail("Drafts must not apply changes"))
                .build();
    }

    @Test void fieldsKeepIndependentDraftsAndSnapshotsCannotBeChangedByLaterInput() {
        var draft = new MenuInputDraft(form());
        draft.receive(" new ");
        var snapshot = draft.values();
        draft.select(1);
        draft.receive("paragraph\nsecond");
        assertEquals("paragraph\nsecond", draft.value("body"));
        draft.select(0);
        assertEquals(" new ", draft.value("name"));
        assertEquals("old", snapshot.text("body"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.fields().put("name", "wrong"));
        assertTrue(draft.submit());
        assertFalse(draft.submit());
        draft.receive("late");
        assertEquals(" new ", draft.value("name"));
    }

    @Test void invalidMessagesRetainTheLastValidDraftAndMultilineReplacementIsValidatedAsAWhole() {
        var draft = new MenuInputDraft(form());
        draft.receive("😀".repeat(8));
        assertNull(draft.problem());
        draft.receive("😀".repeat(9));
        assertEquals(9, draft.problem().length());
        assertEquals("😀".repeat(8), draft.value("name"));
        assertFalse(draft.submit());
        draft.clear();
        assertNull(draft.problem());
        draft.receive("a\nb");
        assertTrue(draft.problem().singleLine());
        assertEquals("", draft.value("name"));
        draft.select(1);
        for (int index = 0; index < 100; index++) draft.receive("x".repeat(33));
        assertEquals("old", draft.value("body"));
        assertNotNull(draft.problem());
    }

    @Test void acceptingAFieldPreservesWhitespaceBlankLinesAndTheOtherField() {
        var draft = new MenuInputDraft(form());
        draft.select(1);
        draft.receive(" first\n\nlast \n");
        draft.select(0);
        assertEquals("original", draft.value("name"));
        assertEquals(" first\n\nlast \n", draft.value("body"));
        assertTrue(draft.submit());
    }

    @Test void formsRejectDuplicateFieldsAndDoNotSilentlyReturnAnUnknownValue() {
        var field = form().fields().getFirst();
        assertThrows(IllegalArgumentException.class, () -> MenuInputForm.builder(Component.empty())
                .field(field).field(field).cancel(Component.empty()).submit(Component.empty(), (_, _) -> { }).build());
        assertThrows(IllegalArgumentException.class, () -> new MenuInputDraft(form()).values().text("missing"));
    }
}
