package org.encinet.mik.feature.community.chat.modifier;

import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpLinkParsingTest {

    static Stream<Arguments> modifiers() {
        return Stream.of(
                Arguments.of(new UrlModifier(), "https://example.com/document.pdf"),
                Arguments.of(new GitHubModifier(), "https://github.com/o/r"),
                Arguments.of(new MinecraftWikiModifier(), "https://minecraft.wiki/w/Diamond")
        );
    }

    @ParameterizedTest
    @MethodSource("modifiers")
    void encodesExtraFragmentDelimitersWithoutDoubleEncoding(
            ChatModifier modifier, String base
    ) {
        String suffix = "?q=%23%25#part%23one#two#三";
        String input = base + suffix;

        ChatReplacement replacement = modifier.find(input, 0, null);

        assertEquals(0, replacement.start());
        assertEquals(input.length(), replacement.end());
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(base + "?q=%23%25#part%23one%23two%23三",
                link.target().toString());
    }

    @ParameterizedTest
    @MethodSource("modifiers")
    void leavesMalformedCandidatesAsTextAndFindsLaterValidLinks(
            ChatModifier modifier, String base
    ) {
        for (String suffix : new String[]{"/%", "/%GG", "/%2", "/{bad}", "/a|b"}) {
            String invalid = base + suffix;
            assertNull(modifier.find(invalid, 0, null), invalid);
            String message = invalid + " then " + base;

            ChatReplacement replacement = modifier.find(message, 0, null);

            assertEquals(invalid.length() + " then ".length(), replacement.start());
            assertEquals(message.length(), replacement.end());
            ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
            assertEquals(base, link.target().toString());
        }
    }
}
