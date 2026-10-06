package org.encinet.mik.module.skript;

import ch.njol.skript.util.Timespan;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkScripting;
import org.encinet.mik.module.afk.AfkSource;
import org.encinet.mik.module.afk.AfkState;
import org.encinet.mik.module.pvp.PvpAccess;
import org.encinet.mik.module.pvp.PvpOverrideState;
import org.encinet.mik.module.space.NonEuclideanSpaceService;
import org.encinet.mik.module.space.SpaceLinkEntrances;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

final class MikSkriptFacade {

    private final JavaPlugin plugin;
    private final Function<Player, String> language;
    private final Function<Player, String> clientVersion;
    private final Function<Player, String> role;
    private final AfkScripting afk;
    private final PvpAccess pvp;
    private final MikSkriptSpaceRegistry spaces;

    MikSkriptFacade(JavaPlugin plugin,
                    Function<Player, String> language,
                    Function<Player, String> clientVersion,
                    Function<Player, String> role,
                    AfkScripting afk,
                    PvpAccess pvp) {
        this(plugin, language, clientVersion, role, afk, pvp, null);
    }

    MikSkriptFacade(JavaPlugin plugin,
                    Function<Player, String> language,
                    Function<Player, String> clientVersion,
                    Function<Player, String> role,
                    AfkScripting afk,
                    PvpAccess pvp,
                    @Nullable NonEuclideanSpaceService spaceService) {
        this.plugin = plugin;
        this.language = language;
        this.clientVersion = clientVersion;
        this.role = role;
        this.afk = afk;
        this.pvp = pvp;
        this.spaces = new MikSkriptSpaceRegistry(spaceService);
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
        return onMainThread(() -> afk != null && afk.isAfk(player.getUniqueId()));
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
        return onMainThread(() -> pvp != null && pvp.isEnabled(player));
    }

    boolean pvpPreference(Player player) {
        return onMainThread(() -> pvp != null && pvp.preferenceEnabled(player));
    }

    boolean pvpOverridden(Player player) {
        return onMainThread(() -> pvp != null && pvp.activeOverride(player).isPresent());
    }

    boolean hasPvpOverride(Player player, String owner, String id) {
        return onMainThread(() -> pvp != null && pvp.hasOverride(player, owner, id));
    }

    Set<String> pvpOverrideIds(Player player, String owner) {
        return onMainThread(() -> pvp == null ? Set.of() : pvp.overrideIds(player, owner));
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
        return onMainThread(() -> pvp != null && pvp.isCombatTagged(player.getUniqueId()));
    }

    Timespan combatTagRemaining(Player player) {
        return onMainThread(() -> {
            if (pvp == null || !pvp.isCombatTagged(player.getUniqueId())) {
                return null;
            }
            return new Timespan(Timespan.TimePeriod.SECOND,
                    pvp.combatTagRemainingSeconds(player.getUniqueId()));
        });
    }

    void setAfk(Player player, String message, boolean broadcast) {
        onMainThread(() -> afk.setAfkFromSkript(player, message, broadcast));
    }

    void clearAfk(Player player, boolean broadcast) {
        onMainThread(() -> afk.clearAfkFromSkript(player, broadcast));
    }

    void setPvpPreference(Player player, boolean enabled) {
        onMainThread(() -> pvp.setPreference(player, enabled));
    }

    void setPvpOverride(Player player, String owner, String id,
                        boolean enabled, int priority, long durationMillis) {
        onMainThread(() -> pvp.setOverride(
                player, owner, id, enabled, priority, durationMillis));
    }

    void clearPvpOverride(Player player, String owner, String id) {
        onMainThread(() -> pvp.clearOverride(player, owner, id));
    }

    void clearPvpOverrides(Player player, String owner) {
        onMainThread(() -> pvp.clearOverrides(player, owner));
    }

    void clearPvpOverridesOwnedBy(String owner) {
        onMainThread(() -> pvp.clearOverridesOwnedBy(owner));
    }

    void registerSpace(
            String owner,
            String id,
            Location firstCornerA,
            Location firstCornerB,
            String firstThrough,
            @Nullable String firstUp,
            Location secondCornerA,
            Location secondCornerB,
            String secondThrough,
            @Nullable String secondUp,
            SpaceLinkEntrances entrances
    ) {
        onMainThread(() -> spaces.register(
                owner,
                id,
                firstCornerA,
                firstCornerB,
                firstThrough,
                firstUp,
                secondCornerA,
                secondCornerB,
                secondThrough,
                secondUp,
                entrances));
    }

    void unregisterSpace(String owner, String id) {
        onMainThread(() -> spaces.unregister(owner, id));
    }

    boolean spaceRegistered(String id) {
        return onMainThread(() -> spaces.registered(id));
    }

    void clearSpacesOwnedBy(String owner) {
        onMainThread(() -> spaces.clearOwnedBy(owner));
    }

    void clearSpaces() {
        onMainThread(spaces::clear);
    }

    private java.util.Optional<AfkState> afkState(Player player) {
        return afk == null
                ? java.util.Optional.empty() : afk.getState(player.getUniqueId());
    }

    private java.util.Optional<PvpOverrideState> pvpOverride(Player player) {
        return pvp == null
                ? java.util.Optional.empty() : pvp.activeOverride(player);
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
