package org.encinet.mik.feature.player.client.tip;

import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.client.ClientMod;
import org.encinet.mik.feature.player.client.ClientMods;
import org.encinet.mik.feature.player.guidance.Tip;
import org.encinet.mik.feature.player.guidance.TipContent;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** Immutable mod-filtered declarations; the supplied tip scope retains ownership and lifecycle. */
public final class ClientModTips {
    private final TipScope tips;
    private final ClientMods mods;
    private final Predicate<UUID> bedrockPlayer;
    private final Predicate<Set<ClientMod>> condition;

    public ClientModTips(TipScope tips, ClientMods mods, Predicate<UUID> bedrockPlayer) {
        this(tips, mods, bedrockPlayer, detected -> true);
    }

    private ClientModTips(TipScope tips, ClientMods mods, Predicate<UUID> bedrockPlayer,
                          Predicate<Set<ClientMod>> condition) {
        this.tips = Objects.requireNonNull(tips, "tips");
        this.mods = Objects.requireNonNull(mods, "mods");
        this.bedrockPlayer = Objects.requireNonNull(bedrockPlayer, "bedrockPlayer");
        this.condition = condition;
    }

    /** Require positive evidence for every listed mod; chaining adds further requirements. */
    public ClientModTips whenDetected(ClientMod first, ClientMod... others) {
        Set<ClientMod> required = EnumSet.of(first, others);
        return where(detected -> detected.containsAll(required));
    }

    /** Require that none of these mods have been detected; unknown connections still do not match. */
    public ClientModTips whenUndetected(ClientMod first, ClientMod... others) {
        Set<ClientMod> excluded = EnumSet.of(first, others);
        return where(detected -> Collections.disjoint(detected, excluded));
    }

    public <C> Tip.Builder<C> define(String id) {
        return tips.<C>define(id).when(this::matches);
    }

    public <C> Tip.Builder<C> define(String id, Message message) {
        return tips.<C>define(id, message).when(this::matches);
    }

    /** Build dynamic content from the latest matching mod snapshot and the delivery language. */
    public <C> Tip.Builder<C> define(String id,
                                    BiFunction<Set<ClientMod>, Language, Optional<TipContent>> content) {
        Objects.requireNonNull(content, "content");
        return this.<C>define(id).resolve((player, context, language) ->
                matchingMods(player).flatMap(detected -> content.apply(detected, language)));
    }

    private ClientModTips where(Predicate<Set<ClientMod>> next) {
        return new ClientModTips(tips, mods, bedrockPlayer, condition.and(next));
    }

    private boolean matches(Player player) {
        return matchingMods(player).isPresent();
    }

    private Optional<Set<ClientMod>> matchingMods(Player player) {
        return bedrockPlayer.test(player.getUniqueId()) ? Optional.empty() : mods.detected(player).filter(condition);
    }
}
