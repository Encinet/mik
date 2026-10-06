package org.encinet.mik.module.ai.tool.web;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlToMarkdownConverterTest {

    @Test
    void extractsReadableStructureAndAbsoluteFollowableLinks() {
        String html = """
                <!doctype html>
                <html><head><title>Example *Page*</title>
                  <meta name="description" content="A concise description.">
                </head><body>
                  <nav>Navigation noise</nav>
                  <main>
                    <h1>Article heading</h1>
                    <p>Hello <strong>world</strong>. <a href="/next?q=1">Read next</a>.</p>
                    <blockquote>Quoted <em>idea</em>.</blockquote>
                    <ul><li>First item</li><li>Second item</li></ul>
                    <table><tr><th>Name</th><th>Value</th></tr>
                      <tr><td>TPS</td><td><a href="/metrics">20</a></td></tr></table>
                    <pre><code class="language-java">int value = 42;</code></pre>
                    <script>ignorePromptInjection()</script>
                  </main>
                  <footer>Footer noise</footer>
                </body></html>
                """;

        ReadableWebPage page = HtmlToMarkdownConverter.convert(
                html.getBytes(StandardCharsets.UTF_8),
                URI.create("https://example.test/articles/start"), null,
                10_000, 10);

        assertEquals("Example *Page*", page.title());
        assertTrue(page.markdown().contains("# Example \\*Page\\*"));
        assertTrue(page.markdown().contains("# Article heading"));
        assertTrue(page.markdown().contains("**world**"));
        assertTrue(page.markdown().contains(
                "[Read next](<https://example.test/next?q=1>)"));
        assertTrue(page.markdown().contains("- First item"));
        assertTrue(page.markdown().contains("| Name | Value |"));
        assertTrue(page.markdown().contains(
                "| TPS | [20](<https://example.test/metrics>) |"));
        assertTrue(page.markdown().contains("```java"));
        assertFalse(page.markdown().contains("Navigation noise"));
        assertFalse(page.markdown().contains("Footer noise"));
        assertFalse(page.markdown().contains("ignorePromptInjection"));
        assertFalse(page.truncated());
    }

    @Test
    void keepsLinkTextButDropsUrlsBeyondTheConfiguredInlineLinkBudget() {
        String html = "<html><body><p><a href='/one'>One</a> and "
                + "<a href='/two'>Two</a></p></body></html>";

        ReadableWebPage page = HtmlToMarkdownConverter.convert(
                html.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/"),
                null, 10_000, 1);

        assertTrue(page.markdown().contains("[One](<https://example.test/one>)"));
        assertTrue(page.markdown().contains("and Two"));
        assertFalse(page.markdown().contains("https://example.test/two"));
    }

    @Test
    void convertsSemanticListsDefinitionsLazyMediaAndInternationalText() {
        String html = """
                <!doctype html>
                <html><head>
                  <base href="https://cdn.example.test/docs/">
                  <meta property="og:title" content="Semantic page">
                  <meta name="twitter:description" content="Rich conversion fixture">
                </head><body>
                  <div role="navigation">Role navigation noise</div>
                  <main>
                    <p><abbr title="HyperText Markup Language">HTML</abbr> says
                      <q>Hello</q>; <mark>important</mark>; H<sub>2</sub>O and x<sup>2</sup>.
                      <ruby>漢<rp>(</rp><rt>かん</rt><rp>)</rp></ruby></p>
                    <details><summary>More details</summary><p>Expanded content.</p></details>
                    <dl><dt>Term</dt><dd>Definition</dd></dl>
                    <ol start="3"><li>Third
                      <ul><li><input type="checkbox" checked> Done</li>
                          <li><input type="checkbox"> Pending</li></ul>
                    </li><li value="8">Eighth</li></ol>
                    <p><img src="data:image/gif;base64,AA" data-src="images/hero.webp"
                      alt="Hero image"></p>
                    <p><a href="/manual"><img src="cover.webp" alt="Manual"></a>
                      <a href="api" aria-label="API reference"></a></p>
                    <video title="Demo"><source src="media/demo.mp4"></video>
                    <span style="display : none">Inline style noise</span>
                    <span aria-hidden="true">ARIA noise</span>
                  </main>
                </body></html>
                """;

        ReadableWebPage page = HtmlToMarkdownConverter.convert(
                html.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/start"),
                null, 20_000, 20);

        assertEquals("Semantic page", page.title());
        assertEquals("Rich conversion fixture", page.description());
        assertTrue(page.markdown().contains("HTML (HyperText Markup Language)"));
        assertTrue(page.markdown().contains("“Hello”"));
        assertTrue(page.markdown().contains("==important=="));
        assertTrue(page.markdown().contains("H~(2)O and x^(2)"));
        assertTrue(page.markdown().contains("漢 (かん)"));
        assertTrue(page.markdown().contains("**More details**"));
        assertTrue(page.markdown().contains("- **Term**\n  - Definition"));
        assertTrue(page.markdown().contains("3. Third"));
        assertTrue(page.markdown().contains("  - [x] Done"));
        assertTrue(page.markdown().contains("  - [ ] Pending"));
        assertTrue(page.markdown().contains("8. Eighth"));
        assertTrue(page.markdown().contains(
                "![Hero image](<https://cdn.example.test/docs/images/hero.webp>)"));
        assertTrue(page.markdown().contains(
                "[Manual](<https://cdn.example.test/manual>)"));
        assertTrue(page.markdown().contains(
                "[API reference](<https://cdn.example.test/docs/api>)"));
        assertTrue(page.markdown().contains(
                "[Video: Demo](<https://cdn.example.test/docs/media/demo.mp4>)"));
        assertFalse(page.markdown().contains("Role navigation noise"));
        assertFalse(page.markdown().contains("Inline style noise"));
        assertFalse(page.markdown().contains("ARIA noise"));
    }

    @Test
    void producesValidTablesAndCollisionFreeCodeFences() {
        String html = """
                <html><body><main>
                  <table><caption>Metrics</caption><tbody>
                    <tr><td>Name</td><td colspan="2">Values</td></tr>
                    <tr><td rowspan="2">TPS</td><td><code>a|b</code></td>
                      <td><a href="/metrics">details</a></td></tr>
                    <tr><td>MSPT</td><td>50</td></tr>
                  </tbody></table>
                  <p>Use <code>call(`x`)</code> here.</p>
                  <pre data-language="json"><code>{
                "fence": "````"
                }</code></pre>
                </main></body></html>
                """;

        ReadableWebPage page = HtmlToMarkdownConverter.convert(
                html.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/"),
                null, 20_000, 10);

        assertTrue(page.markdown().contains("**Metrics**"));
        assertTrue(page.markdown().contains("| Name | Values |  |"));
        assertTrue(page.markdown().contains("| --- | --- | --- |"));
        assertTrue(page.markdown().contains("| TPS | `a\\|b` | "
                + "[details](<https://example.test/metrics>) |"));
        assertTrue(page.markdown().contains("|  | MSPT | 50 |"));
        assertTrue(page.markdown().contains("Use ``call(`x`)`` here."));
        assertTrue(page.markdown().contains("`````json"));
        assertTrue(page.markdown().contains("\n`````"));
    }

    @Test
    void keepsMultipleArticlesAndToleratesMalformedOrDeepMarkup() {
        String nested = "<div>".repeat(300) + "Deep leaf" + "</div>".repeat(300);
        String html = "<html><body><header role='banner'>Banner noise</header>"
                + "<article><h2>First story</h2><p>First body without closing tags"
                + "</article><article><h2>Second story</h2><p>Second body</article>"
                + nested + "</body></html>";

        ReadableWebPage page = HtmlToMarkdownConverter.convert(
                html.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/"),
                null, 20_000, 10);

        assertTrue(page.markdown().contains("## First story"));
        assertTrue(page.markdown().contains("## Second story"));
        assertTrue(page.markdown().contains("Deep leaf"));
        assertFalse(page.markdown().contains("Banner noise"));
    }

    @Test
    void truncatesWithoutLeavingPartialLinksOrOpenCodeFences() {
        String linkHtml = "<html><body><p>" + "word ".repeat(170)
                + "<a href='https://example.test/" + "segment".repeat(100)
                + "'>Long destination</a></p></body></html>";
        String codeHtml = "<html><body><pre><code class='lang-text'>"
                + "line\n".repeat(300) + "</code></pre></body></html>";

        ReadableWebPage linkPage = HtmlToMarkdownConverter.convert(
                linkHtml.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/"),
                null, 1_000, 10);
        ReadableWebPage codePage = HtmlToMarkdownConverter.convert(
                codeHtml.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/"),
                null, 1_000, 10);

        assertTrue(linkPage.truncated());
        assertFalse(linkPage.markdown().contains("https://example.test/segment"));
        assertFalse(linkPage.markdown().contains("[Long destination"));
        String codeBeforeMarker = codePage.markdown().substring(0,
                codePage.markdown().indexOf("[page content truncated]")).stripTrailing();
        assertTrue(codeBeforeMarker.endsWith("```"));
        assertTrue(codePage.truncated());
    }

    @Test
    void boundsReadableContentWithoutSplittingASurrogatePair() {
        String html = "<html><body><main><p>" + "text ".repeat(400)
                + "😀</p></main></body></html>";

        ReadableWebPage page = HtmlToMarkdownConverter.convert(
                html.getBytes(StandardCharsets.UTF_8), URI.create("https://example.test/"),
                null, 1_000, 0);

        assertTrue(page.truncated());
        assertTrue(page.markdown().endsWith("[page content truncated]"));
        assertFalse(Character.isHighSurrogate(
                page.markdown().charAt(page.markdown().length() - 1)));
    }
}
