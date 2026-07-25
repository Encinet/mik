package org.encinet.mik.module.music.lyrics;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses bounded LRC text and creates a deterministic timeline for plain lyrics. */
final class LrcParser {

    static final int MAX_LYRIC_BYTES = 1024 * 1024;
    static final int MAX_LINES = 10_000;
    static final int MAX_LINE_CHARACTERS = 256;
    private static final long DEFAULT_PLAIN_LINE_MILLIS = 5_000;
    private static final long ALIGNMENT_TOLERANCE_MILLIS = 1_000;
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\[((?:\\d{1,3}:)?\\d{1,3}:\\d{1,2}(?:\\.\\d{1,3})?)]");
    private static final Pattern OFFSET = Pattern.compile(
            "(?i)^\\s*\\[offset:([+-]?\\d+)]\\s*$");
    private static final Pattern METADATA = Pattern.compile(
            "(?i)^\\s*\\[(ar|ti|al|by|re|ve|length):.*]\\s*$");
    private static final Pattern ENHANCED_TIMESTAMP = Pattern.compile(
            "<\\d{1,3}:\\d{1,2}(?:\\.\\d{1,3})?>");

    Lyrics parse(LyricSourceText source, Duration duration) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        String original = null;
        ParsedText primary = ParsedText.EMPTY;
        for (String candidate : new String[]{
                value(source.original()), value(source.translation()), value(source.romanization())}) {
            if (candidate == null) {
                continue;
            }
            ParsedText parsed = parseText(candidate);
            if (!parsed.timed().isEmpty() || !parsed.plain().isEmpty()) {
                original = candidate;
                primary = parsed;
                break;
            }
        }
        List<TimedText> base = primary.timed();
        if (base.isEmpty()) {
            base = distribute(primary.plain(), duration);
        }
        if (base.isEmpty()) {
            return null;
        }

        ParsedText translated = source.translation() == null
                || source.translation().equals(original)
                ? ParsedText.EMPTY : parseText(source.translation());
        ParsedText romanized = source.romanization() == null
                || source.romanization().equals(original)
                ? ParsedText.EMPTY : parseText(source.romanization());
        List<String> translations = align(base, translated);
        List<String> romanizations = align(base, romanized);

