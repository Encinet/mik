package org.encinet.mik.feature.world.plot;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

/** Plot-owned destination editing and player-initiated teleport requests. Bukkit access stays on the server thread. */
final class PlotDestinationController {
    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRegistry registry;
    private final java.util.function.Consumer<Runnable> mainThread;
    private final java.util.function.Function<UUID, World> worlds;
    private final Map<UUID, UUID> requests = new HashMap<>();
    private volatile boolean closed;

    PlotDestinationController(JavaPlugin plugin, LanguageService language, PlotRegistry registry) {
        this(plugin, language, registry, action -> Bukkit.getScheduler().runTask(plugin, action), Bukkit::getWorld);
    }

    PlotDestinationController(JavaPlugin plugin, LanguageService language, PlotRegistry registry,
                              java.util.function.Consumer<Runnable> mainThread, java.util.function.Function<UUID, World> worlds) {
        this.plugin = plugin; this.language = language; this.registry = registry;
        this.mainThread = mainThread; this.worlds = worlds;
    }

    boolean canUse(PlotDestination point, UUID actor, boolean staff) {
        Plot plot = registry.byId(point.plotId());
        return plot != null && (point.publicAccess()
                || registry.standing(plot, actor, staff) != PlotRegistry.Standing.OUTSIDER);
    }

    boolean canUse(Player player, PlotDestination point) {
        return canUse(point, player.getUniqueId(), RolePermissions.canModerate(player));
    }

    Plot managed(UUID plotId, UUID actor, boolean staff) {
        return requireEditable(registry, plotId, actor, staff);
    }

    boolean canSetHere(Player player, Plot plot) {
        Location here = player.getLocation();
        return validLocation(here.getWorld(), plot, new PlotDestination(UUID.randomUUID(), plot.id(),
                plot.name(), "", false, here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch(), "", ""));
    }

