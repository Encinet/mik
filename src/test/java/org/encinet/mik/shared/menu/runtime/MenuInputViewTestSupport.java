package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.encinet.mik.shared.menu.ui.UiNode;

/** Lets feature geometry tests embed the same field previews as the live editor. */
public final class MenuInputViewTestSupport {
    public static UiNode content(MenuInputForm form, Component empty, Component clear) {
        var draft = new MenuInputDraft(form);
        return MenuInputView.content(MenuInputView.Presentation.INLINE, form, draft, new MenuInputView.Labels(empty, clear, null),
                _ -> {}, draft::browse, draft::clear, () -> {}, () -> {});
    }
    private MenuInputViewTestSupport() { }
}
