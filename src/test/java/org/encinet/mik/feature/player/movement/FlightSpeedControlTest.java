package org.encinet.mik.feature.player.movement;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightSpeedControlTest {
    private static final LanguageService LANGUAGE = new LanguageService(null) {
        @Override
        public String t(Player player, Message message, Object... args) {
            return message.key();
        }
    };

    @Test
    void aNewRequestCannotBypassTheCeilingAndIsRestoredWhenTheCeilingEnds() {
        Fixture fixture = new Fixture();
        fixture.speed = 0.5f;
        fixture.module.limitExplorationSpeed(fixture.player, 0.2f);
        assertEquals(0.2f, fixture.speed);
        fixture.module.applyRequestedSpeed(fixture.player, 1.0f);
        assertEquals(0.2f, fixture.speed);
        fixture.module.limitExplorationSpeed(fixture.player, 0.25f);
        assertEquals(0.25f, fixture.speed);
        fixture.module.releaseExplorationSpeed(fixture.player);
        assertEquals(1.0f, fixture.speed);
    }

    @Test
    void repeatedCeilingsDoNotCompoundAndLowerUserRequestsRemainLower() {
        Fixture fixture = new Fixture();
        fixture.speed = 0.5f;
        fixture.module.limitExplorationSpeed(fixture.player, 0.2f);
        fixture.module.limitExplorationSpeed(fixture.player, 0.2f);
        fixture.module.applyRequestedSpeed(fixture.player, 0.05f);
        assertEquals(0.05f, fixture.speed);
        fixture.module.limitExplorationSpeed(fixture.player, 0.3f);
        assertEquals(0.05f, fixture.speed);
        fixture.module.releaseExplorationSpeed(fixture.player);
        assertEquals(0.05f, fixture.speed);
    }

    @Test
    void externalSpeedChangesBecomeTheNewRequestAndAreNotOverwrittenOnRelease() {
        Fixture fixture = new Fixture();
        fixture.speed = 0.8f;
        fixture.module.limitExplorationSpeed(fixture.player, 0.2f);
        fixture.speed = 0.1f;
        fixture.module.releaseExplorationSpeed(fixture.player);
        assertEquals(0.1f, fixture.speed);
        fixture.module.limitExplorationSpeed(fixture.player, 0.05f);
        fixture.speed = 0.3f;
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);
        fixture.module.releaseExplorationSpeed(fixture.player);
        assertEquals(0.3f, fixture.speed);
    }

    @Test
    void normalSprintingFlightAndClimbingCanContinueAtTheEffectiveSpeed() {
        Fixture fixture = new Fixture();
        fixture.speed = 0.5f;
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);

        // A short burst of delayed updates is fine even if they are processed together.
        for (int i = 0; i < 4; i++) assertTrue(fixture.move(1.09, 0.37, 0.0));
        fixture.advanceMillis(200);
        for (int i = 0; i < 100; i++) {
            assertTrue(fixture.move(1.09, 0.37, 0.0));
            fixture.advanceMillis(50);
        }
    }

    @Test
    void theLowerRequestedSpeedStillSetsTheBudget() {
        Fixture fixture = new Fixture();
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);
        fixture.module.applyRequestedSpeed(fixture.player, 0.01f);

        assertFalse(fixture.move(1.0, 0.0, 0.0));
        // At this speed Paper can report two vertical steps as one displacement.
        for (int i = 0; i < 50; i++) {
            assertTrue(fixture.move(0.0, 0.074, 0.0));
            fixture.advanceMillis(100);
        }
    }

    @Test
    void doublingTheMovementRateCannotDoubleTheAcceptedDistance() {
        Fixture fixture = new Fixture();
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);

        // Normal-sized steps arriving twice as frequently for five seconds.
        int accepted = 0;
        for (int i = 0; i < 200; i++) {
            if (fixture.move(1.0, 0.0, 0.0)) accepted++;
            fixture.advanceMillis(25);
        }
        assertTrue(accepted >= 100 && accepted <= 116, "accepted distance: " + accepted);
    }

    @Test
    void idleTimeCannotPayForAJumpOrAStreamOfOversizedVerticalSteps() {
        Fixture fixture = new Fixture();
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);
        fixture.advanceMillis(10_000);

        assertFalse(fixture.move(4.0, 0.0, 4.0));
        assertFalse(fixture.move(0.0, -2.0, 0.0));
        int accepted = 0;
        for (int i = 0; i < 100; i++) {
            if (fixture.move(0.0, 0.75, 0.0)) accepted++;
            fixture.advanceMillis(50);
        }
        assertTrue(accepted >= 50 && accepted <= 52, "accepted vertical moves: " + accepted);
    }

    @Test
    void speedChangesAndRepeatedCeilingsDoNotRefillTheBurst() {
        Fixture fixture = new Fixture();
        fixture.speed = 0.5f;
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);
        assertTrue(fixture.move(4.4, 0.0, 0.0));

        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);
        fixture.module.applyRequestedSpeed(fixture.player, 0.05f);
        fixture.module.applyRequestedSpeed(fixture.player, 0.5f);
        fixture.speed = 0.8f; // Another plugin changed the speed while the ceiling was active.

        assertFalse(fixture.move(1.0, 0.0, 0.0));
        assertEquals(0.1f, fixture.speed);
        fixture.advanceMillis(50);
        assertTrue(fixture.move(1.0, 0.0, 0.0));
        fixture.module.releaseExplorationSpeed(fixture.player);
        assertEquals(0.8f, fixture.speed);
        assertTrue(fixture.move(10.0, 0.0, 0.0));
    }

    @Test
    void onlyPositionChangesInFreeFlightUseTheBudget() {
        Fixture fixture = new Fixture();
        fixture.module.limitExplorationSpeed(fixture.player, 0.1f);
        assertTrue(fixture.move(4.4, 0.0, 0.0));
        Location turned = fixture.position.clone();
        turned.setYaw(180.0f);
        assertTrue(fixture.moveTo(turned));

        fixture.flying = false;
        assertTrue(fixture.move(10.0, 0.0, 0.0));
        fixture.flying = true;
        fixture.gliding = true;
        assertTrue(fixture.move(10.0, 0.0, 0.0));
        fixture.gliding = false;
        fixture.riding = true;
        assertTrue(fixture.move(10.0, 0.0, 0.0));
        fixture.riding = false;
        assertFalse(fixture.move(1.0, 0.0, 0.0));
    }

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private long nowNanos;
        private float speed = 0.1f;
        private boolean flying = true;
        private boolean gliding;
        private boolean riding;
        private Location position = new Location(null, 0, 64, 0);
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (_, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getFlySpeed" -> speed;
                    case "isFlying" -> flying;
                    case "isGliding" -> gliding;
                    case "isInsideVehicle" -> riding;
                    case "setFlySpeed" -> {
                        speed = (float) args[0];
                        yield null;
                    }
                    case "sendMessage" -> null;
                    default -> throw new AssertionError(method.getName());
                });
        private final FlightModule module = new FlightModule(null, LANGUAGE, () -> nowNanos);

        private void advanceMillis(long millis) {
            nowNanos += millis * 1_000_000L;
        }

        private boolean move(double dx, double dy, double dz) {
            return moveTo(position.clone().add(dx, dy, dz));
        }

        private boolean moveTo(Location destination) {
            PlayerMoveEvent event = new PlayerMoveEvent(player, position, destination);
            module.onMove(event);
            if (event.isCancelled()) return false;
            position = event.getTo();
            return true;
        }
    }
}
