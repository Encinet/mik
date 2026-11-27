package org.encinet.mik.feature.server.ban.prison;

final class PrisonDuration {
    private PrisonDuration() { }

    static String remaining(long endMillis, long nowMillis) {
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
