package org.encinet.mik.feature.player.effects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent.Cause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.shared.i18n.TextArg;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Actionable cleanup requests, independent of the tutorial tip cooldown. */
public final class EffectCleanupModule implements Listener {
    private static final Duration REQUEST_LIFETIME = Duration.ofMinutes(1);
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1).lifetime(REQUEST_LIFETIME).build();

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final Predicate<UUID> inCombat;
    private final Function<Runnable, BukkitTask> scheduler;
    private final BiFunction<Player, Consumer<Player>, ClickEvent> buttons;
    private final LongSupplier clock;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true;

    public EffectCleanupModule(JavaPlugin plugin, LanguageService languages, Predicate<UUID> inCombat) {
        this(plugin, languages, inCombat, action -> Bukkit.getScheduler().runTask(plugin, action),
                (owner, action) -> ClickEvent.callback(audience -> {
                    if (audience == owner) action.accept(owner);
                }, CALLBACK_OPTIONS), System::currentTimeMillis);
    }

    EffectCleanupModule(JavaPlugin plugin, LanguageService languages, Predicate<UUID> inCombat,
                        Function<Runnable, BukkitTask> scheduler,
                        BiFunction<Player, Consumer<Player>, ClickEvent> buttons, LongSupplier clock) {
        this.plugin = plugin;
        this.languages = languages;
        this.inCombat = inCombat;
        this.scheduler = scheduler;
        this.buttons = buttons;
        this.clock = clock;
    }

    public void enable() {
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        enabled = false;
        HandlerList.unregisterAll(this);
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
        pending.clear();
        requests.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!enabled || event.isCancelled() || !(event.getEntity() instanceof Player player)
                || !eligible(player) || event.getNewEffect() == null
                || !harmful(event.getNewEffect())
                || (event.getAction() != Action.ADDED
                    && (event.getAction() != Action.CHANGED || !event.isOverride()))) return;

        UUID playerId = player.getUniqueId();
        Pending batch = pending.get(playerId);
        if (batch == null) {
            batch = new Pending(player);
            pending.put(playerId, batch);
            Pending captured = batch;
            batch.task = schedule(() -> deliver(playerId, captured));
        }
        batch.changes.put(event.getModifiedType(), event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Pending batch = pending.remove(playerId);
        if (batch != null) {
            batch.task.cancel();
            tasks.remove(batch.task);
        }
        requests.remove(playerId);
    }

    private void deliver(UUID playerId, Pending batch) {
        if (!pending.remove(playerId, batch) || !eligible(batch.player)) return;
        Player player = batch.player;
        Set<Cause> causes = EnumSet.noneOf(Cause.class);
        batch.changes.forEach((type, event) -> {
            if (!event.isCancelled() && player.hasPotionEffect(type)) causes.add(event.getCause());
        });
        if (causes.isEmpty()) return;

        List<PotionEffect> effects = List.copyOf(player.getActivePotionEffects());
        if (effects.stream().noneMatch(EffectCleanupModule::harmful)) return;
        Set<PotionEffectType> types = effects.stream().map(PotionEffect::getType).collect(Collectors.toSet());
        Request previous = requests.get(playerId);
        long now = clock.getAsLong();
        if (previous != null && now < previous.expiresAt
                && previous.types.equals(types) && previous.causes.equals(causes)) return;

        Request request = new Request(player, types, Set.copyOf(causes), now + REQUEST_LIFETIME.toMillis());
        requests.put(playerId, request);
        Language language = languages.language(player);
        Component source = Component.join(JoinConfiguration.separator(Component.text(" / ", NamedTextColor.WHITE)), causes.stream()
                .map(cause -> Component.text(languages.format(language, Message.EFFECT_CLEANUP_SOURCE,
                        TextArg.of("cause", cause.name())), NamedTextColor.GOLD)).toList());
        Component message = languages.rich(language, Message.EFFECT_CLEANUP_PROMPT_RICH, NamedTextColor.YELLOW,
                RichArg.component("source", source))
                .appendSpace().append(button(player, language, request, false));
        if (effects.stream().anyMatch(effect -> !harmful(effect))) {
            message = message.appendSpace().append(button(player, language, request, true));
        }
        player.sendMessage(message);
    }

    private Component button(Player player, Language language, Request request, boolean all) {
        return languages.text(language, all ? Message.EFFECT_CLEANUP_ALL : Message.EFFECT_CLEANUP_NEGATIVE,
                        all ? NamedTextColor.AQUA : NamedTextColor.GREEN)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(buttons.apply(player, actor -> {
                    if (actor == request.player) schedule(() -> clear(actor, request, all));
                }));
    }

    private void clear(Player player, Request request, boolean all) {
        if (!player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (requests.get(playerId) != request || clock.getAsLong() >= request.expiresAt) {
            player.sendMessage(languages.text(player, Message.EFFECT_CLEANUP_EXPIRED, NamedTextColor.YELLOW));
            return;
        }
        requests.remove(playerId);
        if (!eligible(player)) {
            player.sendMessage(languages.text(player, Message.EFFECT_CLEANUP_BLOCKED, NamedTextColor.YELLOW));
            return;
        }
        List<PotionEffect> effects = List.copyOf(player.getActivePotionEffects());
        int removed = 0;
        if (effects.stream().anyMatch(EffectCleanupModule::harmful)) {
            for (PotionEffect effect : effects) {
                if (all || harmful(effect)) {
                    player.removePotionEffect(effect.getType());
                    if (!player.hasPotionEffect(effect.getType())) removed++;
                }
            }
        }
        player.sendMessage(Component.text(languages.format(player, Message.EFFECT_CLEANUP_RESULT,
                TextArg.of("count", removed)), NamedTextColor.GREEN));
    }

    private boolean eligible(Player player) {
        GameMode mode = player.getGameMode();
        return player.isOnline() && !player.isDead()
                && (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR)
                && !inCombat.test(player.getUniqueId());
    }

    private static boolean harmful(PotionEffect effect) {
        return effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL;
    }

    private BukkitTask schedule(Runnable action) {
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = scheduler.apply(() -> {
            tasks.remove(holder[0]);
            if (enabled) action.run();
        });
        tasks.add(holder[0]);
        if (!enabled) {
            holder[0].cancel();
            tasks.remove(holder[0]);
        }
        return holder[0];
    }

    private static final class Pending {
        final Player player;
        final Map<PotionEffectType, EntityPotionEffectEvent> changes = new HashMap<>();
        BukkitTask task;

        Pending(Player player) { this.player = player; }
    }

    private record Request(Player player, Set<PotionEffectType> types, Set<Cause> causes, long expiresAt) {}
}
