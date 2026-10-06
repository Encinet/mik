package org.encinet.mik.module.ai.tool.web;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import static org.encinet.mik.module.ai.tool.web.MarkdownText.cleanInline;
import static org.encinet.mik.module.ai.tool.web.MarkdownText.escapeLabel;
import static org.encinet.mik.module.ai.tool.web.MarkdownText.escapeText;
import static org.encinet.mik.module.ai.tool.web.MarkdownText.firstNonBlank;
import static org.encinet.mik.module.ai.tool.web.MarkdownText.longestRun;

/** Semantic HTML renderer that emits compact, model-friendly Markdown-like text. */
final class HtmlMarkdownRenderer {
    private static final int MAXIMUM_DOM_DEPTH = 128;
    private static final int MAXIMUM_TABLE_ROWS = 50;
    private static final int MAXIMUM_TABLE_COLUMNS = 12;

    private HtmlMarkdownRenderer() {
    }

    static String render(
            Element root,
            String title,
            String description,
            int maximumLinks
    ) {
        Renderer renderer = new Renderer(new InlineLinkBudget(maximumLinks));
        if (!title.isBlank()) {
            renderer.raw("# ");
            renderer.text(title);
            renderer.paragraphBreak();
        }
        if (!description.isBlank() && !description.equalsIgnoreCase(title)) {
            renderer.raw("> ");
            renderer.text(description);
            renderer.paragraphBreak();
        }
        renderer.children(root, false, 0);
        return renderer.finish();
    }

    private static final class Renderer {
        private final StringBuilder output = new StringBuilder();
        private final InlineLinkBudget links;

        private Renderer(InlineLinkBudget links) {
            this.links = links;
        }

        private void children(Element element, boolean compactBlocks, int depth) {
            if (depth >= MAXIMUM_DOM_DEPTH) {
                text(element.text());
                return;
            }
            for (Node child : element.childNodes()) {
                node(child, compactBlocks, depth + 1);
            }
        }

        private void node(Node node, boolean compactBlocks, int depth) {
            if (node instanceof TextNode text) {
                text(text.getWholeText());
                return;
            }
            if (!(node instanceof Element element)) {
                return;
            }
            if (depth >= MAXIMUM_DOM_DEPTH) {
                text(element.text());
                return;
            }
            String tag = element.normalName();
            switch (tag) {
                case "a" -> link(element);
                case "br" -> lineBreak();
                case "wbr" -> space();
                case "hr" -> horizontalRule();
                case "h1", "h2", "h3", "h4", "h5", "h6" ->
                        heading(element, depth);
                case "p", "div", "section", "article", "main", "header", "figure",
                     "details", "address", "center" ->
                        block(element, compactBlocks, depth);
                case "summary" -> wrapped(element, "**", depth);
                case "figcaption", "cite" -> wrapped(element, "*", depth);
                case "dl" -> definitionList(element, depth);
                case "dt" -> wrapped(element, "**", depth);
                case "dd" -> children(element, compactBlocks, depth);
                case "ul", "menu" -> list(element, false, 0, depth);
                case "ol" -> list(element, true, 0, depth);
                case "li" -> children(element, true, depth);
                case "blockquote" -> blockquote(element, depth);
                case "pre" -> codeBlock(element);
                case "code", "kbd", "samp", "var" -> inlineCode(element);
                case "table" -> table(element, depth);
                case "strong", "b" -> wrapped(element, "**", depth);
                case "em", "i" -> wrapped(element, "*", depth);
                case "del", "s", "strike" -> wrapped(element, "~~", depth);
                case "mark" -> wrapped(element, "==", depth);
                case "q" -> quoted(element, depth);
                case "abbr" -> abbreviation(element, depth);
                case "ruby" -> ruby(element, depth);
                case "sup" -> semanticInline(element, "^(", ")", depth);
                case "sub" -> semanticInline(element, "~(", ")", depth);
                case "input" -> checkbox(element);
                case "img" -> image(element);
                case "audio", "video" -> media(element);
                case "source", "track" -> {
                    // Rendered by the containing media or picture element.
                }
                default -> children(element, compactBlocks, depth);
            }
        }

        private void horizontalRule() {
            paragraphBreak();
            raw("---");
            paragraphBreak();
        }

        private void heading(Element element, int depth) {
            paragraphBreak();
            int level = element.normalName().charAt(1) - '0';
            raw("#".repeat(level) + " ");
            children(element, true, depth);
            paragraphBreak();
        }

