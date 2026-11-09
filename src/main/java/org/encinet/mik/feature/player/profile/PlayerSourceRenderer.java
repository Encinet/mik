package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.integration.geyser.BedrockPlayerDetector;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.util.Objects;

/** Resolves source labels from connection facts; surfaces choose their placement. */
public final class PlayerSourceRenderer {
    private final BedrockPlayerDetector bedrock;
    private final LanguageService language;

    public PlayerSourceRenderer(BedrockPlayerDetector bedrock, LanguageService language) {
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.language = Objects.requireNonNull(language, "language");
    }

    public PlayerSource game(Player subject, Audience viewer) {
        return game(subject, viewerLanguage(viewer));
    }

    public PlayerSource game(Player subject, Language viewerLanguage) {
        return bedrock.isBedrockPlayer(subject.getUniqueId())
                ? new PlayerSource(language.t(viewerLanguage, Message.PLAYER_BEDROCK_LABEL)) : PlayerSource.NONE;
    }

    public PlayerSource web(Audience viewer) {
        return new PlayerSource(language.t(viewerLanguage(viewer), Message.PLAYER_WEB_LABEL), NamedTextColor.GREEN);
    }

    public PlayerSource webTabList(Audience viewer, int connections) {
        return new PlayerSource(language.t(viewerLanguage(viewer), Message.WEBCHAT_TAB_LABEL, connections), NamedTextColor.GREEN);
    }

    private Language viewerLanguage(Audience viewer) {
        return viewer instanceof Player player ? language.language(player) : Language.DEFAULT;
    }
}
