package org.encinet.mik.feature.player.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeleportPreferenceModuleTest {
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override
        public String t(Player player, Message message, Object... args) {
            return message.key();
        }

        @Override
        public Component rich(Player player, Message message, NamedTextColor color, RichArg... args) {
            return Component.text(message.key(), color);
        }
    };

    @ParameterizedTest
    @EnumSource(value = PlayerRole.class, names = {"MEMBER", "MODERATOR", "CUSTODIAN"})
    void denialAppliesToEveryRoleAndCommandAlias(PlayerRole role) throws Exception {
        try (Fixture fixture = new Fixture(role, TeleportPolicy.ALWAYS_DENY, false, false)) {
            for (String alias : List.of("tp", "teleport", "minecraft:tp", "minecraft:teleport")) {
                assertTrue(fixture.command(alias).isCancelled(), alias);
            }
            assertEquals(List.of("teleport-denied", "teleport-denied", "teleport-denied", "teleport-denied"),
                    fixture.sender.messages);
            assertTrue(fixture.target.messages.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlayerRole.class, names = {"MEMBER", "MODERATOR", "CUSTODIAN"})
    void consentIsRequestedForEveryRole(PlayerRole role) throws Exception {
        try (Fixture fixture = new Fixture(role, TeleportPolicy.REQUIRE_CONSENT, false, false)) {
            assertTrue(fixture.command("tp").isCancelled());
            assertEquals(List.of("teleport-request-sent"), fixture.sender.messages);
            assertEquals(1, fixture.target.messages.size());
            assertTrue(fixture.target.messages.getFirst().contains("teleport-request-received"));
            assertTrue(fixture.target.messages.getFirst().contains("teleport-request-accept"));
            assertTrue(fixture.target.messages.getFirst().contains("teleport-request-deny-action"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlayerRole.class, names = {"MEMBER", "MODERATOR", "CUSTODIAN"})
    void allowedTeleportsStillNotifyTheTarget(PlayerRole role) throws Exception {
        try (Fixture fixture = new Fixture(role, TeleportPolicy.ALWAYS_ALLOW, false, false)) {
            assertFalse(fixture.command("tp").isCancelled());
            fixture.module.onPlayerTeleport(new PlayerTeleportEvent(fixture.target.player,
                    new Location(null, 0, 64, 0), new Location(null, 10, 70, 10),
                    PlayerTeleportEvent.TeleportCause.COMMAND));
            assertEquals(List.of("teleport-moved-here"), fixture.target.messages);
            assertTrue(fixture.sender.messages.isEmpty());
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlayerRole.class, names = {"MEMBER", "MODERATOR", "CUSTODIAN"})
    void afkBlockingRequiresBothThePreferenceAndAfkStatusForEveryRole(PlayerRole role) throws Exception {
        for (boolean blockWhileAfk : List.of(false, true)) {
            for (boolean afk : List.of(false, true)) {
                try (Fixture fixture = new Fixture(role, TeleportPolicy.ALWAYS_ALLOW, blockWhileAfk, afk)) {
                    boolean denied = blockWhileAfk && afk;
                    assertEquals(denied, fixture.command("tp").isCancelled());
                    assertEquals(denied ? List.of("teleport-denied") : List.of(), fixture.sender.messages);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = PlayerRole.class, names = {"MEMBER", "MODERATOR", "CUSTODIAN"})
    void bareEntitySelectorIsBlockedBeforeConsentOrRoleExemptions(PlayerRole role) throws Exception {
        try (Fixture fixture = new Fixture(role, TeleportPolicy.REQUIRE_CONSENT, false, false)) {
            for (String alias : List.of("tp", "teleport", "minecraft:tp", "minecraft:teleport")) {
                for (String arguments : List.of("@e Sender", "Target @e", "@e",
                        "Target ~ ~ ~ facing entity @e eyes", "@e\t0 64 0")) {
                    assertTrue(fixture.rawCommand("/" + alias + " " + arguments).isCancelled());
                }
            }
            assertEquals(20, fixture.sender.messages.size());
            assertTrue(fixture.sender.messages.stream().allMatch(Message.TELEPORT_BARE_ENTITY_SELECTOR.key()::equals));
            assertTrue(fixture.target.messages.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"@a 0 64 0", "@r Sender", "@p Sender", "@n Sender", "@s 0 64 0",
            "@e[type=minecraft:zombie] Sender", "@e[] Sender", "@e[name=\"look @e\"] Sender"})
    void otherSelectorsAndExplicitBracketsPassTeleportSafety(String arguments) throws Exception {
        try (Fixture fixture = new Fixture(PlayerRole.MODERATOR, TeleportPolicy.ALWAYS_ALLOW, false, false)) {
            for (String alias : List.of("tp", "teleport", "minecraft:tp", "minecraft:teleport")) {
                assertFalse(fixture.rawCommand("/" + alias + " " + arguments).isCancelled());
            }
            assertTrue(fixture.sender.messages.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/msg Sender @e", "/public @e", "/give @e stone"})
    void teleportSafetyDoesNotInterceptOtherCommands(String command) throws Exception {
        try (Fixture fixture = new Fixture(PlayerRole.MODERATOR, TeleportPolicy.ALWAYS_ALLOW, false, false)) {
            assertFalse(fixture.rawCommand(command).isCancelled());
            assertTrue(fixture.sender.messages.isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Server previousServer;
        private final TestPlayer sender;
        private final TestPlayer target = new TestPlayer("Target", PlayerRole.DEFAULT);
        private final TeleportPreferenceModule module;

        Fixture(PlayerRole role, TeleportPolicy policy, boolean blockWhileAfk, boolean afk) throws Exception {
            sender = new TestPlayer("Sender", role);
            AfkService afkService = (AfkService) Proxy.newProxyInstance(AfkService.class.getClassLoader(),
                    new Class<?>[]{AfkService.class}, (proxy, method, args) -> {
                        if (method.getName().equals("isAfk")) return afk;
                        throw new AssertionError(method.getName());
                    });
            module = new TeleportPreferenceModule(null, afkService, LANGUAGES);
            YamlConfiguration settings = new YamlConfiguration();
            settings.set(target.id + ".teleport-policy", policy.id());
            settings.set(target.id + ".block-teleports-while-afk", blockWhileAfk);
            Field settingsField = TeleportPreferenceModule.class.getDeclaredField("settingsData");
            settingsField.setAccessible(true);
            settingsField.set(module, settings);

            BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(
                    BukkitScheduler.class.getClassLoader(), new Class<?>[]{BukkitScheduler.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("runTask") || method.getName().equals("runTaskLater")) return null;
                        throw new AssertionError(method.getName());
                    });
            Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(),
                    new Class<?>[]{Server.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getPlayerExact" -> target.player;
                        case "getScheduler" -> scheduler;
                        default -> throw new AssertionError(method.getName());
                    });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = Bukkit.getServer();
            serverField.set(null, server);
        }

        PlayerCommandPreprocessEvent command(String alias) {
            return rawCommand("/" + alias + " Target Sender");
        }

        PlayerCommandPreprocessEvent rawCommand(String command) {
            var event = new PlayerCommandPreprocessEvent(sender.player, command, Set.of());
            module.onPlayerCommand(event);
            return event;
        }

        @Override
        public void close() throws IllegalAccessException {
            serverField.set(null, previousServer);
        }
    }

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final List<String> messages = new ArrayList<>();
        private final Player player;

        TestPlayer(String name, PlayerRole role) {
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "displayName" -> Component.text(name);
                        case "hasPermission" -> role.permission().equals(args[0]);
                        case "isOp" -> role == PlayerRole.CUSTODIAN;
                        case "equals" -> proxy == args[0];
                        case "sendMessage", "sendActionBar" -> {
                            messages.add(PlainTextComponentSerializer.plainText().serialize((Component) args[0]));
                            yield null;
                        }
                        default -> throw new AssertionError(method.getName());
                    });
        }
    }
}
