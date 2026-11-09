package org.encinet.mik.feature.entertainment.music.ui;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.*;

/** A companion owns its form draft until it is submitted, cancelled or dismissed. */
final class MusicInputPanel implements MenuPanel {
    private final MenuFormSession input;
    private final String focus;
    private final net.kyori.adventure.text.Component title;
    private final MenuFormSession.Actions actions;
    private FloatingMenuHandle handle;

    MusicInputPanel(Player player, MenuInputForm form, String focus, MenuFormSession.Actions actions,
                    Runnable redraw, Consumer<FloatingMenuHandle> finished) {
        this.focus = focus;
        this.title = form.title(); this.actions = actions;
        input = FloatingMenus.inlineInput(player, form, redraw, () -> { if (handle != null) finished.accept(handle); });
    }

    @Override public MenuScene render() { return MenuScene.screen("music-input", title).content(input.content(actions)).build(); }
    @Override public void attach(FloatingMenuHandle handle) { this.handle = handle; input.focus(focus); }
    @Override public void close() { input.close(); }
}