    void create(Player player, UUID plotId, String name, String description) throws SQLException {
        Plot plot = managed(plotId, player.getUniqueId(), RolePermissions.canModerate(player));
        Location here = player.getLocation();
        PlotDestination point = new PlotDestination(UUID.randomUUID(), plotId, name, description, false,
                here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch(), "", "");
        if (!validLocation(here.getWorld(), plot, point)) throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_LOCATION);
        save(player, point, null);
    }

    void edit(Player player, PlotDestination expected, String name, String description) throws SQLException {
        save(player, expected.details(name, description), expected);
    }

    void titles(Player player, PlotDestination expected, String title, String subtitle, Runnable saved) throws SQLException {
        save(player, expected.titles(title, subtitle), expected, saved);
    }

    void preview(Player player, PlotDestination expected, PlotDestination draft) {
        managed(expected.plotId(), player.getUniqueId(), RolePermissions.canModerate(player));
        if (!expected.equals(registry.destination(expected.id())))
            throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_CHANGED);
        PlotDestinationTitles.show(player, expected.titles(draft.title(), draft.subtitle()));
    }

    void publish(Player player, PlotDestination expected) throws SQLException {
        save(player, expected.published(!expected.publicAccess()), expected);
    }

    void relocate(Player player, PlotDestination expected) throws SQLException {
        Plot plot = managed(expected.plotId(), player.getUniqueId(), RolePermissions.canModerate(player));
        Location here = player.getLocation();
        PlotDestination moved = new PlotDestination(expected.id(), expected.plotId(), expected.name(), expected.description(),
                expected.publicAccess(), here.getX(), here.getY(), here.getZ(), here.getYaw(), here.getPitch(), expected.title(), expected.subtitle());
        if (!validLocation(here.getWorld(), plot, moved)) throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_LOCATION);
        save(player, moved, expected);
    }

    private void save(Player player, PlotDestination next, PlotDestination expected) throws SQLException {
        save(player, next, expected, () -> { });
    }

    private void save(Player player, PlotDestination next, PlotDestination expected, Runnable saved) throws SQLException {
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        PlotWriteFeedback.finish(plugin, language, registry, player, registry.writeBehind(staged -> {
            requireEditable(staged, next.plotId(), actor, staff);
            if (!java.util.Objects.equals(expected, staged.destination(next.id())))
                throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_CHANGED);
            staged.saveDestination(next);
            return null;
        }), _ -> {
            send(player, Message.PLOT_DESTINATION_SAVED, NamedTextColor.GREEN);
            saved.run();
        });
    }

    void delete(Player player, PlotDestination expected) throws SQLException {
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        PlotWriteFeedback.finish(plugin, language, registry, player, registry.writeBehind(staged -> {
            requireEditable(staged, expected.plotId(), actor, staff);
            if (!expected.equals(staged.destination(expected.id())))
                throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_CHANGED);
            staged.deleteDestination(expected.id());
            return null;
        }), _ -> send(player, Message.PLOT_DESTINATION_DELETED, NamedTextColor.GREEN));
    }

    private static Plot requireEditable(PlotRegistry registry, UUID plotId, UUID actor, boolean staff) {
        Plot plot = registry.byId(plotId);
        if (plot == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        if (!registry.permitted(plot, actor, staff, PlotPermission.MANAGE_SETTINGS))
            throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
        return plot;
    }

    void teleport(Player player, UUID pointId) {
        if (closed || !registry.writesOpen()) throw new PlotProblem(Message.PLOT_ERROR_BUSY);
        requests.remove(player.getUniqueId());
        PlotDestination point = registry.destination(pointId);
        if (point == null) throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_MISSING);
        if (!canUse(player, point)) throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_PRIVATE);
        Plot plot = registry.byId(point.plotId());
        World world = worlds.apply(plot.world());
        if (world == null) throw new PlotProblem(Message.PLOT_DESTINATION_ERROR_UNSAFE);
        UUID request = UUID.randomUUID();
        UUID actor = player.getUniqueId();
        requests.put(actor, request);
        send(player, Message.PLOT_DESTINATION_LOADING, NamedTextColor.AQUA);
        world.getChunkAtAsync(point.blockX() >> 4, point.blockZ() >> 4).whenComplete((chunk, error) -> {
            if (closed) return;
            mainThread.accept(() -> {
                if (closed || !player.isOnline() || !requests.remove(actor, request)) return;
                PlotDestination latest = registry.destination(pointId);
                Plot fresh = registry.byId(point.plotId());
                if (!point.equals(latest) || fresh == null) {
                    send(player, Message.PLOT_DESTINATION_ERROR_CHANGED, NamedTextColor.RED);
                    return;
                }
                if (!canUse(player, latest)) {
                    send(player, Message.PLOT_DESTINATION_ERROR_PRIVATE, NamedTextColor.RED);
                    return;
                }
                if (error != null || chunk == null || !validLocation(world, fresh, latest)) {
                    send(player, Message.PLOT_DESTINATION_ERROR_UNSAFE, NamedTextColor.RED);
                    return;
                }
                // The destination chunk is loaded; commit on this thread so a later request cannot be overtaken.
                Location destination = new Location(world, point.x(), point.y(), point.z(), point.yaw(), point.pitch());
                if (!player.teleport(destination)) {
                    send(player, Message.PLOT_DESTINATION_ERROR_UNSAFE, NamedTextColor.RED);
                    return;
                }
                // Teleport events may redirect or edit the point; only announce a successful arrival here.
                Location arrived = player.getLocation();
                if (player.isOnline() && world.equals(arrived.getWorld()) && arrived.distanceSquared(destination) <= 9
                        && point.equals(registry.destination(pointId)) && canUse(player, point))
                    PlotDestinationTitles.show(player, point);
            });
        });
    }

    private boolean validLocation(World world, Plot plot, PlotDestination point) {
        if (world == null || !plot.world().equals(world.getUID())
                || point.blockY() <= world.getMinHeight() || point.blockY() >= world.getMaxHeight() - 1) return false;
        Plot effective = registry.at(world.getUID(), point.blockX(), point.blockY(), point.blockZ());
        if (effective == null || !effective.id().equals(plot.id())) return false;
        if (!world.getWorldBorder().isInside(new Location(world, point.x(), point.y(), point.z()))) return false;
        Block feet = world.getBlockAt(point.blockX(), point.blockY(), point.blockZ());
        Block head = world.getBlockAt(point.blockX(), point.blockY() + 1, point.blockZ());
        Block floor = world.getBlockAt(point.blockX(), point.blockY() - 1, point.blockZ());
        Material ground = floor.getType();
        return feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                && feet.getType() != Material.FIRE && feet.getType() != Material.SOUL_FIRE
                && head.getType() != Material.FIRE && head.getType() != Material.SOUL_FIRE
                && !floor.getCollisionShape().getBoundingBoxes().isEmpty()
                && ground != Material.MAGMA_BLOCK && ground != Material.CACTUS
                && ground != Material.CAMPFIRE && ground != Material.SOUL_CAMPFIRE;
    }

    void forget(UUID player) { requests.remove(player); }
    void close() { closed = true; requests.clear(); }

    private void send(Player player, Message key, NamedTextColor color) {
        player.sendMessage(Component.text(language.t(player, key), color));
    }
}
