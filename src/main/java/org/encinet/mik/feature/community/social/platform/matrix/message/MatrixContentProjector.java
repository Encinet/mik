package org.encinet.mik.feature.community.social.platform.matrix.message;

import org.jsoup.Jsoup;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects typed content into bounded display text and independent command text. */
public final class MatrixContentProjector {
    private static final int MAXIMUM_MESSAGE_LENGTH = 4_000;
    private static final int MAXIMUM_INLINE_SPANS = 512;

    private MatrixContentProjector() {
    }

    public static Projection project(MatrixMessageContent content, Map<String, String> displayNames) {
        Map<String, MentionTarget> mentioned = new LinkedHashMap<>();
        content.mentionedUserIds().stream().sorted().forEach(userId -> mentioned.put(userId,
                new MentionTarget(userId, displayNames.getOrDefault(userId, userId))));
        ChatPlaceholder suffixKind = switch (content.kind()) {
            case TEXT -> null;
            case NOTICE -> ChatPlaceholder.NOTICE;
            case EMOTE -> ChatPlaceholder.EMOTE;
            case IMAGE -> ChatPlaceholder.IMAGE;
            case AUDIO -> ChatPlaceholder.AUDIO;
            case VIDEO -> ChatPlaceholder.VIDEO;
            case FILE -> ChatPlaceholder.FILE;
            case LOCATION -> ChatPlaceholder.LOCATION;
            case STICKER -> ChatPlaceholder.STICKER;
            case UNKNOWN -> ChatPlaceholder.MESSAGE;
        };
        String suffix = suffixKind == null ? "" : suffixKind.fallback();
        if (content.bodyIsFilename()) {
            return new Projection(suffix, "", List.of(), suffixKind == null ? List.of()
                    : List.of(new ChatPlaceholder.Span(0, suffix.length(), suffixKind)));
        }
        int textLimit = MAXIMUM_MESSAGE_LENGTH - suffix.length() - (suffix.isEmpty() ? 0 : 1);
        ParsedText text = content.html().map(html -> parseHtml(html, mentioned,
                content.relation().replyEventId().isPresent(), textLimit)).orElse(null);
        boolean formattedCommandEligible = text == null || text.commandEligible();
        if (text == null || text.body().isBlank()) {
            String body = content.relation().replyEventId().isPresent()
                    ? stripReplyFallback(content.body()) : content.body();
            text = normalize(body, locateFallback(body, mentioned), textLimit);
        }
        // Keep the author's content first so bridge routing prefixes remain usable.
        String body = text.body();
        List<ChatPlaceholder.Span> placeholders = new ArrayList<>(text.placeholders());
        if (!suffix.isEmpty()) {
            int start = body.length() + (body.isEmpty() ? 0 : 1);
            body += (body.isEmpty() ? "" : " ") + suffix;
            placeholders.add(new ChatPlaceholder.Span(start, body.length(), suffixKind));
        }
        String command = content.kind() == MatrixMessageContent.Kind.TEXT
                && formattedCommandEligible && text.commandEligible()
                ? text.body() : "";
        return new Projection(body, command, text.mentions(), placeholders);
    }

    private static ParsedText parseHtml(
            String html,
            Map<String, MentionTarget> mentioned,
            boolean reply,
            int maximumLength
    ) {
        Element body = Jsoup.parseBodyFragment(html).body();
        if (reply && body.firstChild() instanceof Element first
                && "mx-reply".equals(first.normalName())) {
            first.remove();
        }
        body.select("script,style,template,iframe,object,svg,math").remove();
        StringBuilder raw = new StringBuilder(Math.min(html.length(), 8_000));
        List<RawSpan> rawSpans = new ArrayList<>();
        boolean complete = true;
        for (Node node : body.childNodes()) {
            complete &= append(node, raw, rawSpans, mentioned, 0);
        }
        return normalize(raw.toString(), rawSpans, maximumLength, complete);
    }

