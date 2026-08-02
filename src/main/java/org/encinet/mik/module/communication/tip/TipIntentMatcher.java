package org.encinet.mik.module.communication.tip;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Scores chat intent instead of reacting to every raw substring occurrence. */
public final class TipIntentMatcher {

    private static final int MATCH_THRESHOLD = 4;
    private static final List<String> HELP_MARKERS = List.of(
            "?", "？", "怎么", "怎麼", "如何", "哪里", "哪裡", "在哪", "哪个",
            "哪個", "能不能", "能否", "可以吗", "可以嗎", "请问", "請問", "求助",
            "指令", "命令", "help", "how", "what", "where", "can i");
    private static final Map<String, Rule> RULES = rules();

    public Optional<Match> match(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank() || normalized.length() > 256 || normalized.startsWith("/")) {
            return Optional.empty();
        }
        int helpBonus = HELP_MARKERS.stream().anyMatch(marker -> contains(normalized, marker)) ? 2 : 0;
        return RULES.entrySet().stream()
                .map(entry -> score(entry.getKey(), entry.getValue(), normalized, helpBonus))
                .filter(match -> match.score >= MATCH_THRESHOLD)
                .max(Comparator.comparingInt(Match::score)
                        .thenComparingInt(match -> match.evidence.length())
                        .thenComparing(Match::topic));
    }

    public Set<String> supportedTopics() {
        return RULES.keySet();
    }

    static String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static Match score(String topic, Rule rule, String message, int helpBonus) {
        Signal strongest = null;
        for (Signal signal : rule.signals) {
            if (contains(message, signal.phrase)
                    && (strongest == null || signal.weight > strongest.weight
                    || signal.weight == strongest.weight
                    && signal.phrase.length() > strongest.phrase.length())) {
                strongest = signal;
            }
        }
        if (strongest == null) return new Match(topic, 0, "");
        return new Match(topic, strongest.weight + helpBonus, strongest.phrase);
    }

    private static boolean contains(String message, String phrase) {
        if (phrase.chars().allMatch(character -> character < 128)
                && phrase.chars().anyMatch(Character::isLetterOrDigit)) {
            Pattern boundary = Pattern.compile("(?<![\\p{L}\\p{N}_])"
                    + Pattern.quote(phrase) + "(?![\\p{L}\\p{N}_])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return boundary.matcher(message).find();
        }
        return message.contains(phrase);
    }

    private static Map<String, Rule> rules() {
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("spawn", rule(
                signal("/spawn", 8), signal("怎么回主城", 7), signal("怎麼回主城", 7),
                signal("如何回主城", 7), signal("主城在哪", 6), signal("主城在哪裡", 6),
                signal("出生点在哪", 6), signal("出生點在哪", 6), signal("回主城", 5),
                signal("spawn", 3), signal("lobby", 3), signal("主城", 2), signal("出生点", 2),
                signal("出生點", 2)));
        rules.put("home", rule(
                signal("/sethome", 8), signal("/home", 8), signal("怎么回家", 7),
                signal("怎麼回家", 7), signal("如何回家", 7), signal("怎么设置家", 7),
                signal("怎麼設定家", 7), signal("设置传送点", 6), signal("設定傳送點", 6),
                signal("设置家", 5), signal("設定家", 5), signal("回家", 4),
                signal("sethome", 4), signal("home", 3), signal("传送点", 2), signal("傳送點", 2)));
        rules.put("teleport", rule(
                signal("/reback", 8), signal("/back", 8), signal("返回死亡点", 7),
                signal("返回死亡點", 7), signal("死亡地点", 6), signal("死亡地點", 6),
                signal("怎么传送", 6), signal("怎麼傳送", 6), signal("如何传送", 6),
                signal("如何傳送", 6), signal("传送回来", 5), signal("傳送回來", 5),
                signal("teleport", 3), signal("back", 3), signal("传送", 2), signal("傳送", 2)));
        rules.put("afk", rule(
                signal("/afk", 8), signal("怎么挂机", 7), signal("怎麼掛機", 7),
                signal("如何挂机", 7), signal("如何掛機", 7), signal("设置挂机", 6),
                signal("設定掛機", 6), signal("暂离状态", 5), signal("暫離狀態", 5),
                signal("afk", 3), signal("挂机", 2), signal("掛機", 2), signal("暂离", 2),
                signal("暫離", 2)));
        rules.put("announcements", rule(
                signal("/announcements", 8), signal("最近公告", 6), signal("公告在哪", 6),
                signal("更新公告", 5), signal("announcements", 4), signal("announcement", 3),
                signal("公告", 2)));
        rules.put("website", rule(
                signal("mcmik.top", 8), signal("服务器官网", 6), signal("伺服器官網", 6),
                signal("官网在哪", 6), signal("官網在哪", 6), signal("网站在哪", 6),
                signal("網站在哪", 6), signal("server website", 5), signal("官网", 2),
                signal("官網", 2), signal("网站", 2), signal("網站", 2), signal("wiki", 3)));
        rules.put("music", rule(
                signal("/music", 8), signal("怎么点歌", 7), signal("怎麼點歌", 7),
                signal("如何点歌", 7), signal("如何點歌", 7), signal("音乐菜单", 6),
                signal("音樂選單", 6), signal("music", 3), signal("点歌", 3), signal("點歌", 3),
                signal("音乐", 2), signal("音樂", 2)));
        rules.put("music-search", rule(
                signal("/music search", 9), signal("怎么搜索歌曲", 8),
                signal("怎麼搜尋歌曲", 8), signal("如何搜索音乐", 8),
                signal("如何搜尋音樂", 8), signal("搜索歌曲", 6), signal("搜尋歌曲", 6),
                signal("search music", 5), signal("search song", 5),
                signal("找歌", 3), signal("搜歌", 3)));
        rules.put("jukebox", rule(
                signal("怎么打开唱片机", 8), signal("怎麼打開唱片機", 8),
                signal("如何控制唱片机", 8), signal("如何控制唱片機", 8),
                signal("唱片机菜单", 7), signal("唱片機選單", 7),
                signal("jukebox menu", 6), signal("jukebox", 4),
                signal("唱片机", 3), signal("唱片機", 3), signal("唱片", 2)));
        rules.put("language", rule(
                signal("/lang", 8), signal("怎么切换语言", 7), signal("怎麼切換語言", 7),
                signal("如何切换语言", 7), signal("如何切換語言", 7), signal("语言菜单", 6),
                signal("語言選單", 6), signal("language", 3), signal("语言", 2), signal("語言", 2)));
        rules.put("pvp", rule(
                signal("/pvp", 8), signal("怎么开pvp", 7), signal("怎麼開pvp", 7),
                signal("如何开启pvp", 7), signal("如何開啟pvp", 7), signal("玩家对战", 5),
                signal("玩家對戰", 5), signal("pvp", 4), signal("对战", 2), signal("對戰", 2)));
        rules.put("trash", rule(
                signal("/trash", 8), signal("怎么丢垃圾", 7), signal("怎麼丟垃圾", 7),
                signal("如何清理物品", 6), signal("垃圾桶", 5),
                signal("trash", 4), signal("丢东西", 3), signal("丟東西", 3)));
        rules.put("performance", rule(
                signal("/tpsbar", 8), signal("服务器卡", 6),
                signal("伺服器卡", 6), signal("怎么看tps", 6), signal("怎麼看tps", 6),
                signal("view distance", 4), signal("tps", 4), signal("mspt", 4),
                signal("lag", 3), signal("延迟", 2), signal("延遲", 2), signal("性能", 2)));
        rules.put("flight", rule(
                signal("/flyspeed", 9), signal("/fly", 8), signal("怎么飞", 7),
                signal("怎麼飛", 7), signal("如何飞行", 7), signal("如何飛行", 7),
                signal("调整飞行速度", 6), signal("調整飛行速度", 6),
                signal("flyspeed", 5), signal("flight", 3), signal("飞行", 2),
                signal("飛行", 2)));
        rules.put("private-chat", rule(
                signal("/msg", 8), signal("/reply", 8), signal("怎么私聊", 7),
                signal("怎麼私聊", 7), signal("如何私聊", 7), signal("怎么回复私聊", 7),
                signal("怎麼回覆私聊", 7), signal("private message", 5),
                signal("whisper", 4), signal("私聊", 3), signal("私訊", 3)));
        rules.put("chat-settings", rule(
                signal("怎么关闭提及提醒", 8), signal("怎麼關閉提及提醒", 8),
                signal("怎么取消延迟消息", 8), signal("怎麼取消延遲訊息", 8),
                signal("聊天设置", 6), signal("聊天設定", 6), signal("提及提醒", 5),
                signal("mention alert", 5), signal("聊天延迟", 4), signal("聊天延遲", 4)));
        rules.put("nametag", rule(
                signal("/nametag", 8), signal("怎么改名称标签", 8),
                signal("怎麼改名稱標籤", 8), signal("如何设置前缀", 7),
                signal("如何設定前綴", 7), signal("名称标签", 5), signal("名稱標籤", 5),
                signal("nametag", 4), signal("名字前缀", 3), signal("名字前綴", 3)));
        rules.put("hat", rule(
                signal("/hat", 8), signal("怎么把物品戴头上", 8),
                signal("怎麼把物品戴頭上", 8), signal("如何戴帽子", 7),
                signal("物品戴在头上", 6), signal("物品戴在頭上", 6),
                signal("wear item", 4), signal("hat", 3), signal("帽子", 2)));
        rules.put("menu", rule(
                signal("shift+f", 8), signal("shift + f", 8), signal("潜行+f", 8),
                signal("潛行+f", 8), signal("/menu", 8), signal("怎么打开菜单", 7),
                signal("怎麼打開選單", 7), signal("主菜单", 5), signal("主選單", 5),
                signal("menu", 3), signal("菜单", 2), signal("選單", 2)));
        return Map.copyOf(rules);
    }

    private static Rule rule(Signal... signals) {
        return new Rule(List.of(signals));
    }

    private static Signal signal(String phrase, int weight) {
        return new Signal(normalize(phrase), weight);
    }

    public record Match(String topic, int score, String evidence) {
    }

    private record Rule(List<Signal> signals) {
    }

    private record Signal(String phrase, int weight) {
    }
}
