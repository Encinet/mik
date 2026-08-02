package org.encinet.mik.module.communication.tip;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Parses a small, safe HTML-like language made only of semantic inline tags. */
public final class TipMarkupParser {

    private static final int MAX_DEPTH = 8;
    private static final int MAX_CONTENT_LENGTH = 2_000;
    private static final Pattern TAG_NAME = Pattern.compile("[a-z][a-z0-9-]{0,31}");
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "action", "code", "command", "emphasis", "feature", "item", "key",
            "mark", "mode", "place", "player", "site", "status", "strong", "value");

    public TipTemplate parse(String input) {
        if (input == null || input.isBlank()) {
            throw new TipFormatException("Tip content must not be blank");
        }
        String source = input.strip();
        if (source.length() > MAX_CONTENT_LENGTH) {
            throw new TipFormatException("Tip content exceeds " + MAX_CONTENT_LENGTH + " characters");
        }

        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(null));
        int cursor = 0;
        while (cursor < source.length()) {
            int opening = source.indexOf('<', cursor);
            if (opening < 0) {
                appendText(stack.peek(), source.substring(cursor));
                break;
            }
            if (opening > cursor) {
                appendText(stack.peek(), source.substring(cursor, opening));
            }
            int closing = source.indexOf('>', opening + 1);
            if (closing < 0) throw error(source, opening, "Unclosed semantic tag");
            String token = source.substring(opening + 1, closing).strip();
            if (token.equals("br") || token.equals("br/") || token.equals("br /")) {
                stack.peek().children.add(new TipTemplate.TextNode("\n"));
            } else if (token.startsWith("/")) {
                closeTag(stack, token.substring(1).strip(), source, opening);
            } else {
                openTag(stack, token, source, opening);
            }
            cursor = closing + 1;
        }
        if (stack.size() != 1) {
            Frame unclosed = stack.peek();
            throw new TipFormatException("Unclosed <" + unclosed.semantic + "> tag");
        }
        List<TipTemplate.Node> nodes = compact(stack.pop().children);
        if (TipTemplate.plainText(nodes).isBlank()) {
            throw new TipFormatException("Tip content must contain visible text");
        }
        return new TipTemplate(nodes);
    }

    private static void openTag(Deque<Frame> stack, String token,
                                String source, int offset) {
        String semantic = token.toLowerCase(Locale.ROOT);
        if (!TAG_NAME.matcher(semantic).matches()) {
            throw error(source, offset, "Inline tags cannot contain attributes: <" + token + ">");
        }
        if (!ALLOWED_TAGS.contains(semantic)) {
            throw error(source, offset, "Unsupported semantic tag <" + semantic + ">");
        }
        if (stack.size() > MAX_DEPTH) {
            throw error(source, offset, "Semantic tags are nested too deeply");
        }
        stack.push(new Frame(semantic));
    }

    private static void closeTag(Deque<Frame> stack, String token,
                                 String source, int offset) {
        String semantic = token.toLowerCase(Locale.ROOT);
        if (!TAG_NAME.matcher(semantic).matches() || stack.size() == 1) {
            throw error(source, offset, "Unexpected closing tag </" + token + ">");
        }
        Frame completed = stack.pop();
        if (!completed.semantic.equals(semantic)) {
            throw error(source, offset, "Expected </" + completed.semantic
                    + "> but found </" + semantic + ">");
        }
        stack.peek().children.add(new TipTemplate.ElementNode(
                completed.semantic, compact(completed.children)));
    }

    private static void appendText(Frame frame, String raw) {
        String text = decodeEntities(raw).replaceAll("[\\t\\n\\r ]+", " ");
        if (!text.isEmpty()) frame.children.add(new TipTemplate.TextNode(text));
    }

    private static List<TipTemplate.Node> compact(List<TipTemplate.Node> source) {
        List<TipTemplate.Node> result = new ArrayList<>();
        for (TipTemplate.Node node : source) {
            if (node instanceof TipTemplate.TextNode text && !result.isEmpty()
                    && result.getLast() instanceof TipTemplate.TextNode previous) {
                result.set(result.size() - 1,
                        new TipTemplate.TextNode(previous.text() + text.text()));
            } else {
                result.add(node);
            }
        }
        return List.copyOf(result);
    }

    private static String decodeEntities(String value) {
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private static TipFormatException error(String source, int offset, String message) {
        int line = 1;
        for (int index = 0; index < Math.min(offset, source.length()); index++) {
            if (source.charAt(index) == '\n') line++;
        }
        return new TipFormatException(message + " at content line " + line);
    }

    private static final class Frame {
        private final String semantic;
        private final List<TipTemplate.Node> children = new ArrayList<>();

        private Frame(String semantic) {
            this.semantic = semantic;
        }
    }

    public static final class TipFormatException extends IllegalArgumentException {
        public TipFormatException(String message) {
            super(message);
        }
    }
}
