package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.ServerChatPublisher;
import org.encinet.mik.feature.community.chat.bridge.SocialChatFailure;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Set;
import java.util.function.BiConsumer;

/** Primary-thread player feedback, scoped to the sender's game and website sessions. */
public final class GameChatReviewFeedback implements BiConsumer<ChatMessage, SocialChatFailure> {
    private final LanguageService languages;
    private final ChatParticipants participants;
    private final ServerChatPublisher notices;

    public GameChatReviewFeedback(LanguageService languages, ChatParticipants participants, ServerChatPublisher notices) {
        this.languages = languages;
        this.participants = participants;
        this.notices = notices;
    }

    @Override public void accept(ChatMessage message, SocialChatFailure report) {
        if (!(message.submission().origin() instanceof ChatOrigin.Player source)) return;
        var sender = source.playerId();
        String text = languages.t(languages.language(sender, null), report.unavailable()
                        ? Message.CHAT_SOCIAL_REVIEW_FAILED : Message.CHAT_SOCIAL_REVIEW_REJECTED,
                report.reason(), report.platformName());
        var player = Bukkit.getPlayer(sender);
        if (player != null && participants.gameRecipients(new ChatAudience(ChatChannel.PUBLIC, Set.of())).contains(sender))
            player.sendMessage(Component.text(text, NamedTextColor.YELLOW));
        notices.publish(ChatServerMessage.publicMessage(
                        new ChatServerMessage.RichText(ChatContent.plain(text)), null, false),
                new ServerChatPublisher.Visibility(sender::equals, false));
    }
}
