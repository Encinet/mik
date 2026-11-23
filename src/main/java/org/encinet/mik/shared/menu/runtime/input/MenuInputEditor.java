package org.encinet.mik.shared.menu.runtime.input;

import org.encinet.mik.shared.menu.runtime.FloatingMenuService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.menu.*;

import java.util.UUID;

/** Executes the form UI's declared input methods and owns drafts, capture and response invalidation. */
public final class MenuInputEditor implements MenuFormSession {
    private final FloatingMenuService menus;
    private final LanguageService language;
    private final Player viewer;
    private final MenuInputForm form;
    private final MenuInputDraft draft;
    private final String screenId = "input:" + UUID.randomUUID();
    private FloatingMenuHandle handle;
    private MenuTextEditor input;
    private MenuInputLease chat;
    private Object chatToken;
    private Component notice;
    private boolean closed;
    private Object submission;
    private Runnable changed;
    private Runnable finished;

    public MenuInputEditor(FloatingMenuService menus, LanguageService language, Player viewer, MenuInputForm form) {
        this.menus = menus; this.language = language; this.viewer = viewer; this.form = form;
        draft = new MenuInputDraft(form);
    }

    public static void open(FloatingMenuService menus, LanguageService language, Player viewer, MenuInputForm form) {
        var editor = new MenuInputEditor(menus, language, viewer, form);
        try {
            var owner = menus.current(viewer);
            if (owner.isEmpty()) editor.handle = menus.present(viewer, editor.scene());
            else editor.handle = owner.get().openWindow(MenuWindowSpec.task(org.encinet.mik.shared.menu.ui.UiKey.item(editor.screenId)), context -> {
                editor.inline(context.redraw(), context.handle()::close);
                return new MenuPanel() {
                    public MenuScene render() { return MenuScene.screen(editor.screenId,form.title())
                            .content(editor.view(MenuInputView.Presentation.INLINE)).build(); }
                    public void attach(FloatingMenuHandle handle) { editor.handle = handle; }
                    public void close() { editor.close(); }
                };
            });
        } catch (RuntimeException | Error failure) {
            try { editor.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public void inline(Runnable changed, Runnable finished) {
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
                text(Message.MENU_INPUT_CLEAR), submission == null ? problem() : text(Message.MENU_INPUT_SAVING),
                submission != null ? text(Message.MENU_INPUT_SAVING) : draft.problem() == null ? null : problem());
        var editing = new MenuInputView.Editing(input != null || chat != null ? draft.selected() : -1,
                chat == null ? null : language.rich(viewer, Message.MENU_QUERY_HINT_RICH, NamedTextColor.AQUA,
                        RichArg.component("chatKey", Component.keybind("key.chat", NamedTextColor.WHITE))),
                text(Message.MENU_QUERY_CANCEL), () -> { stopInput(); redraw(); }, submission != null);
        return MenuInputView.content(presentation, form, draft, labels, this::select,
                this::browse, index -> { if (!closed && submission == null) { stopInput(); draft.clear(index); notice = null; redraw(); } },
                this::confirm, this::cancel, actions, editing);
    }

    private void browse(int index) {
        if (closed || submission != null || draft.submitted()) return;
        stopInput();
        draft.browse(index);
        redraw();
    }

    private void select(int index) {
        if (closed || submission != null) return;
        stopInput();
        draft.select(index);
        notice = null;
        // Publish the selected field before acquiring a dialog lease: a page update invalidates native input.
        redraw();
        if (closed) return;
        switch (draft.field().inputMethod()) {
            case CHAT -> startChat();
            case DIALOG -> startDialog();
        }
    }

    private void startDialog() {
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

    private void startChat() {
        Object token = new Object();
        chatToken = token;
        chat = menus.beginChatOnce(viewer, raw -> {
            if (closed || chatToken != token || chat == null) return;
            var accepted = chat;
            chat = null;
            chatToken = null;
            if (!accepted.complete() || closed) return;
            draft.receive(raw);
            notice = null;
            redraw();
        }, () -> {
            if (chatToken != token) return;
            chat = null;
            chatToken = null;
            if (!closed) redraw();
        });
        redraw();
    }

    @Override public void focus(String field) { browse(fieldIndex(field)); }
    @Override public void edit(String field) { select(fieldIndex(field)); }
    private int fieldIndex(String field) {
        for (int index = 0; index < form.fields().size(); index++) {
            if (form.fields().get(index).key().equals(field)) return index;
        }
        throw new IllegalArgumentException("Unknown input field: " + field);
    }

    private void stopInput() {
        chatToken = null;
        var previousChat = chat;
        chat = null;
        if (previousChat != null) previousChat.close();
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
        if (closed || submission != null || input != null || chat != null || draft.problem() != null) return;
        var values = draft.values();
        notice = form.validation().apply(viewer, values);
        if (notice != null) { redraw(); return; }
        if (form.asynchronousSubmit() == null) {
            try { form.onSubmit().accept(viewer, values); }
            catch (RuntimeException error) { notice = text(Message.MENU_INPUT_SAVE_FAILED); redraw(); return; }
            saved(); return;
        }
        Object ticket = new Object(); submission = ticket; notice = null; redraw();
        var completion = new MenuInputForm.Completion() {
            private void finish(Component failure) {
                menus.execute(() -> {
                    if (closed || submission != ticket) return;
                    submission = null;
                    if (failure == null) saved(); else { notice = failure; redraw(); }
                });
            }
            public void success() { finish(null); }
            public void failure(Component problem) { finish(java.util.Objects.requireNonNull(problem)); }
        };
        try { form.asynchronousSubmit().submit(viewer, values, completion); }
        catch (RuntimeException error) { completion.failure(text(Message.MENU_INPUT_SAVE_FAILED)); }
    }

    private void saved() {
        if (closed || !draft.submit()) return;
        close();
        if (finished != null) finished.run(); else handle.back();
    }

    private void cancel() {
        if (closed) return;
        close();
        if (finished != null) finished.run();
        else handle.back();
    }

    @Override public void close() {
        closed = true;
        submission = null;
        stopInput();
    }

    private Component text(Message message, Object... args) {
        return Component.text(language.t(viewer, message, args), NamedTextColor.WHITE);
    }
}