        private void block(Element element, boolean compactBlocks, int depth) {
            if (!compactBlocks) {
                paragraphBreak();
            }
            children(element, compactBlocks, depth);
            if (compactBlocks) {
                space();
            } else {
                paragraphBreak();
            }
        }

        private void link(Element element) {
            String label = firstNonBlank(cleanInline(element.text(), 180),
                    cleanInline(element.attr("aria-label"), 180),
                    cleanInline(element.attr("title"), 180),
                    cleanInline(element.select("img[alt]").stream()
                            .map(image -> image.attr("alt"))
                            .filter(value -> !value.isBlank()).findFirst().orElse(""), 180));
            String url = links.add(element.absUrl("href"));
            if (url == null) {
                if (!label.isBlank()) {
                    text(label);
                }
                return;
            }
            raw("[" + escapeLabel(label.isBlank() ? url : label) + "](<" + url + ">)");
        }

        private void image(Element element) {
            String alternative = firstNonBlank(cleanInline(element.attr("alt"), 180),
                    cleanInline(element.attr("aria-label"), 180),
                    cleanInline(element.attr("title"), 180));
            if (alternative.isBlank()) {
                return;
            }
            String url = links.add(imageSource(element));
            if (url == null) {
                text("[Image: " + alternative + "]");
                return;
            }
            raw("![" + escapeLabel(alternative) + "](<" + url + ">)");
        }

        private void wrapped(Element element, String marker, int depth) {
            String content = inlineContent(element, depth);
            if (!content.isBlank()) {
                raw(marker + content + marker);
            }
        }

        private void inlineCode(Element element) {
            String code = element.text().replaceAll("\\s+", " ").strip();
            if (code.isBlank()) {
                return;
            }
            String delimiter = "`".repeat(Math.max(1, longestRun(code, '`') + 1));
            String padding = code.startsWith("`") || code.endsWith("`") ? " " : "";
            raw(delimiter + padding + code + padding + delimiter);
        }

        private void codeBlock(Element element) {
            String code = element.wholeText().replace("\r\n", "\n")
                    .replace('\r', '\n').strip();
            if (code.isBlank()) {
                return;
            }
            String fence = "`".repeat(Math.max(3, longestRun(code, '`') + 1));
            paragraphBreak();
            raw(fence + codeLanguage(element) + "\n" + code + "\n" + fence);
            paragraphBreak();
        }

        private void blockquote(Element element, int depth) {
            Renderer nested = new Renderer(links);
            nested.children(element, false, depth);
            String content = nested.finish();
            if (content.isBlank()) {
                return;
            }
            paragraphBreak();
            raw(content.lines().map(line -> "> " + line)
                    .collect(java.util.stream.Collectors.joining("\n")));
            paragraphBreak();
        }

        private void list(
                Element list,
                boolean ordered,
                int listDepth,
                int domDepth
        ) {
            if (domDepth >= MAXIMUM_DOM_DEPTH) {
                text(list.text());
                return;
            }
            paragraphBreak();
            List<Element> items = list.children().stream()
                    .filter(item -> item.normalName().equals("li")).toList();
            boolean reversed = ordered && list.hasAttr("reversed");
            int number = integerAttribute(list, "start",
                    reversed ? Math.max(1, items.size()) : 1);
            for (Element item : items) {
                int itemNumber = ordered
                        ? integerAttribute(item, "value", number) : number;
                lineBreak();
                raw("  ".repeat(Math.min(listDepth, 6))
                        + (ordered ? itemNumber + ". " : "- "));
                for (Node child : item.childNodes()) {
                    if (child instanceof Element nested && isList(nested)) {
                        continue;
                    }
                    node(child, true, domDepth + 1);
                }
                for (Element nested : item.children()) {
                    if (isList(nested)) {
                        list(nested, nested.normalName().equals("ol"),
                                listDepth + 1, domDepth + 1);
                    }
                }
                if (ordered) {
                    number = itemNumber + (reversed ? -1 : 1);
                }
            }
            paragraphBreak();
        }

        private static boolean isList(Element element) {
            return element.normalName().equals("ul") || element.normalName().equals("ol")
                    || element.normalName().equals("menu");
        }

