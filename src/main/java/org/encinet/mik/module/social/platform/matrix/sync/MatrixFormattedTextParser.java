package org.encinet.mik.module.social.platform.matrix.sync;

import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extracts trusted mention ranges from Matrix custom HTML with a plain fallback. */
final class MatrixFormattedTextParser {
    private static final int MAXIMUM_MESSAGE_LENGTH = 4_000;
    private static final int MAXIMUM_MENTIONS = 32;

    private MatrixFormattedTextParser() {
    }

    static ParsedText parse(
            JsonObject content,
            List<MatrixRoomMessage.MatrixMember> mentionedMembers
    ) {
        Map<String, MatrixRoomMessage.MatrixMember> mentioned = new LinkedHashMap<>();
        mentionedMembers.forEach(member -> mentioned.putIfAbsent(
                member.userId(), member));
        String format = MatrixSyncProtocol.string(content, "format");
        String formatted = MatrixSyncProtocol.string(content, "formatted_body");
        if ("org.matrix.custom.html".equals(format) && !formatted.isBlank()) {
            ParsedText parsed = parseHtml(formatted, mentioned);
            if (!parsed.body().isBlank()) {
                return parsed;
            }
        }
        String body = stripReplyFallback(MatrixSyncProtocol.string(content, "body"));
        return new ParsedText(cleanMessage(body), locateFallback(body, mentioned));
    }

    private static ParsedText parseHtml(
            String html,
            Map<String, MatrixRoomMessage.MatrixMember> mentioned
    ) {
        Element body = Jsoup.parseBodyFragment(html).body();
        body.select("mx-reply").remove();
        StringBuilder raw = new StringBuilder(Math.min(html.length(), 8_000));
        List<RawMention> rawMentions = new ArrayList<>();
        for (Node node : body.childNodes()) {
            append(node, raw, rawMentions, mentioned);
        }
        return normalize(raw.toString(), rawMentions);
    }

    private static void append(
            Node node,
            StringBuilder raw,
            List<RawMention> mentions,
            Map<String, MatrixRoomMessage.MatrixMember> mentionedMembers
    ) {
        if (raw.length() >= MAXIMUM_MESSAGE_LENGTH * 2) {
            return;
        }
        if (node instanceof TextNode text) {
            raw.append(text.getWholeText());
            return;
        }
        if (!(node instanceof Element element)) {
            node.childNodes().forEach(child -> append(
                    child, raw, mentions, mentionedMembers));
            return;
        }
        String tag = element.normalName();
        if ("br".equals(tag)) {
            raw.append('\n');
            return;
        }
        boolean block = element.isBlock();
        if (block && !raw.isEmpty()) {
            raw.append('\n');
        }
        MatrixRoomMessage.MatrixMember mentioned = "a".equals(tag)
                ? mentionedMembers.get(matrixUserId(element.attr("href"))) : null;
        int start = raw.length();
        element.childNodes().forEach(child -> append(
                child, raw, mentions, mentionedMembers));
        if (mentioned != null && raw.length() == start) {
            raw.append(mentioned.displayName());
        }
        if (mentioned != null && raw.length() > start
                && mentions.size() < MAXIMUM_MENTIONS) {
            mentions.add(new RawMention(start, raw.length(), mentioned));
        }
        if (block) {
            raw.append('\n');
        }
    }

    private static String matrixUserId(String href) {
        if (href == null) {
            return "";
        }
        String prefix = "https://matrix.to/#/";
        if (!href.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return "";
        }
        String value = href.substring(prefix.length());
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static ParsedText normalize(
            String raw,
            List<RawMention> mentions
    ) {
        int[] boundary = new int[raw.length() + 1];
        StringBuilder clean = new StringBuilder(
                Math.min(raw.length(), MAXIMUM_MESSAGE_LENGTH));
        boolean whitespace = false;
        for (int index = 0; index < raw.length(); index++) {
            boundary[index] = clean.length();
            char character = raw.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                if (!whitespace && !clean.isEmpty()
                        && clean.length() < MAXIMUM_MESSAGE_LENGTH) {
                    clean.append(' ');
                }
                whitespace = true;
            } else if (clean.length() < MAXIMUM_MESSAGE_LENGTH) {
                clean.append(character);
                whitespace = false;
            }
            boundary[index + 1] = clean.length();
        }
        while (!clean.isEmpty() && Character.isWhitespace(
                clean.charAt(clean.length() - 1))) {
            clean.setLength(clean.length() - 1);
        }
        int maximum = clean.length();
        List<ParsedMention> parsed = mentions.stream()
                .map(mention -> new ParsedMention(
                        Math.min(boundary[mention.start()], maximum),
                        Math.min(boundary[mention.end()], maximum),
                        mention.member()))
                .filter(mention -> mention.end() > mention.start())
                .sorted(Comparator.comparingInt(ParsedMention::start))
                .toList();
        return new ParsedText(clean.toString(), parsed);
    }

