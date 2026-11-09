package org.encinet.mik.feature.player.profile;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.injector.ChannelInjector;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.netty.NettyManager;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.retrooper.packetevents.impl.netty.NettyManagerImpl;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebTabListTest {
    private static PacketEventsAPI<?> previousApi;
    @BeforeAll static void protocol() {
        previousApi = PacketEvents.getAPI();
        PacketEvents.setAPI(new PacketEventsAPI<Object>() {
            @Override public boolean isLoaded() { return false; }
            @Override public boolean isInitialized() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public Object getPlugin() { return null; }
            @Override public ServerManager getServerManager() { return () -> ServerVersion.V_1_21_4; }
            @Override public void init() { throw new UnsupportedOperationException(); }
            @Override public ProtocolManager getProtocolManager() { throw new UnsupportedOperationException(); }
            @Override public PlayerManager getPlayerManager() { throw new UnsupportedOperationException(); }
            @Override public NettyManager getNettyManager() { return new NettyManagerImpl(); }
            @Override public ChannelInjector getInjector() { throw new UnsupportedOperationException(); }
        });
    }
    @AfterAll static void restore() { PacketEvents.setAPI(previousApi); }

    @Test void tabEntryCopiesTheRealSignedSkinAndHatWithoutExposingACommandName() {
        UUID owner = UUID.randomUUID();
        var skin = new ProfileProperty("textures", "original-account-textures", "original-signature");
        Component label = Component.text("[Member] Alice [Web ×2]");
        var entry = new WebTabList.Entry(owner, label, List.of(skin), -1);
        var packets = new ArrayList<PacketWrapper<?>>();
        new WebTabList().synchronize(UUID.randomUUID(), List.of(entry), packets::add);
        var update = assertInstanceOf(WrapperPlayServerPlayerInfoUpdate.class, packets.getFirst());
        var info = update.getEntries().getFirst();
        assertNotEquals(owner, info.getProfileId(), "the real game player's profile must remain untouched");
        assertEquals(entry.profileId(), info.getProfileId());
        assertEquals("", info.getGameProfile().getName());
        assertEquals(label, info.getDisplayName());
        assertTrue(info.isListed());
        assertTrue(info.isShowHat());
        assertNotEquals(GameMode.SPECTATOR, info.getGameMode());
        assertEquals(0, info.getLatency());
        assertNull(info.getChatSession());
        assertFalse(update.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.INITIALIZE_CHAT));
        assertTrue(update.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT));
        var buffer = Unpooled.buffer();
        try {
            update.setBuffer(buffer);
            update.write();
            var decoded = new WrapperPlayServerPlayerInfoUpdate(EnumSet.noneOf(WrapperPlayServerPlayerInfoUpdate.Action.class), List.of());
            decoded.setBuffer(buffer);
            decoded.read();
            assertEquals("", decoded.getEntries().getFirst().getGameProfile().getName());
            assertEquals(label, decoded.getEntries().getFirst().getDisplayName());
            assertEquals(0, decoded.getEntries().getFirst().getLatency());
            assertEquals("original-account-textures", decoded.getEntries().getFirst().getGameProfile().getTextureProperties().getFirst().getValue());
            assertEquals("original-signature", decoded.getEntries().getFirst().getGameProfile().getTextureProperties().getFirst().getSignature());
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
        var actual = info.getGameProfile().getTextureProperties().getFirst();
        assertEquals(skin.getName(), actual.getName());
        assertEquals(skin.getValue(), actual.getValue());
        assertEquals(skin.getSignature(), actual.getSignature());
        for (String command : List.of("/tp ", "/tp Al", "/tp web_")) {
            var suggestions = new SuggestionsBuilder(command, 4);
            String profileName = info.getGameProfile().getName();
            if (profileName.startsWith(suggestions.getRemaining())) suggestions.suggest(profileName);
            assertTrue(suggestions.build().isEmpty(), "display-only entries must not suggest a player target");
        }
    }

    @Test void accountsUsingAVanillaDefaultSkinKeepTheirOriginalHeadSelection() {
        for (int i = 0; i < 100; i++) {
            UUID original = UUID.randomUUID();
            var entry = new WebTabList.Entry(original, Component.text("Player"), List.of(), -1);
            assertNotEquals(original, entry.profileId());
            assertEquals(entry.profileId(), new WebTabList.Entry(original, Component.empty(), List.of(), -1).profileId());
            assertEquals(original.hashCode(), entry.profileId().hashCode());
            assertEquals(Math.floorMod(original.hashCode(), 18), Math.floorMod(entry.profileId().hashCode(), 18));
        }
    }

    @Test void deviceCountsAndNameTagsUpdateTheSameEntryWithoutResendingItsSkin() {
        var tab = new WebTabList();
        UUID viewer = UUID.randomUUID(), owner = UUID.randomUUID();
        var skin = List.of(new ProfileProperty("textures", "skin", "signature"));
        var first = new WebTabList.Entry(owner, Component.text("Alice [Web]"), skin, -1);
        var frames = new ArrayList<PacketWrapper<?>>();
        tab.synchronize(viewer, List.of(first), frames::add);
        frames.clear();
        tab.synchronize(viewer, List.of(first), frames::add);
        assertTrue(frames.isEmpty());
        var changed = new WebTabList.Entry(owner, Component.text("[VIP] Alice [Web ×3]"), skin, -1);
        tab.synchronize(viewer, List.of(changed), frames::add);
        assertEquals(1, frames.size());
        var update = assertInstanceOf(WrapperPlayServerPlayerInfoUpdate.class, frames.getFirst());
        assertEquals(EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME), update.getActions());
        assertEquals(first.profileId(), update.getEntries().getFirst().getProfileId());
        assertEquals(changed.displayName(), update.getEntries().getFirst().getDisplayName());
    }

    @Test void resolvedOrChangedSkinReplacesOnlyItsDisplayEntry() {
        var tab = new WebTabList();
        UUID viewer = UUID.randomUUID(), owner = UUID.randomUUID();
        var pending = new WebTabList.Entry(owner, Component.text("Alice"), List.of(), -1);
        var other = new WebTabList.Entry(UUID.randomUUID(), Component.text("Bob"), List.of(), -1);
        var frames = new ArrayList<PacketWrapper<?>>();
        tab.synchronize(viewer, List.of(pending, other), frames::add);
        frames.clear();
        var resolved = new WebTabList.Entry(owner, pending.displayName(), List.of(new ProfileProperty("textures", "real skin", "signature")), -1);
        tab.synchronize(viewer, List.of(resolved, other), frames::add);
        assertEquals(2, frames.size());
        var removal = assertInstanceOf(WrapperPlayServerPlayerInfoRemove.class, frames.get(0));
        assertEquals(List.of(pending.profileId()), removal.getProfileIds());
        var added = assertInstanceOf(WrapperPlayServerPlayerInfoUpdate.class, frames.get(1));
        assertEquals(1, added.getEntries().size());
        assertEquals(pending.profileId(), added.getEntries().getFirst().getProfileId());
        assertEquals("real skin", added.getEntries().getFirst().getGameProfile().getTextureProperties().getFirst().getValue());
    }

    @Test void insertingAnEarlierNameUpdatesOrderWithoutRecreatingExistingHeads() {
        var tab = new WebTabList();
        UUID viewer = UUID.randomUUID(), bob = UUID.randomUUID();
        var before = new WebTabList.Entry(bob, Component.text("Bob"), List.of(), -1);
        var frames = new ArrayList<PacketWrapper<?>>();
        tab.synchronize(viewer, List.of(before), frames::add);
        frames.clear();
        var alice = new WebTabList.Entry(UUID.randomUUID(), Component.text("Alice"), List.of(), -1);
        var after = new WebTabList.Entry(bob, before.displayName(), List.of(), -2);
        tab.synchronize(viewer, List.of(alice, after), frames::add);
        assertEquals(2, frames.size());
        var added = (WrapperPlayServerPlayerInfoUpdate) frames.get(0);
        assertEquals(List.of(alice.profileId()), added.getEntries().stream().map(WrapperPlayServerPlayerInfoUpdate.PlayerInfo::getProfileId).toList());
        var order = (WrapperPlayServerPlayerInfoUpdate) frames.get(1);
        assertEquals(EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER), order.getActions());
        assertEquals(before.profileId(), order.getEntries().getFirst().getProfileId());
        assertEquals(-2, order.getEntries().getFirst().getListOrder());
    }

    @Test void visibilityAndDisconnectCleanupAreViewerLocalAndNeverRemoveARealPlayer() {
        var tab = new WebTabList();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), owner = UUID.randomUUID();
        var entry = new WebTabList.Entry(owner, Component.text("Alice"), List.of(), -1);
        var frames = new ArrayList<PacketWrapper<?>>();
        tab.synchronize(first, List.of(entry), frames::add);
        frames.clear();
        tab.synchronize(second, List.of(), frames::add);
        assertTrue(frames.isEmpty());
        tab.synchronize(first, List.of(), frames::add);
        assertEquals(List.of(entry.profileId()), ((WrapperPlayServerPlayerInfoRemove) frames.getFirst()).getProfileIds());
        assertNotEquals(owner, entry.profileId());
        frames.clear();
        tab.synchronize(first, List.of(), frames::add);
        assertTrue(frames.isEmpty());
        tab.synchronize(second, List.of(entry), frames::add);
        tab.forget(second);
        frames.clear();
        tab.synchronize(second, List.of(entry), frames::add);
        assertTrue(((WrapperPlayServerPlayerInfoUpdate) frames.getFirst()).getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER));
    }
}
