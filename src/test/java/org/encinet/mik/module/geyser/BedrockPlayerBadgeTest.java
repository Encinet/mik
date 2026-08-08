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
        String identityComponent = Files.readString(
                MAIN.resolve("player/identity/PlayerIdentityComponent.java"));

        assertTrue(chat.contains("playerIdentities.render(sender, viewer"));
        assertTrue(tabList.contains(".render(subject, language"));
        assertTrue(nameTag.contains("playerIdentities.renderPreview("));
        assertTrue(identities.contains("platformBadge.prefix(subject, viewer)"));
        assertTrue(identities.contains("nameTags.render(subject, baseName"));
        assertTrue(identityComponent.contains("record PlayerIdentityComponent(Component component)"));
        assertFalse(chat.contains("identity.platformBadge()"));
        assertFalse(chat.contains("identity.nameTag()"));
        for (String consumer : new String[]{chat, tabList}) {
            assertFalse(consumer.contains("net.luckperms"));
            assertFalse(consumer.contains("NameMetaRenderer"));
            assertFalse(consumer.contains("BedrockPlayerBadge"));
        }
    }

    @Test
    void tabListBadgeIsLocalizedPerViewer() throws IOException {
        String badge = Files.readString(MAIN.resolve("geyser/BedrockPlayerBadge.java"));
        String tabList = Files.readString(MAIN.resolve("player/TabListModule.java"));

        assertTrue(badge.contains("Message.PLAYER_BEDROCK_LABEL"));
        assertFalse(badge.contains("globalPrefix"));
        assertTrue(tabList.contains("implements Listener, AfkStateListener, LanguageChangeListener"));
        assertTrue(tabList.contains("languageService.language(viewer)"));
        assertTrue(tabList.contains("Action.UPDATE_DISPLAY_NAME"));
        assertTrue(tabList.contains("addLanguageChangeListener(this)"));
        assertTrue(tabList.contains("removeLanguageChangeListener(this)"));
    }
}
