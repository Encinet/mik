package org.encinet.mik.feature.server.prison;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PrisonDuration {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)([dhms])");
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private PrisonDuration() { }

    static long parse(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Invalid sentence duration");
        String text = input.strip().toLowerCase(Locale.ROOT).replace("小时", "h").replace("分钟", "m")
                .replace("时", "h").replace("分", "m").replace("秒", "s").replace("天", "d").replace(" ", "");
        if (text.equals("0") || text.equals("perm") || text.equals("permanent")
                || text.equals("forever") || text.equals("永久") || text.equals("无期")) return 0;
        Matcher matcher = TOKEN.matcher(text);
        long total = 0;
        int end = 0, count = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Invalid sentence duration: " + input);
            long amount;
            try {
                amount = Math.multiplyExact(Long.parseLong(matcher.group(1)), switch (matcher.group(2)) {
                    case "d" -> DAY;
                    case "h" -> HOUR;
                    case "m" -> MINUTE;
                    case "s" -> SECOND;
                    default -> throw new IllegalArgumentException("Unknown duration unit");
                });
                total = Math.addExact(total, amount);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Sentence duration is too long", exception);
            }
            end = matcher.end();
            count++;
        }
        if (count == 0 || end != text.length()) throw new IllegalArgumentException("Invalid sentence duration: " + input);
        return total;
    }

    static String remaining(long endMillis, long nowMillis) {
        if (endMillis <= 0) return "permanent";
        long remainingMillis = Math.max(0, endMillis - nowMillis);
        long seconds = remainingMillis / 1_000 + (remainingMillis % 1_000 == 0 ? 0 : 1);
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long rest = seconds % 60;
        StringBuilder text = new StringBuilder();
        if (days > 0) text.append(days).append('d');
        if (hours > 0) text.append(hours).append('h');
        if (minutes > 0) text.append(minutes).append('m');
        if (rest > 0 || text.isEmpty()) text.append(rest).append('s');
        return text.toString();
    }
}
