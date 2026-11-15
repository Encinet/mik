package org.encinet.mik.shared.menu;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.ui.UiKey;

/** Reusable form content; the UI chooses input policy and whether the initiating action starts editing. */
public final class MenuFormPanel implements MenuPanel {
    private final MenuFormSession input;
    private final MenuInputForm form;
    private final String focus;
    private final boolean editOnAttach;
    private final MenuFormSession.Actions actions;
    private FloatingMenuHandle handle;

    public MenuFormPanel(Player player, MenuInputForm form, String focus, boolean editOnAttach,
                         MenuFormSession.Actions actions, Runnable redraw, Consumer<FloatingMenuHandle> finished) {
        this.form = form; this.focus = focus; this.editOnAttach = editOnAttach; this.actions = actions;
        input = FloatingMenus.inlineInput(player, form, redraw, () -> { if (handle != null) finished.accept(handle); });
    }

    public static MenuWindowHandle<Void> open(FloatingMenuHandle owner, UiKey key, Player player,
                                               MenuInputForm form, String focus, boolean editOnAttach) {
        return owner.openWindow(MenuWindowSpec.task(key), context -> new MenuFormPanel(player, form, focus,
                editOnAttach, MenuFormSession.Actions.SUBMIT_AND_CANCEL, context.redraw(), FloatingMenuHandle::close));
    }

    @Override public MenuScene render() { return MenuScene.screen("menu-form", form.title()).content(input.content(actions)).build(); }
    @Override public void attach(FloatingMenuHandle handle) {
        this.handle = handle;
        if (editOnAttach) input.edit(focus); else input.focus(focus);
    }
    @Override public void close() { input.close(); }
}
