package org.encinet.mik.feature.world.plot;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PlotNoticeFormTest {
    @Test void existingFullNoticeIsTheMultilineEditingSourceAndOnlySubmissionChangesTheBoard() {
        String original = "现有公告😀\n".repeat(300).strip();
        var board = new AtomicReference<>(original);
        var form = PlotNoticeForm.create(original, (message, _) -> Component.text(message.key()),
                _ -> null, (_, value) -> board.set(value));
        var field = form.fields().getFirst();
        assertTrue(field.multiline());
        assertEquals(PlotNoticeText.MAX_LENGTH, field.maximumLength());
        assertEquals(original, field.initial(), "Existing notices must not be replaced by the card's abbreviated preview");
        var edited = values(" 第一段\r\n\r\n修改后的段落😀 \n");
        assertNull(form.validation().apply(null, edited));
        assertEquals(original, board.get(), "Validation and field editing must not save the board");
        form.onSubmit().accept(null, edited);
        assertEquals("第一段\n\n修改后的段落😀", board.get());
    }

    @Test void textValidationPreservesTheCandidateAndChecksUnicodeLimitsAndControlCharacters() {
        var saved = new ArrayList<String>();
        var form = PlotNoticeForm.create("Old", (message, _) -> Component.text(message.key()),
                _ -> null, (_, value) -> saved.add(value));
        for (String valid : new String[] {"地".repeat(4096), "😀".repeat(4096), "line\n\nlast"})
            assertNull(form.validation().apply(null, values(valid)));
        for (String invalid : new String[] {"地".repeat(4097), "😀".repeat(4097), "before\tafter", "x\u0000y"}) {
            var candidate = values(invalid);
            assertEquals(Component.text(Message.PLOT_ERROR_NOTICE_TEXT.key()).color(net.kyori.adventure.text.format.NamedTextColor.RED),
                    form.validation().apply(null, candidate));
            assertEquals(invalid, candidate.text("body"));
        }
        assertTrue(saved.isEmpty());
    }

    @Test void accessIsCheckedAtSubmissionAndAnEmptyNoticeClearsTheBoard() {
        var denied = new AtomicReference<Component>();
        var saved = new ArrayList<String>();
        var form = PlotNoticeForm.create("Old", (message, _) -> Component.text(message.key()),
                _ -> denied.get(), (_, value) -> saved.add(value));
        assertNull(form.validation().apply(null, values("Changed")));
        denied.set(Component.text("Permission changed"));
        assertEquals(denied.get(), form.validation().apply(null, values("Changed")));
        assertTrue(saved.isEmpty());
        denied.set(null);
        var empty = values(" \n  ");
        assertNull(form.validation().apply(null, empty));
        form.onSubmit().accept(null, empty);
        assertEquals(java.util.List.of(""), saved);
    }

    private static MenuInputForm.Values values(String text) {
        return new MenuInputForm.Values(Map.of("body", text));
    }
}
