package org.encinet.mik.feature.player.client.tip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.client.ClientMod;
import org.encinet.mik.feature.player.client.ClientVersions;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ClientTipsTest {
    // Translation grammar is covered by the shared language tests; retain actual advice components and links here.
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override
        public String t(Language language, Message message, Object... args) {
            return language.id() + ":" + message.key();
        }

        @Override
        public String format(Language language, Message message, TextArg... args) {
            return "<white>" + language.id() + ":" + Arrays.stream(args)
                    .map(arg -> arg.name() + "=" + arg.value()).collect(Collectors.joining(";")) + "</white>";
        }

        @Override
        public Component rich(Language language, Message message, NamedTextColor color, RichArg... args) {
            return Component.text(language.id() + ":", color).append(args[0].component());
        }
    };
    private final TipTestScope tips = new TipTestScope(() -> 0, LANGUAGES);
    private final AtomicReference<Optional<ClientVersions.Version>> version = new AtomicReference<>(Optional.empty());
    private final AtomicReference<Optional<Set<ClientMod>>> mods = new AtomicReference<>(Optional.empty());
    private final AtomicBoolean bedrock = new AtomicBoolean();
    private final AtomicInteger queries = new AtomicInteger();
    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("getUniqueId")) return id;
                if (method.getName().equals("hasPermission")) return true;
                throw new AssertionError(method.getName());
            });

    @BeforeEach
    void register() {
        ClientTips.register(tips, LANGUAGES,
                ignored -> { queries.incrementAndGet(); return version.get(); },
                ignored -> { queries.incrementAndGet(); return mods.get(); }, ignored -> bedrock.get());
    }

    @AfterEach
    void close() { tips.close(); }

    @Test
    void consumesDetectionSnapshotsAndCombinesLocalizedVersionAndModAdvice() {
        version.set(Optional.of(new ClientVersions.Version("1.21.4", "1.21.8", true)));
        mods.set(Optional.of(Set.of(ClientMod.AXIOM, ClientMod.PLASMO_VOICE)));
        tips.join(player);
        assertEquals(0, queries.get(), "detection remains independent and is read at delivery");
        var batch = tips.collect(player, 0, Language.FR_FR);
        assertEquals(2, batch.size());
        String text = plain(batch.getFirst());
        assertTrue(text.contains("fr_fr:"));
        assertTrue(text.contains("1.21.4"));
        assertTrue(text.contains("1.21.8"));
        assertEquals(Set.of("https://modrinth.com/mod/xaeros-minimap", "https://modrinth.com/mod/xaeros-world-map",
                        "https://modrinth.com/mod/voxy"),
                links(batch.getLast()));
    }

    @Test
    void lateInstallationAndAChangedClientVersionCanSuppressQueuedAdvice() {
        version.set(Optional.of(new ClientVersions.Version("1.21.4", "1.21.8", true)));
        mods.set(Optional.of(Set.of()));
        tips.join(player);
        version.set(Optional.of(new ClientVersions.Version("1.21.8", "1.21.8", false)));
        mods.set(Optional.of(EnumSet.allOf(ClientMod.class)));
        assertTrue(tips.collect(player, 10_000).isEmpty());
    }

    @Test
    void eachJoinRechecksVersionAndRemainingModsWithoutAMultiDayCooldown() {
        version.set(Optional.of(new ClientVersions.Version("older", "current", true)));
        mods.set(Optional.of(Set.of()));
        tips.join(player);
        var first = tips.deliver(player, 0);
        assertEquals(2, first.size());
        assertEquals(5, links(first.getLast()).size());

        tips.collect(player, 60_000);
        tips.join(player);
        var unchanged = tips.deliver(player, 60_000);
        assertEquals(2, unchanged.size());
        assertEquals(5, links(unchanged.getLast()).size());

        mods.set(Optional.of(Set.of(ClientMod.AXIOM, ClientMod.PLASMO_VOICE)));
        tips.collect(player, 120_000);
        tips.join(player);
        var partiallyInstalled = tips.deliver(player, 120_000);
        assertEquals(2, partiallyInstalled.size());
        assertEquals(Set.of("https://modrinth.com/mod/xaeros-minimap", "https://modrinth.com/mod/xaeros-world-map",
                        "https://modrinth.com/mod/voxy"),
                links(partiallyInstalled.getLast()));

        version.set(Optional.of(new ClientVersions.Version("current", "current", false)));
        mods.set(Optional.of(EnumSet.allOf(ClientMod.class)));
        tips.collect(player, 180_000);
        tips.join(player);
        assertTrue(tips.deliver(player, 180_000).isEmpty());
    }

    @Test
    void unknownDetectionProducesNoAdvice() {
        tips.join(player);
        assertTrue(tips.collect(player, 0).isEmpty());
    }

    @Test
    void knownVersionAdviceDoesNotWaitForUnknownModDetection() {
        version.set(Optional.of(new ClientVersions.Version("older", "current", true)));
        tips.join(player);

        var batch = tips.collect(player, 0);
        assertEquals(1, batch.size());
        assertTrue(plain(batch.getFirst()).contains("older"));
        assertTrue(links(batch.getFirst()).isEmpty());
    }

    @Test
    void versionNamesStayLiteralWhenTheyContainMiniMessageMarkup() {
        String name = "<click:run_command:'/example'><red>custom</red></click>";
        version.set(Optional.of(new ClientVersions.Version(name, "server <gold> { $name }", true)));
        tips.join(player);

        Component message = tips.collect(player, 10_000).getFirst();
        assertTrue(plain(message).contains(name));
        assertTrue(plain(message).contains("server <gold> { $name }"));
        assertFalse(hasClick(message), "a version label must not create actions");
    }

    @Test
    void bedrockRecipientsDoNotQueryJavaDetectors() {
        bedrock.set(true);
        tips.join(player);
        assertTrue(tips.collect(player, 10_000).isEmpty());
        assertEquals(0, queries.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"飞行速度怎么调", "飛行速度太慢了", "飛行可以快一點嗎", "飛行何以加速", "快",
            "/fly speed", "/FLYSPEED", "How can I FLY faster?", "flight speed", "speed太慢了", "quicker",
            "Wie ändere ich die Fluggeschwindigkeit?", "Kann ich schneller fliegen?",
            "¿Cómo cambio la velocidad de vuelo?", "Comment régler la vitesse de vol ?", "plus vite",
            "Come cambio la velocità di volo?", "Hoe pas ik mijn vliegsnelheid aan?",
            "Como ajustar a velocidade de voo?", "Как изменить скорость полёта?",
            "Як змінити швидкість польоту?", "飛行速度を変えたい", "もっと速く飛びたい",
            "비행 속도를 바꾸고 싶어요", "더 빠르게 날아가고 싶어요", "ปรับความเร็วในการบินอย่างไร", "บินช้าจัง"})
    void recognizesFlightAndSpeedMentionsAcrossSupportedLanguages(String text) {
        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        tips.chat(player, text);
        var batch = tips.collect(player, 0, Language.JA_JP);
        assertEquals(1, batch.size());
        assertEquals("ja_jp:client-tip-axiom-flight-speed-mm", plain(batch.getFirst()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "大家好", "快乐", "快递到了", "butterfly", "firefly", "breakfast", "speedrun",
            "volunteer", "flightplan", "สวัสดีตอนเช้า", "ช้าง", "https://example.net/fly", "https://example.net/飞行速度",
            "www.example.net/speed", "https://example.net/быстро"})
    void ignoresUnrelatedWordsAndLinkedKeywords(String text) {
        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        tips.chat(player, text);
        assertTrue(tips.collect(player, 0).isEmpty());
    }

    @Test
    void flightChatSelectsDetectedOrUndetectedAxiomAdviceAndRechecksBeforeDelivery() {
        tips.chat(player, "飞行速度");
        assertTrue(tips.collect(player, 0).isEmpty(), "unknown detection cannot enable Axiom advice");
        mods.set(Optional.of(Set.of(ClientMod.PLASMO_VOICE)));
        tips.chat(player, "飞行速度");
        var introduction = tips.collect(player, 0);
        assertEquals(1, introduction.size());
        assertEquals(Set.of("https://modrinth.com/mod/axiom"), links(introduction.getFirst()));

        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        tips.chat(player, "飞行速度");
        tips.flush();
        mods.set(Optional.empty());
        assertTrue(tips.collect(player, 0).isEmpty(), "delivery must recheck the current connection's signals");
    }

    @Test
    void axiomIntroductionJoinsTheAmbientPoolOnlyWhileUndetected() {
        mods.set(Optional.of(Set.of(ClientMod.VOXY)));
        assertTrue(tips.collect(player, 0).isEmpty());
        var introduction = tips.collect(player, 15 * 60_000);
        assertEquals(1, introduction.size());
        assertEquals(Set.of("https://modrinth.com/mod/axiom"), links(introduction.getFirst()));

        mods.set(Optional.of(Set.of(ClientMod.AXIOM, ClientMod.VOXY)));
        assertTrue(tips.collect(player, 30 * 60_000).isEmpty());
        mods.set(Optional.empty());
        assertTrue(tips.collect(player, 45 * 60_000).isEmpty());
        mods.set(Optional.of(Set.of(ClientMod.VOXY)));
        assertEquals(1, tips.collect(player, 60 * 60_000).size());
    }

    @Test
    void voxyIntroductionAppearsOnlyWhileUndetectedAndUsesTheCurrentLanguage() {
        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        assertTrue(tips.collect(player, 0).isEmpty());
        var introduction = tips.collect(player, 15 * 60_000, Language.FR_FR);
        assertEquals(1, introduction.size());
        assertEquals("fr_fr:Voxy", plain(introduction.getFirst()));
        assertEquals(Set.of("https://modrinth.com/mod/voxy"), links(introduction.getFirst()));

        mods.set(Optional.of(Set.of(ClientMod.AXIOM, ClientMod.VOXY)));
        assertTrue(tips.collect(player, 30 * 60_000).isEmpty());
        mods.set(Optional.empty());
        assertTrue(tips.collect(player, 45 * 60_000).isEmpty());
        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        assertEquals(1, tips.collect(player, 60 * 60_000).size());
    }

    @Test
    void knownVoxyIsRemovedFromTheJoinRecommendationsAndBedrockGetsNoAmbientAdvice() {
        mods.set(Optional.of(Set.of(ClientMod.VOXY)));
        tips.join(player);
        var recommendations = tips.collect(player, 0);
        assertEquals(1, recommendations.size());
        assertFalse(links(recommendations.getFirst()).contains("https://modrinth.com/mod/voxy"));
        assertEquals(4, links(recommendations.getFirst()).size());

        bedrock.set(true);
        queries.set(0);
        tips.collect(player, 1);
        assertTrue(tips.collect(player, 15 * 60_000 + 1).isEmpty());
        assertEquals(0, queries.get());
    }

    @Test
    void lateAxiomEvidenceCancelsAnAlreadyQueuedIntroduction() {
        mods.set(Optional.of(Set.of()));
        tips.chat(player, "飞行速度");
        tips.flush();
        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        assertTrue(tips.collect(player, 0).isEmpty());
    }

    @Test
    void bedrockChatDoesNotQueryJavaModDetection() {
        bedrock.set(true);
        mods.set(Optional.of(Set.of(ClientMod.AXIOM)));
        tips.chat(player, "fly faster");
        assertTrue(tips.collect(player, 0).isEmpty());
        assertEquals(0, queries.get());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static boolean hasClick(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(ClientTipsTest::hasClick);
    }

    private static Set<String> links(Component component) {
        Set<String> result = new HashSet<>();
        if (component.clickEvent() != null && component.clickEvent().action() == ClickEvent.Action.OPEN_URL
                && component.clickEvent().payload() instanceof ClickEvent.Payload.Text text) {
            result.add(text.value());
        }
        component.children().forEach(child -> result.addAll(links(child)));
        return result;
    }
}