        private void table(Element table, int depth) {
            List<List<Element>> cellsByRow = HtmlTableGrid.rows(
                    table, MAXIMUM_TABLE_ROWS, MAXIMUM_TABLE_COLUMNS);
            if (cellsByRow.isEmpty()) {
                return;
            }
            paragraphBreak();
            Element caption = table.children().stream()
                    .filter(child -> child.normalName().equals("caption"))
                    .findFirst().orElse(null);
            if (caption != null && !caption.text().isBlank()) {
                raw("**");
                children(caption, true, depth);
                raw("**");
                lineBreak();
            }
            int columns = cellsByRow.stream().mapToInt(List::size).max().orElse(0);
            if (columns == 0) {
                return;
            }
            int written = 0;
            for (List<Element> cells : cellsByRow) {
                raw("| ");
                for (int column = 0; column < columns; column++) {
                    Element cell = column < cells.size() ? cells.get(column) : null;
                    if (cell != null) {
                        Renderer nested = new Renderer(links);
                        nested.children(cell, true, depth);
                        raw(nested.finish().replaceAll("\\s+", " ")
                                .replace("|", "\\|"));
                    }
                    raw(" | ");
                }
                lineBreak();
                if (written++ == 0) {
                    raw("| " + "--- | ".repeat(columns));
                    lineBreak();
                }
            }
            paragraphBreak();
        }

        private void definitionList(Element list, int depth) {
            paragraphBreak();
            for (Element child : list.children()) {
                switch (child.normalName()) {
                    case "dt" -> {
                        lineBreak();
                        raw("- **");
                        children(child, true, depth);
                        raw("**");
                        lineBreak();
                    }
                    case "dd" -> {
                        raw("  - ");
                        children(child, true, depth);
                        lineBreak();
                    }
                    default -> node(child, false, depth + 1);
                }
            }
            paragraphBreak();
        }

        private void quoted(Element element, int depth) {
            raw("“");
            children(element, true, depth);
            raw("”");
        }

        private void abbreviation(Element element, int depth) {
            children(element, true, depth);
            String expansion = cleanInline(element.attr("title"), 240);
            if (!expansion.isBlank()
                    && !expansion.equalsIgnoreCase(cleanInline(element.text(), 240))) {
                raw(" (");
                text(expansion);
                raw(")");
            }
        }

        private void ruby(Element element, int depth) {
            Renderer base = new Renderer(links);
            for (Node child : element.childNodes()) {
                if (child instanceof Element annotation
                        && (annotation.normalName().equals("rt")
                        || annotation.normalName().equals("rp"))) {
                    continue;
                }
                base.node(child, true, depth + 1);
            }
            raw(base.finish());
            String reading = element.select("rt").stream().map(Element::text)
                    .filter(value -> !value.isBlank())
                    .collect(java.util.stream.Collectors.joining(" / "));
            if (!reading.isBlank()) {
                raw(" (");
                text(reading);
                raw(")");
            }
        }

        private void semanticInline(
                Element element,
                String opening,
                String closing,
                int depth
        ) {
            String content = inlineContent(element, depth);
            if (!content.isBlank()) {
                raw(opening + content + closing);
            }
        }

        private void checkbox(Element element) {
            if (element.attr("type").equalsIgnoreCase("checkbox")) {
                raw(element.hasAttr("checked") ? "[x] " : "[ ] ");
            }
        }

        private void media(Element element) {
            String kind = element.normalName().equals("video") ? "Video" : "Audio";
            String label = firstNonBlank(cleanInline(element.attr("title"), 180),
                    cleanInline(element.attr("aria-label"), 180),
                    cleanInline(element.text(), 180));
            String display = label.isBlank() ? kind : kind + ": " + label;
            String url = links.add(mediaSource(element));
            if (url == null) {
                text(display);
                return;
            }
            raw("[" + escapeLabel(display) + "](<" + url + ">)");
        }

        private String inlineContent(Element element, int depth) {
            Renderer nested = new Renderer(links);
            nested.children(element, true, depth);
            return nested.finish();
        }

        private static String imageSource(Element image) {
            String direct = firstHttpAttribute(image,
                    "src", "data-src", "data-lazy-src", "data-original", "data-url");
            return direct.isBlank() ? firstSrcsetUrl(image, "srcset", "data-srcset") : direct;
        }

        private static String mediaSource(Element media) {
            String direct = firstHttpAttribute(media, "src");
            if (!direct.isBlank()) {
                return direct;
            }
            for (Element source : media.children()) {
                if (!source.normalName().equals("source")) {
                    continue;
                }
                String sourceUrl = firstHttpAttribute(source, "src");
                if (!sourceUrl.isBlank()) {
                    return sourceUrl;
                }
            }
            return "";
        }

