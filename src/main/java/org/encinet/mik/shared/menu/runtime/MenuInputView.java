package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.FloatingMenuTextWidth;
import org.encinet.mik.shared.menu.ui.*;

import java.util.function.IntConsumer;

/** Field tools stay with their preview; shared feedback and submission use one adaptive footer. */
final class MenuInputView {
    enum Presentation { WINDOW, INLINE }
    record Labels(Component empty, Component clear, Component problem) { }

    static UiNode content(Presentation presentation, MenuInputForm form, MenuInputDraft draft, Labels labels, IntConsumer select,
                          IntConsumer browse, IntConsumer clear, Runnable confirm, Runnable cancel) {
        return content(presentation, form, draft, labels, select, browse, clear, confirm, cancel,
                org.encinet.mik.shared.menu.MenuFormSession.Actions.SUBMIT_AND_CANCEL);
    }

    static UiNode content(Presentation presentation, MenuInputForm form, MenuInputDraft draft, Labels labels, IntConsumer select,
                          IntConsumer browse, IntConsumer clear, Runnable confirm, Runnable cancel,
                          org.encinet.mik.shared.menu.MenuFormSession.Actions actions) {
        var fields = Ui.form().key("form");
        var indices = new java.util.HashMap<String,Integer>();
        boolean multiple = form.fields().size() > 1;
        for (int index = 0; index < form.fields().size(); index++) {
            var field = form.fields().get(index);
            int fieldIndex = index;
            indices.put(field.key(), index);
            boolean labelled = presentation == Presentation.INLINE || multiple || !plain(field.label()).equals(plain(form.title()));
            Component label = labelled ? field.label() : Component.empty();
            if (index != draft.viewed()) {
                fields.field(field.key(), label, Ui.column().key(field.key()).build());
                continue;
            }
            String value = draft.value(field.key());
            var edit = Ui.textField(value).preview(field.preview()).placeholder(labels.empty()).key(field.key())
                    .maxLines(field.multiline() ? 4 : 1).editing(index == draft.selected())
                    .primary((_, _) -> select.accept(fieldIndex));
            if (!value.isEmpty()) edit.actions(Ui.button(UiIcons.CROSS, labels.clear()).key("clear")
                    .primary((_, _) -> clear.accept(fieldIndex)).build());
            fields.field(field.key(), label, edit.build());
        }
        fields.viewedField(form.fields().get(draft.viewed()).key(), key -> browse.accept(indices.get(key)));
        var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(6))).key("tools");
        var submit = Ui.button(form.submit()).key("confirm").primary((_, _) -> confirm.run());
        if (labels.problem() != null) submit.disabled(labels.problem());
        if (actions == org.encinet.mik.shared.menu.MenuFormSession.Actions.SUBMIT_AND_CANCEL)
            tools.child(Ui.button(form.cancel()).key("cancel").primary((_, _) -> cancel.run()).build());
        tools.child(submit.build());
        if (labels.problem() != null) {
            var feedback = Ui.layout(UiLayouts.preferredWidth(UiLength.ui(160), UiLayouts.column(UiLength.ZERO)))
                    .key("feedback").sizing(UiSizing.atLeast(new UiSize(120, 0)))
                    .child(UiKey.named("message"), Ui.text(labels.problem()).key("problem").textWidth(FloatingMenuTextWidth.CONTENT)
                            .alignment(FloatingMenuDecoration.Alignment.LEFT).build(), new LinearSlot(0, LinearSlot.Alignment.START)).build();
            fields.feedback(feedback);
        }
        fields.actions(tools.build());
        var body = Ui.layout(UiLayouts.contentColumn(UiKey.named("form"),UiLength.ui(10))).key("input");
        if (presentation == Presentation.WINDOW) body.child(Ui.heading(form.title()).key("title").build());
        if (!form.description().equals(Component.empty())) body.child(Ui.text(form.description()).key("description").build());
        body.child(fields.build(), LinearSlot.stretch());
        UiNode content = UiTemplates.focus(body.build(), UiKey.named("form"));
        return presentation == Presentation.INLINE ? content : UiTemplates.window("draft", content);
    }

    private static String plain(Component text) { return PlainTextComponentSerializer.plainText().serialize(text); }

    private MenuInputView() { }
}