        List<LyricLine> lines = new ArrayList<>(base.size());
        for (int index = 0; index < base.size(); index++) {
            TimedText line = base.get(index);
            lines.add(new LyricLine(line.timestampMillis(), line.text(),
                    translations.get(index), romanizations.get(index)));
        }
        return new Lyrics(lines);
    }

    private static ParsedText parseText(String text) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length > MAX_LYRIC_BYTES) {
            return ParsedText.EMPTY;
        }
        String[] inputLines = text.replace("\r\n", "\n").replace('\r', '\n')
                .split("\n", MAX_LINES + 1);
        if (inputLines.length > MAX_LINES) {
            return ParsedText.EMPTY;
        }

        long offset = 0;
        for (String line : inputLines) {
            Matcher matcher = OFFSET.matcher(line);
            if (matcher.matches()) {
                try {
                    offset = Math.max(-TimeUnitLimit.ONE_HOUR_MILLIS,
                            Math.min(TimeUnitLimit.ONE_HOUR_MILLIS,
                                    Long.parseLong(matcher.group(1))));
                } catch (NumberFormatException ignored) {
                    offset = 0;
                }
            }
        }

        NavigableMap<Long, LinkedHashSet<String>> timed = new TreeMap<>();
        List<String> plain = new ArrayList<>();
        for (String rawLine : inputLines) {
            if (rawLine.length() > MAX_LINE_CHARACTERS * 8 || OFFSET.matcher(rawLine).matches()
                    || METADATA.matcher(rawLine).matches()) {
                continue;
            }
            Matcher matcher = TIMESTAMP.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            while (matcher.find()) {
                long parsed = timestampMillis(matcher.group(1));
                if (parsed >= 0) {
                    timestamps.add(Math.max(0, parsed + offset));
                }
            }
            String visible = normalizeLine(ENHANCED_TIMESTAMP.matcher(
                    TIMESTAMP.matcher(rawLine).replaceAll("")).replaceAll(""));
            if (visible == null && timestamps.isEmpty()) {
                continue;
            }
            if (timestamps.isEmpty()) {
                plain.add(visible);
            } else {
                String timedText = visible == null ? "" : visible;
                for (long timestamp : timestamps) {
                    timed.computeIfAbsent(timestamp, ignored -> new LinkedHashSet<>()).add(timedText);
                }
            }
        }

        List<TimedText> timeline = timed.entrySet().stream()
                .map(entry -> new TimedText(entry.getKey(), merge(entry.getValue())))
                .toList();
        return new ParsedText(timeline, List.copyOf(plain));
    }

    private static long timestampMillis(String value) {
        String[] parts = value.split(":");
        if (parts.length != 2 && parts.length != 3) {
            return -1;
        }
        try {
            long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0;
            long minutes = Long.parseLong(parts[parts.length - 2]);
            String[] secondParts = parts[parts.length - 1].split("[.]", 2);
            long seconds = Long.parseLong(secondParts[0]);
            if (minutes > (parts.length == 3 ? 59 : 999) || seconds > 59 || hours > 999) {
                return -1;
            }
            long fraction = secondParts.length == 1 ? 0 : fractionMillis(secondParts[1]);
            return Math.addExact(Math.multiplyExact(hours * 60 + minutes, 60_000),
                    seconds * 1_000 + fraction);
        } catch (ArithmeticException | NumberFormatException ignored) {
            return -1;
        }
    }

    private static long fractionMillis(String fraction) {
        return switch (fraction.length()) {
            case 1 -> Long.parseLong(fraction) * 100;
            case 2 -> Long.parseLong(fraction) * 10;
            default -> Long.parseLong(fraction.substring(0, 3));
        };
    }

    private static List<TimedText> distribute(List<String> lines, Duration duration) {
        if (lines.isEmpty()) {
            return List.of();
        }
        long durationMillis = duration == null || duration.isZero() || duration.isNegative()
                ? 0 : duration.toMillis();
        long step = durationMillis > 0
                ? Math.max(1, durationMillis / lines.size()) : DEFAULT_PLAIN_LINE_MILLIS;
        List<TimedText> result = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            result.add(new TimedText(Math.multiplyExact(index, step), lines.get(index)));
        }
        return List.copyOf(result);
    }

    private static List<String> align(List<TimedText> base, ParsedText secondary) {
        List<String> result = new ArrayList<>(java.util.Collections.nCopies(base.size(), null));
        if (!secondary.timed().isEmpty()) {
            NavigableMap<Long, String> timeline = new TreeMap<>();
            secondary.timed().forEach(line -> timeline.put(line.timestampMillis(), line.text()));
            for (int index = 0; index < base.size(); index++) {
                long timestamp = base.get(index).timestampMillis();
                Map.Entry<Long, String> floor = timeline.floorEntry(timestamp);
                Map.Entry<Long, String> ceiling = timeline.ceilingEntry(timestamp);
                Map.Entry<Long, String> closest = closest(timestamp, floor, ceiling);
                if (closest != null && Math.abs(closest.getKey() - timestamp)
                        <= ALIGNMENT_TOLERANCE_MILLIS) {
                    result.set(index, closest.getValue());
                }
            }
            return java.util.Collections.unmodifiableList(result);
        }
        for (int index = 0; index < Math.min(base.size(), secondary.plain().size()); index++) {
            result.set(index, secondary.plain().get(index));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    private static Map.Entry<Long, String> closest(long timestamp,
                                                    Map.Entry<Long, String> floor,
                                                    Map.Entry<Long, String> ceiling) {
        if (floor == null) return ceiling;
        if (ceiling == null) return floor;
        return timestamp - floor.getKey() <= ceiling.getKey() - timestamp ? floor : ceiling;
    }

    private static String merge(LinkedHashSet<String> values) {
        List<String> visible = values.stream().filter(value -> !value.isBlank()).toList();
        String joined = String.join(" / ", visible);
        return joined.length() <= MAX_LINE_CHARACTERS
                ? joined : joined.substring(0, MAX_LINE_CHARACTERS).stripTrailing();
    }

    private static String normalizeLine(String value) {
        String normalized = value.replace('\0', ' ').strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() <= MAX_LINE_CHARACTERS
                ? normalized : normalized.substring(0, MAX_LINE_CHARACTERS).stripTrailing();
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record TimedText(long timestampMillis, String text) {
    }

    private record ParsedText(List<TimedText> timed, List<String> plain) {
        private static final ParsedText EMPTY = new ParsedText(List.of(), List.of());
    }

    private static final class TimeUnitLimit {
        private static final long ONE_HOUR_MILLIS = 60L * 60 * 1_000;

        private TimeUnitLimit() {
        }
    }
}
