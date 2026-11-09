package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry.Box;
import org.encinet.mik.shared.util.PaperTestRegistries;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuArmorStandObstaclesTest {
    private static PaperTestRegistries registries;
    @BeforeAll static void openRegistries() { registries = new PaperTestRegistries(); }
    @AfterAll static void closeRegistries() { registries.close(); }

    @Test void anInvisibleMarkerWithAHelmetOccludesWithoutAPhysicsBodyAndClearsWhenUnequipped() {
        var f = new Fixture();
        when(f.equipment.getItem(EquipmentSlot.HEAD)).thenReturn(f.occupied);
        var boxes = f.capture();
        assertEquals(1, boxes.size());
        assertTrue(new FloatingMenuWorldSpace(f.eye, boxes).firstHit(f.eye, new Vector(0, 0, 1), 2) < 1);
        when(f.equipment.getItem(EquipmentSlot.HEAD)).thenReturn(f.empty);
        assertTrue(f.capture().isEmpty());
    }

    @Test void aVisibleMarkerStillHasAnArticulatedWoodenModel() {
        var f = new Fixture();
        when(f.stand.isVisible()).thenReturn(true);
        assertFalse(f.capture().isEmpty());
        assertTrue(new FloatingMenuWorldSpace(f.eye, f.capture()).firstHit(f.eye, new Vector(0, 0, 1), 2) < 1);
    }

    @Test void helmetGeometryFollowsHeadPoseSmallStateAndTheScaleAttribute() {
        var f = new Fixture();
        when(f.equipment.getItem(EquipmentSlot.HEAD)).thenReturn(f.occupied);
        double width = f.capture().getFirst().envelope().getWidthX();
        when(f.scale.getValue()).thenReturn(2.0);
        assertEquals(width * 2, f.capture().getFirst().envelope().getWidthX(), 1e-8);
        when(f.stand.isSmall()).thenReturn(true);
        assertEquals(width, f.capture().getFirst().envelope().getWidthX(), 1e-8);
        when(f.stand.getHeadPose()).thenReturn(new EulerAngle(0, 0, Math.PI / 4));
        assertTrue(f.capture().getFirst().envelope().getWidthX() > width);
    }

    @Test void heldEquipmentIsVisibleWithHiddenArmsAndChangesWithTheirPose() {
        var f = new Fixture();
        when(f.equipment.getItem(EquipmentSlot.HAND)).thenReturn(f.occupied);
        var first = f.capture();
        assertEquals(1, first.size());
        when(f.stand.getRightArmPose()).thenReturn(new EulerAngle(Math.PI / 2, 0, 0));
        assertNotEquals(first.getFirst().envelope(), f.capture().getFirst().envelope());
    }

    @Test void aScaledMarkersModelIsSampledEvenOutsideThePhysicsQueryRange() {
        var f = new Fixture();
        when(f.equipment.getItem(EquipmentSlot.HEAD)).thenReturn(f.occupied);
        when(f.scale.getValue()).thenReturn(8.0);
        when(f.stand.getLocation()).thenReturn(new Location(f.fixture.world(), 0, 54, 5));
        when(f.stand.getBoundingBox()).thenReturn(new BoundingBox(0, 54, 5, 0, 54, 5));
        var boxes = f.capture();
        assertFalse(boxes.isEmpty());
        assertTrue(new FloatingMenuWorldSpace(f.eye, boxes).firstHit(f.eye, new Vector(0, 0, 1), 8) < 8);
        when(f.stand.getLocation()).thenReturn(new Location(f.fixture.world(), 0, 54, 80));
        assertTrue(f.capture().isEmpty());
    }

    @Test void theBasePlateKeepsItsWorldOrientationWhenTheStandTurns() {
        var f = new Fixture();
        when(f.stand.isVisible()).thenReturn(true);
        when(f.stand.hasBasePlate()).thenReturn(true);
        var before = f.capture().getLast().envelope();
        when(f.stand.getLocation()).thenReturn(new Location(f.fixture.world(), 0, 64, 1, 45, 0));
        var after = f.capture().getLast().envelope();
        assertEquals(before.getWidthX(), after.getWidthX(), 1e-8);
        assertEquals(before.getWidthZ(), after.getWidthZ(), 1e-8);
    }

    @Test void invisibleLivingEquipmentIsNotDiscardedWithTheBody() {
        var f = new Fixture();
        LivingEntity living = mock(LivingEntity.class);
        when(living.isValid()).thenReturn(true);
        when(living.isInvisible()).thenReturn(true);
        when(living.getEquipment()).thenReturn(f.equipment);
        when(living.canUseEquipmentSlot(EquipmentSlot.HEAD)).thenReturn(true);
        when(living.getBoundingBox()).thenReturn(new BoundingBox(-.2, 64, 1, .2, 66, 1.2));
        f.fixture.entity(living);
        when(f.viewer.canSee(living)).thenReturn(true);
        assertTrue(f.capture().isEmpty());
        when(f.equipment.getItem(EquipmentSlot.HEAD)).thenReturn(f.occupied);
        assertFalse(f.capture().isEmpty());
    }

    private static final class Fixture {
        private final FloatingMenuTestWorld fixture = new FloatingMenuTestWorld();
        private final Location eye = new Location(fixture.world(), 0, 65.6, 0);
        private final Player viewer = mock(Player.class);
        private final ArmorStand stand = mock(ArmorStand.class);
        private final EntityEquipment equipment = mock(EntityEquipment.class);
        private final AttributeInstance scale = mock(AttributeInstance.class);
        private final ItemStack empty = mock(ItemStack.class);
        private final ItemStack occupied = mock(ItemStack.class);

        private Fixture() {
            when(empty.isEmpty()).thenReturn(true);
            when(occupied.isEmpty()).thenReturn(false);
            when(equipment.getItem(any())).thenReturn(empty);
            when(stand.isValid()).thenReturn(true);
            when(viewer.canSee(stand)).thenReturn(true);
            when(stand.isMarker()).thenReturn(true);
            when(stand.getBoundingBox()).thenReturn(new BoundingBox(0, 64, 1, 0, 64, 1));
            when(stand.getLocation()).thenReturn(new Location(fixture.world(), 0, 64, 1));
            when(stand.getEquipment()).thenReturn(equipment);
            when(stand.getAttribute(Attribute.SCALE)).thenReturn(scale);
            when(scale.getValue()).thenReturn(1.0);
            when(stand.getHeadPose()).thenReturn(EulerAngle.ZERO);
            when(stand.getBodyPose()).thenReturn(EulerAngle.ZERO);
            when(stand.getRightArmPose()).thenReturn(EulerAngle.ZERO);
            when(stand.getLeftArmPose()).thenReturn(EulerAngle.ZERO);
            when(stand.getRightLegPose()).thenReturn(EulerAngle.ZERO);
            when(stand.getLeftLegPose()).thenReturn(EulerAngle.ZERO);
            fixture.entity(stand);
        }

        private List<Box> capture() { return MenuEntityObstacles.capture(viewer, eye, 8); }
    }
}
