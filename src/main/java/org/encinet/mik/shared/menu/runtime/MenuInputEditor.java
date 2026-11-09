package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;

import java.util.UUID;

/** Page-owned form drafts; each field is edited in a native dialog and only form submission persists it. */
final class MenuInputEditor implements MenuFormSession {
    private final FloatingMenuService menus;
    private final LanguageService language;
    private final Player viewer;
    private final MenuInputForm form;
    private final MenuInputDraft draft;
    private final String screenId = "input:" + UUID.randomUUID();
    private FloatingMenuHandle handle;
    private MenuTextEditor input;
    private Component notice;
    private boolean closed;
    private Runnable changed;
    private Runnable finished;

    MenuInputEditor(FloatingMenuService menus, LanguageService language, Player viewer, MenuInputForm form) {
        this.menus = menus; this.language = language; this.viewer = viewer; this.form = form;
        draft = new MenuInputDraft(form);
    }

    @SuppressWarnings("resource") // Successful presentation transfers cleanup to the page lifecycle.
    static void open(FloatingMenuService menus, LanguageService language, Player viewer, MenuInputForm form) {
        var editor = new MenuInputEditor(menus, language, viewer, form);
        try {
            editor.handle = menus.present(viewer, editor.scene());
            editor.select(0);
        } catch (RuntimeException | Error failure) {
            try { editor.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void inline(Runnable changed, Runnable finished) {
        this.changed = java.util.Objects.requireNonNull(changed);
        this.finished = java.util.Objects.requireNonNull(finished);
    }

    private MenuScene scene() {
        return MenuScene.screen(screenId).stableAnchor().content(view(MenuInputView.Presentation.WINDOW))
                .lifecycle((_, currentHandle, previous, current, _) -> {
                    if (current == FloatingMenuState.CLOSING || current == FloatingMenuState.CLOSED) close();
                    else if (current == FloatingMenuState.SUSPENDED) stopInput();
                    else if (current == FloatingMenuState.OPENING && previous == FloatingMenuState.SUSPENDED)
                        handle = currentHandle;
                }).build();
    }

    @Override public org.encinet.mik.shared.menu.ui.UiNode content(MenuFormSession.Actions actions) {
        return view(MenuInputView.Presentation.INLINE, actions);
    }

    private org.encinet.mik.shared.menu.ui.UiNode view(MenuInputView.Presentation presentation) {
        return view(presentation, MenuFormSession.Actions.SUBMIT_AND_CANCEL);
    }

    private org.encinet.mik.shared.menu.ui.UiNode view(MenuInputView.Presentation presentation, MenuFormSession.Actions actions) {
        var labels = new MenuInputView.Labels(text(Message.MENU_INPUT_EMPTY),
                text(Message.MENU_INPUT_CLEAR), problem());
        return MenuInputView.content(presentation, form, draft, labels, this::select,
                this::browse, index -> { if (!closed) { draft.clear(index); notice = null; redraw(); } }, this::confirm, this::cancel, actions);
    }

    private void browse(int index) {
        if (closed || draft.submitted()) return;
        stopInput();
        draft.browse(index);
        redraw();
    }

    private void select(int index) {
        if (closed) return;
        stopInput();
        draft.select(index);
        notice = null;
        // Publish the selected field before acquiring a dialog lease: a page update invalidates native input.
        redraw();
        if (closed) return;
        var field = draft.field();
        input = new MenuTextEditor(draft.value(field.key()),
                (value, problem, accepted, cancelled) -> {
                    var session = menus.editText(viewer, form.title(), field, value, problem, text(Message.MENU_INPUT_DONE),
                            text(Message.CANCEL), accepted, cancelled);
                    return session::close;
                }, raw -> problem(MenuInputDraft.validate(field, raw)), raw -> {
                    if (closed) return;
                    input = null;
                    draft.receive(raw);
                    redraw();
                }, () -> {
                    if (closed) return;
                    input = null;
                    redraw();
                });
        input.open();
    }

    @Override public void focus(String field) {
        for (int index = 0; index < form.fields().size(); index++) {
            if (form.fields().get(index).key().equals(field)) { select(index); return; }
        }
        throw new IllegalArgumentException("Unknown input field: " + field);
    }

    private void stopInput() {
        var previous = input;
        input = null;
        if (previous != null) previous.close();
    }

    private void redraw() {
        if (!closed && changed != null) { changed.run(); return; }
        if (!closed && handle != null && (handle.state() == FloatingMenuState.ACTIVE || handle.state() == FloatingMenuState.OPENING))
            handle.update(scene());
    }

    private Component problem(MenuInputDraft.Problem invalid) {
        if (invalid == null) return null;
        if (invalid.singleLine()) return text(Message.MENU_INPUT_SINGLE_LINE);
        return text(Message.MENU_INPUT_TOO_LONG, invalid.length(), invalid.field().maximumLength());
    }

    private Component problem() {
        Component invalid = problem(draft.problem());
        return invalid == null ? notice : invalid;
    }

    private void confirm() {
        if (closed || input != null || draft.problem() != null) return;
        var values = draft.values();
        notice = form.validation().apply(viewer, values);
        if (notice != null) { redraw(); return; }
        if (!draft.submit()) return;
        close();
        if (finished != null) finished.run();
        else handle.back();
        form.onSubmit().accept(viewer, values);
    }

    private void cancel() {
        if (closed) return;
        close();
        if (finished != null) finished.run();
        else handle.back();
    }

    @Override public void close() {
        closed = true;
        stopInput();
    }

    private Component text(Message message, Object... args) {
        return Component.text(language.t(viewer, message, args), NamedTextColor.WHITE);
    }
}
