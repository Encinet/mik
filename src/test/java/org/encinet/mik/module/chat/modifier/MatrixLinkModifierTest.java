package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MatrixLinkModifierTest {
    private final MatrixLinkModifier modifier = new MatrixLinkModifier();

    @Test
    void parsesEncodedMatrixToRoomEventAndPreservesRouting() {
        String link = "https://matrix.to/#/%21room%3Aexample.org/"
                + "%24event%3Aexample.org?via=example.org";
        String message = "查看 " + link + "。";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(3, replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertLink(replacement,
                "[Matrix: !room:example.org / $event:example.org]", link);
    }

    @Test
    void normalizesMatrixUriIntoAStandardsCompatibleMatrixToLink() {
        String uri = "matrix:roomid/room:example.org/e/event:example.org"
                + "?via=elsewhere.example";

        ChatReplacement replacement = modifier.find(uri, 0, null);

        assertLink(replacement,
                "[Matrix: !room:example.org / $event:example.org]",
                "https://matrix.to/#/%21room%3Aexample.org/"
                        + "%24event%3Aexample.org?via=elsewhere.example");
    }

    @Test
    void turnsRawUserAndRoomIdentifiersIntoPermalinks() {
        ChatReplacement user = modifier.find("联系 @alice:example.org", 0, null);
        ChatReplacement room = modifier.find("加入 #中文:example.org", 0, null);

        assertLink(user, "[Matrix: @alice:example.org]",
                "https://matrix.to/#/%40alice%3Aexample.org");
        assertLink(room, "[Matrix: #中文:example.org]",
                "https://matrix.to/#/%23%E4%B8%AD%E6%96%87%3Aexample.org");
    }

    @Test
    void rejectsLookalikeDomainsAuthoritiesAndIncompleteReferences() {
        assertNull(modifier.find(
                "https://matrix.to.evil/#/@alice:example.org", 0, null));
        assertNull(modifier.find("matrix://evil/u/alice:example.org", 0, null));
        assertNull(modifier.find("matrix:u/alice", 0, null));
        assertNull(modifier.find("#普通前缀", 0, null));
    }

    private void assertLink(
            ChatReplacement replacement, String label, String target
    ) {
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(label, link.label());
        assertEquals(target, link.target().toString());
    }
}
