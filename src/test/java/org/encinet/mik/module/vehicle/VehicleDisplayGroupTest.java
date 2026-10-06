package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.encinet.mik.module.i18n.Message;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VehicleDisplayGroupTest {
    @Test void axiomGroupWithSharedEntityAnchorAndBakedOffsetsImportsAllMembersForEveryVehicleKind() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var child = VehicleGroupFixtures.block(world);
        var nested = new VehicleGroupFixtures.Node(TextDisplay.class, world);
        root.location = new Location(world, 20000000, 64, -20000000);
        child.location = root.location.clone();
        nested.location = root.location.clone();
        root.transformation = new Transformation(new Vector3f(-2, 0, 4), new Quaternionf().rotateY(0.3f),
                new Vector3f(2, 1, 0.5f), new Quaternionf().rotateZ(0.2f));
        child.transformation = new Transformation(new Vector3f(3, 0, 6), new Quaternionf(),
                new Vector3f(1), new Quaternionf());
        nested.transformation = new Transformation(new Vector3f(3, 1, 6), new Quaternionf().rotateY(0.4f),
                new Vector3f(0.5f), new Quaternionf());
        root.attach(child);
        child.attach(nested);
        Location eye = root.location.clone().add(3.25, 0.25, 0);
        Display picked = VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1),
                List.of(root.entity, child.entity, nested.entity), entity -> false);
        assertSame(child.entity, picked);
        var group = VehicleDisplayGroup.inspect(picked, eye, entity -> false);
        assertEquals(root.id, group.root());
        assertEquals(List.of(root.entity, child.entity, nested.entity), group.displays());
        Matrix4f expectedRoot = new Matrix4f().translate(-2, 0, 4).rotate(root.transformation.getLeftRotation())
                .scale(2, 1, 0.5f).rotate(root.transformation.getRightRotation());
        Matrix4f expectedChild = new Matrix4f().translation(3, 0, 6);
        Matrix4f expectedNested = new Matrix4f().translate(3, 1, 6).rotateY(0.4f).scale(0.5f);
        for (VehicleDefinition.Kind kind : VehicleDefinition.Kind.values()) {
            var imported = VehicleModel.capture("axiom_group", kind, group.origin(), group.displays());
            assertEquals(kind, imported.kind());
            assertEquals(3, imported.parts().size());
            assertTrue(expectedRoot.equals(imported.parts().get(0).transform(), 1.0E-5f));
            assertTrue(expectedChild.equals(imported.parts().get(1).transform(), 1.0E-5f));
            assertTrue(expectedNested.equals(imported.parts().get(2).transform(), 1.0E-5f));
            var draft = VehicleModelDraft.imported(imported);
            assertNull(draft.base());
            assertEquals(imported.parts(), draft.validate().parts());
        }
        assertEquals(root.location, child.entity.getLocation());
        assertEquals(root.entity, child.entity.getVehicle());
        assertEquals(child.entity, nested.entity.getVehicle());
    }

    @Test void selectingAnyNestedMemberImportsExactlyItsTreeAndNotNearbyDisplays() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var child = VehicleGroupFixtures.block(world);
        var nested = VehicleGroupFixtures.block(world);
        var neighbor = VehicleGroupFixtures.block(world);
        root.attach(child);
        child.attach(nested);
        var group = VehicleDisplayGroup.inspect(nested.entity, root.location, entity -> false);
        assertEquals(root.id, group.root());
        assertEquals(List.of(root.entity, child.entity, nested.entity), group.displays());
        assertEquals(root.id, group.parents().get(child.id));
        assertEquals(child.id, group.parents().get(nested.id));
        assertFalse(group.parents().containsKey(neighbor.id));
        assertEquals(child.entity, root.entity.getPassengers().getFirst());
        assertEquals(root.entity, child.entity.getVehicle());
    }

    @Test void invisibleCarriersAreNotConvertedToVisualParts() {
        var world = VehicleGroupFixtures.world();
        var root = new VehicleGroupFixtures.Node(Marker.class, world);
        var stand = new VehicleGroupFixtures.Node(ArmorStand.class, world);
        var block = VehicleGroupFixtures.block(world);
        root.attach(stand);
        stand.attach(block);
        var group = VehicleDisplayGroup.inspect(block.entity, root.location, entity -> false);
        assertEquals(3, group.parents().size());
        assertEquals(List.of(block.entity), group.displays());
        stand.visible = true;
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(block.entity, root.location, entity -> false));
    }

    @Test void liveVehiclesPreviewsPlayersInvalidWorldsAndDistantMembersRejectTheWholeGroup() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var child = VehicleGroupFixtures.block(world);
        root.attach(child);
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> entity.getUniqueId().equals(child.id)));
        child.valid = false;
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
        child.valid = true;
        child.location = new Location(VehicleGroupFixtures.world(), 0, 0, 0);
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
        child.location = new Location(world, 33, 0, 0);
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
        child.location = root.location.clone();
        root.attach(new VehicleGroupFixtures.Node(Player.class, world));
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
    }

    @Test void cycleAndDuplicateReferencesAreRejectedRatherThanPartiallyCaptured() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var child = VehicleGroupFixtures.block(world);
        root.attach(child);
        root.children.add(child);
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
        root.children.removeLast();
        root.parent = child;
        child.children.add(root);
        assertCode(Message.VEHICLE_GROUP_INVALID, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
    }

    @Test void displayAndTotalMemberLimitsAreEnforcedBeforeImport() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        for (int index = 1; index < 128; index++) root.attach(VehicleGroupFixtures.block(world));
        assertEquals(128, VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false).displays().size());
        root.attach(VehicleGroupFixtures.block(world));
        assertCode(Message.VEHICLE_GROUP_LIMIT, () -> VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false));
        var carrier = new VehicleGroupFixtures.Node(Marker.class, world);
        carrier.attach(VehicleGroupFixtures.block(world));
        for (int index = 2; index < 256; index++) carrier.attach(new VehicleGroupFixtures.Node(Marker.class, world));
        assertEquals(256, VehicleDisplayGroup.inspect(carrier.entity, carrier.location, entity -> false).parents().size());
        carrier.attach(new VehicleGroupFixtures.Node(Marker.class, world));
        assertCode(Message.VEHICLE_GROUP_LIMIT, () -> VehicleDisplayGroup.inspect(carrier.entity, carrier.location, entity -> false));
    }

    @Test void emptyAndMissingTreesHaveExplicitFeedback() {
        var world = VehicleGroupFixtures.world();
        var carrier = new VehicleGroupFixtures.Node(Marker.class, world);
        assertCode(Message.VEHICLE_GROUP_NOT_FOUND, () -> VehicleDisplayGroup.inspect(null, carrier.location, entity -> false));
        assertCode(Message.VEHICLE_GROUP_NOT_FOUND, () -> VehicleDisplayGroup.inspect(carrier.entity, carrier.location, entity -> false));
    }

    @Test void selectedStructureMustStillMatchBeforeCaptureAndManualModeDropsItsBinding() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var child = VehicleGroupFixtures.block(world);
        var nested = VehicleGroupFixtures.block(world);
        root.attach(child);
        root.attach(nested);
        var group = VehicleDisplayGroup.inspect(child.entity, root.location, entity -> false);
        var selection = VehicleSelection.group(group);
        assertEquals(Set.of(root.id, child.id, nested.id), selection.ids());
        assertDoesNotThrow(() -> selection.requireUnchanged(group));
        child.attach(nested);
        var changed = VehicleDisplayGroup.inspect(root.entity, root.location, entity -> false);
        assertEquals(selection.ids(), VehicleSelection.group(changed).ids());
        assertCode(Message.VEHICLE_GROUP_CHANGED, () -> selection.requireUnchanged(changed));
        var manual = VehicleSelection.manual(selection.ids());
        assertNull(manual.root());
        assertTrue(manual.structure().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> selection.ids().clear());
        assertThrows(UnsupportedOperationException.class, () -> group.parents().clear());
    }

    @Test void importFlattensCurrentWorldTransformsWithoutApplyingParentMatricesTwice() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var child = VehicleGroupFixtures.block(world);
        root.location = new Location(world, 20000000, 80, -20000000, 65, 0);
        child.location = root.location.clone().add(1.5, 0.5, 2);
        child.location.setYaw(15);
        root.transformation = new Transformation(new Vector3f(0.2f, 0, 0), new Quaternionf().rotateZ(0.1f), new Vector3f(2, 1, 1), new Quaternionf());
        child.transformation = new Transformation(new Vector3f(0.1f, 0.2f, 0), new Quaternionf().rotateX(0.3f), new Vector3f(1, 0.5f, 2), new Quaternionf().rotateY(0.2f));
        root.attach(child);
        var group = VehicleDisplayGroup.inspect(child.entity, root.location, entity -> false);
        var parts = VehicleModel.captureParts(group.origin(), group.displays());
        Matrix4f expected = new Matrix4f().rotateY((float) Math.toRadians(65)).translate(1.5f, 0.5f, 2)
                .rotateY((float) -Math.toRadians(15)).translate(child.transformation.getTranslation())
                .rotate(child.transformation.getLeftRotation()).scale(child.transformation.getScale()).rotate(child.transformation.getRightRotation());
        assertTrue(expected.equals(parts.get(1).transform(), 1.0E-5f));
        assertEquals(child.location, child.entity.getLocation());
        Location snapshotOrigin = group.origin();
        snapshotOrigin.add(10, 0, 0);
        root.location.add(1, 0, 0);
        assertEquals(20000000, group.origin().getX());
    }

    @Test void textPropertiesAreCopiedButSourceHierarchyAndTextStayUntouched() {
        var world = VehicleGroupFixtures.world();
        var root = VehicleGroupFixtures.block(world);
        var text = new VehicleGroupFixtures.Node(TextDisplay.class, world);
        root.attach(text);
        var group = VehicleDisplayGroup.inspect(text.entity, root.location, entity -> false);
        var parts = VehicleModel.captureParts(group.origin(), group.displays());
        assertEquals(VehicleDefinition.PartKind.TEXT, parts.get(1).kind());
        assertEquals("RIGHT", parts.get(1).alignment());
        assertEquals(150, parts.get(1).lineWidth());
        assertEquals((byte) 100, parts.get(1).textOpacity());
        assertTrue(parts.get(1).shadow());
        assertEquals(root.entity, text.entity.getVehicle());
        assertEquals("Plate", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(((TextDisplay) text.entity).text()));
    }

    @Test void importedModelIsANewUnsavedDraftAndEditsDoNotMutateTheCapturedDefinition() {
        var source = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        var draft = VehicleModelDraft.imported(source);
        assertNull(draft.base());
        assertTrue(draft.dirty());
        assertEquals(source.id(), draft.validate().id());
        assertEquals(source.engine(), draft.validate().engine());
        Matrix4f original = new Matrix4f(source.parts().getFirst().transform());
        draft.changePart(0, "position", "2 3 4");
        assertTrue(original.equals(source.parts().getFirst().transform()));
        assertFalse(original.equals(draft.parts().getFirst().transform()));
        assertTrue(draft.undo());
        assertTrue(original.equals(draft.parts().getFirst().transform(), 1.0E-5f));
    }

    private static void assertCode(Message message, Runnable action) {
        assertEquals(message.name(), assertThrows(VehicleImportException.class, action::run).getMessage());
    }
}
