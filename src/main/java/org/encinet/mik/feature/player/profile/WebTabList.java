package org.encinet.mik.feature.player.profile;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import net.kyori.adventure.text.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Viewer-local PlayerInfo entries only; never creates an entity or a server player. */
final class WebTabList {
    record Entry(UUID playerId, Component displayName, List<ProfileProperty> textures, int order) {
        Entry { textures = List.copyOf(textures); }
        UUID profileId() {
            UUID separate = UUID.nameUUIDFromBytes(("mik:web-chat:" + playerId).getBytes(StandardCharsets.UTF_8));
            // Vanilla selects default skins by UUID hash; preserve that choice when no textures are available.
            long correction = Integer.toUnsignedLong(separate.hashCode() ^ playerId.hashCode());
            return new UUID(separate.getMostSignificantBits(), separate.getLeastSignificantBits() ^ correction);
        }
        WrapperPlayServerPlayerInfoUpdate.PlayerInfo playerInfo() {
            UUID id = profileId();
            var skin = textures.stream().map(property -> new TextureProperty(
                    property.getName(), property.getValue(), property.getSignature())).toList();
            // The display name owns visible text. An empty protocol name cannot become a vanilla command target.
            var profile = new UserProfile(id, "", skin);
            var entry = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile);
            entry.setDisplayName(displayName);
            entry.setListed(true);
            entry.setGameMode(GameMode.ADVENTURE);
            entry.setLatency(0);
            entry.setListOrder(order);
            entry.setShowHat(true);
            return entry;
        }
    }

    private static final EnumSet<WrapperPlayServerPlayerInfoUpdate.Action> ADD_ACTIONS = EnumSet.of(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_HAT);
    private final Map<UUID, Map<UUID, Entry>> viewers = new HashMap<>();

    void synchronize(UUID viewer, List<Entry> visible, Consumer<PacketWrapper<?>> send) {
        Map<UUID, Entry> previous = viewers.getOrDefault(viewer, Map.of());
        Map<UUID, Entry> next = new HashMap<>();
        List<UUID> removed = new ArrayList<>();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> added = new ArrayList<>();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> renamed = new ArrayList<>();
        List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> reordered = new ArrayList<>();
        for (Entry entry : visible) {
            UUID id = entry.profileId();
            if (next.put(id, entry) != null) throw new IllegalArgumentException("Duplicate web player");
            Entry before = previous.get(id);
            if (before == null || !before.textures().equals(entry.textures())) {
                if (before != null) removed.add(id);
                added.add(entry.playerInfo());
            } else {
                if (!before.displayName().equals(entry.displayName())) renamed.add(entry.playerInfo());
                if (before.order() != entry.order()) reordered.add(entry.playerInfo());
            }
        }
        previous.keySet().stream().filter(id -> !next.containsKey(id)).forEach(removed::add);
        if (!removed.isEmpty()) send.accept(new WrapperPlayServerPlayerInfoRemove(removed));
        if (!added.isEmpty()) send.accept(new WrapperPlayServerPlayerInfoUpdate(ADD_ACTIONS.clone(), added));
        if (!renamed.isEmpty()) send.accept(new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME, renamed));
        if (!reordered.isEmpty()) send.accept(new WrapperPlayServerPlayerInfoUpdate(
                WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LIST_ORDER, reordered));
        if (next.isEmpty()) viewers.remove(viewer); else viewers.put(viewer, next);
    }

    void forget(UUID viewer) { viewers.remove(viewer); }
    void clear() { viewers.clear(); }
}
