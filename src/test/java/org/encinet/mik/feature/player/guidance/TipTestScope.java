package org.encinet.mik.feature.player.guidance;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** Replaces Bukkit transport while keeping real registration, probability, resolution and scheduling. */
public final class TipTestScope implements TipScope, Tip.Binding {
    private final TipScheduler scheduler;
    private final TipEvents events;
    private final LanguageService languages;
    private final EventHandlers handlers = new EventHandlers();
    private final Queue<Runnable> mainTasks = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Player> players = new ConcurrentHashMap<>();
    private boolean closed;
    private long now;

    public TipTestScope() { this(() -> 0); }

    public TipTestScope(DoubleSupplier random) {
        this(random, new LanguageService(null) {
            @Override
            public String t(Language language, Message message, Object... args) { return message.key(); }
        });
    }

    public TipTestScope(DoubleSupplier random, LanguageService languages) {
        this.languages = languages;
        scheduler = new TipScheduler(error -> { throw new AssertionError(error); }, random,
                id -> TipAudience.forPlayer(players.get(id)));
        events = new TipEvents(handlers, action -> {
            mainTasks.add(action);
            return () -> mainTasks.remove(action);
        }, player -> true, error -> { throw new AssertionError(error); });
    }

    @Override
    public <C> Tip.Builder<C> define(String id) {
        if (closed) throw new IllegalStateException("Closed scope");
        return new Tip.Builder<>(this, id);
    }

    @Override
    public void register(Tip<?> tip) {
        if (closed) throw new IllegalStateException("Closed scope");
        scheduler.register(this, tip.policy(), tip.ambient() ? (id, language) -> {
            Player player = players.get(id);
            return player == null ? java.util.Optional.empty() : tip.resolve(player, null, language);
        } : null);
        events.register(this, tip);
    }

    @Override
    public Component text(Language language, Message message) {
        return MiniMessage.miniMessage().deserialize(languages.t(language, message)).colorIfAbsent(NamedTextColor.WHITE);
    }

    @Override
    public <C> void offer(Tip<C> tip, Player player, C context, double chance, boolean adaptive, int occurrences,
                          Duration delay) {
        if (closed) return;
        if (adaptive && !tip.applicable(player, context)) return;
        players.put(player.getUniqueId(), player);
        scheduler.offer(this, player.getUniqueId(), tip.policy(), now, chance, adaptive, occurrences, delay,
                language -> tip.resolve(player, context, language));
    }

    @Override
    public void cancel(Tip.Policy policy, UUID playerId) { scheduler.cancel(playerId, policy); }

    @Override
    public void close() {
        closed = true;
        events.close();
        scheduler.close(this);
        players.clear();
    }

    public void fire(Event event) { handlers.fire(event); }
    public void teleport(PlayerTeleportEvent event) { fire(event); }
    public void chat(Player player, String text) { fire(chatEvent(player, text)); }
    public void join(Player player) { fire(new PlayerJoinEvent(player, Component.empty())); }

    static AsyncChatEvent chatEvent(Player player, String text) {
        Component message = Component.text(text);
        return new AsyncChatEvent(true, player, Set.of(), ChatRenderer.defaultRenderer(), message, message, null);
    }

    public void flush() {
        Runnable task;
        while ((task = mainTasks.poll()) != null) task.run();
    }

    public List<Component> collect(Player player, long time) {
        return collect(player, time, Language.EN_US);
    }

    public List<Component> collect(Player player, long time, Language language) {
        return collectBatch(player, time, language).stream().map(TipScheduler.Delivery::message).toList();
    }

    /** Simulates the transport accepting a batch; the real scheduler owns all resulting history. */
    public List<Component> deliver(Player player, long time) {
        var batch = collectBatch(player, time, Language.EN_US);
        if (!batch.isEmpty()) scheduler.delivered(player.getUniqueId(), batch, time);
        return batch.stream().map(TipScheduler.Delivery::message).toList();
    }

    TipState.Snapshot snapshot(UUID player) { return scheduler.snapshot(player); }

    private List<TipScheduler.Delivery> collectBatch(Player player, long time, Language language) {
        flush();
        now = time;
        players.put(player.getUniqueId(), player);
        return scheduler.collect(player.getUniqueId(), time, 45_000, true, language);
    }

    /** Replaces only native listener registration, leaving matching and dispatch to production TipEvents. */
    static final class EventHandlers implements TipEvents.Registrar {
        final Map<Class<? extends Event>, Consumer<Event>> callbacks = new ConcurrentHashMap<>();

        @Override
        public Runnable register(Class<? extends Event> type, Consumer<Event> handler) {
            if (callbacks.putIfAbsent(type, handler) != null) throw new IllegalStateException("Duplicate listener: " + type);
            return () -> callbacks.remove(type, handler);
        }

        void fire(Event event) {
            callbacks.forEach((type, callback) -> {
                if (type.isInstance(event)) callback.accept(event);
            });
        }
    }
}
