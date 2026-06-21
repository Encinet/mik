package org.encinet.mik.module.i18n;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Language {
    ZH_CN("zh_cn", Locale.SIMPLIFIED_CHINESE, "简体中文"),
    EN_US("en_us", Locale.US, "English");

    public static final Language DEFAULT = ZH_CN;

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
        return Arrays.stream(values())
                .filter(language -> language.id.equalsIgnoreCase(id))
                .findFirst();
    }

    public static Optional<Language> fromLocale(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return switch (locale.getLanguage().toLowerCase(Locale.ROOT)) {
            case "zh" -> Optional.of(ZH_CN);
            case "en" -> Optional.of(EN_US);
            default -> Optional.empty();
        };
    }
}
