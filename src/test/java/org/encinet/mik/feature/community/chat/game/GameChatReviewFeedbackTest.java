package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.ServerChatPublisher;
import org.encinet.mik.feature.community.chat.bridge.SocialChatFailure;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.shared.i18n.*;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GameChatReviewFeedbackTest {
    @Test void feedbackIsVisibleOnlyToTheSenderAndRespectsCurrentGameAdmission() {
        UUID sender = UUID.randomUUID(), other = UUID.randomUUID();
        var fluent = FluentTestSupport.languages();
        var languages = new LanguageService(null) {
            @Override public Language language(UUID playerId, InetAddress fallbackAddress) {
                assertEquals(sender, playerId);
                return Language.ZH_CN;
            }
            @Override public String t(Language language, Message message, Object... args) {
                return fluent.t(language, message, args);
            }
        };
        for (ChatOrigin origin : List.of(new ChatOrigin.Minecraft(sender), new ChatOrigin.Web(sender))) {
            for (boolean admitted : List.of(false, true)) for (boolean unavailable : List.of(false, true)) {
                try (var bukkit = mockStatic(Bukkit.class)) {
                    var player = mock(Player.class);
                    bukkit.when(() -> Bukkit.getPlayer(sender)).thenReturn(player);
                    var participants = mock(ChatParticipants.class);
                    when(participants.gameRecipients(any())).thenReturn(admitted ? Set.of(sender) : Set.of());
                    var notices = new ArrayList<ChatServerMessage>();
                    var visibility = new ArrayList<ServerChatPublisher.Visibility>();
                    var feedback = new GameChatReviewFeedback(languages, participants, (message, audience) -> {
                        notices.add(message); visibility.add(audience);
                    });
                    var submission = new ChatSubmission(ChatMessageId.random(), origin, ChatSender.minecraft(sender, "Alice"),
                            ChatConversation.minecraftPublic(), "original text", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external());
                    String reason = unavailable ? "Qwen Guard returned HTTP 429" : "Unsafe: Violent";
                    feedback.accept(new ChatMessage(submission, ChatContent.plain("original text"), Set.of()),
                            new SocialChatFailure("qq", "QQ", reason, unavailable));
                    assertEquals(1, notices.size());
                    var notice = notices.getFirst();
                    String text = ((ChatServerMessage.RichText) notice.content()).content().plainText();
                    assertTrue(text.contains("QQ")); assertTrue(text.contains(reason));
                    assertTrue(text.contains(unavailable ? "内容审查失败" : "消息未通过内容审查，原因:"));
                    assertFalse(text.contains("original text"));
                    assertFalse(notice.countsAsUnread());
                    assertTrue(visibility.getFirst().includes().test(sender));
                    assertFalse(visibility.getFirst().includes().test(other));
                    assertFalse(visibility.getFirst().includesOffline());
                    verify(player, times(admitted ? 1 : 0)).sendMessage(Component.text(text, NamedTextColor.YELLOW));
                }
            }
        }
    }
}
