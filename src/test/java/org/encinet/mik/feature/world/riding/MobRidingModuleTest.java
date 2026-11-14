package org.encinet.mik.feature.world.riding;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MobRidingModuleTest {
    @Test
    void anotherPlayersPetCannotBeMountedEvenWithUsePermission() {
        Fixture f = new Fixture(Wolf.class);
        Tameable pet = (Tameable) f.mob;
        when(pet.isTamed()).thenReturn(true);
        when(pet.getOwnerUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(f.mount().isCancelled());
        verify(f.mob, never()).addPassenger(any());
        verify(f.mob, never()).setAware(anyBoolean());
        verify(pet, never()).getOwner();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ownPetAndUnownedHorseCanBeMountedAndControlled(boolean owned) {
        Fixture f = new Fixture(Horse.class);
        Tameable pet = (Tameable) f.mob;
        UUID owner = owned ? f.player.getUniqueId() : null;
        when(pet.getOwnerUniqueId()).thenReturn(owner);
        assertTrue(f.mount().isCancelled());
        verify(f.mob).addPassenger(f.player);
        f.module.tick();
        verify(f.mob).setVelocity(new Vector(0, -0.08, 0.22));
    }

    @Test
    void ownershipTransferStopsExistingControlAndRestoresPet() {
        Fixture f = new Fixture(Wolf.class);
        Tameable pet = (Tameable) f.mob;
        UUID owner = f.player.getUniqueId();
        when(pet.getOwnerUniqueId()).thenReturn(owner);
        f.mount();
        when(pet.getOwnerUniqueId()).thenReturn(UUID.randomUUID());
        f.module.tick();
        verify(f.mob).setAware(true);
        verify(f.mob).removePassenger(f.player);
        verify(f.mob, never()).setVelocity(any());
        clearInvocations(f.mob);
        f.module.tick();
        verifyNoInteractions(f.mob);
    }

    @Test
    void sneakMountControlsMovementAndRequiresReleasingShiftBeforeDismount() {
        Fixture f = new Fixture();
        assertTrue(f.mount().isCancelled());
        verify(f.mob).setAware(false);
        f.module.tick();
        verify(f.mob).setVelocity(new Vector(0, -0.08, 0.22));
        var initial = new EntityDismountEvent(f.player, f.mob);
        f.module.onInitialDismount(initial);
        assertTrue(initial.isCancelled());
        when(f.input.isSneak()).thenReturn(false);
        f.module.onInput(new PlayerInputEvent(f.player, f.input));
        when(f.input.isSneak()).thenReturn(true);
        var next = new EntityDismountEvent(f.player, f.mob);
        f.module.onInitialDismount(next);
        assertFalse(next.isCancelled());
        f.module.onDismount(next);
        verify(f.mob).setAware(true);
        clearInvocations(f.mob);
        f.module.tick();
        verifyNoInteractions(f.mob);
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "plot", "occupied", "vehicle", "dead", "spectator", "not-sneaking", "offhand", "mount-denied"})
    void ineligibleInteractionsCannotTakeControl(String reason) {
        Fixture f = new Fixture();
        switch (reason) {
            case "permission" -> when(f.player.hasPermission("mik.riding.use")).thenReturn(false);
            case "plot" -> f.allowed = false;
            case "occupied" -> when(f.mob.getPassengers()).thenReturn(List.of(mock(Entity.class)));
            case "vehicle" -> when(f.player.isInsideVehicle()).thenReturn(true);
            case "dead" -> when(f.mob.isDead()).thenReturn(true);
            case "spectator" -> when(f.player.getGameMode()).thenReturn(GameMode.SPECTATOR);
            case "not-sneaking" -> when(f.player.isSneaking()).thenReturn(false);
            case "mount-denied" -> when(f.mob.addPassenger(f.player)).thenReturn(false);
            case "offhand" -> { }
            default -> fail(reason);
        }
        var event = new PlayerInteractEntityEvent(f.player, f.mob,
                reason.equals("offhand") ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND);
        f.module.onInteract(event);
        assertFalse(event.isCancelled());
        verify(f.mob, never()).setAware(anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"plot", "permission", "removed", "offline", "unmounted"})
    void changedEligibilityRestoresMobAndStopsSteering(String reason) {
        Fixture f = new Fixture();
        f.mount();
        switch (reason) {
            case "plot" -> f.allowed = false;
            case "permission" -> when(f.player.hasPermission("mik.riding.use")).thenReturn(false);
            case "removed" -> when(f.mob.isValid()).thenReturn(false);
            case "offline" -> when(f.player.isOnline()).thenReturn(false);
            case "unmounted" -> when(f.player.getVehicle()).thenReturn(null);
            default -> fail(reason);
        }
        f.module.tick();
        verify(f.mob).setAware(true);
        verify(f.mob, never()).setVelocity(any());
        if (!reason.equals("unmounted")) verify(f.mob).removePassenger(f.player);
    }

    @Test
    void previouslyUnawareMobRemainsUnawareAfterDismount() {
        Fixture f = new Fixture();
        when(f.mob.isAware()).thenReturn(false);
        f.mount();
        f.module.onDismount(new EntityDismountEvent(f.player, f.mob));
        verify(f.mob, times(2)).setAware(false);
        verify(f.mob, never()).setAware(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disableAndEntityRemovalRestoreAwarenessAndReleaseTheRider(boolean disable) {
        Fixture f = new Fixture();
        f.mount();
        if (disable) f.module.disable();
        else f.module.onRemove(new EntityRemoveFromWorldEvent(f.mob, null));
        verify(f.mob).setAware(true);
        verify(f.mob).removePassenger(f.player);
        clearInvocations(f.mob);
        f.module.tick();
        verifyNoInteractions(f.mob);
    }

    @Test
    void groundJumpPreservesGravityInMidairAndWaterAllowsAscent() {
        Fixture f = new Fixture();
        f.mount();
        when(f.input.isJump()).thenReturn(true);
        when(f.mob.isOnGround()).thenReturn(true);
        f.module.tick();
        verify(f.mob).setVelocity(new Vector(0, 0.42, 0.22));
        when(f.mob.isOnGround()).thenReturn(false);
        f.module.tick();
        verify(f.mob).setVelocity(new Vector(0, -0.08, 0.22));
        when(f.mob.isInWater()).thenReturn(true);
        f.module.tick();
        verify(f.mob).setVelocity(new Vector(0, 0.25, 0.22));
    }

    @Test
    void flyingMobFollowsPitchWhileGroundMobCannotFly() {
        Fixture f = new Fixture(Bee.class);
        when(f.player.getPitch()).thenReturn(-90f);
        f.mount();
        f.module.tick();
        verify(f.mob).setVelocity(argThat(velocity -> Math.abs(velocity.getY() - 0.22) < 1e-9));
    }

    @Test
    void movementUsesViewDirectionWithoutDiagonalSpeedBoostAndFlightUsesPitch() {
        Input input = mock(Input.class);
        when(input.isForward()).thenReturn(true);
        assertEquals(-0.22, MobRidingModule.movement(input, 90, 0, false).getX(), 1e-9);
        assertEquals(0.22, MobRidingModule.movement(input, 0, -90, true).getY(), 1e-9);
        assertEquals(0, MobRidingModule.movement(input, 0, -90, false).getY(), 1e-9);
        when(input.isRight()).thenReturn(true);
        assertEquals(0.22, MobRidingModule.movement(input, 0, 0, false).length(), 1e-9);
        assertTrue(MobRidingModule.movement(input, 0, 0, false).getX() < 0);
        when(input.isSprint()).thenReturn(true);
        assertEquals(0.32, MobRidingModule.movement(input, 0, 0, false).length(), 1e-9);
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final Mob mob;
        final Input input = mock(Input.class);
        boolean allowed = true;
        final MobRidingModule module = new MobRidingModule(null, (_, _) -> allowed);

        Fixture() {
            this(Mob.class);
        }

        Fixture(Class<? extends Mob> type) {
            mob = mock(type);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            when(player.isSneaking()).thenReturn(true);
            when(player.hasPermission("mik.riding.use")).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getCurrentInput()).thenReturn(input);
            when(input.isForward()).thenReturn(true);
            when(input.isSneak()).thenReturn(true);
            when(mob.isValid()).thenReturn(true);
            when(mob.isAware()).thenReturn(true);
            when(mob.getLocation()).thenReturn(new Location(null, 0, 64, 0));
            when(mob.getVelocity()).thenReturn(new Vector(0, -0.08, 0));
            when(mob.addPassenger(player)).thenAnswer(_ -> {
                when(player.getVehicle()).thenReturn(mob);
                return true;
            });
        }

        PlayerInteractEntityEvent mount() {
            var event = new PlayerInteractEntityEvent(player, mob, EquipmentSlot.HAND);
            module.onInteract(event);
            return event;
        }
    }
}
