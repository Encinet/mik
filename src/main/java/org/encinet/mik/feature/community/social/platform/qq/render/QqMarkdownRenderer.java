package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;
import org.encinet.mik.feature.community.social.document.SocialDocumentLayout;

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
        return encode(SocialDocumentLayout.fit(document, maximumLength, value -> encode(value).length()));
    }

    public static String encode(SocialDocument document) {
        StringBuilder markdown = new StringBuilder();
        if (!document.title().isEmpty()) markdown.append("## ").append(escape(document.title()));
        for (SocialDocument.Block block : document.blocks()) {
            if (block instanceof SocialDocument.Image) {
                continue;
            }
            if (!markdown.isEmpty()) markdown.append("\n\n");
            switch (block) {
                case SocialDocument.Instruction instruction -> markdown.append(escapeMultiline(instruction.explanation()))
                        .append("\n\n").append(escape(instruction.invocation()));
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
                    if (fields.omitted()) markdown.append("\n- …");
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
                case SocialDocument.Image _ -> {
                    // Native media is uploaded by QqReplyChannel.
                }
            }
        }
        return markdown.isEmpty() ? escapeMultiline(document.plainText()) : markdown.toString();
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

}
