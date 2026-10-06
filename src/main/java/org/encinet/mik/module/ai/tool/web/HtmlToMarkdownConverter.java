package org.encinet.mik.module.ai.tool.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.encinet.mik.module.ai.tool.web.MarkdownText.cleanInline;
import static org.encinet.mik.module.ai.tool.web.MarkdownText.firstNonBlank;

/** Parses and cleans an HTML document before rendering its readable content. */
final class HtmlToMarkdownConverter {
    private static final String NOISE_SELECTOR = String.join(",",
            "script", "style", "noscript", "template", "svg", "canvas", "iframe",
            "object", "embed", "frame", "frameset", "portal", "nav", "footer", "aside",
            "form", "dialog", "button", "select", "textarea", "[hidden]",
            "[aria-hidden=true]", "[role=navigation]", "[role=banner]",
            "[role=complementary]", "[role=dialog]");

    private HtmlToMarkdownConverter() {
    }

    static ReadableWebPage convert(
            byte[] source,
            URI baseUri,
            String charsetName,
            int maximumCharacters,
            int maximumLinks
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(baseUri, "baseUri");
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(source), charsetName,
                    baseUri.toASCIIString());
        } catch (IOException error) {
            throw new WebFetchException(
                    "invalid_html", "The page could not be parsed as HTML", error);
        }
        String title = firstNonBlank(cleanInline(document.title(), 300),
                metadata(document, "meta[property=og:title],meta[name=twitter:title]", 300));
        String description = metadata(document,
                "meta[name=description],meta[property=og:description],"
                        + "meta[name=twitter:description]", 500);

        document.select(NOISE_SELECTOR).remove();
        removeVisuallyHidden(document);
        Element root = readableRoot(document);
        String markdown = HtmlMarkdownRenderer.render(
                root, title, description, maximumLinks);
        MarkdownTruncator.Result limited = MarkdownTruncator.truncate(
                markdown, maximumCharacters);
        return new ReadableWebPage(
                title, description, limited.text(), limited.truncated());
    }

    private static Element readableRoot(Document document) {
        Element body = document.body();
        if (body == null) {
            return document;
        }
        int bodyCharacters = body.text().length();
        Element primary = document.select("main,[role=main]").stream()
                .max(Comparator.comparingInt(element -> element.text().length()))
                .orElse(null);
        if (isSubstantial(primary, bodyCharacters, 80, 10)) {
            return primary;
        }
        List<Element> articles = document.select("article");
        if (articles.size() == 1
                && isSubstantial(articles.getFirst(), bodyCharacters, 120, 4)) {
            return articles.getFirst();
        }
        Element content = document.select(
                        "#content,#main-content,.main-content,.article-content,.post-content")
                .stream().max(Comparator.comparingInt(element -> element.text().length()))
                .orElse(null);
        return isSubstantial(content, bodyCharacters, 160, 4) ? content : body;
    }

    private static boolean isSubstantial(
            Element candidate,
            int bodyCharacters,
            int minimumCharacters,
            int minimumFraction
    ) {
        if (candidate == null) {
            return false;
        }
        int candidateCharacters = candidate.text().length();
        return candidateCharacters >= minimumCharacters
                && candidateCharacters * minimumFraction >= Math.max(1, bodyCharacters);
    }

    private static String metadata(Document document, String selector, int maximumCharacters) {
        Element element = document.selectFirst(selector);
        return element == null ? ""
                : cleanInline(element.attr("content"), maximumCharacters);
    }

    private static void removeVisuallyHidden(Document document) {
        for (Element element : new ArrayList<>(document.select("[style]"))) {
            String style = element.attr("style").toLowerCase(Locale.ROOT)
                    .replaceAll("\\s+", "");
            if (style.contains("display:none") || style.contains("visibility:hidden")
                    || style.contains("content-visibility:hidden")) {
                element.remove();
            }
        }
    }
}
