package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorViewTest {
    @Test void fixedMountAndBoundedSharedPaginationSurviveFloorCountAndTypographyChanges() {
        var world = new IronElevatorTestWorld();
        world.iron(20);
        var source = platform(world,20);
        var scope = new Panels();
        var panel = new IronElevatorView(scope,null,new IronElevatorView.Actions() {
            public void select(Player player,Object visit,int height) { fail(); }
            public void visible(Player player,Object visit,IronElevatorCatalog.Result catalog,FloatingMenuPage page) { }
            public void pause(Player player,Object visit) { }
        });
        var player = player(world,world.origin(20));
        for (int count : List.of(2,20,100)) {
            var data = snapshot(source,floors(world,count));
            int reads = world.blockReads;
            panel.panel(player,data);
            var surface = scope.surface;
            for (double typography : List.of(.7,1.,1.3,1.8)) {
                var frame = SceneGeometryTestSupport.worldFrame(scope.scene,surface,typography);
                assertTrue(frame.size().width() <= surface.viewport().width() + 1e-6);
                assertTrue(frame.size().height() <= surface.viewport().height() + 1e-6);
                assertTrue(frame.elements().size() < 30,"The shared grid constructs only a bounded page");
                assertTrue(frame.elements().stream().filter(e -> e.node().key().value().startsWith("floor:"))
                        .allMatch(e -> e.node().input().pointerBehavior() != UiPointerBehavior.OPERATE),"Unavailable and current floors stay quiet");
                for (var element : frame.elements()) for (var state : FloatingMenuElementState.values())
                    assertEquals(0,scope.scene.appearance().elementBackground(state,UiPalette.DEFAULT,element.node().style()) >>> 24,
                            "Floor and pagination controls must remain transparent in every interaction state");
                assertEquals(0,surface.center().distanceSquared(SceneGeometryTestSupport.worldOrigin(scope.scene,surface,typography)),1e-12,
                        "Elevator controls must render on the declared mounting plane");
            }
            assertEquals(reads,world.blockReads,"Rendering and repeated layout must not read the world");
            assertEquals(surface,IronElevatorTestSupport.placement(new IronElevatorPanelPlacement(() -> 0),source).surface());
        }
    }

    @Test void pageBookmarkUsesFloorIdentityAcrossRefreshAndRejectsAnOldPagerCallback() {
        var world = new IronElevatorTestWorld(); world.iron(20);
        var scope = new Panels(); var panel = new IronElevatorView(scope,null,new IronElevatorView.Actions() {
            public void select(Player player,Object visit,int height) { fail(); }
            public void visible(Player player,Object visit,IronElevatorCatalog.Result catalog,FloatingMenuPage page) { }
            public void pause(Player player,Object visit) { }
        });
        var source = platform(world,20); var player = player(world,world.origin(20));
        var floors = floors(world,100);
        panel.panel(player,snapshot(source,floors));
        var page = SceneGeometryTestSupport.worldFrame(scope.scene,scope.surface,1).pagination().getFirst();
        page.selectPage().accept(2);
        var next = SceneGeometryTestSupport.worldFrame(scope.scene,scope.surface,1).pagination().getFirst();
        assertEquals(2,next.page().index());
        panel.panel(player,snapshot(source,floors));
        assertEquals(2,SceneGeometryTestSupport.worldFrame(scope.scene,scope.surface,1).pagination().getFirst().page().index());
        var current = scope.scene;
        page.selectPage().accept(0);
        assertSame(current,scope.scene,"Retired views cannot overwrite a new source snapshot");
    }

    @Test void standingAndBackingAreRevalidatedBeforeSharedInputDispatch() {
        var world = new IronElevatorTestWorld(); world.iron(20);
        var scope = new Panels(); var panel = new IronElevatorView(scope,null,new IronElevatorView.Actions() {
            public void select(Player player,Object visit,int height) { fail(); }
            public void visible(Player player,Object visit,IronElevatorCatalog.Result catalog,FloatingMenuPage page) { }
            public void pause(Player player,Object visit) { }
        });
        var feet = world.origin(20);
        var player = player(world,feet);
        panel.panel(player,snapshot(platform(world,20),floors(world,20)));
        assertTrue(scope.available.test(player));
        feet.add(0,.5,0);
        assertFalse(scope.available.test(player));
        feet.subtract(0,.5,0);
        world.put(0,22,-1,org.bukkit.Material.STONE,java.util.Set.of());
        assertFalse(scope.available.test(player));
    }

    @Test void clearAndForgetReleaseTheirScopeSlotAndRetireSourcePredicates() {
        var world = new IronElevatorTestWorld(); world.iron(20);
        var scope = new Panels(); var panel = new IronElevatorView(scope,null,new IronElevatorView.Actions() {
            public void select(Player player,Object visit,int height) { fail(); }
            public void visible(Player player,Object visit,IronElevatorCatalog.Result catalog,FloatingMenuPage page) { }
            public void pause(Player player,Object visit) { }
        });
        var player = player(world,world.origin(20)); var source = platform(world,20);
        panel.panel(player,snapshot(source,floors(world,20)));
        var available = scope.available;
        panel.clear(player);
        assertEquals(1,scope.clears); assertFalse(available.test(player));
        panel.panel(player,snapshot(source,floors(world,20)));
        panel.forget(player.getUniqueId());
        assertEquals(2,scope.clears); assertFalse(scope.available.test(player));
    }

    private static IronElevatorController.PanelSnapshot snapshot(IronElevatorPlatform source,List<IronElevatorCatalog.Floor> floors) {
        var plan = IronElevatorTestSupport.placement(new IronElevatorPanelPlacement(),source);
        var shaft = new IronElevatorShaft(floors.stream().map(IronElevatorCatalog.Floor::level).toList(),java.util.Set.of(new IronElevatorPlatform.Column(0,0)));
        return new IronElevatorController.PanelSnapshot(new Object(),source,new IronElevatorCatalog.Result(shaft,floors,0),plan,java.util.Map.of(),
                viewer -> source.contains(IronElevatorTravelGroup.platformAt(viewer,viewer.getLocation())) && plan.valid());
    }

    private static List<IronElevatorCatalog.Floor> floors(IronElevatorTestWorld world, int count) {
        var floors = new ArrayList<IronElevatorCatalog.Floor>();
        for (int index = 0; index < count; index++) { int y = 20 + index * 5; world.iron(y); floors.add(new IronElevatorCatalog.Floor(IronElevatorShaft.Level.of(platform(world,y)),index)); }
        return floors;
    }
    private static IronElevatorPlatform platform(IronElevatorTestWorld world,int height) { return IronElevatorTestSupport.platform(world.block(0,height,0)); }
    private static Player player(IronElevatorTestWorld world, Location feet) {
        UUID id = UUID.randomUUID();
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(_,method,_) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "getLocation" -> feet.clone();
            case "getWorld" -> world.world;
            case "isInsideVehicle" -> false;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
    private static final class Panels implements WorldMenuScope {
        WorldMenuSurface surface; MenuScene scene; Predicate<Player> available; int clears;
        public FloatingMenuHandle show(Player viewer,UiKey key,WorldMenuSurface surface,MenuScene scene,Predicate<Player> available) {
            this.surface = surface; this.scene = scene; this.available = available; return null;
        }
        public void clear(Player viewer,UiKey key) { clears++; }
        public void clear(UUID viewer) { clears++; }
        public void close() { }
    }
}
