package org.encinet.mik.feature.player.effects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EffectCleanupModuleTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LanguageService LANGUAGES = new LanguageService(null) {
        @Override public Language language(Player player) { return Language.EN_US; }
        @Override public String t(Language language, Message message, Object... args) { return message.key(); }
        @Override public String t(Player player, Message message, Object... args) { return message.key(); }
        @Override public String format(Language language, Message message, TextArg... args) {
            return message.key() + Arrays.toString(args);
        }
        @Override public Component rich(Language language, Message message, NamedTextColor color, RichArg... args) {
            return Component.text(message.key(), color).append(args[0].component());
        }
    };

    @ParameterizedTest
    @CsvSource({"SURVIVAL,false,false", "ADVENTURE,false,false", "CREATIVE,false,true",
            "SPECTATOR,false,true", "CREATIVE,true,false", "SPECTATOR,true,false"})
    void onlyCreativeAndSpectatorOutsideActualCombatArePrompted(GameMode mode, boolean combat, boolean expected) {
        Fixture f = new Fixture();
        f.mode = mode;
        f.combat = combat;
        f.gain(PotionEffectType.POISON, Cause.POTION_DRINK);
        assertTrue(f.messages.isEmpty(), "read effects only after the event has applied");
        f.tick();
        assertEquals(expected ? 1 : 0, f.messages.size());
    }

    @Test
    void harmfulEffectsShareOnePromptWithTheirSourcesAndOnlyOneButtonWhenAllAreHarmful() {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.POISON, Cause.POTION_DRINK);
        f.gain(PotionEffectType.SLOWNESS, Cause.ARROW);
        f.tick();
        assertEquals(1, f.messages.size());
        String text = PLAIN.serialize(f.messages.getFirst());
        assertTrue(text.contains("POTION_DRINK"));
        assertTrue(text.contains("ARROW"));
        assertEquals(List.of("effect-cleanup-negative"), f.buttonLabels());
        f.click(0);
        assertEquals(2, f.effects.size(), "callbacks perform Bukkit writes on the next main-thread task");
        f.tick();
        assertTrue(f.effects.isEmpty());
        assertTrue(PLAIN.serialize(f.messages.getLast()).contains("count, value=2"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theTwoActionsPreserveOrRemoveBeneficialAndNeutralEffects(boolean clearAll) {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.SPEED, Cause.COMMAND);
        f.gain(PotionEffectType.GLOWING, Cause.ARROW);
        f.gain(PotionEffectType.POISON, Cause.PLUGIN);
        f.tick();
        assertEquals(List.of("effect-cleanup-negative", "effect-cleanup-all"), f.buttonLabels());
        assertTrue(f.messages.getFirst().children().stream().anyMatch(c -> c.clickEvent() != null));
        f.click(clearAll ? 1 : 0);
        f.tick();
        assertFalse(f.effects.containsKey(PotionEffectType.POISON));
        assertEquals(!clearAll, f.effects.containsKey(PotionEffectType.SPEED));
        assertEquals(!clearAll, f.effects.containsKey(PotionEffectType.GLOWING));
        f.click(clearAll ? 0 : 1);
        f.tick();
        assertTrue(PLAIN.serialize(f.messages.getLast()).contains("effect-cleanup-expired"));
    }

    @Test
    void positiveNeutralCancelledRemovedAndNonOverridingChangesDoNotPrompt() {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.SPEED, Cause.COMMAND);
        f.gain(PotionEffectType.GLOWING, Cause.ARROW);
        PotionEffect poison = new PotionEffect(PotionEffectType.POISON, 200, 0);
        var cancelled = new EntityPotionEffectEvent(f.player, null, poison, null, Cause.COMMAND, Action.ADDED, true);
        cancelled.setCancelled(true);
        f.module.onPotionEffect(cancelled);
        f.module.onPotionEffect(new EntityPotionEffectEvent(f.player, poison, poison, null,
                Cause.AREA_EFFECT_CLOUD, Action.CHANGED, false));
        f.module.onPotionEffect(new EntityPotionEffectEvent(f.player, poison, null, null,
                Cause.EXPIRATION, Action.REMOVED, false));
        f.tick();
        assertTrue(f.messages.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"combat", "survival", "adventure", "offline", "dead", "removed", "cancelled"})
    void queuedDeliveryRechecksTheFinalEventAndCurrentPlayerState(String change) {
        Fixture f = new Fixture();
        EntityPotionEffectEvent event = f.gain(PotionEffectType.POISON, Cause.COMMAND);
        switch (change) {
            case "combat" -> f.combat = true;
            case "survival" -> f.mode = GameMode.SURVIVAL;
            case "adventure" -> f.mode = GameMode.ADVENTURE;
            case "offline" -> f.online = false;
            case "dead" -> f.dead = true;
            case "removed" -> f.effects.clear();
            case "cancelled" -> event.setCancelled(true);
            default -> fail(change);
        }
        f.tick();
        assertTrue(f.messages.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"combat", "survival", "adventure", "expired", "removed", "quit", "disabled", "other-player"})
    void staleOrIneligibleButtonsCannotRemoveEffects(String change) {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.SPEED, Cause.COMMAND);
        f.gain(PotionEffectType.POISON, Cause.POTION_SPLASH);
        f.tick();
        switch (change) {
            case "combat" -> f.combat = true;
            case "survival" -> f.mode = GameMode.SURVIVAL;
            case "adventure" -> f.mode = GameMode.ADVENTURE;
            case "expired" -> f.now = 60_000;
            case "removed" -> f.effects.remove(PotionEffectType.POISON);
            case "quit" -> f.module.onQuit(new PlayerQuitEvent(f.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            case "disabled" -> f.module.disable();
            case "other-player" -> { }
            default -> fail(change);
        }
        if (change.equals("other-player")) f.actions.get(1).accept(new Fixture().player);
        else f.click(1);
        f.tick();
        assertTrue(f.effects.containsKey(PotionEffectType.SPEED));
        assertEquals(!change.equals("removed"), f.effects.containsKey(PotionEffectType.POISON));
    }

    @Test
    void repeatedApplicationsReuseTheRequestButNewEffectsInvalidateOlderButtons() {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.POISON, Cause.POTION_DRINK);
        f.tick();
        f.gain(PotionEffectType.POISON, Cause.POTION_DRINK);
        f.tick();
        assertEquals(1, f.messages.size());
        f.gain(PotionEffectType.SLOWNESS, Cause.ARROW);
        f.tick();
        assertEquals(2, f.messages.size());
        f.click(0);
        f.tick();
        assertEquals(2, f.effects.size());
        f.click(1);
        f.tick();
        assertTrue(f.effects.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void quittingAndDisablingCancelPendingDelivery(boolean disable) {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.POISON, Cause.PLUGIN);
        if (disable) f.module.disable();
        else f.module.onQuit(new PlayerQuitEvent(f.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(f.queue.getFirst().cancelled);
        f.tick();
        assertTrue(f.messages.isEmpty());
    }

    @Test
    void cancelledRemovalReportsTheNumberActuallyRemoved() {
        Fixture f = new Fixture();
        f.gain(PotionEffectType.POISON, Cause.PLUGIN);
        f.tick();
        f.preventRemoval = true;
        f.click(0);
        f.tick();
        assertTrue(f.effects.containsKey(PotionEffectType.POISON));
        assertTrue(PLAIN.serialize(f.messages.getLast()).contains("count, value=0"));
    }

    private static final class Fixture {
        final UUID id = UUID.randomUUID();
        GameMode mode = GameMode.CREATIVE;
        boolean combat, dead, preventRemoval;
        boolean online = true;
        long now;
        final Map<PotionEffectType, PotionEffect> effects = new HashMap<>();
        final List<Component> messages = new ArrayList<>();
        final List<Consumer<Player>> actions = new ArrayList<>();
        final ArrayDeque<Scheduled> queue = new ArrayDeque<>();
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getGameMode" -> mode;
                    case "isOnline" -> online;
                    case "isDead" -> dead;
                    case "hasPotionEffect" -> effects.containsKey(args[0]);
                    case "getActivePotionEffects" -> List.copyOf(effects.values());
                    case "removePotionEffect" -> { if (!preventRemoval) effects.remove(args[0]); yield null; }
                    case "sendMessage" -> { messages.add((Component) args[0]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        final EffectCleanupModule module = new EffectCleanupModule(null, LANGUAGES, uuid -> combat, action -> {
            Scheduled scheduled = new Scheduled(action);
            queue.add(scheduled);
            return scheduled.task;
        }, (owner, action) -> {
            actions.add(action);
            return ClickEvent.runCommand("/test-button-" + (actions.size() - 1));
        }, () -> now);

        EntityPotionEffectEvent gain(PotionEffectType type, Cause cause) {
            PotionEffect next = new PotionEffect(type, 200, 0);
            PotionEffect old = effects.get(type);
            var event = new EntityPotionEffectEvent(player, old, next, null, cause,
                    old == null ? Action.ADDED : Action.CHANGED, true);
            module.onPotionEffect(event);
            effects.put(type, next);
            return event;
        }
        void tick() {
            List<Scheduled> scheduled = List.copyOf(queue);
            queue.clear();
            scheduled.forEach(task -> { if (!task.cancelled) task.action.run(); });
        }
        void click(int index) { actions.get(index).accept(player); }
        List<String> buttonLabels() {
            return messages.getFirst().children().stream().filter(c -> c.clickEvent() != null)
                    .map(PLAIN::serialize).toList();
        }
    }

    private static final class Scheduled {
        final Runnable action;
        boolean cancelled;
        final BukkitTask task = (BukkitTask) Proxy.newProxyInstance(BukkitTask.class.getClassLoader(),
                new Class<?>[]{BukkitTask.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "cancel" -> { cancelled = true; yield null; }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Scheduled(Runnable action) { this.action = action; }
    }
}
