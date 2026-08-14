package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BilibiliModifierTest {
    private final BilibiliModifier modifier = new BilibiliModifier();

    @Test
    void canonicalizesVideoUrlsAndLeavesSentencePunctuationOutside() {
        String message = "看看 https://m.bilibili.com/video/bv1GJ411x7h7"
                + "?spm_id_from=333.1007。";

        ChatReplacement replacement = modifier.find(message, 0, null);

        assertEquals(message.indexOf("https://"), replacement.start());
        assertEquals(message.length() - 1, replacement.end());
        assertLink(replacement, "[Bilibili: BV1GJ411x7h7]",
                "https://www.bilibili.com/video/BV1GJ411x7h7");
    }

    @Test
    void turnsStandaloneBvAndAvIdsIntoConsistentServiceLinks() {
        assertLink(modifier.find("BV1GJ411x7h7", 0, null),
                "[Bilibili: BV1GJ411x7h7]",
                "https://www.bilibili.com/video/BV1GJ411x7h7");
        assertLink(modifier.find("AV170001", 0, null),
                "[Bilibili: av170001]",
                "https://www.bilibili.com/video/av170001");
    }

    @Test
    void rejectsLookalikeDomainsEmailsAndEmbeddedVideoIds() {
        assertNull(modifier.find(
                "https://bilibili.com.example/video/BV1GJ411x7h7", 0, null));
        assertNull(modifier.find(
                "user@bilibili.com/video/BV1GJ411x7h7", 0, null));
        assertNull(modifier.find("xBV1GJ411x7h7", 0, null));
        assertNull(modifier.find("BV1GJ411x7h7x", 0, null));
    }

    private void assertLink(
            ChatReplacement replacement, String label, String target
    ) {
        ChatNode.Link link = (ChatNode.Link) replacement.nodes().getFirst();
        assertEquals(label, link.label());
        assertEquals(target, link.target().toString());
        assertEquals(ChatLinkPalette.BILIBILI.style(), link.style());
    }
}
