package org.encinet.mik.module.afk;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class AfkDisplayController {

    private static final double DISPLAY_Y_OFFSET = 0.55D;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final MiniMessage SAFE_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()
            ))
            .build();

    private final NamespacedKey ownerKey;
    private final LanguageService languageService;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private final Map<Integer, AfkState> statesByDisplayEntityId = new HashMap<>();
    private int textMetadataIndex = -1;

    AfkDisplayController(JavaPlugin plugin, LanguageService languageService) {
        this.ownerKey = new NamespacedKey(plugin, "afk_display_owner");
        this.languageService = languageService;
        PacketEvents.getAPI().getEventManager().registerListener(new DisplayMetadataListener());
    }

    void update(Player player, AfkState state) {
        TextDisplay display = currentDisplay(player);
        if (display == null) {
            display = spawnDisplay(player);
            displays.put(player.getUniqueId(), display);
        }

        display.text(displayText(player, state));
        statesByDisplayEntityId.put(display.getEntityId(), state);
        sendLocalizedText(display, state);
        display.teleport(displayLocation(player));
    }

    void updateTrackedDisplays(Collection<AfkState> states) {
        for (AfkState state : states) {
            Player player = Bukkit.getPlayer(state.playerId());
            if (player == null || !player.isOnline()) {
                remove(state.playerId());
                continue;
            }
            update(player, state);
        }
    }

    void remove(UUID playerId) {
        TextDisplay display = displays.remove(playerId);
        if (display != null && display.isValid()) {
            statesByDisplayEntityId.remove(display.getEntityId());
            display.remove();
        }
    }

    void removeAll() {
        displays.values().forEach(display -> {
            statesByDisplayEntityId.remove(display.getEntityId());
            if (display.isValid()) {
                display.remove();
            }
        });
        displays.clear();
    }

    void removeOrphans() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (ownerId(display) != null) {
                    display.remove();
                }
            }
        }
    }

    private TextDisplay spawnDisplay(Player player) {
        return player.getWorld().spawn(displayLocation(player), TextDisplay.class, display -> {
            display.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            display.setPersistent(false);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setSilent(true);
            display.setBillboard(Display.Billboard.CENTER);
            display.setViewRange(32.0F);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(96, 0, 0, 0));
            display.setLineWidth(180);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setTeleportDuration(4);
        });
    }

    private TextDisplay currentDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        TextDisplay display = displays.get(playerId);
        if (display != null && display.isValid() && display.getWorld().equals(player.getWorld())) {
            return display;
        }
        remove(playerId);
        return findExistingDisplay(player);
    }

    private TextDisplay findExistingDisplay(Player player) {
        TextDisplay found = null;
        String expectedOwner = player.getUniqueId().toString();
        for (TextDisplay display : player.getWorld().getEntitiesByClass(TextDisplay.class)) {
            if (!expectedOwner.equals(ownerId(display))) {
                continue;
            }
            if (found == null && display.isValid()) {
                found = display;
                displays.put(player.getUniqueId(), display);
                statesByDisplayEntityId.put(display.getEntityId(), statePlaceholder(player));
            } else if (display.isValid()) {
                display.remove();
            }
        }
        return found;
    }

    private String ownerId(TextDisplay display) {
        return display.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    private Location displayLocation(Player player) {
        Location location = player.getLocation();
        location.add(0.0D, player.getHeight() + DISPLAY_Y_OFFSET, 0.0D);
        return location;
    }

    private Component displayText(Player player, AfkState state) {
        if (!state.hasCustomMessage()) {
            return MINI_MESSAGE.deserialize(languageService.t(player, Message.AFK_DISPLAY_TITLE_MM));
        }
        return MINI_MESSAGE.deserialize(
                languageService.t(player, Message.AFK_DISPLAY_WITH_MESSAGE_MM),
                Placeholder.component("message", SAFE_MESSAGE.deserialize(state.message())));
    }

    private void sendLocalizedText(TextDisplay display, AfkState state) {
        if (textMetadataIndex < 0) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(display.getWorld())) {
                continue;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer,
                    new WrapperPlayServerEntityMetadata(display.getEntityId(), List.of(
                            new EntityData<>(textMetadataIndex, EntityDataTypes.ADV_COMPONENT,
                                    displayText(viewer, state))
                    )));
        }
    }

    private AfkState statePlaceholder(Player player) {
        return new AfkState(player.getUniqueId(), null, false, System.currentTimeMillis());
    }

    private class DisplayMetadataListener extends PacketListenerAbstract {

        private DisplayMetadataListener() {
            super(PacketListenerPriority.NORMAL);
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
                return;
            }
            Player viewer = event.getPlayer();
            if (viewer == null) {
                return;
            }

            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            AfkState state = statesByDisplayEntityId.get(packet.getEntityId());
            if (state == null) {
                return;
            }

            boolean changed = false;
            List<EntityData<?>> metadata = packet.getEntityMetadata();
            for (int i = 0; i < metadata.size(); i++) {
                EntityData<?> data = metadata.get(i);
                if (data.getType() == EntityDataTypes.ADV_COMPONENT) {
                    textMetadataIndex = data.getIndex();
                    metadata.set(i, new EntityData<>(data.getIndex(), EntityDataTypes.ADV_COMPONENT,
                            displayText(viewer, state)));
                    changed = true;
                }
            }
            if (changed) {
                packet.setEntityMetadata(metadata);
                event.markForReEncode(true);
            }
        }
    }
}
