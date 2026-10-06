package org.encinet.mik.module.ai.tool.web;

/** Truncates model-facing Markdown without splitting Unicode, links, or code fences. */
final class MarkdownTruncator {
    private MarkdownTruncator() {
    }

    static Result truncate(String value, int maximumCharacters) {
        if (value.length() <= maximumCharacters) {
            return new Result(value, false);
        }
        int end = maximumCharacters;
        int boundary = value.lastIndexOf('\n', maximumCharacters);
        if (boundary >= maximumCharacters * 3 / 4) {
            end = boundary;
        } else {
            int whitespace = lastWhitespace(value, maximumCharacters);
            if (whitespace >= maximumCharacters * 3 / 4) {
                end = whitespace;
            }
        }
        end = avoidPartialInlineLink(value, end);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        String prefix = value.substring(0, end).stripTrailing();
        String openFence = openCodeFence(prefix);
        if (!openFence.isBlank()) {
            prefix += "\n" + openFence;
        }
        return new Result(prefix + "\n\n[page content truncated]", true);
    }

    private static int lastWhitespace(String value, int before) {
        for (int index = Math.min(before, value.length()) - 1; index >= 0; index--) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int avoidPartialInlineLink(String value, int end) {
        int destination = value.lastIndexOf("](<", Math.max(0, end - 1));
        if (destination >= 0 && value.indexOf(">)", destination + 3) >= end) {
            int opening = previousUnescapedOpening(value, destination);
            return imageAwareOpening(value, opening, end);
        }
        int opening = previousUnescapedOpening(value, end - 1);
        if (opening >= 0) {
            destination = value.indexOf("](<", opening);
            if (destination >= end && value.indexOf(">)", destination + 3) > destination) {
                return imageAwareOpening(value, opening, end);
            }
        }
        return end;
    }

    private static int previousUnescapedOpening(String value, int from) {
        int opening = value.lastIndexOf('[', Math.max(0, from));
        while (opening > 0 && value.charAt(opening - 1) == '\\') {
            opening = value.lastIndexOf('[', opening - 2);
        }
        return opening;
    }

    private static int imageAwareOpening(String value, int opening, int fallback) {
        if (opening < 0) {
            return fallback;
        }
        return opening > 0 && value.charAt(opening - 1) == '!' ? opening - 1 : opening;
    }

    private static String openCodeFence(String value) {
        String open = "";
        for (String line : value.split("\\R", -1)) {
            String stripped = line.stripLeading();
            int run = leadingRun(stripped, '`');
            if (run < 3) {
                continue;
            }
            if (open.isBlank()) {
                open = "`".repeat(run);
            } else if (run >= open.length() && stripped.substring(run).isBlank()) {
                open = "";
            }
        }
        return open;
    }

    private static int leadingRun(String value, char character) {
        int length = 0;
        while (length < value.length() && value.charAt(length) == character) {
            length++;
        }
        return length;
    }

    record Result(String text, boolean truncated) {
    }
}