    private static boolean append(
            Node node,
            StringBuilder raw,
            List<RawSpan> spans,
            Map<String, MentionTarget> mentionedMembers,
            int depth
    ) {
        if (depth > 64 || raw.length() >= MAXIMUM_MESSAGE_LENGTH * 2
                || spans.size() >= MAXIMUM_INLINE_SPANS) {
            return false;
        }
        if (node instanceof TextNode text) {
            return appendBounded(raw, text.getWholeText());
        }
        if (!(node instanceof Element element)) {
            return true;
        }
        String tag = element.normalName();
        if ("img".equals(tag)) {
            String alternative = element.attr("alt");
            ChatPlaceholder fallback = element.hasAttr("data-mx-emoticon") ? ChatPlaceholder.FACE : ChatPlaceholder.IMAGE;
            int start = raw.length();
            boolean complete = appendBounded(raw, alternative.isBlank() ? fallback.fallback() : alternative);
            spans.add(new RawSpan(start, raw.length(), new InlineImage(fallback, alternative.isBlank()), complete));
            return complete;
        }
        if ("br".equals(tag) || "hr".equals(tag)) {
            raw.append('\n');
            return true;
        }
        boolean block = element.isBlock();
        if (block && !raw.isEmpty()) {
            raw.append('\n');
        }
        MentionTarget mentioned = "a".equals(tag)
                ? mentionedMembers.get(matrixUserId(element.attr("href"))) : null;
        int start = raw.length();
        boolean complete = true;
        for (Node child : element.childNodes()) {
            complete &= append(child, raw, spans, mentionedMembers, depth + 1);
        }
        if (mentioned != null && raw.length() == start) {
            complete &= appendBounded(raw, mentioned.displayName());
        }
        if (mentioned != null && raw.length() > start) {
            spans.add(new RawSpan(start, raw.length(), mentioned, complete));
        }
        if (block) {
            raw.append('\n');
        }
        return complete;
    }

    private static boolean appendBounded(StringBuilder raw, String text) {
        int length = Math.min(text.length(), MAXIMUM_MESSAGE_LENGTH * 2 - raw.length());
        if (length > 0 && length < text.length() && Character.isHighSurrogate(text.charAt(length - 1))) {
            length--;
        }
        raw.append(text, 0, Math.max(0, length));
        return length == text.length();
    }

