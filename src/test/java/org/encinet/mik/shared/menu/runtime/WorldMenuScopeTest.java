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
                var eye = new Location(world.world(),0,64,2,180,0);
                when(player.getUniqueId()).thenReturn(id); when(player.getWorld()).thenReturn(world.world());
                when(player.getLocation()).thenAnswer(_ -> eye.clone().subtract(0,1.62,0));
                when(player.getEyeLocation()).thenAnswer(_ -> eye.clone());
                when(player.getEyeHeight()).thenReturn(1.62); when(player.getEyeHeight(true)).thenReturn(1.62);
                when(player.isOnline()).thenReturn(true);
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
                var nextId = new AtomicInteger(100); entityIds.when(() -> VirtualMenuEntityRenderer.allocateId(player)).thenAnswer(_ -> nextId.incrementAndGet());
                var plugin = mock(JavaPlugin.class); when(plugin.getDataFolder()).thenReturn(directory); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
                var gizmos = mock(AxiomGizmoService.class); when(gizmos.scope(anyString())).thenReturn(gizmoScope);
                service = new FloatingMenuService(plugin,gizmos,imageService,mock(LanguageService.class),_ -> false);
            } catch (RuntimeException | Error error) { closeMocks(); throw error; }
        }
        WorldMenuSurface surface(double z) { return new WorldMenuSurface(new Location(world.world(),0,64,z),UiRotation.IDENTITY,new FloatingMenuSize(1,1),.25,5); }
        MenuScene scene(AtomicInteger clicks) {
            return MenuScene.screen("test").animation(FloatingMenuAnimation.DEFAULT.reduced())
                    .content(Ui.button(Component.text("Go")).primary((_,_) -> clicks.incrementAndGet()).build()).build();
        }
        PlayerInteractEvent click(Action action,EquipmentSlot hand) { return new PlayerInteractEvent(player,action,null,null,org.bukkit.block.BlockFace.SELF,hand); }
        void tick() throws Exception { var method = FloatingMenuService.class.getDeclaredMethod("tick"); method.setAccessible(true); method.invoke(service); }
        void closeMocks() { settings.close(); status.close(); sounds.close(); renderer.close(); entityIds.close(); bukkit.close(); }
        @Override public void close() {
            try { service.closeAllImmediately(); }
            finally { closeMocks(); }
        }
    }
}
