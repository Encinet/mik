package org.encinet.mik.module.presentation.motd.holiday;

import java.util.Map;
import java.util.random.RandomGenerator;

final class ChineseHolidayMessages {

    private static final String[] GENERIC_TEMPLATES = {
            "<gradient:#f7971e:#ffd200>{holiday}快乐！愿今天轻松一点</gradient>",
            "<gold><bold>{holiday}快乐！</bold></gold><white> 祝你摸鱼顺利，心情不错</white>",
            "<gradient:#ff9a9e:#fad0c4>今天是 {holiday}，祝你玩得开心</gradient>",
            "<gradient:#84fab0:#8fd3f4>又可以放假了~</gradient><white> {holiday}快乐！</white>"
    };

    private static final String[] NATIONAL_MID_AUTUMN_TEMPLATES = {
            "<gradient:#de6262:#ffb88c>国庆中秋双节快乐！愿假期慢一点、月色亮一点</gradient>",
            "<red><bold>国庆快乐！</bold></red><gold> 中秋快乐！祝你假期愉快</gold>",
            "<gradient:#f7971e:#ffd200>双节快乐，愿今天团圆、放松、开心摸鱼</gradient>"
    };

    private static final Map<String, String[]> TEMPLATES = Map.ofEntries(
            Map.entry("春节", new String[]{
                    "<gradient:#ff512f:#f09819><bold>春节快乐！</bold></gradient><white> 新年也要开心摸鱼</white>",
                    "<red><bold>春节快乐！</bold></red><gold> 祝你欧气满满</gold>",
                    "<gradient:#f7971e:#ffd200>新春快乐，愿你今年顺顺利利</gradient>"
            }),
            Map.entry("除夕", new String[]{
                    "<gradient:#ff512f:#dd2476>除夕快乐，今晚适合守岁和团圆</gradient>",
                    "<gold><bold>除夕快乐！</bold></gold><white> 年夜饭后见</white>"
            }),
            Map.entry("元旦", new String[]{
                    "<gradient:#00c6ff:#0072ff>元旦快乐！新的一年继续闪闪发光</gradient>",
                    "<aqua><bold>新年快乐！</bold></aqua><white> 从一个轻松的早晨开始</white>"
            }),
            Map.entry("清明", new String[]{"<gradient:#bdc3c7:#2c3e50>清明时节，愿你安好</gradient>"}),
            Map.entry("劳动", new String[]{
                    "<gradient:#56ab2f:#a8e063>劳动节快乐！今天也可以合法摸鱼</gradient>",
                    "<green><bold>劳动节快乐！</bold></green><white> 辛苦了，来休息一下</white>"
            }),
            Map.entry("端午", new String[]{
                    "<gradient:#11998e:#38ef7d>端午安康！记得吃粽子</gradient>",
                    "<green><bold>端午安康！</bold></green><white> 今天适合划船，也适合摸鱼</white>"
            }),
            Map.entry("中秋", new String[]{
                    "<gradient:#f7971e:#ffd200>中秋快乐，愿月色和心情都很好</gradient>",
                    "<gold><bold>中秋快乐！</bold></gold><white> 今天适合团圆，也适合放松</white>"
            }),
            Map.entry("国庆", new String[]{
                    "<gradient:#de6262:#ffb88c>国庆快乐！愿假期慢一点、开心多一点</gradient>",
                    "<red><bold>国庆快乐！</bold></red><gold> 祝你假期愉快</gold>"
            }),
            Map.entry("圣诞", new String[]{
                    "<gradient:#00b09b:#96c93d>圣诞快乐！愿今天有一点惊喜</gradient>",
                    "<red><bold>圣诞快乐！</bold></red><white> 今天适合下雪和摸鱼</white>"
            }),
            Map.entry("情人", new String[]{"<gradient:#ff758c:#ff7eb3>情人节快乐！也祝一个人过得自在</gradient>"}),
            Map.entry("元宵", new String[]{"<gradient:#f7971e:#ffd200>元宵快乐，愿今晚灯火温柔</gradient>"}),
            Map.entry("儿童", new String[]{"<gradient:#89f7fe:#66a6ff>儿童节快乐！保持一点幼稚也不错</gradient>"}),
            Map.entry("教师", new String[]{"<gradient:#667eea:#764ba2>教师节快乐，感谢每一位认真教人的人</gradient>"})
    );

    private ChineseHolidayMessages() {}

    static String render(String holiday, RandomGenerator rng) {
        if (holiday.contains("国庆") && holiday.contains("中秋")) {
            return fill(random(NATIONAL_MID_AUTUMN_TEMPLATES, rng), holiday);
        }
        for (Map.Entry<String, String[]> entry : TEMPLATES.entrySet()) {
            if (holiday.contains(entry.getKey())) {
                return fill(random(entry.getValue(), rng), holiday);
            }
        }
        return fill(random(GENERIC_TEMPLATES, rng), holiday);
    }

    private static String random(String[] templates, RandomGenerator rng) {
        return templates[rng.nextInt(templates.length)];
    }

    private static String fill(String template, String holiday) {
        return template.replace("{holiday}", holiday);
    }
}
