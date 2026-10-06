package org.encinet.mik.module.plot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.role.RolePermissions;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

/** UUID-backed project arrival settings and safe, player-initiated visits. */
final class PlotArrivalController {
    private static final int RAW_LIMIT = 160;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(),
                    StandardTags.gradient(), StandardTags.rainbow(), StandardTags.reset()))
            .build();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();
    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(700));

    private final JavaPlugin plugin;
    private final LanguageService language;
    private final PlotRegistry registry;

    PlotArrivalController(JavaPlugin plugin, LanguageService language, PlotRegistry registry) {
        this.plugin = plugin;
        this.language = language;
        this.registry = registry;
    }

    boolean canVisit(Player player, Plot plot) {
        return plot.publicProject() || registry.standing(plot, player.getUniqueId(),
                RolePermissions.canModerate(player)) != PlotRegistry.Standing.OUTSIDER;
    }

    boolean canSetHere(Player player, Plot plot) {
        Location current = player.getLocation();
        Plot here = registry.at(current.getWorld().getUID(), current.getBlockX(),
                current.getBlockY(), current.getBlockZ());
        return here != null && here.id().equals(plot.id())
                && validLocation(current.getWorld(), plot,
                new PlotArrival(current.getX(), current.getY(), current.getZ(),
                        current.getYaw(), current.getPitch(), "", ""));
    }

    void setHere(Player player, UUID plotId) throws SQLException {
        Plot plot = managed(player, plotId);
        Location current = player.getLocation();
        PlotArrival previous = registry.arrival(plotId);
        PlotArrival next = new PlotArrival(current.getX(), current.getY(), current.getZ(),
                current.getYaw(), current.getPitch(),
                previous == null ? "" : previous.title(),
                previous == null ? "" : previous.subtitle());
        if (!canSetHere(player, plot) || !validLocation(current.getWorld(), plot, next))
            throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_LOCATION);
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        PlotWriteFeedback.finish(plugin, language, registry, player,
                registry.writeBehind(staged -> {
                    managed(staged, plotId, actor, staff);
                    staged.setArrival(plotId, next);
                    return null;
                }),
                ignored -> send(player, Message.PLOT_ARRIVAL_SET, NamedTextColor.GREEN));
    }

    void clear(Player player, UUID plotId) throws SQLException {
        managed(player, plotId);
        if (registry.arrival(plotId) == null)
            throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_MISSING);
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        PlotWriteFeedback.finish(plugin, language, registry, player,
                registry.writeBehind(staged -> {
                    managed(staged, plotId, actor, staff);
                    staged.clearArrival(plotId);
                    return null;
                }),
                ignored -> send(player, Message.PLOT_ARRIVAL_REMOVED, NamedTextColor.GREEN));
    }

    void setText(Player player, UUID plotId, String title, String subtitle)
            throws SQLException {
        managed(player, plotId);
        PlotArrival current = registry.arrival(plotId);
        if (current == null) throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_MISSING);
        validateText(title, 32);
        validateText(subtitle, 64);
        PlotArrival next = new PlotArrival(current.x(), current.y(), current.z(), current.yaw(), current.pitch(), title, subtitle);
        UUID actor = player.getUniqueId();
        boolean staff = RolePermissions.canModerate(player);
        PlotWriteFeedback.finish(plugin, language, registry, player,
                registry.writeBehind(staged -> {
                    managed(staged, plotId, actor, staff);
                    staged.setArrival(plotId, next);
                    return null;
                }),
                ignored -> send(player, Message.PLOT_ARRIVAL_TEXT_SAVED, NamedTextColor.GREEN));
    }

    Component titlePreview(Plot plot, PlotArrival arrival) {
        return title(plot, arrival);
    }

    void visit(Player player, UUID plotId) {
        Plot plot = registry.byId(plotId);
        if (plot == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        if (!canVisit(player, plot)) throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_PRIVATE);
        PlotArrival arrival = registry.arrival(plotId);
        if (arrival == null) throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_MISSING);
        World world = Bukkit.getWorld(plot.world());
        if (world == null) throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_UNSAFE);
        world.getChunkAtAsync(arrival.blockX() >> 4, arrival.blockZ() >> 4)
                .whenComplete((chunk, error) -> {
                    if (!plugin.isEnabled()) return;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        Plot fresh = registry.byId(plotId);
                        PlotArrival point = registry.arrival(plotId);
                        if (fresh == null || point == null || !point.equals(arrival)) {
                            send(player, Message.PLOT_ERROR_ARRIVAL_MISSING,
                                    NamedTextColor.RED);
                            return;
                        }
                        if (!canVisit(player, fresh)) {
                            send(player, Message.PLOT_ERROR_ARRIVAL_PRIVATE,
                                    NamedTextColor.RED);
                            return;
                        }
                        if (error != null || chunk == null || !validLocation(world, fresh, point)) {
                            send(player, Message.PLOT_ERROR_ARRIVAL_UNSAFE,
                                    NamedTextColor.RED);
                            return;
                        }
                        Location destination = new Location(world, point.x(), point.y(),
                                point.z(), point.yaw(), point.pitch());
                        player.teleportAsync(destination).whenComplete((success, failure) -> {
                            if (!plugin.isEnabled()) return;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                if (failure != null || !Boolean.TRUE.equals(success)) {
                                    send(player, Message.PLOT_ERROR_ARRIVAL_UNSAFE,
                                            NamedTextColor.RED);
                                } else {
                                    Plot arrivedPlot = registry.byId(plotId);
                                    PlotArrival arrivedPoint = registry.arrival(plotId);
                                    if (arrivedPlot != null && point.equals(arrivedPoint)
                                            && canVisit(player, arrivedPlot)
                                            && player.getWorld().equals(world)
                                            && player.getLocation().distanceSquared(destination) < 9) {
                                        player.showTitle(Title.title(title(arrivedPlot, point),
                                                subtitle(point), TITLE_TIMES));
                                    }
                                }
                            });
                        });
                    });
                });
    }

    private Plot managed(Player player, UUID plotId) {
        return managed(registry, plotId, player.getUniqueId(), RolePermissions.canModerate(player));
    }

    private static Plot managed(PlotRegistry registry, UUID plotId, UUID actor, boolean staff) {
        Plot plot = registry.byId(plotId);
        if (plot == null) throw new PlotProblem(Message.PLOT_ERROR_ID);
        if (!registry.permitted(plot, actor, staff, PlotPermission.MANAGE_SETTINGS))
            throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
        return plot;
    }

    static void validateText(String raw, int visibleLimit) {
        if (raw.length() > RAW_LIMIT || raw.codePoints().anyMatch(PlotArrivalController::isLineBreakOrControl))
            throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_TEXT);
        String visible;
        try {
            visible = PLAIN.serialize(MINI_MESSAGE.deserialize(raw));
        } catch (RuntimeException error) {
            throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_TEXT);
        }
        if ((!raw.isBlank() && visible.isBlank())
                || visible.codePointCount(0, visible.length()) > visibleLimit
                || visible.codePoints().anyMatch(PlotArrivalController::isLineBreakOrControl))
            throw new PlotProblem(Message.PLOT_ERROR_ARRIVAL_TEXT);
    }

    private static boolean isLineBreakOrControl(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint)
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static Component title(Plot plot, PlotArrival arrival) {
        return arrival.title().isBlank()
                ? Component.text(plot.name(), NamedTextColor.GOLD)
                : MINI_MESSAGE.deserialize(arrival.title());
    }

    private static Component subtitle(PlotArrival arrival) {
        return arrival.subtitle().isBlank()
                ? Component.empty() : MINI_MESSAGE.deserialize(arrival.subtitle());
    }

    private static boolean validLocation(World world, Plot plot, PlotArrival point) {
        if (world == null || !plot.world().equals(world.getUID())
                || point.blockY() <= world.getMinHeight()
                || point.blockY() >= world.getMaxHeight() - 1
                || (!plot.protects(point.blockX(), point.blockY(), point.blockZ())
                        && !plot.protects(point.blockX(), point.blockY() - 1, point.blockZ()))) return false;
        Location destination = new Location(world, point.x(), point.y(), point.z());
        if (!world.getWorldBorder().isInside(destination)) return false;
        Block feet = world.getBlockAt(point.blockX(), point.blockY(), point.blockZ());
        Block head = world.getBlockAt(point.blockX(), point.blockY() + 1, point.blockZ());
        Block floor = world.getBlockAt(point.blockX(), point.blockY() - 1, point.blockZ());
        Material ground = floor.getType();
        return feet.isPassable() && !feet.isLiquid()
                && head.isPassable() && !head.isLiquid()
                && !floor.getCollisionShape().getBoundingBoxes().isEmpty()
                && ground != Material.MAGMA_BLOCK && ground != Material.CACTUS
                && ground != Material.CAMPFIRE && ground != Material.SOUL_CAMPFIRE;
    }

    private void send(Player player, Message message, NamedTextColor color) {
        player.sendMessage(Component.text(language.t(player, message), color));
    }
}
