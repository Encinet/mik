package org.encinet.mik.feature.community.governance.gateway;

import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.integration.mikd.RequestPort;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.GovernanceActionRequest;
import org.encinet.mik.protocol.GovernanceCommand;
import org.encinet.mik.protocol.GovernanceDeliveryPoll;
import org.encinet.mik.protocol.GovernanceDeliveryReceipt;
import org.encinet.mik.protocol.GovernanceGameGrant;
import org.encinet.mik.protocol.GovernanceObservations;
import org.encinet.mik.protocol.GovernancePasswordRemoval;
import org.encinet.mik.protocol.GovernanceProfile;
import org.encinet.mik.protocol.GovernanceQuery;
import org.encinet.mik.protocol.GovernanceResult;
import org.encinet.mik.protocol.GovernanceVoteView;
import org.encinet.mik.shared.role.PlayerRole;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public final class GovernanceGateway {
    private final RequestPort requests;

    public GovernanceGateway(RequestPort requests) { this.requests = requests; }

    public static ChatPlayer player(UUID id, String name) {
        return ChatPlayer.newBuilder().setId(id.toString()).setName(name).build();
    }

    public static GovernanceActionRequest.Builder action(GovernanceActionRequest.Action action, UUID actor) {
        var request = GovernanceActionRequest.newBuilder().setAction(action);
        if (actor != null) request.setActorId(actor.toString());
        return request;
    }

    public static GovernanceQuery.Builder view(GovernanceQuery.View view, UUID viewer, UUID subject) {
        var query = GovernanceQuery.newBuilder().setView(view);
        if (viewer != null) query.setViewerId(viewer.toString());
        if (subject != null) query.setPlayerId(subject.toString());
        return query;
    }

    public GovernanceResult query(GovernanceQuery.Builder query) throws GovernanceException {
        return exchange(GovernanceCommand.newBuilder().setQuery(query));
    }

    public GovernanceProfile profile(UUID subject) throws GovernanceException {
        var result = query(view(GovernanceQuery.View.PROFILE, null, subject));
        return result.hasProfile() ? result.getProfile() : null;
    }

    public GovernanceProfile requireProfile(UUID subject) throws GovernanceException {
        return inspectProfile(subject, subject, false);
    }

    public GovernanceProfile inspectProfile(UUID viewer, UUID subject, boolean localOperator) throws GovernanceException {
        return checked(query(view(GovernanceQuery.View.PROFILE, viewer, subject).setLocalOperator(localOperator))).getProfile();
    }

    public List<ChatPlayer> findPlayers(UUID viewer, boolean localOperator, String search) throws GovernanceException {
        return checked(query(view(GovernanceQuery.View.FIND_PLAYERS, viewer, null)
                .setLocalOperator(localOperator).setPlayerSearch(search))).getPlayersList();
    }

    public GovernanceResult matters(UUID viewer, UUID subject) throws GovernanceException {
        return query(view(GovernanceQuery.View.MATTERS, viewer, subject).setLimit(100));
    }

    public GovernanceResult history(UUID viewer, UUID subject, int limit, int offset) throws GovernanceException {
        return query(view(GovernanceQuery.View.HISTORY, viewer, subject).setLimit(limit).setOffset(offset));
    }

    public GovernanceVoteView vote(long id, UUID viewer) throws GovernanceException {
        return checked(query(view(GovernanceQuery.View.VOTE, viewer, null).setItemId(id))).getVotes(0);
    }

    public GovernanceResult act(GovernanceActionRequest.Builder action) throws GovernanceException {
        return checked(exchange(GovernanceCommand.newBuilder().setAct(action)));
    }

    public GovernanceResult observe(GovernanceObservations observations) throws GovernanceException {
        return checked(exchange(GovernanceCommand.newBuilder().setObserve(observations)));
    }

    public CompletableFuture<GovernanceResult> pollDeliveries(Collection<UUID> deferred) {
        var poll = GovernanceDeliveryPoll.newBuilder();
        for (UUID player : deferred) poll.addDeferredPlayers(player.toString());
        return delivery(GovernanceCommand.newBuilder().setPollDeliveries(poll));
    }

    public CompletableFuture<List<GovernanceGameGrant>> grantTargets(UUID player) {
        return delivery(GovernanceCommand.newBuilder().setQuery(view(GovernanceQuery.View.GRANT_TARGETS, null, player)))
                .thenApply(GovernanceResult::getGrantsList);
    }

    public CompletableFuture<GovernanceResult> confirm(GovernanceDeliveryReceipt receipt) {
        return delivery(GovernanceCommand.newBuilder().setConfirm(receipt));
    }

    private CompletableFuture<GovernanceResult> delivery(GovernanceCommand.Builder command) {
        return requests.governance(command.build()).thenApply(result -> {
            try { return checked(result); }
            catch (GovernanceException error) { throw new CompletionException(error); }
        });
    }

    public GovernanceResult.Outcome passwordRemoval(GovernancePasswordRemoval request, boolean finalize)
            throws GovernanceException {
        var command = GovernanceCommand.newBuilder();
        return exchange(finalize ? command.setFinalizePasswordRemoval(request)
                : command.setReservePasswordRemoval(request)).getOutcome();
    }

    public CompletionStage<List<StaffMember>> roster() {
        var command = GovernanceCommand.newBuilder().setQuery(view(GovernanceQuery.View.ROSTER, null, null));
        return requests.governance(command.build()).thenApply(result -> result.getStaffList().stream()
                .collect(Collectors.groupingBy(entry -> entry.getPlayer().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(entries -> {
                    var first = entries.getFirst();
                    var moderator = entries.stream().filter(entry -> !entry.getMaintainer()).findFirst().orElse(null);
                    var maintainer = entries.stream().filter(entry -> entry.getMaintainer()).findFirst().orElse(null);
                    return new StaffMember(UUID.fromString(first.getPlayer().getId()), first.getPlayer().getName(),
                            maintainer != null ? PlayerRole.CUSTODIAN : PlayerRole.MODERATOR, moderator != null,
                            moderator != null && moderator.hasStartedMs() ? Instant.ofEpochMilli(moderator.getStartedMs()) : null,
                            maintainer != null && maintainer.hasStartedMs() ? Instant.ofEpochMilli(maintainer.getStartedMs()) : null,
                            maintainer == null ? "" : maintainer.getResponsibility(), moderator != null && moderator.getGrantPending());
                }).toList());
    }

    private GovernanceResult exchange(GovernanceCommand.Builder command) throws GovernanceException {
        try {
            return requests.governance(command.build()).join();
        } catch (CompletionException error) {
            throw new GovernanceException("mikd governance unavailable", error.getCause());
        }
    }

    private static GovernanceResult checked(GovernanceResult result) throws GovernanceException {
        if (result.getOutcome() == GovernanceResult.Outcome.DONE) return result;
        var code = switch (result.getOutcome()) {
            case UNKNOWN_PLAYER -> GovernanceException.Code.PLAYER_UNKNOWN;
            case PASSWORD_REMOVAL_PENDING, CREDENTIAL_STALE -> GovernanceException.Code.PASSWORD_REQUIRED;
            case PASSWORD_PROTECTED, UNRECOGNIZED, DONE -> null;
            default -> GovernanceException.Code.valueOf(result.getOutcome().name());
        };
        if (code == null) throw new GovernanceException(result.getOutcome().name());
        throw new GovernanceException(code);
    }
}
