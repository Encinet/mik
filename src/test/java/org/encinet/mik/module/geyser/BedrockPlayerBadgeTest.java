package org.encinet.mik.module.geyser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockPlayerBadgeTest {

    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik/module");

    @Test
    void markerIsLeadingCompactAndOptional() {
        Component marker = BedrockPlayerBadge.render(true, "Bedrock");

        assertEquals("[Bedrock] ", PlainTextComponentSerializer.plainText().serialize(marker));
        assertEquals(NamedTextColor.AQUA, marker.color());
        assertEquals(Component.empty(), BedrockPlayerBadge.render(false, "Bedrock"));
        assertEquals(Component.empty(), BedrockPlayerBadge.render(true, " "));
    }

    @Test
    void everyRequestedPlayerNameSurfaceUsesTheSharedIdentityComponent() throws IOException {
        String chat = Files.readString(MAIN.resolve("chat/render/ChatMessageFormatter.java"));
        String tabList = Files.readString(MAIN.resolve("player/TabListModule.java"));
        String nameTag = Files.readString(MAIN.resolve("player/NameTagModule.java"));
        String identities = Files.readString(
                MAIN.resolve("player/identity/PlayerIdentityRenderer.java"));

        assertTrue(chat.contains("playerIdentities.render(sender, viewer"));
        assertTrue(tabList.contains("playerIdentities.renderGlobal(player"));
        assertTrue(nameTag.contains("playerIdentities.renderPreview("));
        assertTrue(identities.contains("platformBadge.prefix(subject, viewer)"));
        assertTrue(identities.contains("nameTags.render(subject, baseName"));
        for (String consumer : new String[]{chat, tabList}) {
            assertFalse(consumer.contains("net.luckperms"));
            assertFalse(consumer.contains("NameMetaRenderer"));
            assertFalse(consumer.contains("BedrockPlayerBadge"));
        }
    }
}
