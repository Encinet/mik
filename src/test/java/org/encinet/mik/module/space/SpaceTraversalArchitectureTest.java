package org.encinet.mik.module.space;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceTraversalArchitectureTest {

    @Test
    void onlyOutermostRootsMoveAndEveryRootUsesPassengerAwareRelocation()
            throws IOException {
        String controller = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/space/SpaceTraversalController.java"));
        String module = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/space/NonEuclideanSpaceModule.java"));
        String tree = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/space/SpaceEntityTree.java"));
        String remoteSnap = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/space/SpaceRemoteEntitySnap.java"));

        assertTrue(controller.contains("player.getVehicle() != null"));
        assertTrue(controller.contains("entity.getVehicle() != null"));
        assertTrue(controller.contains("vehicle.getVehicle() != null"));
        assertTrue(controller.contains("root.getVehicle() != null"));
        assertTrue(controller.contains("EntityAddToWorldEvent"));
        assertTrue(controller.contains("EntityRemoveFromWorldEvent"));
        assertTrue(controller.contains("EntityTeleportEvent"));
        assertTrue(controller.contains("ServerTickEndEvent"));
        assertTrue(controller.contains("pollNonLivingEntities()"));
        assertTrue(controller.contains("traverse(entity, previous, current"));
        assertTrue(controller.contains("!(entity instanceof LivingEntity)"));
        assertTrue(controller.contains("!(entity instanceof Vehicle)"));
        assertTrue(controller.contains("!(entity instanceof ComplexEntityPart)"));
        assertTrue(controller.contains("return player.teleport("));
        assertTrue(controller.contains("return entity.teleport("));
        assertTrue(controller.contains("return vehicle.teleport("));
        assertFalse(controller.contains("event.setTo(destination)"));
        int registerTraversal = module.indexOf(
                "registerEvents(traversalController, plugin)");
        int startTraversal = module.indexOf("traversalController.start()");
        assertTrue(registerTraversal >= 0 && startTraversal > registerTraversal,
                "non-living tracking must start after its lifecycle listeners are registered");

        assertTrue(tree.contains("for (Entity passenger : entity.getPassengers())"));
        assertTrue(tree.contains("collect(passenger, members, visited)"));
        assertTrue(tree.contains("transition.transform().mapDirection(member.lookDirection())"));
        assertTrue(tree.contains("transition.transform().mapVector(member.velocity())"));

        int apply = controller.indexOf("entityTree.apply(transition, movement)");
        int snap = controller.indexOf("SpaceRemoteEntitySnap.resetInterpolation(entityTree)");
        assertTrue(apply >= 0 && snap > apply,
                "remote interpolation must be reset after transformed state is applied");
        assertTrue(remoteSnap.contains("entity.getTrackedBy()"));
        assertTrue(remoteSnap.contains("new ClientboundRemoveEntitiesPacket"));
        assertTrue(remoteSnap.contains("serverEntity.sendPairingData"));
        assertTrue(remoteSnap.contains("new ClientboundBundlePacket"));
        assertTrue(remoteSnap.contains("ClientboundSetPassengersPacket"));
        assertTrue(remoteSnap.contains("ClientboundSetEntityLinkPacket"));
        int removePacket = remoteSnap.indexOf("bundle.add(new ClientboundRemoveEntitiesPacket");
        int statePackets = remoteSnap.indexOf("bundle.addAll(state)");
        int relationPackets = remoteSnap.indexOf("bundle.addAll(relations)");
        assertTrue(removePacket >= 0
                && statePackets > removePacket
                && relationPackets > statePackets,
                "the old model must be removed before state and relationships are rebuilt");
        assertTrue(remoteSnap.contains("BundlerInfo.BUNDLE_SIZE_LIMIT"));
        assertTrue(remoteSnap.contains("localViewers.contains(viewer.getUniqueId())"));
        assertFalse(remoteSnap.contains(".removePairing("));
        assertFalse(remoteSnap.contains(".addPairing("));
        assertFalse(remoteSnap.contains("hidePlayer("));
        assertFalse(remoteSnap.contains("hideEntity("));
        assertFalse(remoteSnap.contains(".seenBy"));
        assertFalse(remoteSnap.contains("packetevents"));
    }
}
