package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Cushion;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Firework;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Marker;
import org.bukkit.entity.OminousItemSpawner;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuEntityCoverageTest {
    @ParameterizedTest
    @ValueSource(classes = {Entity.class, Cushion.class, EnderCrystal.class, TNTPrimed.class, FallingBlock.class,
            Item.class, EnderSignal.class, EvokerFangs.class, OminousItemSpawner.class, Arrow.class, Trident.class,
            Firework.class, FishHook.class, LargeFireball.class, ShulkerBullet.class, ExperienceOrb.class})
    void visibleModelsConstrainPlacementAndFreshSnapshotsObserveTheirMovement(Class<? extends Entity> type) {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        var bounds = new BoundingBox(-.1, 63.9, 1, .1, 64.1, 1.2);
        Entity entity = body(type, bounds, true);
        fixture.entity(entity);
        Player viewer = viewer(null);
        var anchor = new FloatingMenuAnchorResolver.Anchor(eye.clone().add(0, 0, 2),
                new Vector(0, 0, 1), new Vector(1, 0, 0), 1, 2, 0);
        var scene = new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN),
                FloatingMenuFraming.COMFORTABLE, List.of(new FloatingMenuAnchorResolver.Surface(
                FloatingMenuPose.at(FloatingMenuPoint.ORIGIN), new FloatingMenuSize(2, 2))));
        var first = FloatingMenuAnchorResolver.fit(eye, anchor, scene, space(viewer, eye));
        assertTrue(first.distance() > 0 && first.distance() < 1);
        bounds.shift(0, 0, -.4);
        assertTrue(FloatingMenuAnchorResolver.fit(eye, anchor, scene, space(viewer, eye)).distance() < first.distance());
        bounds.shift(0, 0, 10);
        assertEquals(anchor.distance(), FloatingMenuAnchorResolver.fit(eye, anchor, scene, space(viewer, eye)).distance());
    }

    @ParameterizedTest
    @ValueSource(classes = {Marker.class, Interaction.class, AreaEffectCloud.class, LightningStrike.class})
    void controlEntitiesAndParticleEffectsDoNotSuspendTheMenu(Class<? extends Entity> type) {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        fixture.entity(body(type, new BoundingBox(-1, 63, -1, 1, 65, 1), true));
        assertTrue(MenuEntityObstacles.capture(viewer(null), eye, 5).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void hiddenAndInvalidNewModelTypesDoNotOcclude(boolean valid) {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        Entity entity = body(TNTPrimed.class, new BoundingBox(-1, 63, -1, 1, 65, 1), valid);
        fixture.entity(entity);
        assertTrue(MenuEntityObstacles.capture(viewer(valid ? entity : null), eye, 5).isEmpty());
    }

    @Test void itemAnimationCanReachTheReadingRangeWhileItsPhysicsBoxIsOutsideIt() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        var physics = new BoundingBox(-.1, 63.9, 2.1, .1, 64.1, 2.3);
        fixture.entity(body(Item.class, physics, true));
        var space = new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer(null), eye, 2));
        assertTrue(space.firstHit(eye, new Vector(0, 0, 1), 2) < 2);
        assertEquals(2.1, physics.getMinZ(), "Sampling must not expand the real entity's physics box");
    }

    @Test void aTridentsShaftCanReachTheReadingRangeWhileItsPhysicsBoxIsOutsideIt() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        fixture.entity(body(Trident.class, new BoundingBox(-.1, 63.9, 3.4, .1, 64.1, 3.6), true, 0));
        var space = new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer(null), eye, 2));
        assertTrue(space.firstHit(eye, new Vector(0, 0, 1), 2) < 2);
    }

    @ParameterizedTest
    @ValueSource(classes = {Item.class, OminousItemSpawner.class})
    void emptyNativeItemModelsDoNotOcclude(Class<? extends Entity> type) {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        ItemStack empty = mock(ItemStack.class);
        when(empty.isEmpty()).thenReturn(true);
        fixture.entity(body(type, new BoundingBox(-1, 63, -1, 1, 65, 1), true, 180, empty));
        assertTrue(MenuEntityObstacles.capture(viewer(null), eye, 5).isEmpty());
    }

    @Test void anEndCrystalsBaseAndBobbingModelCanExtendBelowThePhysicsBox() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        fixture.entity(body(EnderCrystal.class, new BoundingBox(-1, 64.9, 1, 1, 66.9, 3), true));
        var space = new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer(null), eye, 4));
        assertEquals(1, space.firstHit(eye, new Vector(0, 0, 1), 4), 1e-8);
    }

    private static Entity body(Class<? extends Entity> type, BoundingBox bounds, boolean valid) {
        return body(type, bounds, valid, 180);
    }

    private static Entity body(Class<? extends Entity> type, BoundingBox bounds, boolean valid, float yaw) {
        return body(type, bounds, valid, yaw, mock(ItemStack.class));
    }

    private static Entity body(Class<? extends Entity> type, BoundingBox bounds, boolean valid, float yaw, ItemStack content) {
        return (Entity) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (_, method, _) -> switch (method.getName()) {
                    case "isValid" -> valid;
                    case "getBoundingBox" -> bounds.clone();
                    case "getLocation" -> new Location(null, bounds.getCenterX(), bounds.getCenterY(), bounds.getMinZ(), yaw, 0);
                    case "getItemStack", "getItem" -> content;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player viewer(Entity hidden) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (_, method, args) -> switch (method.getName()) {
                    case "canSee" -> args[0] != hidden;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static FloatingMenuWorldSpace space(Player viewer, Location eye) {
        return new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer, eye, 5));
    }
}
