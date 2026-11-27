package org.encinet.mik.feature.community.chat.parse;

import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReferenceModifierTest {

    private final ReferenceModifier modifier = new ReferenceModifier();

    private ChatReplacement find(String text) {
        return modifier.search(text, ChatProcessingContext.linksOnly()).apply(0);
    }

    @Test
    void parsesBareDomainAdjacentToChineseText() {
        String message = "查看google.com这个网站";

        ChatReplacement replacement = find(message);

        int start = message.indexOf("google.com");
        assertEquals(start, replacement.start());
        assertEquals(start + "google.com".length(), replacement.end());
        assertReplacement(replacement, "google.com", "https://google.com/");
    }

    @Test
    void parsesBareSubdomainPortAndResource() {
        String link = "docs.example.co.uk:8443/guide?q=chat#links";

        ChatReplacement replacement = find(link);

        assertEquals(0, replacement.start());
        assertEquals(link.length(), replacement.end());
        assertReplacement(replacement,
                "docs.example.co.uk:8443/guide?q=chat#links", "https://" + link);
    }

    @Test
    void preservesInternationalizedWwwDomainSupport() {
        String message = "访问 www.例子.中国/路径。";

        ChatReplacement replacement = find(message);

        int start = message.indexOf("www.例子.中国");
        assertEquals(start, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement, "www.例子.中国/路径", "https://www.xn--fsqu00a.xn--fiqs8s/路径");
    }

    @Test
    void skipsNonDomainCandidatesAndFindsLaterLink() {
        String message = "config.yaml, version 1.20; docs at google.com";

        ChatReplacement replacement = find(message);

        int start = message.indexOf("google.com");
        assertEquals(start, replacement.start());
        assertEquals(start + "google.com".length(), replacement.end());
        assertReplacement(replacement, "google.com", "https://google.com/");
    }

    @Test
    void rejectsBareDomainsWithoutARegistrySuffixAndInvalidPorts() {
        assertNull(find("config.yaml"));
        assertNull(find("release 1.20"));
        assertNull(find("google.com:123456"));
    }

    @Test
    void preservesExplicitUrlWithPrivateSuffix() {
        String link = "http://service.internal/path";

        ChatReplacement replacement = find(link);

        assertReplacement(replacement, "http://service.internal/path", link);
    }

    @Test
    void removesOnlyUnbalancedClosingPunctuation() {
        String message = "example.com/wiki/Function_(mathematics)).";

        ChatReplacement replacement = find(message);

        String link = "example.com/wiki/Function_(mathematics)";
        assertEquals(link.length(), replacement.end());
        assertReplacement(replacement,
                "example.com/wiki/Function_(mathematics)", "https://" + link);
    }

    @Test
    void keepsTheCompleteUnicodeUrlInBothTheLabelAndTarget() {
        String link = "example.com/" + "路径".repeat(30);

        ChatReplacement replacement = find(link);

        assertReplacement(replacement, link, "https://" + link);
    }

    @Test
    void removesTrackingParametersButPreservesFunctionalQueryAndFragment() {
        String link = "https://example.com/download?UTM_Source=chat&id=42"
                + "&fbclid=opaque&token=essential&spm=feed#checksum";
        String cleaned = "https://example.com/download?id=42"
                + "&token=essential#checksum";

        ChatReplacement replacement = find(link);

        assertReplacement(replacement, cleaned, cleaned);
    }

    @Test
    void removesTheQueryDelimiterWhenEveryParameterIsTracking() {
        String link = "example.com/article?utm_source=chat&fbclid=opaque#comments";
        String cleaned = "example.com/article#comments";

        ChatReplacement replacement = find(link);

        assertReplacement(replacement, cleaned,
                "https://" + cleaned);
    }

    @Test
    void parsesPdfUrlWithRepeatedFragmentDelimiters() {
        String link = "https://files.sciengine.com/pdfs/1002-0268/"
                + "88BFA8C313D54066BAA20CCD8E837A3F.pdf#2#2#2";
        String message = "# " + link;

        ChatReplacement replacement = find(message);

        assertEquals(2, replacement.start());
        assertEquals(message.length(), replacement.end());
        assertReplacement(replacement, link,
                link.substring(0, link.indexOf('#')) + "#2%232%232");
    }

    @Test
    void createsMailtoLinkAndLeavesSentencePunctuationOutside() {
        String message = "联系 Player.One+chat@example.co.uk。";

        ChatReplacement replacement = find(message);

        String address = "Player.One+chat@example.co.uk";
        assertEquals(message.indexOf(address), replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement, "Email: " + address,
                "mailto:" + address);
    }

    @Test
    void acceptsAnExistingMailtoPrefixWithoutDuplicatingIt() {
        String token = "mailto:admin@example.org";

        ChatReplacement replacement = find(token);

        assertEquals(0, replacement.start());
        assertEquals(token.length(), replacement.end());
        assertReplacement(replacement, "Email: admin@example.org", token);
    }

    @Test
    void rejectsMalformedEmailAddresses() {
        assertNull(find("alice@example"));
        assertNull(find("alice..chat@example.org"));
        assertNull(find("alice@example.org_more"));
    }

    @Test
    void encodesExtraFragmentDelimitersWithoutDoubleEncoding() {
        String base = "https://example.com/document.pdf";
        String suffix = "?q=%23%25#part%23one#two#三";
        String input = base + suffix;

        ChatReplacement replacement = find(input);

        assertEquals(0, replacement.start());
        assertEquals(input.length(), replacement.end());
        assertReplacement(replacement, input, base + "?q=%23%25#part%23one%23two%23%E4%B8%89");
    }

    @Test
    void leavesMalformedCandidatesAsTextAndFindsLaterValidLinks() {
        String base = "https://example.com/document.pdf";
        for (String suffix : new String[]{"/%", "/%GG", "/%2", "/{bad}", "/a|b"}) {
            String invalid = base + suffix;
            assertNull(find(invalid), invalid);
            String message = invalid + " then " + base;

            ChatReplacement replacement = find(message);

            assertEquals(invalid.length() + " then ".length(), replacement.start());
            assertEquals(message.length(), replacement.end());
            assertReplacement(replacement, base, base);
        }
    }

    private void assertReplacement(ChatReplacement replacement, String label, String url) {
        var reference = assertInstanceOf(ChatNode.Reference.class, replacement.node());
        var target = assertInstanceOf(ChatNode.Reference.Link.class, reference.target());
        assertEquals(label, reference.visibleText());
        assertEquals(java.net.URI.create(url).toASCIIString(), target.uri().toString());
    }
}
