package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.integration.axiom.AxiomGizmoService;
import org.encinet.mik.integration.axiom.AxiomImageService;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Native packets are mocked; host selection, frame publication, input and cleanup use the actual service. */
class WorldMenuScopeTest {
    @TempDir File directory;

    @Test void movingAMultiPanelWorkspaceKeepsItsNativePlanesTitleAndInputTogether() throws Exception {
        try (var f = new Fixture()) {
            var now = new java.util.concurrent.atomic.AtomicLong(); f.clock(now::get);
            var clicks = new AtomicInteger();
            var scene = MenuScene.screen("workspace",Component.text("Workspace"))
                    .animation(FloatingMenuAnimation.DEFAULT.reduced())
                    .content(UiTemplates.horizontalPanels(UiLength.ui(16),
                            UiTemplates.sidebar("directory",Ui.text(Component.text("Directory")).build()),
                            UiTemplates.window("reader",Ui.button(Component.text("Go")).key("go")
                                    .primary((_,_) -> clicks.incrementAndGet()).build()))).build();
            f.service.openRoot(f.player,scene); f.tick();
            var before = (SceneGeometry)f.foreground("layoutSnapshot");
            assertEquals(2,before.frame().panels().size());
            var title = before.frame().elements().stream().filter(node -> node.node().key().value().equals("workspace-title"))
                    .findFirst().orElseThrow().path();
            var move = f.surface("move");
            f.eye.setDirection(MenuSurfaceAttachment.vector(move.center()).subtract(f.eye.toVector()));
            f.tick(); now.addAndGet(100_000_000);
            f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND));
            assertNotNull(f.foreground("windowMove"));
            f.eye.setYaw(f.eye.getYaw()+65); f.eye.setPitch(f.eye.getPitch()+20); f.tick();
            assertTrue(f.surface("move").intersect(f.eye,0).isPresent());
            var moved = (SceneGeometry)f.foreground("layoutSnapshot");
            assertEquals(2,moved.frame().panels().size());
            assertNotEquals(before.poses().get(title),moved.poses().get(title));
            assertFalse((boolean)f.foreground("titleSpawned"),"The title belongs to the same logical group");
            now.addAndGet(100_000_000);
            f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND)); f.tick();
            assertNull(f.foreground("windowMove")); assertEquals(0,clicks.get());
            var go = f.surface("go");
            f.eye.setDirection(MenuSurfaceAttachment.vector(go.center()).subtract(f.eye.toVector()));
            f.tick(); now.addAndGet(100_000_000);
            f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND));
            assertEquals(1,clicks.get(),"Placed native controls retain their actual input geometry");
        }
    }

    @Test void theGrabbedMoveButtonStaysUnderThePointerAndPlacementIgnoresWorldProtection() throws Exception {
        try (var f = new Fixture()) {
            var now = new java.util.concurrent.atomic.AtomicLong(); f.clock(now::get);
            var clicks = new AtomicInteger();
            f.service.openRoot(f.player,f.scene(clicks)); f.tick();
            var surface = f.surface("move");
            f.eye.setDirection(MenuSurfaceAttachment.vector(surface.center()).subtract(f.eye.toVector()));
            f.tick(); now.addAndGet(100_000_000);
            f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND));
            assertNotNull(f.foreground("windowMove"));
            float yaw = f.eye.getYaw(), pitch = f.eye.getPitch();
            for (var turn : new float[][]{{35,12},{-70,-20},{115,25},{0,0}}) {
                f.eye.setYaw(yaw+turn[0]); f.eye.setPitch(pitch+turn[1]); f.tick();
                assertTrue(f.surface("move").intersect(f.eye,0).isPresent(),
                        "The grabbed control keeps its eye-relative position at yaw="+turn[0]+", pitch="+turn[1]);
            }
            now.addAndGet(100_000_000);
            f.eye.setYaw(yaw+160); f.eye.setPitch(-35);
            assertTrue(f.surface("move").intersect(f.eye,0).isEmpty(),"Placement cannot require aiming at the last published button");
            var place = f.blockClick(Action.LEFT_CLICK_BLOCK,EquipmentSlot.HAND);
            place.setUseInteractedBlock(Event.Result.DENY);
            place.setUseItemInHand(Event.Result.DENY);
            f.service.onWorldClick(place); f.tick();
            assertNull(f.foreground("windowMove"));
            assertEquals(Event.Result.DENY,place.useInteractedBlock());
            assertTrue(f.surface("move").intersect(f.eye,0).isPresent());
            assertEquals(0,clicks.get());
        }
    }

    @Test void contentRootPopoversFindTheirSourceBeforeAndAfterIndependentWindows() throws Exception {
        try (var f = new Fixture()) {
            var scene = MenuScene.screen("launcher")
                    .animation(FloatingMenuAnimation.DEFAULT.reduced())
                    .content(Ui.button(Component.text("Choose destination")).key("source").primary((_,_) -> {}).build()).build();
            var root = f.service.openRoot(f.player,scene); f.tick();
            for (int pass = 0; pass < 2; pass++) {
                root.openPopover(UiKey.named("source"),_ -> () -> MenuScene.screen("destinations",Component.text("Destinations"))
                        .content(Ui.text(Component.text("Available places")).build()).build());
                f.tick();
                assertNotNull(((SceneGeometry)f.foreground("layoutSnapshot")).popover());
                root.closePopover();
                var tool = root.openWindow(MenuWindowSpec.tool(UiKey.named("editor")),_ -> () -> f.scene(new AtomicInteger()));
                f.tick();
                var workspace = (MenuWorkspaceSession)f.foreground("workspace");
                var frame = ((SceneGeometry)f.foreground("layoutSnapshot")).frame();
                assertFalse(frame.panels().stream().anyMatch(surface -> MenuWorkspaceSession.segment(surface.path(),MenuWorkspaceSession.MAIN.value())),
                        "Logical workspace ownership does not require a background plane");
                assertTrue(frame.elements().stream().anyMatch(node -> workspace.ownerOf(node.path()).equals(root.id())
                        && node.node().key().value().equals("move")));
                assertTrue(frame.elements().stream().anyMatch(node -> workspace.ownerOf(node.path()).equals(tool.id())
                        && node.node().key().value().equals("close")));
                tool.close(); f.tick();
            }
        }
    }

    @Test void cancelledEntityInteractionsStillPlaceOrCancelAnActiveWindowMove() throws Exception {
        try (var f = new Fixture()) {
            var now = new java.util.concurrent.atomic.AtomicLong(); f.clock(now::get);
            var root = f.service.openRoot(f.player,f.scene(new AtomicInteger())); f.tick();
            var workspace = (MenuWorkspaceSession)f.foreground("workspace");
            root.move(); f.eye.setYaw(130); f.tick(); now.addAndGet(100_000_000);
            var cancel = new org.bukkit.event.player.PlayerInteractEntityEvent(f.player,mock(org.bukkit.entity.Entity.class),EquipmentSlot.HAND);
            cancel.setCancelled(true);
            f.service.onPopoverEntityClick(cancel);
            assertNull(f.foreground("windowMove")); assertEquals(UiTransform.IDENTITY,workspace.transform(root.id()));
            root.move(); f.eye.setYaw(70); f.tick(); now.addAndGet(100_000_000);
            var place = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
            when(place.getDamager()).thenReturn(f.player); when(place.isCancelled()).thenReturn(true);
            f.service.onPopoverEntityAttack(place);
            assertNull(f.foreground("windowMove")); assertNotEquals(UiTransform.IDENTITY,workspace.transform(root.id()));
            verify(place).setCancelled(true);
        }
    }

    @Test void aSideWindowKeepsItsGrabbedControlAlignedAcrossRepeatedMoves() throws Exception {
        try (var f = new Fixture()) {
            var now = new java.util.concurrent.atomic.AtomicLong(); f.clock(now::get);
            var root = f.service.openRoot(f.player,f.scene(new AtomicInteger()));
            var tool = root.openWindow(MenuWindowSpec.tool(UiKey.named("side")),_ -> () -> f.scene(new AtomicInteger()));
            f.tick();
            for (int pass = 0; pass < 3; pass++) {
                var surface = f.surface(tool.id(),"move");
                f.eye.setDirection(MenuSurfaceAttachment.vector(surface.center()).subtract(f.eye.toVector()));
                f.tick(); now.addAndGet(100_000_000);
                f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND));
                assertNotNull(f.foreground("windowMove"));
                f.eye.setYaw(f.eye.getYaw()+45); f.eye.setPitch(f.eye.getPitch()+12); f.tick();
                assertTrue(f.surface(tool.id(),"move").intersect(f.eye,0).isPresent());
                now.addAndGet(100_000_000);
                f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND)); f.tick();
                assertNull(f.foreground("windowMove"));
                assertTrue(f.surface(tool.id(),"move").intersect(f.eye,0).isPresent());
            }
        }
    }

    @Test void moveButtonFollowsTheViewThenPlacesAndSecondaryClickRestoresThePreviousPose() throws Exception {
        try (var f = new Fixture()) {
            var now = new java.util.concurrent.atomic.AtomicLong(); f.clock(now::get);
            var clicks = new AtomicInteger(); var refreshes = new AtomicInteger();
            var scene = f.scene(clicks).toBuilder().refreshEvery(1,(_,_) -> refreshes.incrementAndGet()).build();
            var root = f.service.openRoot(f.player,scene); f.tick();
            var workspace = (MenuWorkspaceSession) f.foreground("workspace");
            refreshes.set(0);
            double originalScale = (double)f.foreground("spatialScale");
            root.move();
            f.tick();
            assertEquals(originalScale,(double)f.foreground("spatialScale"),1e-8,"Starting a move retains the actual fitted reading size");
            f.eye.setYaw(140); f.eye.setPitch(15); f.tick();
            var placed = workspace.transform(root.id());
            assertNotEquals(UiTransform.IDENTITY,placed);
            assertEquals(0,refreshes.get(),"Moving retains the measured page instead of refreshing its business content");
            root.move();
            f.tick(); assertEquals(placed,workspace.transform(root.id()));
            root.move(); f.eye.setYaw(100); f.eye.setPitch(-20); f.tick();
            assertNotEquals(placed,workspace.transform(root.id()));
            now.addAndGet(100_000_000);
            var cancel = f.blockClick(Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            cancel.setUseInteractedBlock(Event.Result.DENY);
            f.service.onWorldClick(cancel);
            assertEquals(placed,workspace.transform(root.id()));
            assertEquals(0,clicks.get(),"Placement clicks cannot activate content behind the moving window");
            assertNull(f.foreground("windowMove"));
            root.minimize(); assertEquals(FloatingMenuState.SUSPENDED,root.state());
            root.restore(); assertEquals(FloatingMenuState.ACTIVE,root.state());
            assertEquals(placed,workspace.transform(root.id()));
        }
    }

    @Test void nearestVisiblePanelOwnsInputAndScopesWithEqualKeysRemainIndependent() throws Exception {
        try (var f = new Fixture()) {
            var first = f.service.worldPanels(); var second = f.service.worldPanels();
            var farClicks = new AtomicInteger(); var nearClicks = new AtomicInteger();
            var far = first.show(f.player,UiKey.named("controls"),f.surface(0),f.scene(farClicks),_ -> true);
            var near = second.show(f.player,UiKey.named("controls"),f.surface(.5),f.scene(nearClicks),_ -> true);
            f.tick();
            var click = f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND);
            f.service.onWorldClick(click);
            assertEquals(Event.Result.DENY,click.useItemInHand()); assertEquals(0,farClicks.get()); assertEquals(1,nearClicks.get());
            f.service.onWorldClick(f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND));
            assertEquals(1,nearClicks.get(),"Duplicate packets share the viewer input gate");
            assertEquals(near.id(),second.show(f.player,UiKey.named("controls"),f.surface(.5),f.scene(nearClicks),_ -> true).id());
            first.close(); assertEquals(FloatingMenuState.CLOSED,far.state()); assertEquals(FloatingMenuState.ACTIVE,near.state());
            second.close(); assertEquals(FloatingMenuState.CLOSED,near.state());
        }
    }

    @Test void foregroundTemporarilySuspendsWorldViewsAndOldWorldHandlesCannotCloseIt() throws Exception {
        try (var f = new Fixture()) {
            var scope = f.service.worldPanels();
            var world = scope.show(f.player,UiKey.named("controls"),f.surface(0),f.scene(new AtomicInteger()),_ -> true);
            f.tick();
            var foreground = world.openChild(f.scene(new AtomicInteger()));
            assertEquals(FloatingMenuState.SUSPENDED,world.state());
            f.tick(); foreground.close(); f.tick();
            assertEquals(FloatingMenuState.CLOSED,foreground.state()); assertEquals(FloatingMenuState.ACTIVE,world.state());
            scope.clear(f.player,UiKey.named("controls"));
            var next = f.service.openRoot(f.player,f.scene(new AtomicInteger()));
            world.close(); assertNotEquals(FloatingMenuState.CLOSED,next.state());
        }
    }

    @Test void sourceRevalidationAndProtectionDenialsPreventActionsWhileOffhandCannotActivate() throws Exception {
        try (var f = new Fixture()) {
            var valid = new java.util.concurrent.atomic.AtomicBoolean(true);
            var clicks = new AtomicInteger(); var scope = f.service.worldPanels();
            scope.show(f.player,UiKey.named("controls"),f.surface(0),f.scene(clicks),_ -> valid.get()); f.tick();
            f.service.onWorldClick(f.click(Action.RIGHT_CLICK_AIR,EquipmentSlot.OFF_HAND));
            assertEquals(0,clicks.get());
            var denied = new PlayerInteractEvent(f.player,Action.RIGHT_CLICK_BLOCK,null,f.world.world().getBlockAt(0,64,-1),org.bukkit.block.BlockFace.SOUTH,EquipmentSlot.HAND);
            denied.setUseInteractedBlock(Event.Result.DENY); f.service.onWorldClick(denied);
            assertEquals(Event.Result.DENY,denied.useItemInHand()); assertEquals(0,clicks.get());
            valid.set(false); var stale = f.click(Action.LEFT_CLICK_AIR,EquipmentSlot.HAND);
            f.service.onWorldClick(stale); assertNotEquals(Event.Result.DENY,stale.useItemInHand()); assertEquals(0,clicks.get());
        }
    }

    @Test void foregroundReadOnlyTextTitlesAndBackgroundsShieldBlockClicksWithoutAnActionTarget() throws Exception {
        var scenes = java.util.List.of(
                MenuScene.screen("reading").content(Ui.text(Component.text("Read only")).build()).build(),
                MenuScene.screen("title",Component.text("Title only")).build(),
                MenuScene.screen("background").decoration(new FloatingMenuDecoration("panel",FloatingMenuPoint.ORIGIN,
                        new FloatingMenuDecoration.Surface(3,2,0xFF333333),FloatingMenuDecoration.Motion.NONE)).build());
        try (var f = new Fixture()) {
            for (var scene : scenes) {
                var menu = f.service.openRoot(f.player,scene.toBuilder().animation(FloatingMenuAnimation.DEFAULT.reduced()).build());
                f.tick();
                assertEquals(FloatingMenuState.ACTIVE,menu.state());
                for (var action : java.util.List.of(Action.LEFT_CLICK_BLOCK,Action.RIGHT_CLICK_BLOCK)) {
                    for (var hand : java.util.List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND)) {
                        var click = f.blockClick(action,hand);
                        f.service.onWorldClick(click);
                        assertEquals(Event.Result.DENY,click.useInteractedBlock(),scene.screenId());
                        assertEquals(Event.Result.DENY,click.useItemInHand(),scene.screenId());
                    }
                }
            }
        }
    }

    @Test void foregroundRenderingShieldsNearbyClicksAndReleasesOutsideItsRangeOrAfterClose() throws Exception {
        try (var f = new Fixture()) {
            f.service.openRoot(f.player,MenuScene.screen("reading").animation(FloatingMenuAnimation.DEFAULT.reduced())
                    .content(Ui.text(Component.text("Read only")).build()).build());
            f.tick();
            f.eye.setYaw(210);
            var nearby = f.blockClick(Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            f.service.onWorldClick(nearby);
            assertEquals(Event.Result.DENY,nearby.useInteractedBlock());
            f.eye.setYaw(270);
            var outside = f.blockClick(Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            f.service.onWorldClick(outside);
            assertNotEquals(Event.Result.DENY,outside.useInteractedBlock());
            f.service.closeAllImmediately();
            f.eye.setYaw(180);
            var closed = f.blockClick(Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            f.service.onWorldClick(closed);
            assertNotEquals(Event.Result.DENY,closed.useInteractedBlock());
        }
    }

    @Test void foregroundRenderingRemainsProtectedDuringOpeningAndClosing() throws Exception {
        try (var f = new Fixture()) {
            var menu = f.service.openRoot(f.player,MenuScene.screen("reading")
                    .content(Ui.text(Component.text("Read only")).build()).build());
            assertEquals(FloatingMenuState.OPENING,menu.state());
            var opening = f.blockClick(Action.RIGHT_CLICK_BLOCK,EquipmentSlot.HAND);
            f.service.onWorldClick(opening);
            assertEquals(Event.Result.DENY,opening.useInteractedBlock());
            for (int tick=0;tick<10;tick++) f.tick();
            assertEquals(FloatingMenuState.ACTIVE,menu.state());
            menu.close();
            assertEquals(FloatingMenuState.CLOSING,menu.state());
            var closing = f.blockClick(Action.LEFT_CLICK_BLOCK,EquipmentSlot.HAND);
            f.service.onWorldClick(closing);
            assertEquals(Event.Result.DENY,closing.useInteractedBlock());
        }
    }

    @Test void boundedSlotsCleanupAndFailedRedrawLeaveOtherPanelsIntact() throws Exception {
        try (var f = new Fixture()) {
            var scope = f.service.worldPanels();
            var initial = scope.show(f.player,UiKey.named("first"),f.surface(0),f.scene(new AtomicInteger()),_ -> true);
            f.tick();
            assertThrows(IllegalArgumentException.class,() -> scope.show(f.player,UiKey.named("first"),f.surface(0),
                    MenuScene.screen("bad",Component.text("External title")).content(Ui.text(Component.text("body")).build()).build(),_ -> true));
            assertEquals(FloatingMenuState.ACTIVE,initial.state());
            for (int index=1;index<8;index++) scope.show(f.player,UiKey.named("slot"+index),f.surface(0),f.scene(new AtomicInteger()),_ -> true);
            assertThrows(IllegalStateException.class,() -> scope.show(f.player,UiKey.named("ninth"),f.surface(0),f.scene(new AtomicInteger()),_ -> true));
            scope.clear(f.player.getUniqueId()); assertEquals(FloatingMenuState.CLOSED,initial.state());
            scope.close(); assertThrows(IllegalStateException.class,() -> scope.show(f.player,UiKey.named("first"),f.surface(0),f.scene(new AtomicInteger()),_ -> true));
        }
    }

    @Test void clearingViewsKeepsFeatureScopesUsableUntilTheServiceIsDisposed() throws Exception {
        try (var f = new Fixture()) {
            var scope = f.service.worldPanels();
            var old = scope.show(f.player,UiKey.named("controls"),f.surface(0),f.scene(new AtomicInteger()),_ -> true);
            f.tick();
            f.service.closeAllImmediately();
            assertEquals(FloatingMenuState.CLOSED,old.state());
            var fresh = scope.show(f.player,UiKey.named("controls"),f.surface(0),f.scene(new AtomicInteger()),_ -> true);
            assertNotEquals(old.id(),fresh.id());
            old.close();
            f.tick();
            assertEquals(FloatingMenuState.ACTIVE,fresh.state());
            f.service.disable();
            assertEquals(FloatingMenuState.CLOSED,fresh.state());
            assertThrows(IllegalStateException.class,() -> scope.show(f.player,UiKey.named("controls"),
                    f.surface(0),f.scene(new AtomicInteger()),_ -> true));
        }
    }

    @Test void oneFailedPacketCleanupDoesNotRetainOtherSlotsOrSkipResourceOwners() {
        try (var f = new Fixture()) {
            var scope = f.service.worldPanels();
            var closed = new AtomicInteger();
            var scene = f.scene(new AtomicInteger()).toBuilder().lifecycle((_,_,_,state,_) -> {
                if (state == FloatingMenuState.CLOSED) closed.incrementAndGet();
            }).build();
            var first = scope.show(f.player,UiKey.named("first"),f.surface(0),scene,_ -> true);
            var second = scope.show(f.player,UiKey.named("second"),f.surface(0),scene,_ -> true);
            var failed = new java.util.concurrent.atomic.AtomicBoolean();
            doAnswer(_ -> {
                if (!failed.getAndSet(true)) throw new IllegalStateException("Packet transport unavailable");
                return null;
            }).when(f.renderer.constructed().getFirst()).destroy(eq(f.player),any(int[].class));
            assertThrows(IllegalStateException.class,scope::close);
            assertEquals(FloatingMenuState.CLOSED,first.state());
            assertEquals(FloatingMenuState.CLOSED,second.state());
            assertEquals(2,closed.get());
            verify(f.gizmoScope,times(2)).remove(eq(f.player),any(UUID.class));
            verify(f.imageService,times(2)).remove(eq(f.player),any(UUID.class));
            assertDoesNotThrow(scope::close);
            var another = f.service.worldPanels();
            assertDoesNotThrow(() -> another.show(f.player,UiKey.named("first"),f.surface(0),scene,_ -> true));
            another.close();
        }
    }

    @Test void initiallyHiddenPanelsPublishTheirSuspendedLifecycleAndCloseFromThatState() {
        try (var f = new Fixture()) {
            f.service.openRoot(f.player,f.scene(new AtomicInteger()));
            var states = new java.util.ArrayList<FloatingMenuState>();
            var previous = new java.util.ArrayList<FloatingMenuState>();
            var scope = f.service.worldPanels();
            var scene = f.scene(new AtomicInteger()).toBuilder().lifecycle((_,_,before,after,_) -> {
                previous.add(before); states.add(after);
            }).build();
            var world = scope.show(f.player,UiKey.named("controls"),f.surface(0),scene,_ -> true);
            assertEquals(FloatingMenuState.SUSPENDED,world.state());
            scope.close();
            assertEquals(java.util.List.of(FloatingMenuState.SUSPENDED,FloatingMenuState.CLOSED),states);
            assertEquals(java.util.List.of(FloatingMenuState.CLOSED,FloatingMenuState.SUSPENDED),previous);
        }
    }

    @Test void simultaneousPanelsShareOneEntityBudgetAcrossScopes() {
        try (var f = new Fixture()) {
            var content = Ui.stack();
            for (int index=0;index<100;index++) content.child(Ui.button(Component.text("Go"))
                    .key("control:"+index).primary((_,_) -> {}).build());
            var scene = MenuScene.screen("dense").content(content.build()).build();
            var first = f.service.worldPanels(); var second = f.service.worldPanels();
            var handle = first.show(f.player,UiKey.named("first"),f.surface(0),scene,_ -> true);
            for (int index=1;index<6;index++) second.show(f.player,UiKey.named("panel:"+index),f.surface(0),scene,_ -> true);
            assertThrows(UiOverflowException.class,() -> second.show(f.player,UiKey.named("over-budget"),f.surface(0),scene,_ -> true));
            first.close();
            assertEquals(FloatingMenuState.CLOSED,handle.state());
            assertDoesNotThrow(() -> second.show(f.player,UiKey.named("after-release"),f.surface(0),scene,_ -> true));
            second.close();
            verify(f.renderer.constructed().getFirst(),atLeastOnce()).forget(eq(f.player.getUniqueId()),any(int[].class));
        }
    }

    private final class Fixture implements AutoCloseable {
        final FloatingMenuTestWorld world = new FloatingMenuTestWorld();
        final Location eye = new Location(world.world(),0,64,2,180,0);
        final Player player = mock(Player.class);
        final AxiomGizmoService.Scope gizmoScope = mock(AxiomGizmoService.Scope.class);
        final AxiomImageService imageService = mock(AxiomImageService.class);
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final MockedStatic<VirtualMenuEntityRenderer> entityIds = mockStatic(VirtualMenuEntityRenderer.class);
        final MockedConstruction<VirtualMenuEntityRenderer> renderer = mockConstruction(VirtualMenuEntityRenderer.class);
        final MockedConstruction<MenuSoundFeedback> sounds = mockConstruction(MenuSoundFeedback.class);
        final MockedConstruction<MenuUsageDisplayController> status = mockConstruction(MenuUsageDisplayController.class);
        final MockedConstruction<FloatingMenuSettingsStore> settings = mockConstruction(FloatingMenuSettingsStore.class,
                (store,_) -> when(store.preferences(any(UUID.class))).thenReturn(FloatingMenuPreferences.DEFAULT));
        final FloatingMenuService service;
        Fixture() {
            try {
                UUID id = UUID.randomUUID();
                when(player.getUniqueId()).thenReturn(id); when(player.getWorld()).thenReturn(world.world());
                when(player.getLocation()).thenAnswer(_ -> eye.clone().subtract(0,1.62,0));
                when(player.getEyeLocation()).thenAnswer(_ -> eye.clone());
                when(player.getEyeHeight()).thenReturn(1.62); when(player.getEyeHeight(true)).thenReturn(1.62);
                when(player.isOnline()).thenReturn(true);
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
                var nextId = new AtomicInteger(100); entityIds.when(() -> VirtualMenuEntityRenderer.allocateId(player)).thenAnswer(_ -> nextId.incrementAndGet());
                var plugin = mock(JavaPlugin.class); when(plugin.isEnabled()).thenReturn(true); when(plugin.getDataFolder()).thenReturn(directory); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
                var gizmos = mock(AxiomGizmoService.class); when(gizmos.scope(anyString())).thenReturn(gizmoScope);
                var language = mock(LanguageService.class);
                when(language.text(any(Player.class),any(org.encinet.mik.shared.i18n.Message.class),any(net.kyori.adventure.text.format.TextColor.class),any(Object[].class))).thenReturn(Component.text("Window"));
                service = new FloatingMenuService(plugin,gizmos,imageService,language,_ -> false);
            } catch (RuntimeException | Error error) { closeMocks(); throw error; }
        }
        WorldMenuSurface surface(double z) { return new WorldMenuSurface(new Location(world.world(),0,64,z),UiRotation.IDENTITY,new FloatingMenuSize(1,1),.25,5); }
        MenuScene scene(AtomicInteger clicks) {
            return MenuScene.screen("test").animation(FloatingMenuAnimation.DEFAULT.reduced())
                    .content(Ui.button(Component.text("Go")).primary((_,_) -> clicks.incrementAndGet()).build()).build();
        }
        MenuSurfaceAttachment.Surface surface(String key) throws Exception {
            return surface(null,key);
        }
        MenuSurfaceAttachment.Surface surface(UUID owner, String key) throws Exception {
            var geometry = (SceneGeometry)foreground("layoutSnapshot");
            String path = buttonPath(geometry,(MenuWorkspaceSession)foreground("workspace"),owner,key);
            for (Object button : (java.util.List<?>)foreground("buttons")) {
                var id = button.getClass().getDeclaredField("id"); id.setAccessible(true);
                if (!path.equals(id.get(button))) continue;
                var surface = button.getClass().getDeclaredField("surface"); surface.setAccessible(true);
                return (MenuSurfaceAttachment.Surface)surface.get(button);
            }
            throw new AssertionError("Missing button: "+key);
        }
        private static String buttonPath(SceneGeometry geometry, MenuWorkspaceSession workspace, UUID owner, String key) {
            return geometry.frame().elements().stream().filter(node -> node.node().key().value().equals(key))
                    .filter(node -> owner == null || workspace.ownerOf(node.path()).equals(owner))
                    .findFirst().orElseThrow().path();
        }
        PlayerInteractEvent click(Action action,EquipmentSlot hand) { return new PlayerInteractEvent(player,action,null,null,org.bukkit.block.BlockFace.SELF,hand); }
        PlayerInteractEvent blockClick(Action action,EquipmentSlot hand) {
            return new PlayerInteractEvent(player,action,null,FloatingMenuTestWorld.air(),org.bukkit.block.BlockFace.SOUTH,hand);
        }
        void tick() throws Exception { var method = FloatingMenuService.class.getDeclaredMethod("tick"); method.setAccessible(true); method.invoke(service); }
        @SuppressWarnings("unchecked")
        void clock(java.util.function.LongSupplier clock) throws Exception {
            var field = FloatingMenuService.class.getDeclaredField("inputGates"); field.setAccessible(true);
            ((java.util.Map<UUID,MenuInputGate>)field.get(service)).put(player.getUniqueId(),new MenuInputGate(clock));
        }
        Object foreground(String name) throws Exception {
            var sessions = FloatingMenuService.class.getDeclaredField("sessions"); sessions.setAccessible(true);
            var session = ((java.util.Map<?,?>)sessions.get(service)).get(player.getUniqueId());
            var field = session.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(session);
        }
        void closeMocks() { settings.close(); status.close(); sounds.close(); renderer.close(); entityIds.close(); bukkit.close(); }
        @Override public void close() {
            try { service.closeAllImmediately(); }
            finally { closeMocks(); }
        }
    }
}
