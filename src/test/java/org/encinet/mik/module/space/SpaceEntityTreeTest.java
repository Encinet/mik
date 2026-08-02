package org.encinet.mik.module.space;

import io.papermc.paper.math.Angle;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpaceEntityTreeTest {

    private static final World WORLD = world();

    @Test
    void nestedVehicleTreeKeepsSeatsAndTransformsEveryMemberState() {
        FakeEntity vehicle = new FakeEntity(Vehicle.class,
                location(0.0, 64.0, 0.0, new SpaceVector(0.0, 0.0, 1.0)),
                new BoundingBox(-0.7, 64.0, -0.7, 0.7, 64.7, 0.7),
                new Vector(0.1, 0.0, 0.8), 1.5F, 0.0F);
        FakeEntity rider = new FakeEntity(LivingEntity.class,
                location(0.0, 64.7, 0.0, new SpaceVector(0.3, -0.1, 0.9)),
                new BoundingBox(-0.3, 64.7, -0.3, 0.3, 66.5, 0.3),
                new Vector(-0.2, 0.25, 0.6), 4.0F, 25.0F);
        FakeEntity shoulderPassenger = new FakeEntity(LivingEntity.class,
                location(0.0, 66.5, 0.0, new SpaceVector(-0.2, 0.15, 0.8)),
                new BoundingBox(-0.2, 66.5, -0.2, 0.2, 67.1, 0.2),
                new Vector(0.15, -0.05, 0.4), 7.0F, -40.0F);
        vehicle.mount(rider);
        rider.mount(shoulderPassenger);

        SpaceEntityTree tree = SpaceEntityTree.capture(vehicle.entity());
        assertVector(new SpaceVector(-0.7, 0.0, -0.7),
                tree.collisionShape().minimum());
        assertVector(new SpaceVector(0.7, 3.1, 0.7),
                tree.collisionShape().maximum());

        SpaceFrame source = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 64.0, 0.0),
                new SpaceVector(0.0, 0.0, 1.0),
                new SpaceVector(0.0, 1.0, 0.0));
        SpaceFrame destination = SpaceFrame.fromAxes(
                new SpaceVector(30.0, 50.0, 10.0),
                new SpaceVector(1.0, 0.0, 0.0),
                new SpaceVector(0.0, 1.0, 0.0));
        SpaceTransform transform = new SpaceTransform(source, destination);
        SpaceVector rootLook = destination.forward();
        SpaceVector rootVelocity = transform.mapVector(
                new SpaceVector(0.1, 0.0, 0.8));
        SpaceTransition transition = new SpaceTransition(
                "mounted", "mounted:route", "source", "destination", "world",
                transform, new SpaceAperture(4.0, 4.0),
                destination.origin(), destination.origin(), rootLook, rootVelocity);

        SpaceVector riderLook = direction(rider.location());
        SpaceVector riderVelocity = vector(rider.velocity());
        SpaceVector riderBody = bodyDirection(rider.bodyYaw());
        SpaceVector nestedLook = direction(shoulderPassenger.location());
        SpaceVector nestedVelocity = vector(shoulderPassenger.velocity());
        SpaceVector nestedBody = bodyDirection(shoulderPassenger.bodyYaw());

        tree.apply(transition, new SpaceMovementResolver.Result(
                destination.origin(), false, false, false));

        assertSame(vehicle.entity(), rider.entity().getVehicle());
        assertSame(rider.entity(), shoulderPassenger.entity().getVehicle());
        assertEquals(List.of(rider.entity()), vehicle.entity().getPassengers());
        assertEquals(List.of(shoulderPassenger.entity()), rider.entity().getPassengers());

        assertVector(rootLook, vehicle.currentLook());
        assertVector(rootVelocity, vector(vehicle.velocity()));
        assertVector(transform.mapDirection(riderLook), rider.currentLook());
        assertVector(transform.mapVector(riderVelocity), vector(rider.velocity()));
        assertVector(transform.mapDirection(riderBody), bodyDirection(rider.bodyYaw()));
        assertVector(transform.mapDirection(nestedLook), shoulderPassenger.currentLook());
        assertVector(transform.mapVector(nestedVelocity),
                vector(shoulderPassenger.velocity()));
        assertVector(transform.mapDirection(nestedBody),
                bodyDirection(shoulderPassenger.bodyYaw()));
        assertEquals(1.5F, vehicle.fallDistance());
        assertEquals(4.0F, rider.fallDistance());
        assertEquals(7.0F, shoulderPassenger.fallDistance());
    }

    @Test
    void passengerCannotBeCapturedAsAnIndependentTraversalRoot() {
        FakeEntity vehicle = new FakeEntity(Vehicle.class,
                location(0.0, 64.0, 0.0, new SpaceVector(0.0, 0.0, 1.0)),
                new BoundingBox(-0.5, 64.0, -0.5, 0.5, 65.0, 0.5),
                new Vector(), 0.0F, 0.0F);
        FakeEntity rider = new FakeEntity(LivingEntity.class,
                location(0.0, 65.0, 0.0, new SpaceVector(0.0, 0.0, 1.0)),
                new BoundingBox(-0.3, 65.0, -0.3, 0.3, 66.8, 0.3),
                new Vector(), 0.0F, 0.0F);
        vehicle.mount(rider);

        assertThrows(IllegalArgumentException.class,
                () -> SpaceEntityTree.capture(rider.entity()));
    }

    private static Location location(
            double x,
            double y,
            double z,
            SpaceVector look
    ) {
        Location location = new Location(WORLD, x, y, z);
        location.setDirection(new Vector(look.x(), look.y(), look.z()));
        return location;
    }

    private static SpaceVector direction(Location location) {
        return vector(location.getDirection());
    }

    private static SpaceVector bodyDirection(float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        return new SpaceVector(-Math.sin(yaw), 0.0, Math.cos(yaw));
    }

    private static SpaceVector vector(Vector vector) {
        return new SpaceVector(vector.getX(), vector.getY(), vector.getZ());
    }

    private static void assertVector(SpaceVector expected, SpaceVector actual) {
        // Bukkit rotations are stored as floats, so direction round-trips lose a
        // small amount of precision even though velocity remains double based.
        assertEquals(expected.x(), actual.x(), 1.0E-6);
        assertEquals(expected.y(), actual.y(), 1.0E-6);
        assertEquals(expected.z(), actual.z(), 1.0E-6);
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "FakeWorld[world]";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    private static final class FakeEntity implements InvocationHandler {

        private final Entity entity;
        private final UUID id = UUID.randomUUID();
        private final List<Entity> passengers = new ArrayList<>();
        private Location location;
        private BoundingBox bounds;
        private Vector velocity;
        private Entity vehicle;
        private float fallDistance;
        private float bodyYaw;

        private FakeEntity(
                Class<? extends Entity> type,
                Location location,
                BoundingBox bounds,
                Vector velocity,
                float fallDistance,
                float bodyYaw
        ) {
            this.location = location.clone();
            this.bounds = bounds.clone();
            this.velocity = velocity.clone();
            this.fallDistance = fallDistance;
            this.bodyYaw = bodyYaw;
            this.entity = (Entity) Proxy.newProxyInstance(
                    type.getClassLoader(), new Class<?>[]{type}, this);
        }

        private Entity entity() {
            return entity;
        }

        private Location location() {
            return location.clone();
        }

        private Vector velocity() {
            return velocity.clone();
        }

        private float fallDistance() {
            return fallDistance;
        }

        private float bodyYaw() {
            return bodyYaw;
        }

        private SpaceVector currentLook() {
            return direction(location);
        }

        private void mount(FakeEntity passenger) {
            passengers.add(passenger.entity);
            passenger.vehicle = entity;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getVehicle" -> vehicle;
                case "getPassengers" -> List.copyOf(passengers);
                case "getLocation" -> location.clone();
                case "getWorld" -> WORLD;
                case "getBoundingBox" -> bounds.clone();
                case "getVelocity" -> velocity.clone();
                case "setVelocity" -> {
                    velocity = ((Vector) arguments[0]).clone();
                    yield null;
                }
                case "getFallDistance" -> fallDistance;
                case "setFallDistance" -> {
                    fallDistance = (float) arguments[0];
                    yield null;
                }
                case "getBodyYaw" -> bodyYaw;
                case "setBodyYaw" -> {
                    bodyYaw = (float) arguments[0];
                    yield null;
                }
                case "setRotation" -> {
                    location.setYaw(((Angle) arguments[0]).degrees());
                    location.setPitch(((Angle) arguments[1]).degrees());
                    yield null;
                }
                case "isValid" -> true;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "FakeEntity[" + id + "]";
                default -> defaultValue(method.getReturnType());
            };
        }
    }
}
