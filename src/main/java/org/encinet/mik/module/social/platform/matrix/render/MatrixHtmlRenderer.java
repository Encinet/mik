package org.encinet.mik.module.social.platform.matrix.render;

import org.encinet.mik.module.social.document.SocialDocument;

import java.util.Objects;

/** Escapes a platform-neutral document into Matrix's restricted custom-HTML dialect. */
public final class MatrixHtmlRenderer {
    private MatrixHtmlRenderer() {
    }

    public static String render(SocialDocument document) {
        Objects.requireNonNull(document, "document");
        StringBuilder html = new StringBuilder("<h3>")
                .append(escape(document.title())).append("</h3>");
        for (SocialDocument.Block block : document.blocks()) {
            switch (block) {
                case SocialDocument.Paragraph paragraph -> html.append("<p>")
                        .append(multiline(paragraph.text())).append("</p>");
                case SocialDocument.Fields fields -> {
                    html.append("<ul>");
                    for (SocialDocument.Field field : fields.fields()) {
                        html.append("<li><strong>").append(escape(field.label()))
                                .append(":</strong> ").append(multiline(field.value()))
                                .append("</li>");
                    }
                    html.append("</ul>");
                }
                case SocialDocument.ItemList list -> {
                    html.append(list.ordered() ? "<ol>" : "<ul>");
                    for (String item : list.items()) {
                        html.append("<li>").append(multiline(item)).append("</li>");
                    }
                    html.append(list.ordered() ? "</ol>" : "</ul>");
                }
                case SocialDocument.Image image -> {
                    if (image.source() instanceof SocialDocument.RemoteImage remote) {
                        html.append("<p><a href=\"")
                                .append(escape(remote.url().toASCIIString()))
                                .append("\">").append(escape(image.alternativeText()))
                                .append("</a></p>");
                    }
                }
            }
        }
        return html.toString();
    }

    public static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '"' -> result.append("&quot;");
                case '\'' -> result.append("&#39;");
                default -> result.append(value.charAt(index));
            }
        }
        return result.toString();
    }

    private static String multiline(String value) {
        return escape(value).replace("\n", "<br>");
    }
}