        private static String firstHttpAttribute(Element element, String... attributes) {
            for (String attribute : attributes) {
                if (!element.hasAttr(attribute) || element.attr(attribute).isBlank()) {
                    continue;
                }
                String resolved = element.absUrl(attribute);
                if (resolved.isBlank()) {
                    resolved = resolveUrl(element, element.attr(attribute));
                }
                String canonical = InlineLinkBudget.httpUrl(resolved);
                if (!canonical.isBlank()) {
                    return canonical;
                }
            }
            return "";
        }

        private static String firstSrcsetUrl(Element element, String... attributes) {
            for (String attribute : attributes) {
                if (!element.hasAttr(attribute)) {
                    continue;
                }
                String[] candidates = element.attr(attribute).split(",");
                for (int index = candidates.length - 1; index >= 0; index--) {
                    String candidate = candidates[index].strip();
                    if (candidate.isBlank()) {
                        continue;
                    }
                    String rawUrl = candidate.split("\\s+", 2)[0];
                    String canonical = InlineLinkBudget.httpUrl(resolveUrl(element, rawUrl));
                    if (!canonical.isBlank()) {
                        return canonical;
                    }
                }
            }
            return "";
        }

        private static String resolveUrl(Element element, String rawUrl) {
            try {
                return URI.create(element.baseUri()).resolve(rawUrl.strip()).toString();
            } catch (IllegalArgumentException error) {
                return "";
            }
        }

        private static int integerAttribute(Element element, String name, int fallback) {
            try {
                return Math.clamp(Integer.parseInt(element.attr(name)), -1_000_000, 1_000_000);
            } catch (NumberFormatException error) {
                return fallback;
            }
        }

        private void text(String source) {
            String normalized = Objects.requireNonNullElse(source, "")
                    .replaceAll("\\s+", " ");
            if (normalized.isBlank()) {
                space();
                return;
            }
            boolean leadingSpace = Character.isWhitespace(normalized.charAt(0));
            String clean = normalized.strip();
            if (leadingSpace) {
                space();
            }
            raw(escapeText(clean));
            if (Character.isWhitespace(normalized.charAt(normalized.length() - 1))) {
                space();
            }
        }

        private void raw(String value) {
            output.append(value);
        }

        private void space() {
            if (!output.isEmpty()) {
                char last = output.charAt(output.length() - 1);
                if (!Character.isWhitespace(last)) {
                    output.append(' ');
                }
            }
        }

        private void lineBreak() {
            trimSpaces();
            if (!output.isEmpty() && output.charAt(output.length() - 1) != '\n') {
                output.append('\n');
            }
        }

        private void paragraphBreak() {
            trimSpaces();
            int newlines = 0;
            for (int index = output.length() - 1;
                 index >= 0 && output.charAt(index) == '\n'; index--) {
                newlines++;
            }
            if (!output.isEmpty()) {
                output.append("\n".repeat(Math.max(0, 2 - newlines)));
            }
        }

        private void trimSpaces() {
            while (!output.isEmpty()) {
                char last = output.charAt(output.length() - 1);
                if (last == ' ' || last == '\t') {
                    output.setLength(output.length() - 1);
                } else {
                    break;
                }
            }
        }

        private String finish() {
            trimSpaces();
            return output.toString().strip();
        }

        private static String codeLanguage(Element pre) {
            Element code = pre.selectFirst("code");
            for (Element candidate : new Element[]{code, pre}) {
                if (candidate == null) {
                    continue;
                }
                String attribute = firstNonBlank(candidate.attr("data-language"),
                        candidate.attr("data-lang"));
                String sanitized = sanitizeLanguage(attribute);
                if (!sanitized.isBlank()) {
                    return sanitized;
                }
                for (String className : candidate.classNames()) {
                    for (String prefix : new String[]{
                            "language-", "lang-", "highlight-source-"}) {
                        if (className.startsWith(prefix)
                                && className.length() > prefix.length()) {
                            sanitized = sanitizeLanguage(className.substring(prefix.length()));
                            if (!sanitized.isBlank()) {
                                return sanitized;
                            }
                        }
                    }
                }
            }
            return "";
        }

        private static String sanitizeLanguage(String value) {
            return Objects.requireNonNullElse(value, "").strip()
                    .replaceAll("[^A-Za-z0-9_+.-]", "");
        }
    }
}
