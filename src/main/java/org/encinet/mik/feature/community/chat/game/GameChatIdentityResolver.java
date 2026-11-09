package org.encinet.mik.feature.community.chat.game;

import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Reads prepared name-tag snapshots without requiring an online game connection. */
public final class GameChatIdentityResolver implements UnaryOperator<ChatSender> {
    private final PlayerNameTagRenderer names;

    public GameChatIdentityResolver(PlayerNameTagRenderer names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    @Override public ChatSender apply(ChatSender sender) {
        return snapshot(sender.minecraftId().orElseThrow(), sender.minecraftName());
    }

    public ChatSender snapshot(UUID id, String name) {
        var tag = names.current(id);
        return new ChatSender(name, Optional.of(id), Optional.empty(),
                name, tag.prefix(), tag.suffix(), tag.usernameColor().value());
    }
}
