package org.encinet.mik.feature.player.pvp;

import com.google.common.base.Function;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PvpProtectionTipTest {
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override
        public String t(Player player, Message message, Object... args) { return message.key(); }
    };

    @ParameterizedTest
    @MethodSource("gameModes")
    void onlySurvivalAndAdventureParticipantsReceiveTheTip(GameMode attackerMode, GameMode victimMode) {
        try (Fixture fixture = new Fixture()) {
            fixture.attacker.mode = attackerMode;
            fixture.victim.mode = victimMode;
            assertTrue(fixture.attack().isCancelled(), "PVP protection remains in force in every game mode");

            int expected = vulnerable(attackerMode) && vulnerable(victimMode) ? 1 : 0;
            assertEquals(expected, fixture.tips.collect(fixture.attacker.player, 2000).size());
            assertTrue(fixture.tips.collect(fixture.victim.player, 2000).isEmpty(),
                    "only the active attacker receives guidance");
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void bothPlayersMustHavePvpDisabled(boolean attackerEnabled, boolean victimEnabled) {
        try (Fixture fixture = new Fixture()) {
            if (attackerEnabled) fixture.enabled.add(fixture.attacker.id);
            if (victimEnabled) fixture.enabled.add(fixture.victim.id);
            var event = fixture.attack();

            assertEquals(!(attackerEnabled && victimEnabled), event.isCancelled());
            int expected = !attackerEnabled && !victimEnabled ? 1 : 0;
            assertEquals(expected, fixture.tips.collect(fixture.attacker.player, 2000).size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"attacker-enabled", "victim-enabled", "attacker-creative", "victim-spectator",
            "victim-offline", "victim-missing", "attacker-override", "victim-override"})
    void changedConditionsSuppressQueuedGuidance(String change) {
        try (Fixture fixture = new Fixture()) {
            fixture.attack();
            switch (change) {
                case "attacker-enabled" -> fixture.enabled.add(fixture.attacker.id);
                case "victim-enabled" -> fixture.enabled.add(fixture.victim.id);
                case "attacker-creative" -> fixture.attacker.mode = GameMode.CREATIVE;
                case "victim-spectator" -> fixture.victim.mode = GameMode.SPECTATOR;
                case "victim-offline" -> fixture.victim.online = false;
                case "victim-missing" -> fixture.players.remove(fixture.victim.id);
                case "attacker-override" -> fixture.overrides.add(fixture.attacker.id);
                case "victim-override" -> fixture.overrides.add(fixture.victim.id);
                default -> throw new AssertionError(change);
            }

            assertTrue(fixture.tips.collect(fixture.attacker.player, 2000).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void forcedPvpRulesDoNotRecommendAPersonalToggle(boolean victimOverride) {
        try (Fixture fixture = new Fixture()) {
            fixture.overrides.add(victimOverride ? fixture.victim.id : fixture.attacker.id);
            assertTrue(fixture.attack().isCancelled());
            assertTrue(fixture.tips.collect(fixture.attacker.player, 2000).isEmpty());
        }
    }

    @Test
    void repeatedAttacksShareTheCentralDailyCooldownWithoutLearningState() {
        try (Fixture fixture = new Fixture()) {
            fixture.attack();
            fixture.attack();
            assertTrue(fixture.tips.collect(fixture.attacker.player, 1999).isEmpty());
            assertEquals(1, fixture.tips.deliver(fixture.attacker.player, 2000).size());

            fixture.tips.collect(fixture.attacker.player, 60_000);
            fixture.attack();
            assertTrue(fixture.tips.deliver(fixture.attacker.player, 62_000).isEmpty());

            long later = Duration.ofDays(1).toMillis() + 2000;
            fixture.tips.collect(fixture.attacker.player, later);
            fixture.attack();
            assertEquals(1, fixture.tips.deliver(fixture.attacker.player, later + 2000).size());
        }
    }

    @Test
    void enablingPvpAloneDoesNotCreateCombatTags() {
        try (Fixture fixture = new Fixture()) {
            fixture.enabled.add(fixture.attacker.id);
            fixture.enabled.add(fixture.victim.id);
            assertFalse(fixture.controller.isCombatTagged(fixture.attacker.id));
            assertFalse(fixture.controller.isCombatTagged(fixture.victim.id));

            var damage = fixture.attack();
            fixture.controller.onConfirmedPvpDamage(damage);
            assertTrue(fixture.controller.isCombatTagged(fixture.attacker.id));
            assertTrue(fixture.controller.isCombatTagged(fixture.victim.id));
        }
    }

    @Test
    void playerShotProjectilesUseTheSameProtectionScenario() {
        try (Fixture fixture = new Fixture()) {
            Projectile arrow = (Projectile) Proxy.newProxyInstance(Projectile.class.getClassLoader(),
                    new Class<?>[]{Projectile.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getShooter")) return fixture.attacker.player;
                        throw new UnsupportedOperationException(method.getName());
                    });
            assertTrue(fixture.damage(arrow, fixture.victim.player).isCancelled());
            assertEquals(1, fixture.tips.collect(fixture.attacker.player, 2000).size());
        }
    }

    @Test
    void selfDamageAndNonPlayerTargetsDoNotOfferPvpGuidance() {
        try (Fixture fixture = new Fixture()) {
            assertFalse(fixture.damage(fixture.attacker.player, fixture.attacker.player).isCancelled());
            Entity mob = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getPassengers")) return List.of();
                        throw new UnsupportedOperationException(method.getName());
                    });
            assertFalse(fixture.damage(fixture.attacker.player, mob).isCancelled());
            assertTrue(fixture.tips.collect(fixture.attacker.player, 2000).isEmpty());
        }
    }

    private static Stream<Arguments> gameModes() {
        return Arrays.stream(GameMode.values()).flatMap(attacker -> Arrays.stream(GameMode.values())
                .map(victim -> Arguments.of(attacker, victim)));
    }

    private static boolean vulnerable(GameMode mode) {
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }

    private static final class Fixture implements AutoCloseable {
        final TipTestScope tips = new TipTestScope();
        final Participant attacker = new Participant();
        final Participant victim = new Participant();
        final Map<UUID, Player> players = new HashMap<>();
        final Set<UUID> enabled = new HashSet<>();
        final Set<UUID> overrides = new HashSet<>();
        final PvpCombatController controller;

        Fixture() {
            players.put(attacker.id, attacker.player);
            players.put(victim.id, victim.player);
            PvpStateResolver states = new PvpStateResolver() {
                @Override public boolean effectiveEnabled(UUID id) { return enabled.contains(id); }
                @Override public boolean effectiveEnabled(UUID id, boolean preference) { return preference; }
                @Override public boolean hasOverride(UUID id) { return overrides.contains(id); }
            };
            // Blocked attacks only query effective state; no settings writes or server lifecycle are needed.
            controller = new PvpCombatController(null, LANGUAGES, null, states, tips, players::get);
        }

        EntityDamageByEntityEvent attack() { return damage(attacker.player, victim.player); }

        @SuppressWarnings("removal")
        EntityDamageByEntityEvent damage(Entity source, Entity target) {
            DamageSource damageSource = (DamageSource) Proxy.newProxyInstance(DamageSource.class.getClassLoader(),
                    new Class<?>[]{DamageSource.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getCausingEntity" -> source instanceof Player ? source : null;
                        case "getDirectEntity" -> source;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            DamageCause cause = source instanceof Projectile ? DamageCause.PROJECTILE : DamageCause.ENTITY_ATTACK;
            Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
            modifiers.put(DamageModifier.BASE, 1.0);
            Map<DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(DamageModifier.class);
            functions.put(DamageModifier.BASE, damage -> 0.0);
            var event = new EntityDamageByEntityEvent(source, target, cause, damageSource,
                    modifiers, functions, false);
            controller.onEntityDamageByEntity(event);
            return event;
        }

        @Override public void close() { tips.close(); }
    }

    private static final class Participant {
        final UUID id = UUID.randomUUID();
        GameMode mode = GameMode.SURVIVAL;
        boolean online = true;
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getGameMode" -> mode;
                    case "isOnline" -> online;
                    case "sendActionBar" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
