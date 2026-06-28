package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.time.Duration;

public final class WelcomeModule implements Listener {

    private static final long MESSAGE_DELAY_TICKS = 60L;
    private static final Duration RETURN_THRESHOLD = Duration.ofDays(30);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final FireworkShow[] FIREWORK_SHOW = {
            new FireworkShow(FireworkEffect.Type.BALL_LARGE,
                    Color.fromRGB(255, 210, 64), Color.fromRGB(255, 80, 80), Color.WHITE),
            new FireworkShow(FireworkEffect.Type.STAR,
                    Color.fromRGB(84, 196, 255), Color.fromRGB(180, 105, 255), Color.fromRGB(255, 255, 120)),
            new FireworkShow(FireworkEffect.Type.BURST,
                    Color.fromRGB(80, 255, 170), Color.fromRGB(255, 120, 220), Color.fromRGB(120, 170, 255))
    };

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    public WelcomeModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("WelcomeModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Component message = welcomeMessage(player);
        if (message == null) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(message);
                launchWelcomeFireworks(player);
            }
        }, MESSAGE_DELAY_TICKS);
    }

    private Component welcomeMessage(Player player) {
        if (!player.hasPlayedBefore()) {
            return mm(player, Message.WELCOME_FIRST_JOIN_MM);
        }

        long lastSeen = player.getLastSeen();
        long now = System.currentTimeMillis();
        if (lastSeen <= 0 || now <= lastSeen) {
            return null;
        }

        long daysAway = Duration.ofMillis(now - lastSeen).toDays();
        if (daysAway < RETURN_THRESHOLD.toDays()) {
            return null;
        }
        return mm(player, Message.WELCOME_RETURN_MM, daysAway);
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private void launchWelcomeFireworks(Player player) {
        for (int i = 0; i < FIREWORK_SHOW.length; i++) {
            FireworkShow show = FIREWORK_SHOW[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    launchFirework(player.getLocation(), show);
                }
            }, i * 8L);
        }
    }

    private void launchFirework(Location location, FireworkShow show) {
        Firework firework = location.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(show.type())
                .withColor(show.colors())
                .withFade(Color.WHITE, Color.fromRGB(255, 180, 80))
                .flicker(true)
                .trail(true)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    private record FireworkShow(FireworkEffect.Type type, Color... colors) {
    }
}
