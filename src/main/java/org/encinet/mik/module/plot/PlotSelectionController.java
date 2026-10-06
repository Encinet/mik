package org.encinet.mik.module.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.FloatingMenuState;
import org.encinet.mik.module.i18n.Message;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

final class PlotSelectionController implements Listener {
    private static final long TIMEOUT = Duration.ofMinutes(20).toNanos();
    private final PlotSelectionState selections;
    private final BiPredicate<Player, UUID> allowed;
    private final BiConsumer<Player, UUID> preview;
    private final Consumer<Player> hint;
    private final Consumer<Player> stopped;
    private final BiConsumer<Player, UUID> finish;
    private final BiConsumer<Player, PlotProblem> problem;
    private final LongSupplier clock;
    private final Predicate<Player> menuOpen;
    private final Predicate<Player> selectionTool;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private BukkitTask task;

    enum Mode { PICKING, PREVIEW }

    private record Session(PlotEditorContext context, UUID world, long deadline, Mode mode) {
        UUID plotId() { return context.plotId(); }
    }

    PlotSelectionController(PlotSelectionState selections, BiPredicate<Player, UUID> allowed,
                            BiConsumer<Player, UUID> preview, Consumer<Player> hint,
                            Consumer<Player> stopped, BiConsumer<Player, UUID> finish,
                            BiConsumer<Player, PlotProblem> problem) {
        this(selections, allowed, preview, hint, stopped, finish, problem, System::nanoTime,
                player -> FloatingMenus.current(player)
                        .map(handle -> handle.state() != FloatingMenuState.CLOSING).orElse(false),
                player -> player.getInventory().getItemInMainHand().getType() == Material.WOODEN_AXE);
    }

    PlotSelectionController(PlotSelectionState selections, BiPredicate<Player, UUID> allowed,
                            BiConsumer<Player, UUID> preview, Consumer<Player> hint,
                            Consumer<Player> stopped, BiConsumer<Player, UUID> finish,
                            BiConsumer<Player, PlotProblem> problem, LongSupplier clock,
                            Predicate<Player> menuOpen, Predicate<Player> selectionTool) {
        this.selections = selections;
        this.allowed = allowed;
        this.preview = preview;
        this.hint = hint;
        this.stopped = stopped;
        this.finish = finish;
        this.problem = problem;
        this.clock = clock;
        this.menuOpen = menuOpen;
        this.selectionTool = selectionTool;
    }

