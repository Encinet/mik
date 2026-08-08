package org.encinet.mik.module.player.identity;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.encinet.mik.module.geyser.BedrockPlayerBadge;
import org.encinet.mik.module.i18n.Language;

import java.util.Objects;

/** Composes one atomic player identity for viewer-localized and preview surfaces. */
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
        return compose(
                platformBadge.prefix(subject, viewer),
                nameTags.render(subject, baseName));
    }

    /** Resolves live tags with a badge translated for a known viewer language. */
    public PlayerIdentityComponent render(
            Player subject,
            Language language,
            Component baseName
    ) {
        return compose(
                platformBadge.prefix(subject, language),
                nameTags.render(subject, baseName));
    }

    /** Renders unsaved tag values without changing the live player identity. */
    public PlayerIdentityComponent renderPreview(
            Player subject,
            Audience viewer,
            Component baseName,
            PlayerNameTag preview
    ) {
        return compose(
                platformBadge.prefix(subject, viewer),
                nameTags.render(subject, baseName, preview));
    }

    private PlayerIdentityComponent compose(Component platformBadge, Component nameTag) {
        return new PlayerIdentityComponent(Component.text()
                .append(platformBadge)
                .append(nameTag)
                .build());
    }
}
