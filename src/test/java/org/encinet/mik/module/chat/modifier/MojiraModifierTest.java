package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MojiraModifierTest {

    private static final String CANONICAL_URL = "https://mojira.dev/MC-4";

    private final MojiraModifier modifier = new MojiraModifier();

    @Test
    void parsesBareIssueKey() {
        String message = "fixed in MC-4.";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(9, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertReplacement(replacement, "MC-4", CANONICAL_URL);
    }

    @Test
    void normalizesIssueKeyCase() {
        ChatReplacement replacement = modifier.find("mc-4", 0, null);

        assertReplacement(replacement, "MC-4", CANONICAL_URL);
    }

    @Test
    void parsesIssueKeyAdjacentToChineseText() {
        String message = "请看MC-4，谢谢";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(2, replacement.start());
        assertEquals(6, replacement.end());
        assertReplacement(replacement, "MC-4", CANONICAL_URL);
    }

    @Test
    void convertsEverySupportedIssueUrlToMojiraDev() {
        List<String> urls = List.of(
                "https://mojira.dev/MC-4",
                "https://bugs-legacy.mojang.com/browse/MC-4",
                "https://bugs.mojang.com/browse/MC-4",
                "https://report.bugs.mojang.com/servicedesk/customer/portal/2/MC-4",
                "https://mojira.atlassian.net/browse/MC-4"
        );

        for (String url : urls) {
            ChatReplacement replacement = modifier.find(url, 0, null);

            assertEquals(0, replacement.start(), url);
            assertEquals(url.length(), replacement.end(), url);
            assertReplacement(replacement, "MC-4", CANONICAL_URL);
        }
    }

    @Test
    void consumesQueryButPreservesTrailingPunctuation() {
        String message = "https://bugs.mojang.com/browse/MC-4?focusedCommentId=123).";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(message.length() - 2, replacement.end());
        assertReplacement(replacement, "MC-4", CANONICAL_URL);
    }

    @Test
    void rejectsEmbeddedKeys() {
        assertNull(modifier.find("XMC-4", 0, null));
        assertNull(modifier.find("MC-4A", 0, null));
    }

    private void assertReplacement(ChatReplacement replacement, String label, String url) {
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(label, link.label());
        assertEquals(url, link.target().toString());
    }
}
