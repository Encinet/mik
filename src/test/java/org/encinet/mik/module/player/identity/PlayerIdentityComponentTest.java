package org.encinet.mik.module.player.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerIdentityComponentTest {

    @Test
    void identityIsAnAtomicComponent() {
        PlayerIdentityComponent identity = new PlayerIdentityComponent(
                Component.text("[Bedrock] [Member] Alex"));

        assertEquals("[Bedrock] [Member] Alex", plain(identity.component()));
        assertEquals(1, PlayerIdentityComponent.class.getRecordComponents().length);
        assertEquals("component",
                PlayerIdentityComponent.class.getRecordComponents()[0].getName());
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
