package org.encinet.mik.feature.player.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenuTextScale;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.role.PlayerRole;
import org.encinet.mik.shell.menu.SettingsMenuTestSupport;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertDisjoint;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;
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

    @Test void webAccountActionWorksInStandaloneAndEmbeddedSecurityPages() throws Exception {
        for (Language locale : Language.values()) {
            var bundle = FluentTestSupport.bundle(locale);
            var language = new LanguageService(null) {
                @Override public String t(Language selected, Message key, Object... args) { return FluentTestSupport.text(bundle, key, args); }
            };
            for (boolean present : new boolean[] {false, true}) {
                var opened = new AtomicInteger();
                var panel = PasswordSettings.scene(locale, language, present, PlayerRole.MEMBER, present,
                        (_, _) -> { }, (_, _) -> { }, (_, _) -> opened.incrementAndGet());
                var hosted = SettingsMenuTestSupport.frame(panel, message -> language.t(locale, message), "SECURITY");
                for (var page : List.of(panel, hosted)) {
                    var web = entry(page, "web-account");
                    assertNotNull(web);
                    assertTrue(web.enabled());
                    assertEquals(language.t(locale, Message.WEB_ACCOUNT_TITLE),
                            PlainTextComponentSerializer.plainText().serialize(web.label()));
                    web.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
                    for (var scale : FloatingMenuTextScale.values()) {
                        assertDisjoint(SceneGeometryTestSupport.measure(page, scale.factor()));
                        SceneGeometryTestSupport.assertVisualContentsFit(page, scale.factor());
                    }
                }
                assertEquals(2, opened.get());
            }
        }
    }

    private static MenuScene scene(LanguageService language, boolean present, PlayerRole role, boolean retained) {
        return PasswordSettings.scene(Language.ZH_CN, language, present, role, retained,
                (_, _) -> { }, (_, _) -> { }, (_, _) -> { });
    }

}
