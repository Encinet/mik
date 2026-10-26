package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TipTest {
    @Test
    void ambientDefinitionsNeedNoEventAndManualDefinitionsRemainManual() {
        try (TipTestScope tips = new TipTestScope()) {
            tips.define("manual").resolve((player, context, language) -> {
                fail("manual definitions must not join the random pool implicitly");
                return Optional.empty();
            }).build();
            AtomicBoolean relevant = new AtomicBoolean(false);
            tips.define("daily", Message.IRON_ELEVATOR_TIP_MM).ambient().when(player -> relevant.get()).build();
            Player player = player();

            assertTrue(tips.collect(player, 0).isEmpty());
            assertTrue(tips.collect(player, 15 * 60_000).isEmpty());
            relevant.set(true);
            assertTrue(tips.collect(player, 15 * 60_000 + 1000).isEmpty());
            assertEquals("iron-elevator-tip-mm", plain(tips.deliver(player, 30 * 60_000).getFirst()));
            assertTrue(tips.collect(player, 45 * 60_000).isEmpty());
        }
    }

    @Test
    void registeredScenarioKeepsEachPlayersTypedContextSeparate() {
        TipTestScope binding = new TipTestScope();
        Tip<String> homes = binding.<String>define("home-return")
                .resolve((player, name, language) -> Optional.of(new TipContent("return", Component.text(name))))
                .build();
        Player first = player();
        Player second = player();
        homes.offer(first, "west-home");
        homes.offer(second, "east-home");

        assertTrue(binding.collect(first, 1000).isEmpty());
        assertEquals("west-home", plain(binding.collect(first, 2000).getFirst()));
        assertEquals("east-home", plain(binding.collect(second, 2000).getFirst()));
    }

    @Test
    void resolverReceivesDeliveryLanguageTogetherWithTheOriginalContext() {
        TipTestScope binding = new TipTestScope();
        Tip<String> scene = binding.<String>define("localized")
                .resolve((player, name, language) -> Optional.of(
                        new TipContent("v1", Component.text(language.id() + ":" + name))))
                .build();
        Player player = player();
        scene.offer(player, "west-home");

        assertTrue(binding.collect(player, 1000, Language.EN_US).isEmpty());
        assertEquals("fr_fr:west-home", plain(binding.collect(player, 2000, Language.FR_FR).getFirst()));
    }

    @Test
    void registeredResolverRechecksModuleStateAndCancellationDropsPendingOffers() {
        TipTestScope binding = new TipTestScope();
        AtomicBoolean available = new AtomicBoolean(true);
        Tip<Void> scene = binding.<Void>define("controls")
                .resolve((player, ignored, language) -> available.get()
                        ? Optional.of(new TipContent("v1", Component.text("controls"))) : Optional.empty())
                .build();
        Player player = player();
        scene.offer(player);
        available.set(false);
        assertTrue(binding.collect(player, 2000).isEmpty());

        available.set(true);
        scene.offer(player);
        scene.cancel(player.getUniqueId());
        assertTrue(binding.collect(player, 4000).isEmpty());
    }

    @Test
    void invalidConfigurationDoesNotReserveTheScenarioId() {
        TipTestScope binding = new TipTestScope();
        assertThrows(IllegalArgumentException.class, () -> binding.<Void>define("controls")
                .delay(Duration.ofSeconds(20)).lifetime(Duration.ofSeconds(20))
                .resolve((player, ignored, language) -> Optional.empty()).build());
        assertThrows(NullPointerException.class, () -> binding.<Void>define("controls").build());
        assertDoesNotThrow(() -> binding.<Void>define("controls")
                .resolve((player, ignored, language) -> Optional.empty()).build());
    }

    @Test
    void randomTriggersSampleOnceAndOnlyAcceptedEventsReachTheResolver() {
        AtomicReference<Double> roll = new AtomicReference<>(0.1);
        AtomicInteger draws = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        TipTestScope scope = new TipTestScope(() -> {
            draws.incrementAndGet();
            return roll.get();
        });
        Tip<String> scene = scope.<String>define("random-context")
                .resolve((player, context, language) -> {
                    evaluations.incrementAndGet();
                    return Optional.of(new TipContent("v1", Component.text(context)));
                }).build();
        Player player = player();

        scene.offerRandomly(player, "skipped", 0);
        scene.offerRandomly(player, "boundary", 0.1);
        assertTrue(scope.collect(player, 1000).isEmpty());
        assertEquals(1, draws.get());
        assertEquals(0, evaluations.get());

        roll.set(0.099);
        scene.offerRandomly(player, "selected", 0.1);
        assertTrue(scope.collect(player, 2000).isEmpty());
        roll.set(0.5);
        scene.offerRandomly(player, "skipped-refresh", 0.1);
        assertEquals("selected", plain(scope.collect(player, 3000).getFirst()));
        assertEquals(3, draws.get(), "collecting a waiting scene must not reroll the chance");
        assertEquals(1, evaluations.get());

        scene.offerRandomly(player, "certain", 1);
        assertEquals("certain", plain(scope.collect(player, 5000).getFirst()));
        assertEquals(3, draws.get());
    }

    @Test
    void invalidTriggerProbabilitiesAreRejectedWithoutPublishing() {
        TipTestScope scope = new TipTestScope();
        Tip<Void> scene = scope.<Void>define("random-validation")
                .resolve((player, ignored, language) -> { fail("invalid chance was queued"); return Optional.empty(); })
                .build();
        Player player = player();
        for (double chance : new double[]{-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> scene.offerRandomly(player, chance));
            assertThrows(IllegalArgumentException.class, () -> scope.define("invalid-auto", Message.HOME_TIP_CREATE_MM)
                    .on(TipTriggers.teleport(chance)).build());
        }
        assertTrue(scope.collect(player, 2000).isEmpty());
    }

    @Test
    void messageDefinitionsCanUseTypedContextWithoutAContentWrapper() {
        TipTestScope scope = new TipTestScope();
        Tip<String> scene = scope.<String>define("typed-message", Message.HOME_TIP_RETURN_MM)
                .when((player, name) -> name.equals("available"))
                .suggestCommand("/home").build();
        Player player = player();
        scene.offer(player, "deleted");
        assertTrue(scope.collect(player, 2000).isEmpty());
        scene.offer(player, "available");
        assertEquals("home-tip-return-mm", plain(scope.collect(player, 4000).getFirst()));
    }

    private static Player player() {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static String plain(Component message) {
        return PlainTextComponentSerializer.plainText().serialize(message);
    }

}
