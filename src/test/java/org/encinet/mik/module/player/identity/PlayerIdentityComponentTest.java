package org.encinet.mik.module.player.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerIdentityComponentTest {

    @Test
    void partsRemainIndependentAndCombineInIdentityOrder() {
        PlayerIdentityComponent identity = new PlayerIdentityComponent(
                Component.text("[Bedrock] "),
                Component.text("[Member] Alex"));

        assertEquals("[Bedrock] ", plain(identity.platformBadge()));
        assertEquals("[Member] Alex", plain(identity.nameTag()));
        assertEquals("[Bedrock] [Member] Alex", plain(identity.combined()));
    }

    @Test
    void rawNameTagsNormalizeMissingDecorations() {
        PlayerNameTag nameTag = new PlayerNameTag(null, null);

        assertEquals("", nameTag.prefix());
        assertEquals("", nameTag.suffix());
        assertEquals(PlayerNameTag.empty(), nameTag);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
