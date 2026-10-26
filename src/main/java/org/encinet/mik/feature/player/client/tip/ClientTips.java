package org.encinet.mik.feature.player.client.tip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.player.PlayerJoinEvent;
import org.encinet.mik.feature.player.client.ClientMod;
import org.encinet.mik.feature.player.client.ClientMods;
import org.encinet.mik.feature.player.client.ClientVersions;
import org.encinet.mik.feature.player.guidance.TipContent;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.guidance.TipTrigger;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Stateless client guidance consuming the same detection capabilities as other features. */
public final class ClientTips {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<Recommendation> RECOMMENDED = List.of(
            new Recommendation(ClientMod.AXIOM, "Axiom", "https://modrinth.com/mod/axiom"),
            new Recommendation(ClientMod.PLASMO_VOICE, "Plasmo Voice", "https://modrinth.com/mod/plasmo-voice"),
            new Recommendation(ClientMod.XAERO_MINIMAP, "Xaero's Minimap", "https://modrinth.com/mod/xaeros-minimap"),
            new Recommendation(ClientMod.XAERO_WORLD_MAP, "Xaero's World Map", "https://modrinth.com/mod/xaeros-world-map"));
    // Partial installation changes the advice text, not the identity of the recommended list.
    private static final String MOD_SITUATION = RECOMMENDED.stream().map(item -> item.mod.name()).sorted()
            .collect(Collectors.joining(","));

    private ClientTips() {}

    public static void register(TipScope tips, LanguageService languages, ClientVersions versions, ClientMods mods,
                                Predicate<UUID> bedrockPlayer) {
        tips.<Void>define("client-version")
                .group("client-setup").priority(60).interval(Duration.ofDays(7))
                .delay(Duration.ofSeconds(10)).lifetime(Duration.ofSeconds(45))
                .on(TipTrigger.event(PlayerJoinEvent.class))
                .when(player -> !bedrockPlayer.test(player.getUniqueId()))
                .resolve((player, ignored, language) -> versions.version(player).filter(ClientVersions.Version::outdated)
                        .map(version -> versionAdvice(languages, language, version)))
                .build();
        tips.<Void>define("client-mods")
                .group("client-setup").priority(30).interval(Duration.ofDays(7))
                .delay(Duration.ofSeconds(10)).lifetime(Duration.ofSeconds(45))
                .on(TipTrigger.event(PlayerJoinEvent.class))
                .when(player -> !bedrockPlayer.test(player.getUniqueId()))
                .resolve((player, ignored, language) -> mods.detected(player)
                        .flatMap(present -> modAdvice(languages, language, present)))
                .build();
    }

    private static TipContent versionAdvice(LanguageService languages, Language language, ClientVersions.Version version) {
        return new TipContent(version.serverVersion() + ":" + version.name(),
                MINI_MESSAGE.deserialize(languages.format(language, Message.CLIENT_VERSION_BEHIND_MM,
                        TextArg.of("clientVersion", MINI_MESSAGE.escapeTags(version.name())),
                        TextArg.of("serverVersion", MINI_MESSAGE.escapeTags(version.serverVersion())))));
    }

    private static Optional<TipContent> modAdvice(LanguageService languages, Language language, Set<ClientMod> detected) {
        var missing = RECOMMENDED.stream().filter(item -> !detected.contains(item.mod)).toList();
        if (missing.isEmpty()) return Optional.empty();
        Component links = Component.join(JoinConfiguration.separator(Component.text(" · ", NamedTextColor.WHITE)),
                missing.stream().map(item -> Component.text(item.name, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(item.url))).toList());
        return Optional.of(new TipContent(MOD_SITUATION, languages.rich(language,
                Message.CLIENT_MODS_UNDETECTED_RICH, NamedTextColor.WHITE, RichArg.component("mods", links))));
    }

    private record Recommendation(ClientMod mod, String name, String url) {}
}