    private static List<ParsedMention> locateFallback(
            String rawBody,
            Map<String, MatrixRoomMessage.MatrixMember> mentioned
    ) {
        String body = cleanMessage(stripReplyFallback(rawBody));
        List<ParsedMention> result = new ArrayList<>();
        boolean[] occupied = new boolean[body.length()];
        for (MatrixRoomMessage.MatrixMember member : mentioned.values()) {
            Match match = firstAvailableMatch(body, occupied, member);
            if (match == null) {
                continue;
            }
            java.util.Arrays.fill(occupied, match.start(), match.end(), true);
            result.add(new ParsedMention(match.start(), match.end(), member));
        }
        result.sort(Comparator.comparingInt(ParsedMention::start));
        return List.copyOf(result);
    }

    private static Match firstAvailableMatch(
            String body,
            boolean[] occupied,
            MatrixRoomMessage.MatrixMember member
    ) {
        List<String> labels = new ArrayList<>();
        labels.add(member.userId());
        if (!member.displayName().equals(member.userId())) {
            labels.add("@" + member.displayName());
        }
        Match best = null;
        for (String label : labels) {
            int from = 0;
            while (from < body.length()) {
                int start = body.indexOf(label, from);
                if (start < 0) {
                    break;
                }
                int end = start + label.length();
                boolean free = true;
                for (int index = start; index < end && free; index++) {
                    free = !occupied[index];
                }
                if (free && boundary(body, start, end)) {
                    Match candidate = new Match(start, end);
                    if (best == null || candidate.start() < best.start()) {
                        best = candidate;
                    }
                    break;
                }
                from = start + 1;
            }
        }
        return best;
    }

    private static boolean boundary(String text, int start, int end) {
        return (start == 0 || !nameCharacter(text.codePointBefore(start)))
                && (end == text.length() || !nameCharacter(text.codePointAt(end)));
    }

    private static boolean nameCharacter(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint) || codePoint == '_'
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static String cleanMessage(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(
                Math.min(value.length(), MAXIMUM_MESSAGE_LENGTH));
        boolean whitespace = false;
        for (int index = 0;
             index < value.length() && result.length() < MAXIMUM_MESSAGE_LENGTH;
             index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || Character.isWhitespace(character)) {
                if (!whitespace && !result.isEmpty()) {
                    result.append(' ');
                }
                whitespace = true;
            } else {
                result.append(character);
                whitespace = false;
            }
        }
        return result.toString().strip();
    }

    private static String stripReplyFallback(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n')
                .split("\n", -1);
        if (lines.length == 0 || !lines[0].startsWith("> ")) {
            return value;
        }
        int firstBodyLine = 0;
        while (firstBodyLine < lines.length && lines[firstBodyLine].startsWith("> ")) {
            firstBodyLine++;
        }
        if (firstBodyLine < lines.length && lines[firstBodyLine].isBlank()) {
            firstBodyLine++;
        }
        return String.join("\n", java.util.Arrays.copyOfRange(
                lines, firstBodyLine, lines.length));
    }

    record ParsedText(String body, List<ParsedMention> mentions) {
        ParsedText {
            body = body == null ? "" : body;
            mentions = List.copyOf(mentions);
        }
    }

    record ParsedMention(
            int start,
            int end,
            MatrixRoomMessage.MatrixMember member
    ) {
    }

    private record RawMention(
            int start,
            int end,
            MatrixRoomMessage.MatrixMember member
    ) {
    }

    private record Match(int start, int end) {
    }
}
