package org.encinet.mik.module.i18n;

import java.util.Map;
import java.util.Locale;
import java.util.Optional;

public enum Language {
    ZH_CN("zh_cn", Locale.SIMPLIFIED_CHINESE, "简体中文"),
    ZH_HK("zh_hk", Locale.forLanguageTag("zh-HK"), "繁體中文（香港）"),
    ZH_TW("zh_tw", Locale.TRADITIONAL_CHINESE, "繁體中文（台灣）"),
    LZH("lzh", Locale.forLanguageTag("lzh"), "文言（華夏）"),
    EN_US("en_us", Locale.US, "English"),
    DE_DE("de_de", Locale.GERMANY, "Deutsch"),
    ES_ES("es_es", Locale.forLanguageTag("es-ES"), "Español"),
    FR_FR("fr_fr", Locale.FRANCE, "Français"),
    IT_IT("it_it", Locale.ITALY, "Italiano"),
    JA_JP("ja_jp", Locale.JAPAN, "日本語"),
    KO_KR("ko_kr", Locale.KOREA, "한국어"),
    NL_NL("nl_nl", Locale.forLanguageTag("nl-NL"), "Nederlands"),
    PT_BR("pt_br", Locale.forLanguageTag("pt-BR"), "Português"),
    RU_RU("ru_ru", Locale.forLanguageTag("ru-RU"), "Русский"),
    TH_TH("th_th", Locale.forLanguageTag("th-TH"), "ไทย"),
    UK_UA("uk_ua", Locale.forLanguageTag("uk-UA"), "Українська");

    public static final Language DEFAULT = ZH_CN;
    private static final Map<String, Language> BY_ID = Map.ofEntries(
            Map.entry("zh_cn", ZH_CN),
            Map.entry("zh_hk", ZH_HK),
            Map.entry("zh_tw", ZH_TW),
            Map.entry("lzh", LZH),
            Map.entry("en_us", EN_US),
            Map.entry("de_de", DE_DE),
            Map.entry("es_es", ES_ES),
            Map.entry("fr_fr", FR_FR),
            Map.entry("it_it", IT_IT),
            Map.entry("ja_jp", JA_JP),
            Map.entry("ko_kr", KO_KR),
            Map.entry("nl_nl", NL_NL),
            Map.entry("pt_br", PT_BR),
            Map.entry("pt_pt", PT_BR),
            Map.entry("ru_ru", RU_RU),
            Map.entry("th_th", TH_TH),
            Map.entry("uk_ua", UK_UA)
    );

    private final String id;
    private final Locale locale;
    private final String displayName;

    Language(String id, Locale locale, String displayName) {
        this.id = id;
        this.locale = locale;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public Locale locale() {
        return locale;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<Language> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = normalizeId(id);
        Language exact = BY_ID.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }
        if (!normalized.contains("_")) {
            return Optional.empty();
        }
        return fromLocale(Locale.forLanguageTag(normalized.replace('_', '-')));
    }

    public static Optional<Language> fromLocale(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "lzh" -> Optional.of(LZH);
            case "zh" -> Optional.of(switch (locale.getCountry().toUpperCase(Locale.ROOT)) {
                case "HK", "MO" -> ZH_HK;
                case "TW" -> ZH_TW;
                default -> ZH_CN;
            });
            case "en" -> Optional.of(EN_US);
            case "de" -> Optional.of(DE_DE);
            case "es" -> Optional.of(ES_ES);
            case "fr" -> Optional.of(FR_FR);
            case "it" -> Optional.of(IT_IT);
            case "ja" -> Optional.of(JA_JP);
            case "ko" -> Optional.of(KO_KR);
            case "nl" -> Optional.of(NL_NL);
            case "pt" -> Optional.of(PT_BR);
            case "ru" -> Optional.of(RU_RU);
            case "th" -> Optional.of(TH_TH);
            case "uk" -> Optional.of(UK_UA);
            default -> Optional.empty();
        };
    }

    private static String normalizeId(String id) {
        return id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
