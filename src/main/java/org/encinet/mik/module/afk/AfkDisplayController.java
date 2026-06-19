package org.encinet.mik.module.afk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class AfkDisplayController {

    private static final String SCOREBOARD_TAG = "mik_afk_display";
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

    private final Map<UUID, TextDisplay> displays = new HashMap<>();

    void update(Player player, AfkState state) {
        TextDisplay display = displays.get(player.getUniqueId());
        if (display == null || !display.isValid() || !display.getWorld().equals(player.getWorld())) {
            remove(player.getUniqueId());
            display = spawnDisplay(player);
            displays.put(player.getUniqueId(), display);
        }

        display.text(displayText(state));
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
            display.remove();
        }
    }

    void removeAll() {
        displays.values().forEach(display -> {
            if (display.isValid()) {
                display.remove();
            }
        });
        displays.clear();
    }

    void removeOrphans() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (display.getScoreboardTags().contains(SCOREBOARD_TAG)) {
                    display.remove();
                }
            }
        }
    }

    private TextDisplay spawnDisplay(Player player) {
        return player.getWorld().spawn(displayLocation(player), TextDisplay.class, display -> {
            display.addScoreboardTag(SCOREBOARD_TAG);
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

    private Location displayLocation(Player player) {
        Location location = player.getLocation();
        location.add(0.0D, player.getHeight() + DISPLAY_Y_OFFSET, 0.0D);
        return location;
    }

    private Component displayText(AfkState state) {
        if (!state.hasCustomMessage()) {
            return MINI_MESSAGE.deserialize("<gold><bold>挂机中</bold></gold>");
        }
        return MINI_MESSAGE.deserialize(
                "<gold><bold>挂机中</bold></gold> <gray>·</gray> <white><message></white>",
                Placeholder.component("message", SAFE_MESSAGE.deserialize(state.message())));
    }
}
