package org.encinet.mik.feature.world.plot;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Private packet-only rulers. No entity is added to the world or participates in collisions/saves. */
final class PlotBoundaryDisplays {
    private final Map<UUID, Frame> frames = new HashMap<>();
    private BukkitTask task;
    private long tick;

    private record Node(int id, UUID uuid, Object value) { }
    private static final class Frame {
        UUID world;
        List<Node> nodes = new ArrayList<>();
        long touched;
    }

    void enable(Plugin plugin) {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            tick++;
            for (UUID player : List.copyOf(frames.keySet())) {
                Player viewer = Bukkit.getPlayer(player);
                Frame frame = frames.get(player);
                if (viewer == null || !viewer.isOnline() || tick - frame.touched > 30
                        || !frame.world.equals(viewer.getWorld().getUID())) forget(player);
            }
        }, 1, 1);
    }

    void disable() {
        if (task != null) task.cancel();
        task = null;
        for (UUID player : List.copyOf(frames.keySet())) forget(player);
    }

    void forget(UUID player) {
        Frame frame = frames.remove(player);
        Player viewer = Bukkit.getPlayer(player);
        if (frame != null && viewer != null && viewer.isOnline()) destroy(viewer, frame.nodes);
    }

    void show(Player viewer, List<PlotBoundaryFrame.Line> lines, List<PlotBoundaryFrame.Label> labels) {
        if (lines.size() > PlotBoundaryFrame.MAX_LINES || labels.size() > 2) throw new IllegalArgumentException("Plot frame budget exceeded");
        Frame frame = frames.get(viewer.getUniqueId());
        if (frame == null || !frame.world.equals(viewer.getWorld().getUID())) {
            forget(viewer.getUniqueId());
            frame = new Frame();
            frame.world = viewer.getWorld().getUID();
            frames.put(viewer.getUniqueId(), frame);
        }
        frame.touched = tick;
        List<Object> values = new ArrayList<>(lines);
        values.addAll(labels);
        List<Node> next = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            Node previous = index < frame.nodes.size() ? frame.nodes.get(index) : null;
            if (previous != null && previous.value().equals(value)) { next.add(previous); continue; }
            if (previous != null) destroy(viewer, List.of(previous));
            int id = ((CraftWorld) viewer.getWorld()).getHandle().getNextEntityId();
            UUID uuid = UUID.randomUUID();
            if (value instanceof PlotBoundaryFrame.Line line) line(viewer, id, uuid, line);
            else label(viewer, id, uuid, (PlotBoundaryFrame.Label) value);
            next.add(new Node(id, uuid, value));
        }
        if (frame.nodes.size() > values.size()) destroy(viewer, frame.nodes.subList(values.size(), frame.nodes.size()));
        frame.nodes = next;
    }

    private static void line(Player viewer, int id, UUID uuid, PlotBoundaryFrame.Line line) {
        var first = line.edge().first();
        var second = line.edge().second();
        double x = Math.min(first.horizontal(), second.horizontal()), y = Math.min(first.vertical(), second.vertical());
        double z = Math.min(first.forward(), second.forward());
        float thickness = .045F;
        var entity = new net.minecraft.world.entity.Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,
                ((CraftWorld) viewer.getWorld()).getHandle());
        entity.setId(id);
        BlockDisplay display = (BlockDisplay) entity.getBukkitEntity();
        configure(display);
        display.setBlock(line.material().createBlockData());
        display.setTransformation(new Transformation(new Vector3f(-thickness / 2, -thickness / 2, -thickness / 2),
                new Quaternionf(), new Vector3f((float) (Math.abs(second.horizontal() - first.horizontal()) + thickness),
                (float) (Math.abs(second.vertical() - first.vertical()) + thickness),
                (float) (Math.abs(second.forward() - first.forward()) + thickness)), new Quaternionf()));
        spawn(viewer, entity, uuid, x, y, z);
    }

    private static void label(Player viewer, int id, UUID uuid, PlotBoundaryFrame.Label label) {
        var entity = new net.minecraft.world.entity.Display.TextDisplay(EntityTypes.TEXT_DISPLAY,
                ((CraftWorld) viewer.getWorld()).getHandle());
        entity.setId(id);
        TextDisplay display = (TextDisplay) entity.getBukkitEntity();
        configure(display);
        display.setBillboard(Display.Billboard.CENTER);
        var point = label.point();
        display.text(Component.text(label.name(), label.color()).append(Component.newline())
                .append(Component.text(point.x() + ", " + point.y() + ", " + point.z(), NamedTextColor.WHITE)));
        display.setLineWidth(240);
        display.setBackgroundColor(Color.fromARGB(200, 24, 32, 44));
        display.setDefaultBackground(false);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(.7F), new Quaternionf()));
        spawn(viewer, entity, uuid, point.x() + .5, point.y() + 1.4, point.z() + .5);
    }

    private static void configure(Display display) {
        display.setBrightness(new Display.Brightness(15, 15));
        display.setViewRange(1);
        // Zero culling dimensions keep long thin rulers visible when their origin is off-screen.
        display.setDisplayWidth(0);
        display.setDisplayHeight(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
    }

    private static void spawn(Player viewer, Entity entity, UUID uuid, double x, double y, double z) {
        send(viewer, new ClientboundAddEntityPacket(entity.getId(), uuid, x, y, z, 0, 0, entity.getType(), 0, Vec3.ZERO, 0));
        send(viewer, new ClientboundSetEntityDataPacket(entity.getId(), entity.getEntityData().packAll()));
    }
    private static void destroy(Player viewer, List<Node> nodes) {
        if (!nodes.isEmpty()) send(viewer, new ClientboundRemoveEntitiesPacket(nodes.stream().mapToInt(Node::id).toArray()));
    }
    private static void send(Player viewer, Packet<?> packet) { ((CraftPlayer) viewer).getHandle().connection.send(packet); }
}
