package org.encinet.mik.feature.player.movement;

import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private float speed = 0.1f;
        private final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (_, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getFlySpeed" -> speed;
                    case "setFlySpeed" -> {
                        speed = (float) args[0];
                        yield null;
                    }
                    case "sendMessage" -> null;
                    default -> throw new AssertionError(method.getName());
                });
        private final FlightModule module = new FlightModule(null, LANGUAGE);
    }
}
