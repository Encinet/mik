package org.encinet.mik.feature.player.pvp;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PvpQuickToggleTest {
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override public String t(Player player, Message message, Object... args) {
            return message.key() + Arrays.toString(args);
        }
    };

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anUnrestrictedShortcutChangesTheStateInEitherDirection(boolean enabled) {
        try (var tips = new TipTestScope()) {
            var module = new TestPvp(tips);
            module.enabled = enabled;
            assertTrue(module.quickToggleDisabledReason(module.player).isEmpty());
            module.toggleQuickPvp(module.player);
            assertEquals(!enabled, module.enabled);
            assertEquals(1, module.toggles);
            assertTrue(module.messages.isEmpty());
        }
    }

    @Test void combatStartingAfterTheMenuWasBuiltIsRecheckedAndExpiryRestoresTheShortcut() {
        try (var tips = new TipTestScope()) {
            var module = new TestPvp(tips);
            module.enabled = true;
            assertTrue(module.quickToggleDisabledReason(module.player).isEmpty());

            module.remainingSeconds = 3;
            var reason = module.quickToggleDisabledReason(module.player).orElseThrow();
            assertEquals("pvp-quick-combat-cooldown[3]", PlainTextComponentSerializer.plainText().serialize(reason));
            module.toggleQuickPvp(module.player);
            assertTrue(module.enabled);
            assertEquals(0, module.toggles);
            assertEquals(List.of(reason), module.messages);

            module.remainingSeconds = 0;
            assertTrue(module.quickToggleDisabledReason(module.player).isEmpty());
            module.toggleQuickPvp(module.player);
            assertFalse(module.enabled);
            assertEquals(1, module.toggles);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aNewOverrideBlocksTheShortcutForEitherForcedStateUntilItIsRemoved(boolean forcedState) {
        try (var tips = new TipTestScope()) {
            var module = new TestPvp(tips);
            assertTrue(module.quickToggleDisabledReason(module.player).isEmpty());
            module.enabled = forcedState;
            module.override = Optional.of(new PvpOverrideState("event", "arena", forcedState, 1, 0));

            var reason = module.quickToggleDisabledReason(module.player).orElseThrow();
            assertEquals("pvp-quick-override[]", PlainTextComponentSerializer.plainText().serialize(reason));
            module.toggleQuickPvp(module.player);
            assertEquals(forcedState, module.enabled);
            assertEquals(0, module.toggles);
            assertEquals(List.of(reason), module.messages);

            module.override = Optional.empty();
            module.toggleQuickPvp(module.player);
            assertEquals(!forcedState, module.enabled);
            assertEquals(1, module.toggles);
        }
    }

    /** Replace state and persistence boundaries; execute the actual shortcut guard and dispatch. */
    private static final class TestPvp extends PvpModule {
        final UUID playerId = UUID.randomUUID();
        final List<Component> messages = new ArrayList<>();
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (_, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "sendMessage" -> { messages.add((Component) args[0]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        boolean enabled;
        long remainingSeconds;
        int toggles;
        Optional<PvpOverrideState> override = Optional.empty();

        TestPvp(TipTestScope tips) { super(null, LANGUAGES, tips); }
        @Override public boolean isEnabled(Player player) { return enabled; }
        @Override public boolean isCombatTagged(UUID playerId) { return remainingSeconds > 0; }
        @Override public long combatTagRemainingSeconds(UUID playerId) { return remainingSeconds; }
        @Override public Optional<PvpOverrideState> activeOverride(Player player) { return override; }
        @Override public void togglePvp(Player player) { enabled = !enabled; toggles++; }
    }
}
