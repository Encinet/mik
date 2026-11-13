package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.social.document.SocialDocument;

/** Encodes a bounded document using QQ Markdown; layout belongs to QqDocumentRenderer. */
final class QqMarkdownRenderer {
    private QqMarkdownRenderer() {
    }

    static String encode(SocialDocument document) {
        StringBuilder markdown = new StringBuilder();
        if (!document.title().isEmpty()) markdown.append("## ").append(QqText.markdownLiteral(document.title()));
        for (SocialDocument.Block block : document.blocks()) {
            if (block instanceof SocialDocument.Image) {
                continue;
            }
            if (!markdown.isEmpty()) markdown.append("\n\n");
            switch (block) {
                case SocialDocument.Instruction instruction -> markdown.append(escapeMultiline(instruction.explanation()))
                        .append("\n\n").append(QqText.markdownLiteral(instruction.invocation()));
                case SocialDocument.Paragraph paragraph ->
                        markdown.append(escapeMultiline(paragraph.text()));
                case SocialDocument.Fields fields -> {
                    for (int index = 0; index < fields.fields().size(); index++) {
                        SocialDocument.Field field = fields.fields().get(index);
                        if (index > 0) {
                            markdown.append('\n');
                        }
                        markdown.append("- **").append(QqText.markdownLiteral(field.label()))
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

    private static String escapeMultiline(String value) {
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                result.append("  \n");
            }
            result.append(QqText.markdownLiteral(lines[index]));
        }
        return result.toString();
    }

}
