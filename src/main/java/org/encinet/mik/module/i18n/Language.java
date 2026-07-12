package org.encinet.mik.module.i18n;

import java.util.Map;
import java.util.Locale;
import java.util.Optional;

public enum Language {
    ZH_CN("zh_cn", Locale.SIMPLIFIED_CHINESE, "简体中文"),
    ZH_HK("zh_hk", Locale.forLanguageTag("zh-HK"), "繁體中文（香港）"),
    ZH_TW("zh_tw", Locale.TRADITIONAL_CHINESE, "繁體中文（台灣）"),
    LZH("lzh", Locale.forLanguageTag("lzh"), "文言（華夏）"),
    EN_US("en_us", Locale.US, "English");

    public static final Language DEFAULT = ZH_CN;
    private static final Map<String, Language> BY_ID = Map.of(
            "zh_cn", ZH_CN,
            "zh_hk", ZH_HK,
            "zh_tw", ZH_TW,
            "lzh", LZH,
            "en_us", EN_US
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
        return Optional.ofNullable(BY_ID.get(normalizeId(id)));
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
            default -> Optional.empty();
        };
    }

    private static String normalizeId(String id) {
        return id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
