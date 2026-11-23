package org.encinet.mik.shell.mikd;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.player.security.GameAccountFacts;
import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.server.ban.BanSeverity;
import org.encinet.mik.feature.player.profile.GameProfileFacts;
import org.encinet.mik.protocol.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class GameQueries {
    @FunctionalInterface
    public interface AssistantTools {
        AssistantToolResult execute(AssistantToolCall call, int remainingMs) throws Exception;
    }

    private final Plugin plugin;
    private final Supplier<List<BanRecord>> activeBans;
    private final BiFunction<UUID, String, GameAccountFacts> accountFacts;
    private final BiPredicate<UUID, String> accountAllowed;
    private final GameProfileFacts profiles;
    private final AssistantTools assistantTools;

    public GameQueries(Plugin plugin, Supplier<List<BanRecord>> activeBans,
               BiFunction<UUID, String, GameAccountFacts> accountFacts, BiPredicate<UUID, String> accountAllowed,
               GameProfileFacts profiles, AssistantTools assistantTools) {
        this.plugin = plugin;
        this.activeBans = activeBans;
        this.accountFacts = accountFacts;
        this.accountAllowed = accountAllowed;
        this.profiles = profiles;
        this.assistantTools = assistantTools;
    }

    public GameQueryReply execute(GameQueryJob job) throws Exception {
        var reply = GameQueryReply.newBuilder().setId(job.getId());
        return switch (job.getOperationCase()) {
            case BANS -> reply.setBans(bans(activeBans.get())).build();
            case RESOLVE_PLAYER -> {
                var found = await(Bukkit.getScheduler().callSyncMethod(plugin, () -> resolve(job.getResolvePlayer())), job);
                yield (found == null ? reply.setNotFound(GameQueryEmpty.getDefaultInstance()) : reply.setPlayer(found)).build();
            }
            case ACCOUNT -> reply.setAccount(account(job.getAccount())).build();
            case GAME_PROFILE -> {
                var query = job.getGameProfile();
                if (!query.getCommunityAuthorized() && !accountFacts.apply(UUID.fromString(query.getViewerUuid()), "").localCustodian()) {
                    yield reply.setGameProfile(GameProfileView.newBuilder().setForbidden(true)).build();
                }
                var profile = await(Bukkit.getScheduler().callSyncMethod(plugin, () -> profiles.profile(query)), job);
                yield (profile == null ? reply.setNotFound(GameQueryEmpty.getDefaultInstance()) : reply.setGameProfile(profile)).build();
            }
            case ASSISTANT_TOOL -> reply.setAssistantTool(
                    assistantTools.execute(job.getAssistantTool(), job.getRemainingMs())).build();
            case OPERATION_NOT_SET -> reply.setFailed(GameQueryEmpty.getDefaultInstance()).build();
        };
    }

    private static <T> T await(Future<T> pending, GameQueryJob job) throws Exception {
        try { return pending.get(job.getRemainingMs(), TimeUnit.MILLISECONDS); }
        finally { if (!pending.isDone()) pending.cancel(false); }
    }

    private static ChatPlayer resolve(String name) {
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return player(online.getUniqueId(), online.getName());
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) return null;
        return player(offline.getUniqueId(), offline.getName() == null ? name : offline.getName());
    }

    private AccountPolicy account(AccountQuery query) {
        UUID id = UUID.fromString(query.getUuid());
        var facts = accountFacts.apply(id, query.getExpectedLoginReference());
        return AccountPolicy.newBuilder().setLocalCustodian(facts.localCustodian()).setCredentialsChangedAt(facts.credentialsChangedAt())
                .setLoginMatches(facts.loginMatches())
                .setBanned(!accountAllowed.test(id, query.getName())).build();
    }

    private static GameBans bans(List<BanRecord> records) {
        var bans = GameBans.newBuilder();
        for (var record : records) {
            var ban = GameBan.newBuilder().setName(record.playerName())
                    .setUuid(record.playerUuid() == null ? "" : record.playerUuid().toString())
                    .setReason(BanSeverity.userReason(record.reason())).setBannedBy(record.source())
                    .setBannedAt(record.createdAt().toString());
            if (record.expiresAt() != null) ban.setExpiresAt(record.expiresAt().toString());
            bans.addBans(ban);
        }
        return bans.build();
    }

    private static ChatPlayer player(UUID id, String name) {
        return ChatPlayer.newBuilder().setId(id.toString()).setName(name).build();
    }

}
