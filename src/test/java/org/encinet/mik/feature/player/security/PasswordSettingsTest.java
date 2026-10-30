package org.encinet.mik.feature.player.security;

import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuScene;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordSettingsTest {
    @Test void ordinaryAccountsCanSetOrRemoveWhileApplicationsAndStaffExplainDisabledRemoval() throws Exception {
        var bundle = FluentTestSupport.bundle(Language.ZH_CN);
        var language = new LanguageService(null) {
            @Override public String t(Language locale, Message key, Object... args) { return FluentTestSupport.text(bundle, key, args); }
        };
        var fresh = scene(language, false, PlayerRole.MEMBER, false);
        assertTrue(entry(fresh, "set-or-change").enabled());
        assertNull(entry(fresh, "remove"));
        assertTrue(entry(scene(language, true, PlayerRole.MEMBER, false), "remove").enabled());
        var applicant = scene(language, true, PlayerRole.MEMBER, true);
        assertFalse(entry(applicant, "remove").enabled());
        assertNotNull(entry(applicant, "required"));
        var staff = scene(language, true, PlayerRole.MODERATOR, false);
        assertFalse(entry(staff, "remove").enabled());
        assertEquals(FluentTestSupport.text(bundle, Message.PASSWORD_KEEP_ROLE),
                PlainTextComponentSerializer.plainText().serialize(entry(staff, "remove").disabledReason()));
        assertTrue(entry(staff, "set-or-change").enabled());
        assertFalse(entry(scene(language, false, PlayerRole.CUSTODIAN, false), "set-or-change").enabled());
    }

    private static MenuScene scene(LanguageService language, boolean present, PlayerRole role, boolean retained) {
        return PasswordSettings.scene(Language.ZH_CN, language, present, role, retained, (player, menu) -> { }, (player, menu) -> { });
    }

}
