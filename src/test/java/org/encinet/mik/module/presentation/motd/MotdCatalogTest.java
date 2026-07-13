package org.encinet.mik.module.presentation.motd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

class MotdCatalogTest {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @Test
    void everyLanguageHasACompleteParsableProfile() {
        for (Language language : Language.values()) {
            MotdProfileSpec profile = MotdCatalog.profile(language);
            assertNotNull(profile, language.id());
            assertTrue(!profile.brand().isBlank(), language.id() + " brand");
            assertTrue(!profile.category().isBlank(), language.id() + " category");
            assertTrue(profile.normal().length > 0, language.id() + " normal MOTDs");
            assertTrue(profile.eggs().length > 0, language.id() + " repeat-ping branches");
            assertTrue(profile.afk().length > 0, language.id() + " AFK MOTDs");
            assertTrue(profile.night().length > 0, language.id() + " night MOTDs");
            assertTrue(profile.knownPlayer().length > 0, language.id() + " known-player MOTDs");
            assertTrue(Arrays.stream(profile.knownPlayer()).allMatch(line -> line.contains("{player}")),
                    language.id() + " known-player placeholder");

            parseAll(profile.brand(), profile.category());
            parseAll(profile.normal());
            parseAll(profile.afk());
            parseAll(profile.night());
            parseAll(profile.knownPlayer());
            for (String[] branch : profile.eggs()) {
                assertTrue(branch.length > 0, language.id() + " empty repeat-ping branch");
                parseAll(branch);
            }
        }
    }

    @Test
    void addedLanguagesUseDedicatedLocalizedFamilies() {
        MotdProfileSpec english = MotdCatalog.profile(Language.EN_US);
        assertTrue(!Arrays.equals(english.normal(), MotdCatalog.profile(Language.DE_DE).normal()));
        assertTrue(!Arrays.equals(english.normal(), MotdCatalog.profile(Language.JA_JP).normal()));
        assertTrue(!Arrays.equals(english.normal(), MotdCatalog.profile(Language.UK_UA).normal()));
    }

    private static void parseAll(String... lines) {
        for (String line : lines) {
            MINI_MESSAGE.deserialize(line.replace("{player}", "Player"));
        }
    }
}
