package org.encinet.mik.feature.player.guidance;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TipNewcomerTest {
    @Test
    void eventAndManualProbabilitiesDoubleForPlayersWithoutTheMemberPermission() {
        try (TipTestScope tips = new TipTestScope(() -> 0.15)) {
            Tip<?> tip = tips.define("intro", Message.HOME_TIP_CREATE_MM).delay(Duration.ZERO)
                    .on(TipTrigger.event(PlayerJoinEvent.class).chance(0.1)).build();
            TestPlayer member = new TestPlayer(RolePermissions.MEMBER);
            TestPlayer newcomer = new TestPlayer();
            TestPlayer staffWithoutMember = new TestPlayer(RolePermissions.MODERATOR, RolePermissions.CUSTODIAN);

            tips.join(member.player);
            tips.join(newcomer.player);
            tips.join(staffWithoutMember.player);
            assertTrue(tips.collect(member.player, 0).isEmpty());
            assertEquals(1, tips.collect(newcomer.player, 0).size());
            assertEquals(1, tips.collect(staffWithoutMember.player, 0).size());

            tip.offerRandomly(member.player, 0.1);
            tip.offerRandomly(newcomer.player, 0.1);
            assertTrue(tips.collect(member.player, 0).isEmpty());
            assertEquals(1, tips.collect(newcomer.player, 0).size());

            member.permissions.remove(RolePermissions.MEMBER);
            newcomer.permissions.add(RolePermissions.MEMBER);
            tip.offerRandomly(member.player, 0.1);
            tip.offerRandomly(newcomer.player, 0.1);
            assertEquals(1, tips.collect(member.player, 0).size());
            assertTrue(tips.collect(newcomer.player, 0).isEmpty());
        }
    }

    @Test
    void chatBonusAppliesAfterAdaptiveReductionWithoutChangingMatchDensity() {
        AtomicReference<Double> roll = new AtomicReference<>(0.4);
        try (TipTestScope tips = new TipTestScope(roll::get)) {
            tips.define("chat", Message.HOME_TIP_CREATE_MM)
                    .on(TipTriggers.chatMentions(Pattern.compile("home"))).build();
            TestPlayer member = new TestPlayer(RolePermissions.MEMBER);
            TestPlayer newcomer = new TestPlayer();
            tips.chat(member.player, "home");
            tips.chat(newcomer.player, "home");
            assertEquals(1, tips.collect(member.player, 0).size());
            assertEquals(1, tips.collect(newcomer.player, 0).size());

            // A second immediate match has base probability 1/4: 40% loses for a member,
            // but wins against the newcomer's doubled 1/2 probability.
            tips.chat(member.player, "home");
            tips.chat(newcomer.player, "home");
            assertTrue(tips.collect(member.player, 0).isEmpty());
            assertEquals(1, tips.collect(newcomer.player, 0).size());
            assertEquals(tips.snapshot(member.id).history().get("chat").pressure(),
                    tips.snapshot(newcomer.id).history().get("chat").pressure());

            newcomer.permissions.add(RolePermissions.MEMBER);
            roll.set(0.15);
            tips.chat(newcomer.player, "home");
            assertTrue(tips.collect(newcomer.player, 0).isEmpty(),
                    "after promotion the third match uses 1/9, rather than the newcomer's 2/9");
        }
    }

    @Test
    void bonusCapsAtCertaintyAndNeverEnablesAZeroChanceSource() {
        try (TipTestScope tips = new TipTestScope(() -> {
            fail("certain and disabled opportunities must not draw a random number");
            return 0;
        })) {
            Tip<?> tip = tips.define("intro", Message.HOME_TIP_CREATE_MM).delay(Duration.ZERO)
                    .on(TipTrigger.event(PlayerJoinEvent.class).chance(0.75)).build();
            TestPlayer newcomer = new TestPlayer();
            tips.join(newcomer.player);
            assertEquals(1, tips.collect(newcomer.player, 0).size());
            tip.offerRandomly(newcomer.player, 0);
            assertTrue(tips.collect(newcomer.player, 0).isEmpty());
        }
    }

    @Test
    void bonusStillRespectsFeaturePermissionsAndTheGlobalDisplayInterval() {
        try (TipTestScope tips = new TipTestScope()) {
            tips.define("restricted", Message.HOME_TIP_CREATE_MM)
                    .on(TipTriggers.chatMentions(Pattern.compile("restricted")))
                    .when(player -> player.hasPermission("feature.allowed")).build();
            tips.define("first", Message.HOME_TIP_CREATE_MM)
                    .on(TipTriggers.chatMentions(Pattern.compile("first"))).build();
            tips.define("second", Message.HOME_TIP_RETURN_MM)
                    .on(TipTriggers.chatMentions(Pattern.compile("second"))).build();
            TestPlayer newcomer = new TestPlayer();
            tips.chat(newcomer.player, "restricted");
            assertTrue(tips.collect(newcomer.player, 0).isEmpty());
            assertTrue(tips.snapshot(newcomer.id).history().isEmpty());

            tips.chat(newcomer.player, "first");
            assertEquals(1, tips.deliver(newcomer.player, 0).size());
            tips.chat(newcomer.player, "second");
            assertTrue(tips.collect(newcomer.player, 1000).isEmpty());
            assertTrue(tips.collect(newcomer.player, 45_000).isEmpty(), "cooling offers are discarded");
        }
    }

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final Set<String> permissions;
        private final Player player;

        TestPlayer(String... granted) {
            permissions = new HashSet<>(Set.of(granted));
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "hasPermission" -> permissions.contains(args[0]);
                        default -> throw new AssertionError(method.getName());
                    });
        }
    }
}