    void enable(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    void disable() {
        if (task != null) task.cancel();
        task = null;
        sessions.clear();
        HandlerList.unregisterAll(this);
    }

    void start(Player player, UUID plotId) {
        start(player, plotId, Mode.PICKING);
    }

    void start(Player player, UUID plotId, Mode mode) {
        if (!allowed.test(player, plotId)) return;
        UUID world = player.getWorld().getUID();
        selections.resume(player.getUniqueId(), plotId, world);
        sessions.put(player.getUniqueId(), new Session(selections.context(player.getUniqueId()), world,
                clock.getAsLong() + TIMEOUT, mode));
        preview.accept(player, plotId);
        hint.accept(player);
    }

    void stop(UUID playerId) {
        sessions.remove(playerId);
    }

    UUID plotId(UUID playerId) {
        Session session = sessions.get(playerId);
        return session == null ? null : session.plotId();
    }

    Mode mode(UUID playerId) {
        Session session = sessions.get(playerId);
        return session == null ? null : session.mode();
    }

    boolean ownsPreview(UUID playerId, UUID plotId) {
        Session session = sessions.get(playerId);
        return session != null && Objects.equals(plotId, session.plotId());
    }

    void pause(UUID playerId) {
        Session session = sessions.get(playerId);
        if (session != null) sessions.put(playerId,
                new Session(session.context(), session.world(), clock.getAsLong() + TIMEOUT, Mode.PREVIEW));
    }

    void keepProject(UUID playerId, UUID plotId) {
        Session session = sessions.get(playerId);
        if (session != null && !session.context().equals(selections.context(playerId, plotId))) stop(playerId);
        else pause(playerId);
    }

    private boolean active(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        if (clock.getAsLong() - session.deadline() >= 0
                || !session.world().equals(player.getWorld().getUID())
                || !session.context().equals(selections.context(player.getUniqueId()))
                || !allowed.test(player, session.plotId())) {
            stop(player.getUniqueId());
            stopped.accept(player);
            return false;
        }
        return true;
    }

    private boolean picking(Player player) {
        return active(player) && mode(player.getUniqueId()) == Mode.PICKING && !menuOpen.test(player)
                && selectionTool.test(player);
    }

    private void tick() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                stop(playerId);
                continue;
            }
            refresh(player);
        }
    }

    void refresh(Player player) {
        if (!active(player)) return;
        Session session = sessions.get(player.getUniqueId());
        preview.accept(player, session.plotId());
        if (!menuOpen.test(player)) hint.accept(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !active(event.getPlayer())) return;
        touch(event.getPlayer().getUniqueId());
        if (!picking(event.getPlayer())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        boolean right = event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR;
        if (right && player.isSneaking()) {
            pause(player.getUniqueId());
            finish.accept(player, session.plotId());
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !session.world().equals(block.getWorld().getUID())) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        mark(player, session, !right, new PlotPosition(session.world(), block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        String command = event.getMessage().strip();
        if (!PlotSelectionCommands.matches(command)) return;
        Player player = event.getPlayer();
        if (!active(player)) return;
        event.setCancelled(true);
        Session session = sessions.get(player.getUniqueId());
        Location current = player.getLocation();
        try {
            PlotSelectionCommands.Mark mark = PlotSelectionCommands.parse(command,
                    new PlotPosition(session.world(), current.getBlockX(), current.getBlockY(), current.getBlockZ()));
            if (mark.point().y() < player.getWorld().getMinHeight()
                    || mark.point().y() >= player.getWorld().getMaxHeight())
                throw new PlotProblem(Message.PLOT_ERROR_SELECTION);
            mark(player, session, mark.first(), mark.point());
        } catch (PlotProblem error) {
            problem.accept(player, error);
        }
    }

    private void mark(Player player, Session session, boolean first, PlotPosition point) {
        selections.bindContext(player.getUniqueId(), session.context(), session.world());
        selections.mark(player.getUniqueId(), first, point);
        sessions.put(player.getUniqueId(), new Session(session.context(), session.world(),
                clock.getAsLong() + TIMEOUT, session.mode()));
        preview.accept(player, session.plotId());
        hint.accept(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(BlockDamageEvent event) {
        if (picking(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (picking(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (picking(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && picking(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled() || !player.isSneaking() || !active(player) || menuOpen.test(player)) return;
        event.setCancelled(true);
        UUID plotId = plotId(player.getUniqueId());
        pause(player.getUniqueId());
        finish.accept(player, plotId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.isCancelled()) return;
        UUID playerId = event.getPlayer().getUniqueId();
        Session session = sessions.get(playerId);
        Location from = event.getFrom(), to = event.getTo();
        if (session == null || to == null || to.getWorld() == null
                || !session.world().equals(to.getWorld().getUID())
                || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;
        touch(playerId);
    }

    private void touch(UUID playerId) {
        Session session = sessions.get(playerId);
        long now = clock.getAsLong();
        if (session == null || now - session.deadline() >= 0) return;
        sessions.put(playerId, new Session(session.context(), session.world(), now + TIMEOUT, session.mode()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.isCancelled()) return;
        if (sessions.containsKey(event.getEntity().getUniqueId())) {
            stop(event.getEntity().getUniqueId());
            stopped.accept(event.getEntity());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (sessions.containsKey(event.getPlayer().getUniqueId())) {
            stop(event.getPlayer().getUniqueId());
            stopped.accept(event.getPlayer());
        }
    }
}
