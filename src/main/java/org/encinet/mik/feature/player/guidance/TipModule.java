package org.encinet.mik.feature.player.guidance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/** Main-thread guidance transport. History lives with each player, not in a global player archive. */
public final class TipModule implements Listener {
    private static final long GLOBAL_COOLDOWN_MILLIS = Duration.ofSeconds(45).toMillis();
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final Predicate<Player> available;
    private final TipScheduler scheduler;
    private final TipEvents events;
    private final TipStore store;
    private BukkitTask task;
    private boolean running;

    public TipModule(JavaPlugin plugin, LanguageService language, Predicate<Player> available) {
        this.plugin = plugin;
        this.language = language;
        this.available = available;
        this.scheduler = new TipScheduler(error -> plugin.getLogger().log(Level.WARNING, "Tip source failed", error));
        this.events = new TipEvents((type, handler) -> {
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(type, listener, EventPriority.MONITOR,
                    (ignored, event) -> handler.accept(event), plugin, true);
            return () -> HandlerList.unregisterAll(listener);
        }, action -> {
            BukkitTask dispatch = Bukkit.getScheduler().runTask(plugin, action);
            return dispatch::cancel;
        }, player -> player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player,
                error -> plugin.getLogger().log(Level.WARNING, "Tip trigger failed", error));
        this.store = new TipStore(new NamespacedKey(plugin, "tip_history"));
    }

    public void enable() {
        running = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::load);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void disable() {
        running = false;
        HandlerList.unregisterAll(this);
        events.close();
        if (task != null) task.cancel();
        RuntimeException failure = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            try { save(player); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        scheduler.clear();
        if (failure != null) throw failure;
    }

    public TipScope scope() { return new Scope(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { load(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        try { save(player); }
        finally {
            events.forget(player);
            scheduler.forget(player.getUniqueId());
        }
    }

    private void load(Player player) {
        UUID id = player.getUniqueId();
        if (!scheduler.loaded(id)) scheduler.restore(id, store.load(player, System.currentTimeMillis()));
    }

    private void save(Player player) {
        UUID id = player.getUniqueId();
        if (!scheduler.changed(id)) return;
        store.save(player, scheduler.snapshot(id));
        scheduler.saved(id);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (!player.isOnline()) continue;
            load(player);
            UUID id = player.getUniqueId();
            Language recipientLanguage = language.language(player);
            var batch = scheduler.collect(id, now, GLOBAL_COOLDOWN_MILLIS,
                    !player.isDead() && available.test(player), recipientLanguage);
            if (!batch.isEmpty()) {
                Component message = Component.text(language.t(recipientLanguage, Message.TIP_LABEL), NamedTextColor.AQUA)
                        .append(Component.text(" | ", NamedTextColor.WHITE));
                for (int i = 0; i < batch.size(); i++) {
                    if (i > 0) message = message.append(Component.newline());
                    message = message.append(batch.get(i).message());
                }
                player.sendMessage(message);
                scheduler.delivered(id, batch, now);
            }
            save(player);
        }
    }

    private final class Scope implements TipScope, Tip.Binding {
        private boolean closed;

        @Override
        public <C> Tip.Builder<C> define(String id) {
            requireMainThread();
            if (closed || !running) throw new IllegalStateException("Tip scope is closed");
            return new Tip.Builder<>(this, id);
        }

        @Override
        public void register(Tip<?> tip) {
            requireMainThread();
            if (closed || !running) throw new IllegalStateException("Tip scope is closed");
            scheduler.register(this, tip.policy(), tip.ambient() ? (id, recipientLanguage) -> {
                Player player = Bukkit.getPlayer(id);
                return player != null && player.isOnline()
                        ? tip.resolve(player, null, recipientLanguage) : Optional.empty();
            } : null);
            events.register(this, tip);
        }

        @Override
        public Component text(Language recipientLanguage, Message message) {
            return MiniMessage.miniMessage().deserialize(language.t(recipientLanguage, message))
                    .colorIfAbsent(NamedTextColor.WHITE);
        }

        @Override
        public <C> void offer(Tip<C> tip, Player player, C context, double chance, boolean adaptive, int occurrences) {
            requireMainThread();
            if (closed || !running) return;
            if (adaptive && !tip.applicable(player, context)) return;
            load(player);
            UUID id = player.getUniqueId();
            int entityId = player.getEntityId();
            scheduler.offer(this, id, tip.policy(), System.currentTimeMillis(), chance, adaptive, occurrences,
                    recipientLanguage -> {
                        Player current = Bukkit.getPlayer(id);
                        return current != null && current.isOnline() && current.getEntityId() == entityId
                                ? tip.resolve(current, context, recipientLanguage) : Optional.empty();
                    });
        }

        @Override
        public void cancel(Tip.Policy policy, UUID playerId) {
            requireMainThread();
            if (!closed && running) scheduler.cancel(playerId, policy);
        }

        @Override
        public void close() {
            requireMainThread();
            closed = true;
            events.close(this);
            scheduler.close(this);
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Guidance must be used on the main thread");
    }
}
