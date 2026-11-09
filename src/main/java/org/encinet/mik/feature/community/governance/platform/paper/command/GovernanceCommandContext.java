package org.encinet.mik.feature.community.governance.platform.paper.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.shared.i18n.Message;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

/** Thread handoff and Bukkit player-snapshot boundary shared by command handlers. */
final class GovernanceCommandContext {
    private final Plugin plugin;
    private final MembershipService membership;
    private final GovernanceTaskExecutor executor;
    private final GovernanceText text;

    GovernanceCommandContext(
            Plugin plugin,
            MembershipService membership,
            GovernanceTaskExecutor executor,
            GovernanceText text
    ) {
        this.plugin = plugin;
        this.membership = membership;
        this.executor = executor;
        this.text = text;
    }

    void ensureKnown(KnownPlayer player) throws GovernanceException {
        membership.observeKnownPlayer(player.playerId(), player.playerName(),
                player.firstJoinedAt(), player.lastSuccessfulLoginAt(), player.member());
    }

    KnownPlayer snapshot(OfflinePlayer player) {
        String name = player.getName() == null
                ? player.getUniqueId().toString() : player.getName();
        long now = System.currentTimeMillis();
        long first = player.getFirstPlayed() > 0 ? player.getFirstPlayed() : now;
        long last = player.getLastLogin() > 0 ? player.getLastLogin() : first;
        Player online = player.getPlayer();
        boolean member = online != null && RolePermissions.isMember(online);
        return new KnownPlayer(player.getUniqueId(), name,
                Instant.ofEpochMilli(first), Instant.ofEpochMilli(last), member);
    }

    CompletableFuture<Suggestions> suggestPlayers(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }

    Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        text.send(sender, Message.PLAYER_ONLY, NamedTextColor.RED);
        return null;
    }

    void onMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    void submit(CommandSender sender, GovernanceTaskExecutor.Task operation) {
        submit(sender, operation, () -> { });
    }

    <T> CompletableFuture<T> query(java.util.concurrent.Callable<T> read) {
        var result = new CompletableFuture<T>();
        try {
            executor.submit(() -> {
                try { result.complete(read.call()); }
                catch (Exception error) { result.completeExceptionally(error); }
            });
        } catch (RuntimeException error) { result.completeExceptionally(error); }
        return result;
    }

    void submit(CommandSender sender, GovernanceTaskExecutor.Task operation,
                Runnable onFailure) {
        executor.submit(() -> {
            try {
                operation.run();
            } catch (GovernanceException error) {
                if (error.getCause() != null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Governance command failed for " + sender.getName(), error);
                }
                onMain(() -> {
                    if (error.getCause() != null) {
                        text.send(sender, Message.GOVERNANCE_STORAGE_ERROR, NamedTextColor.RED);
                    } else if (error.code() != null) {
                        text.send(sender, text.error(error.code()), NamedTextColor.RED);
                    } else {
                        text.send(sender, Message.GOVERNANCE_OPERATION_ERROR, NamedTextColor.RED);
                    }
                    onFailure.run();
                });
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Unexpected governance operation failure for " + sender.getName(), error);
                onMain(() -> {
                    text.send(sender, Message.GOVERNANCE_OPERATION_ERROR, NamedTextColor.RED);
                    onFailure.run();
                });
            }
        });
    }

    static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof java.util.concurrent.ExecutionException)
                && error.getCause() != null && error.getCause() != error) error = error.getCause();
        return error;
    }

    void reportFailure(CommandSender sender, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof GovernanceException failure && failure.code() != null && failure.getCause() == null) {
            text.send(sender, text.error(failure.code()), NamedTextColor.RED);
        } else {
            plugin.getLogger().log(Level.SEVERE, "Governance operation failed for " + sender.getName(), cause);
            text.send(sender, Message.GOVERNANCE_OPERATION_ERROR, NamedTextColor.RED);
        }
    }

    record KnownPlayer(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            Instant lastSuccessfulLoginAt,
            boolean member
    ) {
        KnownPlayer withMember(boolean currentMember) {
            return new KnownPlayer(playerId, playerName, firstJoinedAt,
                    lastSuccessfulLoginAt, currentMember);
        }
    }
}
