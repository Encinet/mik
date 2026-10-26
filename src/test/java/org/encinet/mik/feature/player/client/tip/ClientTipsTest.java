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

import java.lang.reflect.Proxy;
import java.time.Duration;
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
        var batch = tips.collect(player, 10_000, Language.FR_FR);
        assertEquals(2, batch.size());
        String text = plain(batch.getFirst());
        assertTrue(text.contains("fr_fr:"));
        assertTrue(text.contains("1.21.4"));
        assertTrue(text.contains("1.21.8"));
        assertEquals(Set.of("https://modrinth.com/mod/xaeros-minimap", "https://modrinth.com/mod/xaeros-world-map"),
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
    void partialInstallationDoesNotRestartTheRecommendationsCooldown() {
        mods.set(Optional.of(Set.of()));
        tips.join(player);
        assertEquals(4, links(tips.deliver(player, 10_000).getFirst()).size());

        mods.set(Optional.of(Set.of(ClientMod.AXIOM, ClientMod.PLASMO_VOICE)));
        tips.collect(player, 60_000);
        tips.join(player);
        assertTrue(tips.deliver(player, 70_000).isEmpty());

        long later = Duration.ofDays(7).toMillis() + 10_000;
        tips.collect(player, later);
        tips.join(player);
        assertEquals(2, links(tips.deliver(player, later + 10_000).getFirst()).size());
    }

    @Test
    void unknownDetectionProducesNoAdvice() {
        tips.join(player);
        assertTrue(tips.collect(player, 10_000).isEmpty());
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
