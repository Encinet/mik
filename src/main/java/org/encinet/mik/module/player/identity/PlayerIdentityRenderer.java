package org.encinet.mik.module.player.identity;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.OfflinePlayer;
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
        return render(subject, viewer, baseName, null);
    }

    /** Resolves live tags while applying identity interactions only to the name. */
    public PlayerIdentityComponent render(
            Player subject,
            Audience viewer,
            Component baseName,
            HoverEvent<?> nameHover
    ) {
        Objects.requireNonNull(subject, "subject");
        return compose(
                platformBadge.prefix(subject, viewer),
                nameTags.render(subject, hoveredName(baseName, nameHover)));
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

    /** Renders one resolved tag snapshot for either an online or offline player. */
    public PlayerIdentityComponent renderSnapshot(
            OfflinePlayer subject,
            Audience viewer,
            Component baseName,
            PlayerNameTag nameTag
    ) {
        return renderSnapshot(subject, viewer, baseName, nameTag, null);
    }

    /** Renders an offline-capable tag snapshot with name-only interactions. */
    public PlayerIdentityComponent renderSnapshot(
            OfflinePlayer subject,
            Audience viewer,
            Component baseName,
            PlayerNameTag nameTag,
            HoverEvent<?> nameHover
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(baseName, "baseName");
        Objects.requireNonNull(nameTag, "nameTag");
        return compose(
                platformBadge.prefix(subject.getUniqueId(), viewer),
                nameTags.render(subject, hoveredName(
                        baseName, nameHover), nameTag));
    }

    private Component hoveredName(
            Component baseName,
            HoverEvent<?> nameHover
    ) {
        Component name = Objects.requireNonNull(baseName, "baseName");
        return nameHover == null ? name : name.hoverEvent(nameHover);
    }

    private PlayerIdentityComponent compose(Component platformBadge, Component nameTag) {
        return new PlayerIdentityComponent(Component.text()
                .append(platformBadge)
                .append(nameTag)
                .build());
    }
}
