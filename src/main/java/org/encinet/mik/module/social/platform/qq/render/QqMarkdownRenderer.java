package org.encinet.mik.module.social.platform.qq.render;

import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Objects;

/** Escapes and renders a platform-neutral document as bounded QQ Markdown. */
public final class QqMarkdownRenderer {
    private static final String SPECIAL_CHARACTERS = "\\\\`*_{}[]<>()#+-.!|>~";

    private QqMarkdownRenderer() {
    }

    public static String render(SocialDocument document, int maximumLength) {
        Objects.requireNonNull(document, "document");
        if (maximumLength < 16) {
            throw new IllegalArgumentException("maximumLength must be at least 16");
        }
        StringBuilder markdown = new StringBuilder("## ").append(escape(document.title()));
        for (SocialDocument.Block block : document.blocks()) {
            if (block instanceof SocialDocument.Image) {
                continue;
            }
            markdown.append("\n\n");
            switch (block) {
                case SocialDocument.Paragraph paragraph ->
                        markdown.append(escapeMultiline(paragraph.text()));
                case SocialDocument.Fields fields -> {
                    for (int index = 0; index < fields.fields().size(); index++) {
                        SocialDocument.Field field = fields.fields().get(index);
                        if (index > 0) {
                            markdown.append('\n');
                        }
                        markdown.append("- **").append(escape(field.label()))
                                .append("：** ").append(escapeMultiline(field.value()));
                    }
                }
                case SocialDocument.ItemList list -> {
                    for (int index = 0; index < list.items().size(); index++) {
                        if (index > 0) {
                            markdown.append('\n');
                        }
                        markdown.append(list.ordered() ? (index + 1) + ". " : "- ")
                                .append(escapeMultiline(list.items().get(index)));
                    }
                }
                case SocialDocument.Image ignored -> {
                    // Native media is uploaded by QqReplyChannel.
                }
            }
        }
        return limit(markdown.toString(), maximumLength);
    }

    public static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (SPECIAL_CHARACTERS.indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static String escapeMultiline(String value) {
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                result.append("  \n");
            }
            result.append(escape(lines[index]));
        }
        return result.toString();
    }

    private static String limit(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        int end = maximumLength - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        int backslashes = 0;
        while (end - backslashes - 1 >= 0 && value.charAt(end - backslashes - 1) == '\\') {
            backslashes++;
        }
        if ((backslashes & 1) == 1) {
            end--;
        }
        return value.substring(0, Math.max(0, end)).stripTrailing() + '…';
    }
}
