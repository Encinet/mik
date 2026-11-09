package org.encinet.mik.shell.menu;

import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuScene;

import java.util.function.Function;

/** Exercises feature layouts inside the production settings host, without duplicating its frame. */
public final class SettingsMenuTestSupport {
    private SettingsMenuTestSupport() { }

    public static MenuScene frame(MenuScene panel, Function<Message, String> text, String category) {
        return SettingsMenu.frame(panel, SettingsMenu.navigation(text, SettingsMenu.Category.valueOf(category),
                new org.encinet.mik.shared.menu.ui.UiReadingPosition(org.encinet.mik.shared.menu.ui.UiKey.named("category:" + category.toLowerCase(java.util.Locale.ROOT)),0,SettingsMenu.Category.valueOf(category).ordinal()), _ -> {},
                _ -> (_, _) -> { })).build();
    }
}
