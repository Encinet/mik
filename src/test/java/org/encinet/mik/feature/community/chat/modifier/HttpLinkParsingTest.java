package org.encinet.mik.feature.community.chat.modifier;

import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpLinkParsingTest {

    private static final String BASE = "https://example.com/document.pdf";
    private final UrlModifier modifier = new UrlModifier();

    @Test
    void encodesExtraFragmentDelimitersWithoutDoubleEncoding() {
        String suffix = "?q=%23%25#part%23one#two#三";
        String input = BASE + suffix;

        ChatReplacement replacement = modifier.find(input, 0, null);

        assertEquals(0, replacement.start());
        assertEquals(input.length(), replacement.end());
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(BASE + "?q=%23%25#part%23one%23two%23三",
                link.target().toString());
    }

    @Test
    void leavesMalformedCandidatesAsTextAndFindsLaterValidLinks() {
        for (String suffix : new String[]{"/%", "/%GG", "/%2", "/{bad}", "/a|b"}) {
            String invalid = BASE + suffix;
            assertNull(modifier.find(invalid, 0, null), invalid);
            String message = invalid + " then " + BASE;

            ChatReplacement replacement = modifier.find(message, 0, null);

            assertEquals(invalid.length() + " then ".length(), replacement.start());
            assertEquals(message.length(), replacement.end());
            ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
            assertEquals(BASE, link.target().toString());
        }
    }
}
