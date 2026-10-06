package org.encinet.mik.module.chat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBridgeArchitectureTest {

    @Test
    void inboundSocialChatEntersChatModuleInsteadOfFormattingInTheGateway()
            throws Exception {
        String gateway = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/social/game/"
                        + "BukkitSocialChatGateway.java"));
        String chat = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/chat/ChatModule.java"));
        String formatter = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/chat/render/"
                        + "ChatMessageFormatter.java"));
        String nameTags = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/player/identity/"
                        + "PlayerNameTagRenderer.java"));
        String plugin = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/Mik.java"));
        String generation = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/social/runtime/"
                        + "SocialPlatformGeneration.java"));

        assertTrue(gateway.contains("identityBindings.find(identity.key())"));
        assertTrue(gateway.contains("playerNameTags.resolve("));
        assertTrue(gateway.contains("nameTag.prefix(), nameTag.suffix()"));
        assertTrue(gateway.contains("sink.display(platform.displayName(), enriched)"));
        assertFalse(gateway.contains("NamedTextColor"));
        assertFalse(gateway.contains("sendMessage("));
        assertTrue(chat.contains("implements Listener, SocialChatGameSink"));
        assertTrue(chat.contains("chatProcessor.process(submission)"));
        assertTrue(chat.contains("formatter.externalPublicMessage"));
        assertFalse(chat.contains("Bukkit.getOfflinePlayer(playerId)"));
        assertTrue(formatter.contains("new PlayerNameTag(sender.prefix(), sender.suffix())"));
        assertTrue(formatter.contains("Bukkit.getOfflinePlayer(playerId)"));
        assertTrue(formatter.contains("Message.CHAT_SOCIAL_ACCOUNT_LABEL"));
        assertTrue(formatter.contains("return externalPlayerIdentity("));
        assertTrue(chat.contains("componentImporter.importComponent(finalBody)"));
        assertTrue(formatter.contains("standardSeparator(),\n"
                + "                message, copyText, copyHint, null"));
        assertFalse(generation.substring(
                        generation.indexOf("private void deliverChat"),
                        generation.indexOf("private void sendChat"))
                .contains("contentGuard"));
        assertTrue(generation.contains("allowsOutboundText("));
        assertTrue(nameTags.contains("loadUser(playerId)"));
        assertTrue(nameTags.contains("if (Bukkit.isPrimaryThread())"));
        assertTrue(nameTags.contains("OfflinePlayer player"));
        assertTrue(plugin.indexOf("socialChatGateway.bind(chatModule)")
                < plugin.indexOf("startManaged(\"social\", socialModule::enable, socialModule::disable)"));
    }
}