    private static String matrixUserId(String href) {
        try {
            URI uri = URI.create(href);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"matrix.to".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || !"/".equals(uri.getPath())) {
                return "";
            }
            String fragment = uri.getRawFragment();
            if (fragment == null || !fragment.startsWith("/")) {
                return "";
            }
            String value = fragment.substring(1).split("\\?", 2)[0];
            // RFC 3986 path/fragment encoding: '+' is a literal MXID character.
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static ParsedText normalize(String raw, List<RawSpan> spans, int maximumLength) {
        return normalize(raw, spans, maximumLength, true);
    }

    private static ParsedText normalize(String raw, List<RawSpan> spans,
                                        int maximumLength, boolean complete) {
        int[] boundary = new int[raw.length() + 1];
        StringBuilder clean = new StringBuilder(Math.min(raw.length(), maximumLength));
        int consumed = 0;
        boolean whitespace = false;
        while (consumed < raw.length()) {
            int codePoint = raw.codePointAt(consumed);
            int width = Character.charCount(codePoint);
            boolean space = Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                    || Character.isSpaceChar(codePoint);
            String next = space ? (whitespace || clean.isEmpty() ? "" : " ")
                    : new String(Character.toChars(codePoint));
            if (clean.length() + next.length() > maximumLength) {
                break;
            }
            for (int index = consumed; index < consumed + width; index++) {
                boundary[index] = clean.length();
            }
            clean.append(next);
            whitespace = space;
            consumed += width;
            boundary[consumed] = clean.length();
        }
        boolean truncated = !complete || consumed < raw.length();
        if (truncated) {
            // Reserve the ellipsis without splitting Unicode or retaining a partial mention.
            while (clean.length() >= maximumLength) {
                clean.setLength(clean.length() - Character.charCount(clean.codePointBefore(clean.length())));
            }
        }
        while (!clean.isEmpty() && Character.isWhitespace(clean.charAt(clean.length() - 1))) {
            clean.setLength(clean.length() - 1);
        }
        int end = clean.length();
        for (RawSpan span : spans) {
            if (span.start() <= consumed && (!span.complete() || span.end() > consumed
                    || truncated && boundary[span.end()] > end)) {
                end = Math.min(end, boundary[span.start()]);
            }
        }
        clean.setLength(end);
        while (!clean.isEmpty() && Character.isWhitespace(clean.charAt(clean.length() - 1))) {
            clean.setLength(clean.length() - 1);
        }
        int maximum = clean.length();
        int consumedEnd = consumed;
        List<ParsedMention> parsed = spans.stream()
                .filter(mention -> mention.source() instanceof MentionTarget && mention.complete()
                        && mention.end() <= consumedEnd
                        && (!truncated || boundary[mention.end()] <= maximum))
                .map(mention -> new ParsedMention(boundary[mention.start()],
                        Math.min(boundary[mention.end()], maximum),
                        ((MentionTarget) mention.source()).userId()))
                .filter(mention -> mention.end() > mention.start())
                .sorted(Comparator.comparingInt(ParsedMention::start))
                .toList();
        if (truncated) {
            clean.append('…');
        }
        List<ChatPlaceholder.Span> placeholders = spans.stream()
                .filter(span -> span.source() instanceof InlineImage image && image.fallback()
                        && span.complete() && span.end() <= consumedEnd
                        && boundary[span.end()] <= maximum && boundary[span.end()] > boundary[span.start()]
                        && parsed.stream().noneMatch(mention -> boundary[span.start()] < mention.end()
                        && mention.start() < boundary[span.end()]))
                .map(span -> new ChatPlaceholder.Span(boundary[span.start()], boundary[span.end()],
                        ((InlineImage) span.source()).kind()))
                .sorted(Comparator.comparingInt(ChatPlaceholder.Span::start)).toList();
        boolean commandEligible = !truncated && spans.stream().noneMatch(span -> span.source() instanceof InlineImage);
        return new ParsedText(clean.toString(), parsed, placeholders, commandEligible);
    }

    private static List<RawSpan> locateFallback(
            String rawBody,
            Map<String, MentionTarget> mentioned
    ) {
        String body = rawBody;
        List<RawSpan> result = new ArrayList<>();
        boolean[] occupied = new boolean[body.length()];
        for (MentionTarget member : mentioned.values()) {
            boolean uniqueName = mentioned.values().stream()
                    .filter(other -> other.displayName().equals(member.displayName())).count() == 1;
            Match match = firstAvailableMatch(body, occupied, member, uniqueName);
            if (match == null) {
                continue;
            }
            java.util.Arrays.fill(occupied, match.start(), match.end(), true);
            result.add(new RawSpan(match.start(), match.end(), member, true));
        }
        result.sort(Comparator.comparingInt(RawSpan::start));
        return List.copyOf(result);
    }

    private static Match firstAvailableMatch(
            String body,
            boolean[] occupied,
            MentionTarget member, boolean uniqueName
    ) {
        List<String> labels = new ArrayList<>();
        labels.add(member.userId());
        if (uniqueName && !member.displayName().equals(member.userId())) {
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

    private record ParsedText(String body, List<ParsedMention> mentions,
                              List<ChatPlaceholder.Span> placeholders, boolean commandEligible) {
        ParsedText {
            body = body == null ? "" : body;
            mentions = List.copyOf(mentions);
            placeholders = List.copyOf(placeholders);
        }
    }

    public record ParsedMention(
            int start,
            int end,
            String userId
    ) {
    }

    private record RawSpan(
            int start,
            int end,
            InlineSource source,
            boolean complete
    ) { }

    public record Projection(String body, String commandBody, List<ParsedMention> mentions, List<ChatPlaceholder.Span> placeholders) {
        public Projection {
            placeholders = List.copyOf(placeholders);
            mentions = List.copyOf(mentions);
        }
    }

    private sealed interface InlineSource permits MentionTarget, InlineImage { }

    private record MentionTarget(String userId, String displayName) implements InlineSource { }

    private record InlineImage(ChatPlaceholder kind, boolean fallback) implements InlineSource { }

    private record Match(int start, int end) {
    }
}
