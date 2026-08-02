package org.encinet.mik.module.player.identity;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.module.geyser.BedrockPlayerBadge;

import java.util.Objects;

/** Composes independent player identity parts for viewer-local, global and preview surfaces. */
public final class PlayerIdentityRenderer {

    private final BedrockPlayerBadge platformBadge;
    private final PlayerNameTagRenderer nameTags;

    public PlayerIdentityRenderer(
            BedrockPlayerBadge platformBadge,
            PlayerNameTagRenderer nameTags
    ) {
        this.platformBadge = Objects.requireNonNull(platformBadge, "platformBadge");
        this.nameTags = Objects.requireNonNull(nameTags, "nameTags");
    }

    /** Resolves live tags and a badge localized for one viewer. */
    public PlayerIdentityComponent render(
            Player subject,
            Audience viewer,
            Component baseName
    ) {
        return new PlayerIdentityComponent(
                platformBadge.prefix(subject, viewer),
                nameTags.render(subject, baseName));
    }

    /** Resolves live tags for a component shared by all viewers, such as a Tab entry. */
    public PlayerIdentityComponent renderGlobal(Player subject, Component baseName) {
        return new PlayerIdentityComponent(
                platformBadge.globalPrefix(subject),
                nameTags.render(subject, baseName));
    }

    /** Renders unsaved tag values without changing the live player identity. */
    public PlayerIdentityComponent renderPreview(
            Player subject,
            Audience viewer,
            Component baseName,
            PlayerNameTag preview
    ) {
        return new PlayerIdentityComponent(
                platformBadge.prefix(subject, viewer),
                nameTags.render(subject, baseName, preview));
    }
}
