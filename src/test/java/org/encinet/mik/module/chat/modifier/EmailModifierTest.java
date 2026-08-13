package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailModifierTest {
    private final EmailModifier modifier = new EmailModifier();

    @Test
    void createsMailtoLinkAndLeavesSentencePunctuationOutside() {
        String message = "联系 Player.One+chat@example.co.uk。";

        ChatReplacement replacement = modifier.find(message, 0, null);

        String address = "Player.One+chat@example.co.uk";
        assertEquals(message.indexOf(address), replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertLink(replacement, "[Email: " + address + "]",
                "mailto:" + address);
    }

    @Test
    void acceptsAnExistingMailtoPrefixWithoutDuplicatingIt() {
        String token = "mailto:admin@example.org";

        ChatReplacement replacement = modifier.find(token, 0, null);

        assertEquals(0, replacement.start());
        assertEquals(token.length(), replacement.end());
        assertLink(replacement, "[Email: admin@example.org]", token);
    }

    @Test
    void rejectsMatrixIdsAndMalformedAddresses() {
        assertNull(modifier.find("@alice:example.org", 0, null));
        assertNull(modifier.find("alice@example", 0, null));
        assertNull(modifier.find("alice..chat@example.org", 0, null));
        assertNull(modifier.find("alice@example.org_more", 0, null));
    }

    private void assertLink(
            ChatReplacement replacement, String label, String target
    ) {
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(label, link.label());
        assertEquals(target, link.target().toString());
    }
}
