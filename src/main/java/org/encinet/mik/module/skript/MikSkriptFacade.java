package org.encinet.mik.module.skript;

import ch.njol.skript.util.Timespan;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkModule;
import org.encinet.mik.module.afk.AfkSource;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.pvp.PvpModule;
import org.encinet.mik.module.pvp.PvpOverrideState;

import java.util.function.Function;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

final class MikSkriptFacade {

    private final JavaPlugin plugin;
    private final Function<Player, String> language;
    private final Function<Player, String> clientVersion;
    private final Function<Player, String> role;
    private final AfkModule afkModule;
    private final PvpModule pvpModule;

    MikSkriptFacade(JavaPlugin plugin,
                    Function<Player, String> language,
                    Function<Player, String> clientVersion,
                    Function<Player, String> role,
                    AfkModule afkModule,
                    PvpModule pvpModule) {
        this.plugin = plugin;
        this.language = language;
        this.clientVersion = clientVersion;
        this.role = role;
        this.afkModule = afkModule;
        this.pvpModule = pvpModule;
    }

    String language(Player player) {
        return onMainThread(() -> language.apply(player));
    }

    String clientVersion(Player player) {
        return onMainThread(() -> clientVersion.apply(player));
    }

    String role(Player player) {
        return onMainThread(() -> role.apply(player));
    }

    boolean afk(Player player) {
        return onMainThread(() -> afkModule != null && afkModule.isAfk(player.getUniqueId()));
    }

    String afkMessage(Player player) {
        return onMainThread(() -> afkState(player).filter(AfkState::hasCustomMessage)
                .map(AfkState::message).orElse(null));
    }

    String afkSource(Player player) {
        return onMainThread(() -> afkState(player).map(AfkState::source)
                .map(AfkSource::id).orElse("none"));
    }

    Timespan afkDuration(Player player) {
        return onMainThread(() -> afkState(player)
                .map(state -> new Timespan(Math.max(0L, System.currentTimeMillis() - state.sinceMillis())))
                .orElse(null));
    }

    boolean pvpEnabled(Player player) {
        return onMainThread(() -> pvpModule != null && pvpModule.isEnabled(player));
    }

    boolean pvpPreference(Player player) {
        return onMainThread(() -> pvpModule != null && pvpModule.preferenceEnabled(player));
    }

    boolean pvpOverridden(Player player) {
        return onMainThread(() -> pvpModule != null && pvpModule.activeOverride(player).isPresent());
    }

    boolean hasPvpOverride(Player player, String owner, String id) {
        return onMainThread(() -> pvpModule != null && pvpModule.hasOverride(player, owner, id));
    }

    Set<String> pvpOverrideIds(Player player, String owner) {
        return onMainThread(() -> pvpModule == null ? Set.of() : pvpModule.overrideIds(player, owner));
    }

    Boolean pvpOverrideValue(Player player) {
        return onMainThread(() -> pvpOverride(player).map(PvpOverrideState::enabled).orElse(null));
    }

    String pvpOverrideId(Player player) {
        return onMainThread(() -> pvpOverride(player).map(PvpOverrideState::id).orElse(null));
    }

    String pvpOverrideOwner(Player player) {
        return onMainThread(() -> pvpOverride(player).map(PvpOverrideState::owner).orElse(null));
    }

    Number pvpOverridePriority(Player player) {
        return onMainThread(() -> pvpOverride(player).map(PvpOverrideState::priority).orElse(null));
    }

    Timespan pvpOverrideRemaining(Player player) {
        return onMainThread(() -> pvpOverride(player).filter(PvpOverrideState::expires)
                .map(state -> new Timespan(Math.max(0L, state.expiresAtMillis() - System.currentTimeMillis())))
                .orElse(null));
    }

    boolean combatTagged(Player player) {
        return onMainThread(() -> pvpModule != null && pvpModule.isCombatTagged(player.getUniqueId()));
    }

    Timespan combatTagRemaining(Player player) {
        return onMainThread(() -> {
            if (pvpModule == null || !pvpModule.isCombatTagged(player.getUniqueId())) {
                return null;
            }
            return new Timespan(Timespan.TimePeriod.SECOND,
                    pvpModule.combatTagRemainingSeconds(player.getUniqueId()));
        });
    }

    void setAfk(Player player, String message, boolean broadcast) {
        onMainThread(() -> afkModule.setAfkFromSkript(player, message, broadcast));
    }

    void clearAfk(Player player, boolean broadcast) {
        onMainThread(() -> afkModule.clearAfkFromSkript(player, broadcast));
    }

    void setPvpPreference(Player player, boolean enabled) {
        onMainThread(() -> pvpModule.setPreference(player, enabled));
    }

    void setPvpOverride(Player player, String owner, String id,
                        boolean enabled, int priority, long durationMillis) {
        onMainThread(() -> pvpModule.setOverride(
                player, owner, id, enabled, priority, durationMillis));
    }

    void clearPvpOverride(Player player, String owner, String id) {
        onMainThread(() -> pvpModule.clearOverride(player, owner, id));
    }

    void clearPvpOverrides(Player player, String owner) {
        onMainThread(() -> pvpModule.clearOverrides(player, owner));
    }

    void clearPvpOverridesOwnedBy(String owner) {
        onMainThread(() -> pvpModule.clearOverridesOwnedBy(owner));
    }

    String normalizePvpOverrideId(String id) {
        return PvpModule.normalizeOverrideId(id);
    }

    private java.util.Optional<AfkState> afkState(Player player) {
        return afkModule == null
                ? java.util.Optional.empty() : afkModule.getState(player.getUniqueId());
    }

    private java.util.Optional<PvpOverrideState> pvpOverride(Player player) {
        return pvpModule == null
                ? java.util.Optional.empty() : pvpModule.activeOverride(player);
    }

    private void onMainThread(Runnable action) {
        onMainThread(() -> {
            action.run();
            return null;
        });
    }

    private <T> T onMainThread(Callable<T> action) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return action.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to access MIK state", e);
            }
        }

        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, action).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while accessing MIK state on the server thread", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to access MIK state on the server thread", e.getCause());
        }
    }
}
