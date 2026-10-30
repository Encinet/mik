package org.encinet.mik.feature.player.security;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuCommand;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.UiTemplates;
import org.encinet.mik.shared.role.PlayerRole;

/** The settings panel shares its restrictions with commands and the credential service. */
final class PasswordSettings {
    private PasswordSettings() { }

    static MenuScene scene(Language locale, LanguageService language, boolean present, PlayerRole role,
                           boolean retained, FloatingMenuCommand edit, FloatingMenuCommand remove) {
        MenuScene.Builder menu = MenuScene.screen("account-security", text(locale, language, Message.PASSWORD_TITLE))
                .withoutTitle().stableAnchor().content(UiTemplates.window("security", UiTemplates.menu(
                        UiTemplates.title("heading"), UiTemplates.list("status"), UiTemplates.list("actions"))));
        menu.information("heading", text(locale, language, Message.PASSWORD_TITLE)).region("heading");
        menu.information("state", text(locale, language, present ? Message.PASSWORD_ENABLED : Message.PASSWORD_DISABLED)).region("status");
        Message restriction = (present ? PasswordEdit.REMOVE : PasswordEdit.SET).restriction(present, role);
        if (present && restriction == null && retained) restriction = Message.PASSWORD_REQUIRED_APPLICATION;
        if (restriction != null) menu.information("required", text(locale, language, restriction)).region("status");
        var change = menu.control("set-or-change", text(locale, language, present ? Message.PASSWORD_CHANGE : Message.PASSWORD_SET))
                .region("actions").primary(edit);
        if (!present && restriction != null) change.disabled(text(locale, language, restriction));
        if (present) {
            var button = menu.control("remove", text(locale, language, Message.PASSWORD_REMOVE)).region("actions").primary(remove);
            if (restriction != null) button.disabled(text(locale, language, restriction));
        }
        return menu.build();
    }

    private static Component text(Language locale, LanguageService language, Message key) {
        return Component.text(language.t(locale, key), NamedTextColor.YELLOW);
    }
}
