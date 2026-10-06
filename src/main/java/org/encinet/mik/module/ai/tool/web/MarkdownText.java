package org.encinet.mik.module.ai.tool.web;

import java.util.Objects;

/** Shared normalization and escaping for model-facing Markdown-like text. */
final class MarkdownText {
    private MarkdownText() {
    }

    static String cleanInline(String value, int maximumCharacters) {
        String clean = Objects.requireNonNullElse(value, "")
                .replaceAll("\\s+", " ").strip();
        if (clean.length() <= maximumCharacters) {
            return clean;
        }
        int end = maximumCharacters;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end).stripTrailing() + "…";
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static int longestRun(String value, char character) {
        int longest = 0;
        int current = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == character) {
                longest = Math.max(longest, ++current);
            } else {
                current = 0;
            }
        }
        return longest;
    }

    static String escapeText(String value) {
        return value.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    static String escapeLabel(String value) {
        return escapeText(value);
    }
}
