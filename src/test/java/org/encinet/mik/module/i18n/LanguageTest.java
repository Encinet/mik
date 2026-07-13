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
        assertEquals(Language.DE_DE, Language.fromId("de-DE").orElseThrow());
        assertEquals(Language.ES_ES, Language.fromId("es_es").orElseThrow());
        assertEquals(Language.FR_FR, Language.fromId("fr-FR").orElseThrow());
        assertEquals(Language.IT_IT, Language.fromId("it_it").orElseThrow());
        assertEquals(Language.JA_JP, Language.fromId("ja-JP").orElseThrow());
        assertEquals(Language.KO_KR, Language.fromId("ko_kr").orElseThrow());
        assertEquals(Language.NL_NL, Language.fromId("nl-NL").orElseThrow());
        assertEquals(Language.PT_BR, Language.fromId("pt_br").orElseThrow());
        assertEquals(Language.PT_BR, Language.fromId("pt-PT").orElseThrow());
        assertEquals(Language.RU_RU, Language.fromId("ru-RU").orElseThrow());
        assertEquals(Language.TH_TH, Language.fromId("th_th").orElseThrow());
        assertEquals(Language.UK_UA, Language.fromId("uk-UA").orElseThrow());
        assertEquals(Language.DE_DE, Language.fromId("de_at").orElseThrow());
        assertEquals(Language.ES_ES, Language.fromId("es_mx").orElseThrow());
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
        assertEquals(Language.DE_DE, Language.fromLocale(Locale.forLanguageTag("de-AT")).orElseThrow());
        assertEquals(Language.ES_ES, Language.fromLocale(Locale.forLanguageTag("es-MX")).orElseThrow());
        assertEquals(Language.FR_FR, Language.fromLocale(Locale.CANADA_FRENCH).orElseThrow());
        assertEquals(Language.IT_IT, Language.fromLocale(Locale.ITALIAN).orElseThrow());
        assertEquals(Language.JA_JP, Language.fromLocale(Locale.JAPANESE).orElseThrow());
        assertEquals(Language.KO_KR, Language.fromLocale(Locale.KOREAN).orElseThrow());
        assertEquals(Language.NL_NL, Language.fromLocale(Locale.forLanguageTag("nl-BE")).orElseThrow());
        assertEquals(Language.PT_BR, Language.fromLocale(Locale.forLanguageTag("pt-BR")).orElseThrow());
        assertEquals(Language.RU_RU, Language.fromLocale(Locale.forLanguageTag("ru-RU")).orElseThrow());
        assertEquals(Language.TH_TH, Language.fromLocale(Locale.forLanguageTag("th-TH")).orElseThrow());
        assertEquals(Language.UK_UA, Language.fromLocale(Locale.forLanguageTag("uk-UA")).orElseThrow());
    }
}
