package org.encinet.mik.module.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void resolvesMinecraftLanguageIds() {
        assertEquals(Language.ZH_CN, Language.fromId("zh_cn").orElseThrow());
        assertEquals(Language.ZH_HK, Language.fromId("ZH-HK").orElseThrow());
        assertEquals(Language.ZH_TW, Language.fromId("zh_tw").orElseThrow());
        assertEquals(Language.LZH, Language.fromId("lzh").orElseThrow());
        assertEquals(Language.EN_US, Language.fromId("en-US").orElseThrow());
        assertTrue(Language.fromId("zh").isEmpty());
    }

    @Test
    void preservesChineseLocaleRegions() {
        assertEquals(Language.ZH_CN, Language.fromLocale(Locale.forLanguageTag("zh-CN")).orElseThrow());
        assertEquals(Language.ZH_HK, Language.fromLocale(Locale.forLanguageTag("zh-HK")).orElseThrow());
        assertEquals(Language.ZH_HK, Language.fromLocale(Locale.forLanguageTag("zh-MO")).orElseThrow());
        assertEquals(Language.ZH_TW, Language.fromLocale(Locale.forLanguageTag("zh-TW")).orElseThrow());
        assertEquals(Language.LZH, Language.fromLocale(Locale.forLanguageTag("lzh")).orElseThrow());
        assertEquals(Language.EN_US, Language.fromLocale(Locale.UK).orElseThrow());
    }
}
