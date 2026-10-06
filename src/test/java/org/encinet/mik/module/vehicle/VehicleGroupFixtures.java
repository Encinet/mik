package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class VehicleGroupFixtures {
    static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, arguments) -> switch (method.getName()) {
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Group test world";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    static Node block(World world) { return new Node(BlockDisplay.class, world); }

    static final class Node {
        final UUID id = UUID.randomUUID();
        final Entity entity;
        final List<Node> children = new ArrayList<>();
        Node parent;
        Location location;
        Transformation transformation = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf());
        boolean valid = true;
        boolean visible;

        Node(Class<? extends Entity> type, World world) {
            location = new Location(world, 0, 0, 0);
            var block = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                    (proxy, method, arguments) -> method.getName().equals("getAsString") ? "minecraft:stone" : null);
            entity = (Entity) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isValid" -> valid;
                case "getWorld" -> location.getWorld();
                case "getLocation" -> location.clone();
                case "getVehicle" -> parent == null ? null : parent.entity;
                case "getPassengers" -> children.stream().map(child -> child.entity).toList();
                case "isVisible" -> visible;
                case "getTransformation" -> transformation;
                case "getBlock" -> block;
                case "getBillboard" -> Display.Billboard.FIXED;
                case "getBrightness", "getBackgroundColor" -> null;
                case "text" -> Component.text("Plate");
                case "getAlignment" -> TextDisplay.TextAlignment.RIGHT;
                case "getLineWidth" -> 150;
                case "getTextOpacity" -> (byte) 100;
                case "isShadowed" -> true;
                case "isSeeThrough" -> false;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> id.toString();
                default -> throw new UnsupportedOperationException("Unexpected entity access or mutation: " + method.getName());
            });
        }

        Node attach(Node child) {
            if (child.parent != null) child.parent.children.remove(child);
            child.parent = this;
            children.add(child);
            return this;
        }
    }
}
