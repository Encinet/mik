package org.encinet.mik.feature.player.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BackModuleTest {
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override
        public Component text(Language language, Message message, TextColor color, Object... args) {
            return Component.text(message.key(), color);
        }
    };
    private final World world = world();
    private final AtomicReference<Location> position = new AtomicReference<>(new Location(world, 1, 64, 1));
    private final UUID id = UUID.randomUUID();
    private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
            new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getLocation" -> position.get().clone();
                default -> throw new AssertionError(method.getName());
            });

    @Test
    void teleportSamplesBothRemindersIndependentlyAndUsesClickableCommands() {
        TipTestScope tips = new TipTestScope();
        BackModule module = new BackModule(null, LANGUAGES, tips);
        teleport(module, tips, new Location(world, 100, 70, 100));

        var batch = tips.collect(player, 2000);
        assertEquals(List.of("back-tip-return-mm", "back-tip-undo-mm"), batch.stream().map(BackModuleTest::plain).toList());
        assertEquals(ClickEvent.suggestCommand("/back"), batch.getFirst().clickEvent());
        assertEquals(ClickEvent.suggestCommand("/reback"), batch.getLast().clickEvent());
    }

    @Test
    void probabilityBoundaryDoesNotQueueTheUnselectedReminder() {
        AtomicInteger draws = new AtomicInteger();
        TipTestScope tips = new TipTestScope(() -> draws.getAndIncrement() == 0 ? 0.099 : 0.1);
        BackModule module = new BackModule(null, LANGUAGES, tips);
        teleport(module, tips, new Location(world, 100, 70, 100));

        assertEquals(List.of("back-tip-return-mm"), tips.collect(player, 2000).stream().map(BackModuleTest::plain).toList());
        assertEquals(2, draws.get());
    }

    @Test
    void rotationAndMovementWithinTheSameBlockDoNotSampleTips() {
        TipTestScope tips = new TipTestScope(() -> { throw new AssertionError("No location change"); });
        BackModule module = new BackModule(null, LANGUAGES, tips);
        teleport(module, tips, new Location(world, 1.8, 64.5, 1.8, 90, 45));

        assertTrue(tips.collect(player, 2000).isEmpty());
    }

    @Test
    void locationAndHistoryAreRecheckedAfterTheCandidateWasQueued() {
        TipTestScope tips = new TipTestScope();
        BackModule module = new BackModule(null, LANGUAGES, tips);
        Location origin = position.get().clone();
        teleport(module, tips, new Location(world, 100, 70, 100));
        position.set(origin);
        assertTrue(tips.collect(player, 2000).isEmpty(), "a player already at the saved location needs no return tip");

        teleport(module, tips, new Location(world, 100, 70, 100));
        module.onPlayerQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(tips.collect(player, 4000).isEmpty(), "session history must be cleared on quit");
    }

    @Test
    void changingWorldAtTheSameCoordinatesStillCreatesAnOpportunity() {
        TipTestScope tips = new TipTestScope();
        BackModule module = new BackModule(null, LANGUAGES, tips);
        teleport(module, tips, new Location(world(), 1, 64, 1));
        assertEquals(2, tips.collect(player, 2000).size());
    }

    @Test
    void crossingZeroUsesBlockFloorsRatherThanTruncatedCoordinates() {
        TipTestScope tips = new TipTestScope();
        BackModule module = new BackModule(null, LANGUAGES, tips);
        position.set(new Location(world, -0.2, 64, -0.2));
        teleport(module, tips, new Location(world, 0.2, 64, 0.2));
        assertEquals(2, tips.collect(player, 2000).size());
    }

    private void teleport(BackModule module, TipTestScope tips, Location destination) {
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, position.get().clone(), destination,
                PlayerTeleportEvent.TeleportCause.COMMAND);
        // Guidance subscribes first; predicates must wait until the feature has updated its history.
        tips.teleport(event);
        module.onPlayerTeleport(event);
        position.set(destination);
        tips.flush();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static World world() {
        UUID id = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUID" -> id;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> id.hashCode();
                    default -> throw new AssertionError(method.getName());
                });
    }
}
