package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

final class VehicleModel {
    static Quaterniond orientation(float yaw) { return new Quaterniond().rotateY(-Math.toRadians(yaw)); }

    static VehicleDefinition capture(String id, VehicleDefinition.Kind kind, Location origin, Collection<Display> displays) {
        List<VehicleDefinition.Part> parts = captureParts(origin, displays);
        return VehicleDefinition.preset(id, kind, parts, bounds(parts));
    }

    static List<VehicleDefinition.Part> captureParts(Location origin, Collection<Display> displays) {
        if (displays.isEmpty() || displays.size() > 128) throw new IllegalArgumentException("Select 1..128 display entities");
        List<VehicleDefinition.Part> parts = new ArrayList<>();
        for (Display display : displays) {
            if (!display.isValid() || !display.getWorld().equals(origin.getWorld())) throw new IllegalArgumentException("Invalid selection world");
            Matrix4f local = relativeTransform(origin, display);
            VehicleDefinition.PartKind partKind;
            String payload;
            String itemTransform = "NONE";
            String alignment = "CENTER";
            int background = 0x40000000;
            int width = 200;
            byte opacity = -1;
            boolean shadow = false;
            boolean seeThrough = false;
            if (display instanceof BlockDisplay block) { partKind = VehicleDefinition.PartKind.BLOCK; payload = block.getBlock().getAsString(); }
            else if (display instanceof ItemDisplay item) {
                partKind = VehicleDefinition.PartKind.ITEM;
                payload = Base64.getEncoder().encodeToString(item.getItemStack().serializeAsBytes());
                itemTransform = item.getItemDisplayTransform().name();
            } else if (display instanceof TextDisplay text) {
                partKind = VehicleDefinition.PartKind.TEXT;
                payload = GsonComponentSerializer.gson().serialize(text.text());
                alignment = text.getAlignment().name();
                background = text.getBackgroundColor() == null ? 0x40000000 : text.getBackgroundColor().asARGB();
                width = text.getLineWidth();
                opacity = text.getTextOpacity();
                shadow = text.isShadowed();
                seeThrough = text.isSeeThrough();
            } else throw new IllegalArgumentException("Unsupported display type");
            Display.Brightness brightness = display.getBrightness();
            parts.add(new VehicleDefinition.Part(partKind, payload, local, VehicleDefinition.Animation.BODY,
                    display.getBillboard().name(), itemTransform, alignment, brightness == null ? -1 : brightness.getBlockLight(),
                    brightness == null ? -1 : brightness.getSkyLight(), background, width, opacity, shadow, seeThrough));
        }
        return List.copyOf(parts);
    }

    static Matrix4f relativeTransform(Location origin, Display display) {
        Location location = display.getLocation();
        Transformation transformation = display.getTransformation();
        return new Matrix4f().rotateY((float) Math.toRadians(origin.getYaw()))
                .translate((float) (location.getX() - origin.getX()), (float) (location.getY() - origin.getY()), (float) (location.getZ() - origin.getZ()))
                .rotateY((float) -Math.toRadians(location.getYaw())).rotateX((float) Math.toRadians(location.getPitch()))
                .translate(transformation.getTranslation()).rotate(transformation.getLeftRotation())
                .scale(transformation.getScale()).rotate(transformation.getRightRotation());
    }

    static VehicleDefinition.Collider bounds(List<VehicleDefinition.Part> parts) {
        Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
        boolean found = false;
        for (VehicleDefinition.Part part : parts) {
            if (part.kind() == VehicleDefinition.PartKind.TEXT) continue;
            found = true;
            float low = part.kind() == VehicleDefinition.PartKind.BLOCK ? 0 : -0.5f;
            float high = part.kind() == VehicleDefinition.PartKind.BLOCK ? 1 : 0.5f;
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = part.transform().transformPosition(new Vector3f((corner & 1) == 0 ? low : high,
                        (corner & 2) == 0 ? low : high, (corner & 4) == 0 ? low : high));
                minimum.min(point);
                maximum.max(point);
            }
        }
        if (!found) throw new IllegalArgumentException("At least one block or item display required");
        VehicleVector center = new VehicleVector((minimum.x + maximum.x) / 2, (minimum.y + maximum.y) / 2,
                (minimum.z + maximum.z) / 2);
        VehicleVector half = new VehicleVector(Math.max(0.25, (maximum.x - minimum.x) / 2),
                Math.max(0.25, (maximum.y - minimum.y) / 2), Math.max(0.25, (maximum.z - minimum.z) / 2));
        return new VehicleDefinition.Collider(center, half);
    }
}
