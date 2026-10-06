package org.encinet.mik.module.safety;

import org.bukkit.entity.EnderPearl;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderPearlGuardTest {

    private static final UUID OWNER = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0");

    @Test
    void commandSummonedPearlWithOwnerCannotLaunch() {
        EnderPearlGuard guard = new EnderPearlGuard(null);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(
                pearl(CreatureSpawnEvent.SpawnReason.COMMAND,
                        new AtomicReference<>(OWNER), new AtomicBoolean()));

        guard.onProjectileLaunch(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void ownerAddedAfterSummonCannotTeleportOnHit() {
        EnderPearlGuard guard = new EnderPearlGuard(null);
        AtomicReference<UUID> owner = new AtomicReference<>();
        AtomicBoolean removed = new AtomicBoolean();
        EnderPearl pearl = pearl(CreatureSpawnEvent.SpawnReason.COMMAND, owner, removed);
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
        guard.onProjectileLaunch(launch);
        assertFalse(launch.isCancelled());

        owner.set(OWNER);
        ProjectileHitEvent hit = new ProjectileHitEvent(pearl, null, null, null);
        hit.setCancelled(true); // A cancelled block hit can still teleport its owner.
        guard.onProjectileHit(hit);

        assertTrue(hit.isCancelled());
        assertTrue(removed.get());
    }

    @Test
    void ordinarilyThrownPearlRemainsUsable() {
        EnderPearlGuard guard = new EnderPearlGuard(null);
        AtomicBoolean removed = new AtomicBoolean();
        EnderPearl pearl = pearl(CreatureSpawnEvent.SpawnReason.DEFAULT,
                new AtomicReference<>(OWNER), removed);
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
        ProjectileHitEvent hit = new ProjectileHitEvent(pearl, null, null, null);

        guard.onProjectileLaunch(launch);
        guard.onProjectileHit(hit);

        assertFalse(launch.isCancelled());
        assertFalse(hit.isCancelled());
        assertFalse(removed.get());
    }

    private static EnderPearl pearl(CreatureSpawnEvent.SpawnReason reason,
                                    AtomicReference<UUID> owner, AtomicBoolean removed) {
        return (EnderPearl) Proxy.newProxyInstance(
                EnderPearl.class.getClassLoader(), new Class<?>[]{EnderPearl.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getEntitySpawnReason" -> reason;
                    case "getOwnerUniqueId" -> owner.get();
                    case "remove" -> {
                        removed.set(true);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
