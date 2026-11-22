package org.encinet.mik.feature.community.ai.turn;

import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

final class AiFollowUps {
    private static final Duration LIFETIME = Duration.ofMinutes(2);
    private static final ClickCallback.Options CALLBACK = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(LIFETIME).build();

    private final Map<UUID, Offer> offers = new HashMap<>();
    private final LongSupplier nanoTime;
    private final BiConsumer<Player, String> ask;

    AiFollowUps(LongSupplier nanoTime, BiConsumer<Player, String> ask) {
        this.nanoTime = nanoTime;
        this.ask = ask;
    }

    ClickEvent<?> click(UUID id, String turnId, String question) {
        return ClickEvent.callback(audience -> {
            if (!(audience instanceof Player actor) || !Bukkit.isPrimaryThread()) return;
            var offer = offers.get(id);
            if (offer == null || offer.owner() != actor || !offer.turnId().equals(turnId)
                    || offer.expired(nanoTime.getAsLong()) || Bukkit.getPlayer(id) != actor) return;
            ask.accept(actor, question);
        }, CALLBACK);
    }

    void offer(Player player, String turnId) {
        offers.put(player.getUniqueId(), new Offer(player, turnId, nanoTime.getAsLong() + LIFETIME.toNanos()));
    }

    void revoke(UUID id) {
        offers.remove(id);
    }

    void quit(Player player) {
        var id = player.getUniqueId();
        var offer = offers.get(id);
        if (offer != null && offer.owner() == player) offers.remove(id);
    }

    void expire(long now) {
        offers.values().removeIf(offer -> offer.expired(now));
    }

    void clear() {
        offers.clear();
    }

    private record Offer(Player owner, String turnId, long deadline) {
        boolean expired(long now) {
            return now - deadline >= 0;
        }
    }
}
