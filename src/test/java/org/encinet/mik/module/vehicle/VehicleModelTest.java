package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleModelTest {
    @Test void capturePreservesYawPitchRotationAndNonUniformScaleAtFarCoordinates() {
        World world = proxy(World.class);
        Location origin = new Location(world, 20000000.125, 80.5, -20000000.375, 70, 0);
        Location source = origin.clone().add(1.125, 0.375, 0.75);
        source.setYaw(25);
        source.setPitch(-15);
        Transformation transformation = new Transformation(new Vector3f(0.2f, 0.3f, -0.1f),
                new Quaternionf().rotateZ(0.3f), new Vector3f(2, 0.5f, 1), new Quaternionf().rotateY(0.2f));
        BlockDisplay display = display(world, source, transformation);
        var definition = VehicleModel.capture("far_car", VehicleDefinition.Kind.CAR, origin, List.of(display));
        Matrix4f reconstructed = new Matrix4f().rotateY((float) -Math.toRadians(origin.getYaw()))
                .mul(definition.parts().getFirst().transform());
        Matrix4f expected = new Matrix4f().translation(1.125f, 0.375f, 0.75f)
                .rotateY((float) -Math.toRadians(source.getYaw())).rotateX((float) Math.toRadians(source.getPitch()))
                .translate(transformation.getTranslation()).rotate(transformation.getLeftRotation())
                .scale(transformation.getScale()).rotate(transformation.getRightRotation());
        assertTrue(expected.equals(reconstructed, 1.0E-5f), "expected=" + expected + " actual=" + reconstructed);
        assertEquals(Display.Billboard.FIXED.name(), definition.parts().getFirst().billboard());
    }

    @Test void modelBoundsIncludeAllTransformedCorners() {
        var part = VehicleFixtures.part(new Matrix4f().translation(2, 3, 4).rotateY((float) (Math.PI / 2)).scale(2, 1, 3));
        var bounds = VehicleModel.bounds(List.of(part));
        assertEquals(3.5, bounds.center().coordinateX(), 1.0E-5);
        assertEquals(3.5, bounds.center().coordinateY(), 1.0E-5);
        assertEquals(3, bounds.center().coordinateZ(), 1.0E-5);
        assertEquals(1.5, bounds.halfSize().coordinateX(), 1.0E-5);
        assertEquals(1, bounds.halfSize().coordinateZ(), 1.0E-5);
    }

    @Test void captureRejectsMissingEntitiesAndDifferentWorlds() {
        World world = proxy(World.class);
        World other = proxy(World.class);
        Location origin = new Location(world, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleModel.capture("empty", VehicleDefinition.Kind.CAR, origin, List.of()));
        assertThrows(IllegalArgumentException.class, () -> VehicleModel.capture("other", VehicleDefinition.Kind.CAR, origin,
                List.of(display(other, new Location(other, 0, 0, 0), new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf())))));
    }

    private BlockDisplay display(World world, Location location, Transformation transformation) {
        BlockData block = (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{BlockData.class},
                (object, method, args) -> method.getName().equals("getAsString") ? "minecraft:stone" : null);
        return (BlockDisplay) Proxy.newProxyInstance(BlockDisplay.class.getClassLoader(), new Class<?>[]{BlockDisplay.class},
                (object, method, args) -> switch (method.getName()) {
                    case "isValid" -> true;
                    case "getWorld" -> world;
                    case "getLocation" -> location.clone();
                    case "getTransformation" -> transformation;
                    case "getBlock" -> block;
                    case "getBillboard" -> Display.Billboard.FIXED;
                    default -> null;
                });
    }

    private <Value> Value proxy(Class<Value> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> switch (method.getName()) {
            case "equals" -> object == args[0];
            case "hashCode" -> System.identityHashCode(object);
            default -> null;
        }));
    }
}
