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
import org.encinet.mik.feature.player.guidance.TipTriggers;
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
import java.util.regex.Pattern;

/** Stateless client guidance consuming the same detection capabilities as other features. */
public final class ClientTips {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern FLIGHT_SPEED_MENTION = Pattern.compile(
            "[飞飛](?:行|翔|得|太|快|慢|速)|速度|太快|太慢|(?<![\\p{L}\\p{N}_])快(?![\\p{L}\\p{N}_])"
                    + "|飛[ぶびべん]|スピード|速[いく]|遅[いく]"
                    + "|비행|날기|날아|날고|속도|빠르|빠른|빠르게|느리|느린|느리게"
                    + "|บิน|ความเร็ว|เร็ว"
                    + "|(?<![\\p{IsLatin}\\p{IsCyrillic}\\p{M}\\p{N}_])(?:"
                    + "fly(?:ing)?|flight|flyspeed|speed|fast(?:er|est)?|quick(?:er|ly)?|slow(?:er|est)?"
                    + "|fliegen|fliege|fliegst|fliegt|flug(?:geschwindigkeit)?|geschwindigkeit|schnell(?:er)?|langsam(?:er)?"
                    + "|volar|vuelo|volando|velocidad|r[áa]pid[oa]s?|lent[oa]s?"
                    + "|voler|vol|vitesse|vite|rapide(?:ment)?|lentement"
                    + "|volare|volo|velocità|veloc[ei]"
                    + "|vlieg(?:snelheid)?|vlucht|snelheid|snel(?:ler)?|langzaam"
                    + "|voar|voo|voando|velocidade"
                    + "|пол[её]т[\\p{IsCyrillic}]*|летать|летаю|скорост(?:ь|и|ью)|быстр(?:о|ее)|медленн(?:о|ее)"
                    + "|політ|польот(?:у|ів|и|ом)|літати|літаю|швидк(?:ість|ості|о)|швидше|повільн(?:о|іше)"
                    + ")(?![\\p{IsLatin}\\p{IsCyrillic}\\p{M}\\p{N}_])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Recommendation AXIOM_RECOMMENDATION =
            new Recommendation(ClientMod.AXIOM, "Axiom", "https://modrinth.com/mod/axiom");
    private static final Recommendation VOXY_RECOMMENDATION =
            new Recommendation(ClientMod.VOXY, "Voxy", "https://modrinth.com/mod/voxy");
    private static final List<Recommendation> RECOMMENDED = List.of(
            AXIOM_RECOMMENDATION,
            new Recommendation(ClientMod.PLASMO_VOICE, "Plasmo Voice", "https://modrinth.com/mod/plasmo-voice"),
            new Recommendation(ClientMod.XAERO_MINIMAP, "Xaero's Minimap", "https://modrinth.com/mod/xaeros-minimap"),
            new Recommendation(ClientMod.XAERO_WORLD_MAP, "Xaero's World Map", "https://modrinth.com/mod/xaeros-world-map"),
            VOXY_RECOMMENDATION);

    private ClientTips() {}

    public static void register(TipScope tips, LanguageService languages, ClientVersions versions, ClientMods mods,
                                Predicate<UUID> bedrockPlayer) {
        ClientModTips modTips = new ClientModTips(tips, mods, bedrockPlayer);
        tips.<Void>define("client-version")
                .group("client-setup").priority(60).withoutHistory()
                .lifetime(Duration.ofSeconds(45))
                .on(TipTrigger.event(PlayerJoinEvent.class))
                .when(player -> !bedrockPlayer.test(player.getUniqueId()))
                .resolve((player, ignored, language) -> versions.version(player).filter(ClientVersions.Version::outdated)
                        .map(version -> versionAdvice(languages, language, version)))
                .build();
        modTips.define("client-mods", (detected, language) -> modAdvice(languages, language, detected))
                .group("client-setup").priority(30).withoutHistory()
                .lifetime(Duration.ofSeconds(45))
                .on(TipTrigger.event(PlayerJoinEvent.class))
                .build();
        modTips.whenDetected(ClientMod.AXIOM)
                .define("axiom-flight-speed", Message.CLIENT_TIP_AXIOM_FLIGHT_SPEED_MM)
                .on(TipTriggers.chatMentions(FLIGHT_SPEED_MENTION))
                .build();
        modTips.whenUndetected(ClientMod.AXIOM)
                .define("axiom-intro", (detected, language) -> Optional.of(new TipContent("axiom-intro",
                        languages.rich(language, Message.CLIENT_TIP_AXIOM_INTRO_RICH, NamedTextColor.WHITE,
                                RichArg.component("mod", modLink(AXIOM_RECOMMENDATION))))))
                .ambient()
                .on(TipTriggers.chatMentions(FLIGHT_SPEED_MENTION))
                .build();
        modTips.whenUndetected(ClientMod.VOXY)
                .define("voxy-intro", (detected, language) -> Optional.of(new TipContent("voxy-intro",
                        languages.rich(language, Message.CLIENT_TIP_VOXY_INTRO_RICH, NamedTextColor.WHITE,
                                RichArg.component("mod", modLink(VOXY_RECOMMENDATION))))))
                .ambient()
                .build();
    }

    private static TipContent versionAdvice(LanguageService languages, Language language, ClientVersions.Version version) {
        return new TipContent("client-version",
                MINI_MESSAGE.deserialize(languages.format(language, Message.CLIENT_VERSION_BEHIND_MM,
                        TextArg.of("clientVersion", MINI_MESSAGE.escapeTags(version.name())),
                        TextArg.of("serverVersion", MINI_MESSAGE.escapeTags(version.serverVersion())))));
    }

    private static Optional<TipContent> modAdvice(LanguageService languages, Language language, Set<ClientMod> detected) {
        var missing = RECOMMENDED.stream().filter(item -> !detected.contains(item.mod)).toList();
        if (missing.isEmpty()) return Optional.empty();
        Component links = Component.join(JoinConfiguration.separator(Component.text(" · ", NamedTextColor.WHITE)),
                missing.stream().map(ClientTips::modLink).toList());
        return Optional.of(new TipContent("client-mods", languages.rich(language,
                Message.CLIENT_MODS_UNDETECTED_RICH, NamedTextColor.WHITE, RichArg.component("mods", links))));
    }

    private static Component modLink(Recommendation item) {
        return Component.text(item.name, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(item.url));
    }

    private record Recommendation(ClientMod mod, String name, String url) {}
}
