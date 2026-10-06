package org.encinet.mik.module.governance.platform.paper.dashboard;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Main-menu bridge; every database operation runs on the governance storage queue. */
public final class GovernanceDashboardAccess {
    private static final int RECENT_RESULTS = 2;

    private final JavaPlugin plugin;
    private final VotingService voting;
    private final GovernanceTaskExecutor executor;
    private final GovernanceText text;
    private final GovernanceDeliveryCoordinator delivery;

    public GovernanceDashboardAccess(JavaPlugin plugin, VotingService voting,
                                     GovernanceTaskExecutor executor, GovernanceText text,
                                     GovernanceDeliveryCoordinator delivery) {
        this.plugin = plugin;
        this.voting = voting;
        this.executor = executor;
        this.text = text;
        this.delivery = delivery;
    }

    public void load(UUID playerId, Consumer<GovernanceDashboardSnapshot> onLoaded,
                     Runnable onFailure) {
        executor.submit(() -> {
            try {
                delivery.reviewOpenVotes();
                List<GovernanceDashboardSnapshot.OpenVote> cards = new ArrayList<>();
                for (GovernanceVote vote : voting.openVotes()) {
                    boolean voter = voting.isVoter(vote.id(), playerId);
                    VoteChoice ownChoice = voter
                            ? voting.ballotChoice(vote.id(), playerId).orElse(null) : null;
                    cards.add(new GovernanceDashboardSnapshot.OpenVote(
                            vote, voter, ownChoice));
                }
                GovernanceDashboardSnapshot snapshot = new GovernanceDashboardSnapshot(
                        cards, voting.recentVotes(RECENT_RESULTS));
                onMain(() -> onLoaded.accept(snapshot));
            } catch (GovernanceException | RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not load main-menu governance dashboard", error);
                onMain(onFailure);
            }
        });
    }

    public void castBallot(UUID playerId, long voteId, VoteChoice choice,
                           Consumer<BallotOutcome> onComplete) {
        executor.submit(() -> {
            BallotOutcome outcome;
            try {
                voting.castBallot(voteId, playerId, choice);
                outcome = new BallotOutcome(true, Message.GOVERNANCE_BALLOT_RECORDED);
            } catch (GovernanceException error) {
                if (error.getCause() != null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not cast main-menu governance ballot", error);
                }
                outcome = new BallotOutcome(false, error.getCause() != null
                        ? Message.GOVERNANCE_STORAGE_ERROR
                        : error.code() == null ? Message.GOVERNANCE_OPERATION_ERROR
                        : text.error(error.code()));
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE,
                        "Unexpected main-menu governance ballot failure", error);
                outcome = new BallotOutcome(false, Message.GOVERNANCE_OPERATION_ERROR);
            }
            BallotOutcome completed = outcome;
            onMain(() -> onComplete.accept(completed));
        });
    }

    private void onMain(Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
    }

    public record BallotOutcome(boolean recorded, Message feedback) { }
}
