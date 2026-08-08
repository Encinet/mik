package org.encinet.mik.module.chat.modifier;

import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UrlModifierTest {

    private final UrlModifier modifier = new UrlModifier();

    @Test
    void parsesBareDomainAdjacentToChineseText() {
        String message = "查看google.com这个网站";

        ChatReplacement replacement = modifier.find(message, 0, null);

        int start = message.indexOf("google.com");
        assertEquals(start, replacement.start());
        assertEquals(start + "google.com".length(), replacement.end());
        assertReplacement(replacement, "[google.com]", "https://google.com");
    }

    @Test
    void parsesBareSubdomainPortAndResource() {
        String link = "docs.example.co.uk:8443/guide?q=chat#links";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertEquals(0, replacement.start());
        assertEquals(link.length(), replacement.end());
        assertReplacement(replacement, "[docs.example.co.uk:8443/guide?q=....]", "https://" + link);
    }

    @Test
    void preservesInternationalizedWwwDomainSupport() {
        String message = "访问 www.例子.中国/路径。";

        ChatReplacement replacement = modifier.find(message, 0, null);

        int start = message.indexOf("www.例子.中国");
        assertEquals(start, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement, "[www.例子.中国/路径]", "https://www.例子.中国/路径");
    }

    @Test
    void skipsNonDomainCandidatesAndFindsLaterLink() {
        String message = "config.yaml, version 1.20; docs at google.com";

        ChatReplacement replacement = modifier.find(message, 0, null);

        int start = message.indexOf("google.com");
        assertEquals(start, replacement.start());
        assertEquals(start + "google.com".length(), replacement.end());
        assertReplacement(replacement, "[google.com]", "https://google.com");
    }

    @Test
    void rejectsEmailDomainsAndTextWithoutARegistrySuffix() {
        assertNull(modifier.find("user@google.com", 0, null));
        assertNull(modifier.find("config.yaml", 0, null));
        assertNull(modifier.find("release 1.20", 0, null));
        assertNull(modifier.find("google.com:123456", 0, null));
    }

    @Test
    void preservesExplicitUrlWithPrivateSuffix() {
        String link = "http://service.internal/path";

        ChatReplacement replacement = modifier.find(link, 0, null);

        assertReplacement(replacement, "[http://service.internal/path]", link);
    }

    @Test
    void removesOnlyUnbalancedClosingPunctuation() {
        String message = "example.com/wiki/Function_(mathematics)).";

        ChatReplacement replacement = modifier.find(message, 0, null);

        String link = "example.com/wiki/Function_(mathematics)";
        assertEquals(link.length(), replacement.end());
        assertReplacement(replacement, "[example.com/wiki/Function_(mathe....]", "https://" + link);
    }

    private void assertReplacement(ChatReplacement replacement, String label, String url) {
        assertEquals(label, ((TextComponent) replacement.component()).content());
        assertEquals(ClickEvent.openUrl(url), replacement.component().clickEvent());
    }
}
