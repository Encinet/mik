package org.encinet.mik.module.presentation.motd.holiday;

import org.encinet.mik.module.i18n.Language;

import java.util.Map;
import java.util.random.RandomGenerator;

public final class HolidayMessageCatalog {

    private static final Map<String, String> TRADITIONAL_NAMES = Map.ofEntries(
            Map.entry("春节", "春節"),
            Map.entry("除夕", "除夕"),
            Map.entry("元旦", "元旦"),
            Map.entry("清明", "清明"),
            Map.entry("劳动", "勞動"),
            Map.entry("端午", "端午"),
            Map.entry("中秋", "中秋"),
            Map.entry("国庆", "國慶"),
            Map.entry("圣诞", "聖誕"),
            Map.entry("情人", "情人"),
            Map.entry("元宵", "元宵"),
            Map.entry("儿童", "兒童"),
            Map.entry("教师", "教師"),
            Map.entry("与", "與"),
            Map.entry("节", "節")
    );

    private HolidayMessageCatalog() {}

    public static String render(Language language, HolidayOccurrence holiday, RandomGenerator rng) {
        if (language == Language.ZH_CN) {
            return ChineseHolidayMessages.render(holiday.name(), rng);
        }

        String name = toTraditional(holiday.name());
        return switch (language) {
            case ZH_HK -> "<gradient:#f7971e:#ffd200>今日是 {holiday}，祝你玩得開心</gradient>"
                    .replace("{holiday}", name);
            case ZH_TW -> "<gradient:#f7971e:#ffd200>今天是 {holiday}，祝你玩得開心</gradient>"
                    .replace("{holiday}", name);
            default -> throw new IllegalArgumentException("Holiday MOTD is not enabled for " + language.id());
        };
    }

    static String toTraditional(String holiday) {
        String converted = holiday;
        for (Map.Entry<String, String> entry : TRADITIONAL_NAMES.entrySet()) {
            converted = converted.replace(entry.getKey(), entry.getValue());
        }
        return converted;
    }
}
