package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.encinet.mik.shared.menu.runtime.space.MenuModelGeometry;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;
import org.joml.Matrix4d;

import java.util.ArrayList;
import java.util.List;

/** Vanilla articulated pivots, in model pixels. Equipment uses conservative default-model envelopes. */
final class MenuArmorStandObstacles {
    private MenuArmorStandObstacles() { }

    static List<Box> boxes(Location eye, ArmorStand stand, double maximumDistance) {
        var equipment = stand.getEquipment();
        boolean body = stand.isVisible();
        boolean head = equipped(equipment, EquipmentSlot.HEAD);
        boolean chest = equipped(equipment, EquipmentSlot.CHEST);
        boolean legs = equipped(equipment, EquipmentSlot.LEGS);
        boolean feet = equipped(equipment, EquipmentSlot.FEET);
        boolean hand = equipped(equipment, EquipmentSlot.HAND);
        boolean offHand = equipped(equipment, EquipmentSlot.OFF_HAND);
        if (!body && !head && !chest && !legs && !feet && !hand && !offHand) return List.of();

        var attribute = stand.getAttribute(Attribute.SCALE);
        double scale = (attribute == null ? 1 : attribute.getValue()) * (stand.isSmall() ? .5 : 1);
        if (!Double.isFinite(scale) || scale <= 0) return List.of();
        var location = stand.getLocation();
        var offset = location.toVector().subtract(eye.toVector());
        // Even a posed, equipped stand fits this default-model envelope; most world stands need no part transforms.
        if (offset.lengthSquared() > Math.pow(maximumDistance + 4 * scale, 2)) return List.of();
        // LivingEntityRenderer's root reflection, foot anchor, and model-pixel conversion.
        var root = new Matrix4d().translate(offset.getX(), offset.getY(), offset.getZ())
                .rotateY(Math.PI - Math.toRadians(location.getYaw())).scale(-scale, -scale, scale)
                .translate(0, -1.501, 0).scale(1.0 / 16);
        List<Box> result = new ArrayList<>();
        var headPose = part(root, 0, 1, 0, stand.getHeadPose());
        if (body) add(result, headPose, new BoundingBox(-1, -7, -1, 1, 0, 1), maximumDistance);
        if (head) add(result, headPose, new BoundingBox(-6, -10, -6, 6, 2, 6), maximumDistance);
        var torso = part(root, 0, 0, 0, stand.getBodyPose());
        if (body) {
            add(result, torso, new BoundingBox(-6, 0, -1.5, 6, 3, 1.5), maximumDistance);
            add(result, torso, new BoundingBox(-3, 3, -1, -1, 10, 1), maximumDistance);
            add(result, torso, new BoundingBox(1, 3, -1, 3, 10, 1), maximumDistance);
            add(result, torso, new BoundingBox(-4, 10, -1, 4, 12, 1), maximumDistance);
        }
        if (chest) add(result, torso, new BoundingBox(-5, -1, -3, 5, 13, 3), maximumDistance);
        arm(result, part(root, -5, 2, 0, stand.getRightArmPose()), false,
                body && stand.hasArms(), chest, hand, maximumDistance);
        arm(result, part(root, 5, 2, 0, stand.getLeftArmPose()), true,
                body && stand.hasArms(), chest, offHand, maximumDistance);
        leg(result, root, -1.9, stand.getRightLegPose(), body, legs, feet, maximumDistance);
        leg(result, root, 1.9, stand.getLeftLegPose(), body, legs, feet, maximumDistance);
        if (body && stand.hasBasePlate())
            add(result, new Matrix4d(root).rotateY(-Math.toRadians(location.getYaw())),
                    new BoundingBox(-6, 23, -6, 6, 24, 6), maximumDistance);
        return List.copyOf(result);
    }

    private static void arm(List<Box> result, Matrix4d pose, boolean left, boolean body, boolean armor,
                            boolean held, double range) {
        if (body) add(result, pose, new BoundingBox(left ? 0 : -2, -2, -1, left ? 2 : 0, 10, 1), range);
        if (armor) add(result, pose, new BoundingBox(left ? -2 : -4, -3, -3, left ? 4 : 2, 11, 3), range);
        // ItemInHandLayer applies this pose even when the stand's wooden arms are hidden.
        if (held) add(result, new Matrix4d(pose).rotateX(-Math.PI / 2).rotateY(Math.PI)
                .translate(left ? -1 : 1, 2, -10), new BoundingBox(-8, -12, -8, 8, 12, 8), range);
    }

    private static void leg(List<Box> result, Matrix4d root, double x, EulerAngle rotation,
                            boolean body, boolean leggings, boolean boots, double range) {
        if (body) add(result, part(root, x, 12, 0, rotation), new BoundingBox(-1, 0, -1, 1, 11, 1), range);
        if (leggings || boots) add(result, part(root, x, 11, 0, rotation),
                new BoundingBox(-3, leggings ? -1 : 7, -3, 3, 13, 3), range);
    }

    private static Matrix4d part(Matrix4d root, double x, double y, double z, EulerAngle pose) {
        return new Matrix4d(root).translate(x, y, z).rotateZYX(pose.getZ(), pose.getY(), pose.getX());
    }

    private static boolean equipped(EntityEquipment equipment, EquipmentSlot slot) {
        return equipment != null && !equipment.getItem(slot).isEmpty();
    }

    private static void add(List<Box> result, Matrix4d pose, BoundingBox bounds, double range) {
        Box box = MenuModelGeometry.transform(bounds, pose, range);
        if (box != null) result.add(box);
    }
}
