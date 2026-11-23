package org.encinet.mik.feature.entertainment.music.entry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackController;
import org.encinet.mik.feature.entertainment.music.application.MusicProblem;
import org.encinet.mik.feature.entertainment.music.application.MusicRooms;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscKeys;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackOrigin;
import org.encinet.mik.feature.entertainment.music.jukebox.VanillaRecordSilencer;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace;
import org.encinet.mik.shared.util.PaperTestRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MusicDeviceListenerTest {
    private PaperTestRegistries registries;
    @BeforeEach void openRegistries() { registries = new PaperTestRegistries(); }
    @AfterEach void closeRegistries() { registries.close(); }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void aNewVanillaOrCustomDiscReplacesTheOccupiedSlotAndConsumesOnlyTheNewItem(boolean custom) {
        var fixture = new Fixture(custom);
        when(fixture.devices.playInsertedDisc(fixture.player,fixture.box,PlaybackOrigin.PLAYER)).thenAnswer(_ -> {
            assertSame(fixture.replacement,fixture.slot.get());
            return true;
        });
        fixture.listener.interact(fixture.event);
        assertSame(fixture.replacement,fixture.slot.get());
        verify(fixture.inventory).setItemInMainHand(fixture.remaining);
        verify(fixture.event).setCancelled(true);
        verify(fixture.backend,never()).stopAndEject(any());
        verify(fixture.devices,never()).ejectInsertedDisc(any(),any());
        verify(fixture.silencer).release(fixture.block);
    }

    @Test void aRejectedReplacementRestoresTheOriginalDiscAndLeavesThePlayersStackIntact() {
        var fixture = new Fixture(true);
        var error = new MusicProblem(MusicProblem.Code.FORBIDDEN);
        when(fixture.devices.playInsertedDisc(fixture.player,fixture.box,PlaybackOrigin.PLAYER)).thenThrow(error);
        fixture.listener.interact(fixture.event);
        assertSame(fixture.original,fixture.slot.get());
        verify(fixture.inventory,never()).setItemInMainHand(any());
        verify(fixture.devices,never()).recordRemoved(any());
        verify(fixture.workspace).error(fixture.player,error);
        verify(fixture.silencer).release(fixture.block);
    }

    private static final class Fixture {
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final Block block = mock(Block.class);
        final Jukebox box = mock(Jukebox.class);
        final ItemStack original = mock(ItemStack.class), replacement = mock(ItemStack.class),
                requested = mock(ItemStack.class), remaining = mock(ItemStack.class);
        final AtomicReference<ItemStack> slot = new AtomicReference<>(original);
        final MusicPlaybackController devices = mock(MusicPlaybackController.class);
        final JukeboxPlaybackService backend = mock(JukeboxPlaybackService.class);
        final MusicWorkspace workspace = mock(MusicWorkspace.class);
        final VanillaRecordSilencer silencer = mock(VanillaRecordSilencer.class);
        final PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        final MusicDeviceListener listener = new MusicDeviceListener(mock(JavaPlugin.class),devices,backend,workspace,silencer,
                (_,_) -> true,mock(MusicRooms.class));

        Fixture(boolean custom) {
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getPlayer()).thenReturn(player);
            when(event.getItem()).thenReturn(requested);
            when(block.getState()).thenReturn(box);
            when(box.getBlock()).thenReturn(block);
            when(box.getRecord()).thenAnswer(_ -> slot.get());
            when(box.hasRecord()).thenAnswer(_ -> slot.get() != null);
            doAnswer(call -> { slot.set(call.getArgument(0)); return null; }).when(box).setRecord(any());
            var nonAir = mock(Material.class);
            when(original.getType()).thenReturn(nonAir);
            when(requested.getType()).thenReturn(nonAir);
            when(requested.getAmount()).thenReturn(2);
            when(requested.asOne()).thenReturn(replacement);
            when(requested.asQuantity(1)).thenReturn(remaining);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getInventory()).thenReturn(inventory);
            when(backend.activeExperienceMode(block)).thenReturn(Optional.empty());
            if (custom) {
                var meta = mock(ItemMeta.class); var data = mock(PersistentDataContainer.class);
                when(requested.getItemMeta()).thenReturn(meta);
                when(meta.getPersistentDataContainer()).thenReturn(data);
                when(data.get(MusicDiscKeys.TRACK,PersistentDataType.STRING)).thenReturn("custom");
            } else when(requested.hasData(any())).thenReturn(true);
        }
    }
}
