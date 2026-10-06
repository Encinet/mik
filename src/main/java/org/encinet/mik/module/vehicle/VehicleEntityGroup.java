package org.encinet.mik.module.vehicle;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class VehicleEntityGroup {
    private final VehicleInstance instance;
    private final World world;
    private final NamespacedKey key;
    private final List<Display> displays = new ArrayList<>();
    private final List<ArmorStand> seats = new ArrayList<>();
    private final List<Interaction> interactions = new ArrayList<>();
    private boolean removing;

    VehicleEntityGroup(VehicleInstance instance, World world, NamespacedKey key) {
        this.instance = instance;
        this.world = world;
        this.key = key;
        try {
            Location location = location(instance.body.origin());
            for (VehicleDefinition.Part part : instance.body.definition.parts()) {
                Display display = createDisplay(location, part, this::tag);
                displays.add(display);
            }
            for (VehicleDefinition.Seat seat : instance.body.definition.seats()) {
                ArmorStand host = world.spawn(location(instance.body.point(seat.position())), ArmorStand.class, entity -> {
                    entity.setVisible(false);
                    entity.setMarker(true);
                    entity.setSmall(true);
                    entity.setGravity(false);
                    entity.setInvulnerable(true);
                    entity.setSilent(true);
                    entity.setCollidable(false);
                    entity.setBasePlate(false);
                });
                seats.add(host);
                tag(host);
            }
            for (VehicleDefinition.Collider collider : instance.body.definition.colliders()) {
                Interaction interaction = world.spawn(location, Interaction.class);
                interactions.add(interaction);
                tag(interaction);
                interaction.setResponsive(true);
            }
            synchronize();
        } catch (RuntimeException exception) {
            try { remove(); } catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    static Display createDisplay(Location location, VehicleDefinition.Part part, java.util.function.Consumer<Display> initializer) {
        java.util.function.Consumer<Display> configure = entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setBillboard(Display.Billboard.valueOf(part.billboard()));
            if (part.blockLight() >= 0 && part.skyLight() >= 0)
                entity.setBrightness(new Display.Brightness(part.blockLight(), part.skyLight()));
            entity.setViewRange(1.5f);
            entity.setDisplayWidth(48);
            entity.setDisplayHeight(48);
            entity.setTeleportDuration(1);
            entity.setInterpolationDuration(1);
            initializer.accept(entity);
        };
        return switch (part.kind()) {
            case BLOCK -> location.getWorld().spawn(location, BlockDisplay.class, entity -> {
                entity.setBlock(Bukkit.createBlockData(part.payload()));
                configure.accept(entity);
            });
            case ITEM -> location.getWorld().spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(ItemStack.deserializeBytes(Base64.getDecoder().decode(part.payload())));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(part.itemTransform()));
                configure.accept(entity);
            });
            case TEXT -> location.getWorld().spawn(location, TextDisplay.class, entity -> {
                entity.text(GsonComponentSerializer.gson().deserialize(part.payload()));
                entity.setBackgroundColor(Color.fromARGB(part.background()));
                entity.setLineWidth(part.lineWidth());
                entity.setTextOpacity(part.textOpacity());
                entity.setShadowed(part.shadow());
                entity.setSeeThrough(part.seeThrough());
                entity.setDefaultBackground(false);
                entity.setAlignment(TextDisplay.TextAlignment.valueOf(part.alignment()));
                configure.accept(entity);
            });
        };
    }

    void synchronize() {
        VehicleBody body = instance.body;
        Location origin = location(body.origin());
        Matrix4f rotation = new Matrix4f().rotate(new Quaternionf().set(body.orientation));
        for (int index = 0; index < displays.size(); index++) {
            Display display = displays.get(index);
            VehicleDefinition.Part part = body.definition.parts().get(index);
            Matrix4f transform = new Matrix4f(rotation).mul(part.transform());
            switch (part.animation()) {
                case FRONT_WHEEL -> transform.rotateY((float) (-body.steering * 0.55)).rotateX((float) body.wheelAngle);
                case WHEEL -> transform.rotateX((float) body.wheelAngle);
                case PROPELLER -> transform.rotateZ((float) body.propellerAngle);
                case RUDDER -> transform.rotateY((float) (-body.steering * 0.55));
                case BODY -> { }
            }
            if (!display.teleport(origin)) throw new IllegalStateException("Display teleport rejected");
            display.setTransformationMatrix(transform);
            display.setInterpolationDelay(0);
        }
        double yaw = Math.toDegrees(Math.atan2(-body.forward().coordinateX(), body.forward().coordinateZ()));
        for (int index = 0; index < seats.size(); index++) {
            Location seat = location(body.point(body.definition.seats().get(index).position()));
            seat.setYaw((float) yaw);
            VehicleSeatMover.move(seats.get(index), seat);
        }
        for (int index = 0; index < interactions.size(); index++) {
            VehicleCollision.Box box = VehicleCollision.Box.body(body.definition.colliders().get(index), body.origin(), body.orientation);
            VehicleVector extent = box.extent();
            Interaction interaction = interactions.get(index);
            interaction.teleport(location(box.center().subtract(new VehicleVector(0, extent.coordinateY(), 0))));
            interaction.setInteractionWidth((float) Math.max(extent.coordinateX(), extent.coordinateZ()) * 2);
            interaction.setInteractionHeight((float) extent.coordinateY() * 2);
        }
    }

    Player driver() {
        for (int index = 0; index < seats.size(); index++) if (instance.body.definition.seats().get(index).driver()) {
            for (Entity passenger : seats.get(index).getPassengers()) if (passenger instanceof Player player) return player;
        }
        return null;
    }

    boolean occupied() { return seats.stream().anyMatch(seat -> !seat.getPassengers().isEmpty()); }
    java.util.Set<java.util.UUID> entityIds() {
        java.util.Set<java.util.UUID> ids = new java.util.LinkedHashSet<>();
        for (Entity entity : displays) ids.add(entity.getUniqueId());
        for (Entity entity : seats) ids.add(entity.getUniqueId());
        for (Entity entity : interactions) ids.add(entity.getUniqueId());
        return java.util.Set.copyOf(ids);
    }
    int passengerCount() { return seats.stream().mapToInt(seat -> seat.getPassengers().size()).sum(); }
    boolean contains(Entity entity) { return displays.contains(entity) || seats.contains(entity) || interactions.contains(entity); }
    boolean valid() { return displays.stream().allMatch(Entity::isValid) && seats.stream().allMatch(Entity::isValid)
            && interactions.stream().allMatch(Entity::isValid); }
    int firstAvailable(boolean driverRequired) {
        for (int index = 0; index < seats.size(); index++)
            if (seats.get(index).getPassengers().isEmpty() && driverRequired == instance.body.definition.seats().get(index).driver()) return index;
        return -1;
    }
    int seatOf(Entity entity) {
        for (int index = 0; index < seats.size(); index++) if (seats.get(index).equals(entity)) return index;
        return -1;
    }
    ArmorStand seat(int index) { return seats.get(index); }
    boolean removing() { return removing; }

    void remove() {
        removing = true;
        RuntimeException failure = null;
        List<Entity> all = new ArrayList<>(seats);
        all.addAll(displays);
        all.addAll(interactions);
        for (Entity entity : all) {
            try {
                if (entity instanceof ArmorStand) entity.eject();
                entity.remove();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        displays.clear();
        seats.clear();
        interactions.clear();
        if (failure != null) throw failure;
    }

    private void tag(Entity entity) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, instance.id.toString());
    }
    private Location location(VehicleVector point) { return new Location(world, point.coordinateX(), point.coordinateY(), point.coordinateZ()); }
}
