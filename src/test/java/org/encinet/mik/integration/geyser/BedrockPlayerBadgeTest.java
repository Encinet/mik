package org.encinet.mik.integration.geyser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockPlayerBadgeTest {

    @Test
    void markerIsLeadingCompactAndOptional() {
        Component marker = BedrockPlayerBadge.render(true, "Bedrock");

        assertEquals("[Bedrock] ", PlainTextComponentSerializer.plainText().serialize(marker));
        assertEquals(NamedTextColor.AQUA, marker.color());
        assertEquals(Component.empty(), BedrockPlayerBadge.render(false, "Bedrock"));
        assertEquals(Component.empty(), BedrockPlayerBadge.render(true, " "));
    }
}
