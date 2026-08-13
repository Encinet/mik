package org.encinet.mik.module.geyser;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Objects;
import java.util.UUID;

/** Shared visual identity for Bedrock players across every player-name surface. */
public final class BedrockPlayerBadge {

    private final BedrockPlayerDetector detector;
    private final LanguageService languageService;

    public BedrockPlayerBadge(
            BedrockPlayerDetector detector,
            LanguageService languageService
    ) {
        this.detector = Objects.requireNonNull(detector, "detector");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    /** A viewer-localized badge for chat and other viewer-specific output. */
    public Component prefix(Player subject, Audience viewer) {
        Objects.requireNonNull(subject, "subject");
        return prefix(subject.getUniqueId(), viewer);
    }

    /** A viewer-localized badge that also works for an offline player UUID. */
    public Component prefix(UUID subjectId, Audience viewer) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(viewer, "viewer");
        Language language = viewer instanceof Player player
                ? languageService.language(player)
                : Language.DEFAULT;
        return prefix(subjectId, language);
    }

    /** A badge translated for a known viewer language. */
    public Component prefix(Player subject, Language language) {
        Objects.requireNonNull(subject, "subject");
        return prefix(subject.getUniqueId(), language);
    }

    /** A known-language badge that also works for an offline player UUID. */
    public Component prefix(UUID subjectId, Language language) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(language, "language");
        return render(
                detector.isBedrockPlayer(subjectId),
                languageService.t(language, Message.PLAYER_BEDROCK_LABEL));
    }

    static Component render(boolean bedrockPlayer, String label) {
        return bedrockPlayer && label != null && !label.isBlank()
                ? Component.text("[" + label + "] ", NamedTextColor.AQUA)
                : Component.empty();
    }
}
